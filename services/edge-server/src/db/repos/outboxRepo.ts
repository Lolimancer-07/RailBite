import type Database from "better-sqlite3";

export interface OutboxRow {
  seq: number;
  entity: string;
  payload: string;
  created_at: string;
  synced_at: string | null;
}

export class OutboxRepo {
  constructor(private db: Database.Database) {}

  append(entity: string, payload: unknown, createdAt: string = new Date().toISOString()): number {
    const jsonStr = typeof payload === "string" ? payload : JSON.stringify(payload);
    const res = this.db
      .prepare("INSERT INTO outbox (entity, payload, created_at) VALUES (?, ?, ?)")
      .run(entity, jsonStr, createdAt);
    return Number(res.lastInsertRowid);
  }

  getUnsynced(limit: number = 100): Array<{ seq: number; entity: string; payload: any; createdAt: string }> {
    const rows = this.db
      .prepare("SELECT * FROM outbox WHERE synced_at IS NULL ORDER BY seq ASC LIMIT ?")
      .all(limit) as OutboxRow[];

    return rows.map((r) => ({
      seq: r.seq,
      entity: r.entity,
      payload: JSON.parse(r.payload),
      createdAt: r.created_at,
    }));
  }

  markSynced(seqs: number[], syncedAt: string = new Date().toISOString()): void {
    if (seqs.length === 0) return;
    const placeholders = seqs.map(() => "?").join(",");
    this.db
      .prepare(`UPDATE outbox SET synced_at = ? WHERE seq IN (${placeholders})`)
      .run(syncedAt, ...seqs);
  }
}
