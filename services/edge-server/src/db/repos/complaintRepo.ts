import type Database from "better-sqlite3";
import type { Complaint } from "@railbite/shared";

export interface ComplaintRow {
  id: string;
  order_id: string;
  tier: 1 | 2;
  type: string;
  text: string | null;
  photo_path: string | null;
  photo_sha256: string | null;
  photo_ts: string | null;
  status: "OPEN" | "REPLACEMENT_SENT" | "RESOLVED" | "ESCALATED";
  due_at: string | null;
  created_at: string;
}

export class ComplaintRepo {
  constructor(private db: Database.Database) {}

  create(complaint: {
    id: string;
    orderId: string;
    tier: 1 | 2;
    type: string;
    text?: string;
    photoPath?: string;
    photoSha256?: string;
    photoTs?: string;
    status: "OPEN" | "REPLACEMENT_SENT" | "RESOLVED" | "ESCALATED";
    dueAt?: string;
    createdAt: string;
  }): void {
    this.db
      .prepare(
        `INSERT INTO complaints (
          id, order_id, tier, type, text, photo_path, photo_sha256, photo_ts, status, due_at, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      )
      .run(
        complaint.id,
        complaint.orderId,
        complaint.tier,
        complaint.type,
        complaint.text || null,
        complaint.photoPath || null,
        complaint.photoSha256 || null,
        complaint.photoTs || null,
        complaint.status,
        complaint.dueAt || null,
        complaint.createdAt,
      );
  }

  countTier2ForPnr(pnr: string): number {
    const row = this.db
      .prepare(
        `SELECT COUNT(*) as cnt 
         FROM complaints c 
         JOIN orders o ON o.id = c.order_id 
         WHERE o.pnr = ? AND c.tier = 2`,
      )
      .get(pnr) as { cnt: number };
    return row.cnt;
  }

  findById(id: string): Complaint | null {
    const row = this.db.prepare("SELECT * FROM complaints WHERE id = ?").get(id) as ComplaintRow | undefined;
    if (!row) return null;
    return {
      id: row.id,
      orderId: row.order_id,
      tier: row.tier,
      type: row.type as any,
      text: row.text || undefined,
      photoPath: row.photo_path || undefined,
      photoSha256: row.photo_sha256 || undefined,
      photoTs: row.photo_ts || undefined,
      status: row.status,
      dueAt: row.due_at || undefined,
      createdAt: row.created_at,
    };
  }

  updateStatus(id: string, status: "OPEN" | "REPLACEMENT_SENT" | "RESOLVED" | "ESCALATED"): void {
    this.db.prepare("UPDATE complaints SET status = ? WHERE id = ?").run(status, id);
  }

  listActive(): Complaint[] {
    const rows = this.db
      .prepare("SELECT * FROM complaints WHERE status != 'RESOLVED' ORDER BY created_at ASC")
      .all() as ComplaintRow[];

    return rows.map((row) => ({
      id: row.id,
      orderId: row.order_id,
      tier: row.tier,
      type: row.type as any,
      text: row.text || undefined,
      photoPath: row.photo_path || undefined,
      photoSha256: row.photo_sha256 || undefined,
      photoTs: row.photo_ts || undefined,
      status: row.status,
      dueAt: row.due_at || undefined,
      createdAt: row.created_at,
    }));
  }
}

