import type { Complaint } from "@railbite/shared";
import { toDb, type Db } from "../db.js";
import type Database from "better-sqlite3";

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

function mapComplaint(row: ComplaintRow): Complaint {
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

export class ComplaintRepo {
  private db: Db;

  constructor(db: Db | Database.Database) {
    this.db = toDb(db);
  }

  async create(complaint: {
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
  }): Promise<void> {
    await this.db.run(
      `INSERT INTO complaints (
        id, order_id, tier, type, text, photo_path, photo_sha256, photo_ts, status, due_at, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
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
      ],
    );
  }

  async countTier2ForPnr(pnr: string): Promise<number> {
    const row = await this.db.get<{ cnt: number | string }>(
      `SELECT COUNT(*) as cnt
       FROM complaints c
       JOIN orders o ON o.id = c.order_id
       WHERE o.pnr = ? AND c.tier = 2`,
      [pnr],
    );
    return Number(row?.cnt || 0);
  }

  async findById(id: string): Promise<Complaint | null> {
    const row = await this.db.get<ComplaintRow>("SELECT * FROM complaints WHERE id = ?", [id]);
    return row ? mapComplaint(row) : null;
  }

  async updateStatus(
    id: string,
    status: "OPEN" | "REPLACEMENT_SENT" | "RESOLVED" | "ESCALATED",
  ): Promise<void> {
    await this.db.run("UPDATE complaints SET status = ? WHERE id = ?", [status, id]);
  }

  async listActive(): Promise<Complaint[]> {
    const rows = await this.db.all<ComplaintRow>(
      "SELECT * FROM complaints WHERE status != 'RESOLVED' ORDER BY created_at ASC",
    );
    return rows.map(mapComplaint);
  }
}
