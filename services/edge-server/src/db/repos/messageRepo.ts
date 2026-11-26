import type { OrderMessage, OrderMessageSenderRole } from "@railbite/shared";
import type Database from "better-sqlite3";
import { toDb, type Db } from "../db.js";

interface MessageRow {
  id: string;
  order_id: string;
  sender_role: OrderMessageSenderRole;
  sender_id: string | null;
  preset_code: string | null;
  body: string;
  created_at: number | string;
}

function toModel(row: MessageRow): OrderMessage {
  return {
    id: row.id,
    orderId: row.order_id,
    senderRole: row.sender_role,
    senderId: row.sender_id,
    presetCode: row.preset_code,
    body: row.body,
    createdAt: Number(row.created_at),
  };
}

export class MessageRepo {
  private db: Db;

  constructor(db: Db | Database.Database) {
    this.db = toDb(db);
  }

  async findById(id: string): Promise<OrderMessage | null> {
    const row = await this.db.get<MessageRow>(
      "SELECT id, order_id, sender_role, sender_id, preset_code, body, created_at FROM order_messages WHERE id = ?",
      [id],
    );
    return row ? toModel(row) : null;
  }

  async listByOrder(orderId: string): Promise<OrderMessage[]> {
    const rows = await this.db.all<MessageRow>(
      `SELECT id, order_id, sender_role, sender_id, preset_code, body, created_at
       FROM order_messages
       WHERE order_id = ?
       ORDER BY created_at ASC, id ASC`,
      [orderId],
    );
    return rows.map(toModel);
  }

  async insertIfAbsent(input: OrderMessage): Promise<{ inserted: boolean; message: OrderMessage }> {
    const res = await this.db.run(
      `INSERT INTO order_messages (id, order_id, sender_role, sender_id, preset_code, body, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT (id) DO NOTHING`,
      [
        input.id,
        input.orderId,
        input.senderRole,
        input.senderId ?? null,
        input.presetCode ?? null,
        input.body,
        input.createdAt,
      ],
    );
    if (res.changes === 0) {
      const existing = await this.findById(input.id);
      if (existing) {
        return { inserted: false, message: existing };
      }
    }
    return { inserted: true, message: input };
  }
}
