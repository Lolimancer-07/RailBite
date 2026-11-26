import { AsyncLocalStorage } from "node:async_hooks";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import Database from "better-sqlite3";
import pg from "pg";

const { Pool } = pg;

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export type DbDialect = "sqlite" | "postgres";

export interface RunResult {
  changes: number;
  /** SQLite only — Postgres callers must use RETURNING when they need an id. */
  lastInsertId: number | null;
}

/**
 * Minimal async database facade shared by the local/test SQLite driver and the
 * production Postgres driver. Repositories speak this interface only, so every
 * SQL statement has to stay portable (or be built through the helpers below).
 */
export interface Db {
  readonly dialect: DbDialect;
  all<T = any>(sql: string, params?: ReadonlyArray<unknown>): Promise<T[]>;
  get<T = any>(sql: string, params?: ReadonlyArray<unknown>): Promise<T | null>;
  run(sql: string, params?: ReadonlyArray<unknown>): Promise<RunResult>;
  exec(sql: string): Promise<void>;
  transaction<T>(fn: (tx: Db) => Promise<T>): Promise<T>;
  close(): Promise<void>;
}

export function isDb(value: unknown): value is Db {
  return Boolean(value) && typeof (value as Db).all === "function" && typeof (value as Db).run === "function";
}

/**
 * Converts `?` placeholders to Postgres `$1..$n`. The produced SQL is cached by
 * node-postgres per connection, so the small rewrite cost is paid once.
 */
export function toPgParams(sql: string): string {
  let i = 0;
  let out = "";
  let inSingle = false;
  for (let c = 0; c < sql.length; c++) {
    const ch = sql[c];
    if (ch === "'") {
      // Handle escaped quote ('') inside a string literal.
      if (inSingle && sql[c + 1] === "'") {
        out += "''";
        c++;
        continue;
      }
      inSingle = !inSingle;
      out += ch;
      continue;
    }
    out += ch === "?" && !inSingle ? `$${++i}` : ch;
  }
  return out;
}

class SqliteDb implements Db {
  readonly dialect = "sqlite" as const;
  private tail: Promise<unknown> = Promise.resolve();
  private txContext = new AsyncLocalStorage<boolean>();

  constructor(private readonly raw: Database.Database) {}

  /** Serializes access so a multi-statement transaction can never interleave. */
  private exclusive<T>(fn: () => T | Promise<T>): Promise<T> {
    if (this.txContext.getStore()) return Promise.resolve(fn());
    const run = this.tail.then(fn, fn);
    this.tail = run.then(
      () => undefined,
      () => undefined,
    );
    return Promise.resolve(run);
  }

  all<T = any>(sql: string, params: ReadonlyArray<unknown> = []): Promise<T[]> {
    return this.exclusive(() => this.raw.prepare(sql).all(...params) as T[]);
  }

  get<T = any>(sql: string, params: ReadonlyArray<unknown> = []): Promise<T | null> {
    return this.exclusive(
      () => (this.raw.prepare(sql).get(...params) as T | undefined) ?? null,
    );
  }

  run(sql: string, params: ReadonlyArray<unknown> = []): Promise<RunResult> {
    return this.exclusive(() => {
      const res = this.raw.prepare(sql).run(...params);
      return { changes: res.changes, lastInsertId: Number(res.lastInsertRowid) };
    });
  }

  exec(sql: string): Promise<void> {
    return this.exclusive(() => {
      this.raw.exec(sql);
    });
  }

  transaction<T>(fn: (tx: Db) => Promise<T>): Promise<T> {
    return this.exclusive(async () => {
      this.raw.exec("BEGIN IMMEDIATE");
      try {
        const result = await this.txContext.run(true, () => fn(this));
        this.raw.exec("COMMIT");
        return result;
      } catch (err) {
        try {
          this.raw.exec("ROLLBACK");
        } catch {
          // Connection-level failure — the original error is the useful one.
        }
        throw err;
      }
    });
  }

  async close(): Promise<void> {
    this.raw.close();
  }
}

class PgDb implements Db {
  readonly dialect = "postgres" as const;

  constructor(private readonly pool: pg.Pool) {}

  static create(connectionString: string): PgDb {
    const pool = new Pool({ connectionString, max: 10 });
    pool.on("error", (err) => {
      // Idle client errors must not crash the process (Render restarts included).
      console.error("[db] idle Postgres client error:", err.message);
    });
    return new PgDb(pool);
  }

  async all<T = any>(sql: string, params: ReadonlyArray<unknown> = []): Promise<T[]> {
    const res = await this.pool.query(toPgParams(sql), params as unknown[]);
    return res.rows as T[];
  }

  async get<T = any>(sql: string, params: ReadonlyArray<unknown> = []): Promise<T | null> {
    const res = await this.pool.query(toPgParams(sql), params as unknown[]);
    return (res.rows[0] as T | undefined) ?? null;
  }

  async run(sql: string, params: ReadonlyArray<unknown> = []): Promise<RunResult> {
    const res = await this.pool.query(toPgParams(sql), params as unknown[]);
    return { changes: res.rowCount ?? 0, lastInsertId: null };
  }

  async exec(sql: string): Promise<void> {
    await this.pool.query(translateSqliteToPg(sql));
  }

  async transaction<T>(fn: (tx: Db) => Promise<T>): Promise<T> {
    const client = await this.pool.connect();
    const tx = new PgTransaction(client);
    try {
      await client.query("BEGIN");
      const result = await fn(tx);
      await client.query("COMMIT");
      return result;
    } catch (err) {
      try {
        await client.query("ROLLBACK");
      } catch {
        // Original error wins.
      }
      throw err;
    } finally {
      client.release();
    }
  }

  async close(): Promise<void> {
    await this.pool.end();
  }
}

class PgTransaction implements Db {
  readonly dialect = "postgres" as const;

  constructor(private readonly client: pg.PoolClient) {}

  async all<T = any>(sql: string, params: ReadonlyArray<unknown> = []): Promise<T[]> {
    const res = await this.client.query(toPgParams(sql), params as unknown[]);
    return res.rows as T[];
  }

  async get<T = any>(sql: string, params: ReadonlyArray<unknown> = []): Promise<T | null> {
    const res = await this.client.query(toPgParams(sql), params as unknown[]);
    return (res.rows[0] as T | undefined) ?? null;
  }

  async run(sql: string, params: ReadonlyArray<unknown> = []): Promise<RunResult> {
    const res = await this.client.query(toPgParams(sql), params as unknown[]);
    return { changes: res.rowCount ?? 0, lastInsertId: null };
  }

  async exec(sql: string): Promise<void> {
    await this.client.query(translateSqliteToPg(sql));
  }

  async transaction<T>(fn: (tx: Db) => Promise<T>): Promise<T> {
    return fn(this);
  }

  async close(): Promise<void> {
    // Owned by the pool — released by PgDb.transaction.
  }
}

/** Overload so repositories/tests can pass a raw better-sqlite3 handle. */
export function toDb(db: Db | Database.Database): Db {
  return isDb(db) ? db : new SqliteDb(db);
}

let appDb: Db | null = null;

export interface OpenDbOptions {
  databaseUrl?: string | null;
  sqlitePath?: string;
}

/** Opens the production database (Postgres when DATABASE_URL is set, else SQLite). */
export async function openDb(options: OpenDbOptions = {}): Promise<Db> {
  const url = options.databaseUrl ?? process.env.DATABASE_URL;
  if (url && url.trim().length > 0) {
    const db = PgDb.create(url.trim());
    await migrateDb(db);
    return db;
  }

  const sqlitePath = options.sqlitePath ?? process.env.DB_PATH ?? path.resolve(process.cwd(), "railbite.db");
  const dir = path.dirname(sqlitePath);
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  const raw = new Database(sqlitePath);
  raw.pragma("journal_mode = WAL");
  raw.pragma("foreign_keys = ON");
  const db = new SqliteDb(raw);
  await migrateDb(db);
  return db;
}

/** Process-wide database used by the HTTP server. */
export async function getAppDb(): Promise<Db> {
  if (!appDb) appDb = await openDb();
  return appDb;
}

export async function closeAppDb(): Promise<void> {
  if (appDb) {
    await appDb.close();
    appDb = null;
  }
}

function migrationsDir(): string | null {
  let dir = path.resolve(__dirname, "migrations");
  if (!fs.existsSync(dir)) {
    const srcFallback = path.resolve(__dirname, "../../src/db/migrations");
    if (fs.existsSync(srcFallback)) dir = srcFallback;
    else return null;
  }
  return dir;
}

/** SQLite-only syntax translated for Postgres (the migration files stay dialect-neutral). */
export function translateSqliteToPg(sql: string): string {
  return sql
    .replace(/^\s*PRAGMA[^;]*;?\s*$/gim, "")
    .replace(/INTEGER\s+PRIMARY\s+KEY\s+AUTOINCREMENT/gi, "BIGSERIAL PRIMARY KEY")
    .replace(/\bcreated_at\s+INTEGER\s+NOT\s+NULL/gi, "created_at BIGINT NOT NULL");
}

function splitStatements(sql: string): string[] {
  return sql
    .split(/;\s*(?:\r?\n|$)/)
    .map((statement) =>
      statement
        .split("\n")
        .filter((line) => !line.trim().startsWith("--"))
        .join("\n")
        .trim(),
    )
    .filter((statement) => statement.length > 0);
}

function isDuplicateColumnError(err: unknown): boolean {
  const anyErr = err as any;
  return (
    anyErr?.code === "42701" ||
    String(anyErr?.message || "").toLowerCase().includes("duplicate column name") ||
    String(anyErr?.message || "").toLowerCase().includes("column already exists")
  );
}

/**
 * Applies every migration once (tracked in `schema_migrations`). Safe to run on
 * every boot: already-applied files are skipped and ADD COLUMN statements are
 * tolerated the first time an existing SQLite database is adopted.
 */
export async function migrateDb(db: Db): Promise<void> {
  await db.exec(
    "CREATE TABLE IF NOT EXISTS schema_migrations (filename TEXT PRIMARY KEY, applied_at TEXT NOT NULL)",
  );
  const appliedRows = await db.all<{ filename: string }>("SELECT filename FROM schema_migrations");
  const applied = new Set(appliedRows.map((r) => r.filename));

  const dir = migrationsDir();
  if (!dir) return;

  const files = fs
    .readdirSync(dir)
    .filter((f) => f.endsWith(".sql"))
    .sort();

  for (const file of files) {
    if (applied.has(file)) continue;
    const sql = fs.readFileSync(path.join(dir, file), "utf-8");
    for (const statement of splitStatements(sql)) {
      try {
        await db.exec(db.dialect === "postgres" ? translateSqliteToPg(statement) : statement);
      } catch (err) {
        if (isDuplicateColumnError(err)) continue;
        throw new Error(`Migration ${file} failed: ${(err as Error).message}`);
      }
    }
    await db.run(
      "INSERT INTO schema_migrations (filename, applied_at) VALUES (?, ?) ON CONFLICT (filename) DO NOTHING",
      [file, new Date().toISOString()],
    );
  }
}
