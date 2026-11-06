import Database from "better-sqlite3";

export function initCloudDb(target?: Database.Database | string): Database.Database {
  const db =
    typeof target === "object" && target !== null
      ? target
      : new Database(typeof target === "string" ? target : ":memory:");

  db.pragma("journal_mode = WAL");
  db.pragma("foreign_keys = ON");

  db.exec(`
    CREATE TABLE IF NOT EXISTS trips (
      id TEXT PRIMARY KEY,
      train_no TEXT NOT NULL,
      contractor_name TEXT NOT NULL,
      created_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS synced_outbox (
      trip_id TEXT NOT NULL,
      seq INTEGER NOT NULL,
      entity TEXT NOT NULL,
      payload TEXT NOT NULL,
      created_at TEXT NOT NULL,
      received_at TEXT NOT NULL,
      PRIMARY KEY (trip_id, seq)
    );

    CREATE TABLE IF NOT EXISTS contractor_scores (
      trip_id TEXT PRIMARY KEY,
      score INTEGER NOT NULL DEFAULT 100,
      penalties_json TEXT NOT NULL DEFAULT '[]',
      updated_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS synced_complaints (
      id TEXT PRIMARY KEY,
      trip_id TEXT NOT NULL,
      order_id TEXT NOT NULL,
      type TEXT NOT NULL,
      text TEXT,
      photo_sha256 TEXT,
      photo_path TEXT,
      penalty_points INTEGER NOT NULL,
      created_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS synced_ledger (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      trip_id TEXT NOT NULL,
      order_id TEXT NOT NULL,
      captain_id TEXT NOT NULL,
      amount_paise INTEGER NOT NULL,
      passenger_confirmed INTEGER DEFAULT 0,
      ts TEXT NOT NULL
    );
  `);

  return db;
}
