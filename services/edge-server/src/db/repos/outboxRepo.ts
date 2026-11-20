import { toDb, type Db } from "../db.js";
import type Database from "better-sqlite3";

export interface OutboxRow {
  seq: number;
  entity: string;
  payload: string;
  created_at: string;
  synced_at: string | null;
}

export class OutboxRepo {
  private db: Db;

  constructor(db: Db | Database.Database) {
    this.db = toDb(db);
  }

  async append(
    entity: string,
    payload: unknown,
    createdAt: string = new Date().toISOString(),
  ): Promise<void> {
    const jsonStr = typeof payload === "string" ? payload : JSON.stringify(payload);
    await this.db.run("INSERT INTO outbox (entity, payload, created_at) VALUES (?, ?, ?)", [
      entity,
      jsonStr,
      createdAt,
    ]);
  }

  async getUnsynced(
    limit: number = 100,
  ): Promise<Array<{ seq: number; entity: string; payload: any; createdAt: string }>> {
    const rows = await this.db.all<OutboxRow>(
      "SELECT * FROM outbox WHERE synced_at IS NULL ORDER BY seq ASC LIMIT ?",
      [limit],
    );

    return rows.map((r) => ({
      seq: Number(r.seq),
      entity: r.entity,
      payload: JSON.parse(r.payload),
      createdAt: r.created_at,
    }));
  }

  async markSynced(seqs: number[], syncedAt: string = new Date().toISOString()): Promise<void> {
    if (seqs.length === 0) return;
    const placeholders = seqs.map(() => "?").join(",");
    await this.db.run(
      `UPDATE outbox SET synced_at = ? WHERE seq IN (${placeholders})`,
      [syncedAt, ...seqs],
    );
  }
}
