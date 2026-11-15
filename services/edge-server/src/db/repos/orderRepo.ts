import type Database from "better-sqlite3";
import type { Order, OrderStatus } from "@railbite/shared";

export interface OrderRow {
  id: string;
  pnr: string;
  coach: string;
  berth: number;
  diners: number;
  track: "READY" | "COOK";
  status: OrderStatus;
  payment_mode: "COD" | "UPI_QUEUED";
  total_paise: number;
  source: "WIFI" | "SMS" | "RELAY";
  verified: number;
  delivery_code: string | null;
  created_at: string;
  updated_at: string;
}

export interface OrderItemRow {
  order_id: string;
  item_id: string;
  qty: number;
  unit_price_paise: number;
  name?: string;
}

export interface AssignmentRow {
  id: number;
  order_id: string;
  captain_id: string;
  attempt: number;
  assigned_at: string;
  accepted_at: string | null;
  delivered_at: string | null;
  rejected_at: string | null;
}

export class OrderRepo {
  constructor(private db: Database.Database) {}

  setDeliveryCode(orderId: string, code: string): void {
    this.db.prepare("UPDATE orders SET delivery_code = ? WHERE id = ?").run(code, orderId);
  }

  getDeliveryCode(orderId: string): string | null {
    const row = this.db.prepare("SELECT delivery_code FROM orders WHERE id = ?").get(orderId) as
      | { delivery_code: string | null }
      | undefined;
    return row?.delivery_code ?? null;
  }

  upsertFeedback(
    orderId: string,
    feedback: { received: boolean; rating?: number; reasons?: string[]; now: string },
  ): void {
    this.db
      .prepare(
        `INSERT OR REPLACE INTO order_feedback (order_id, received, rating, reasons, created_at)
         VALUES (?, ?, ?, ?, ?)`,
      )
      .run(
        orderId,
        feedback.received ? 1 : 0,
        feedback.rating ?? null,
        feedback.reasons ? JSON.stringify(feedback.reasons) : null,
        feedback.now,
      );
  }

  getFeedback(orderId: string): { received: boolean; rating: number | null; reasons: string[] } | null {
    const row = this.db
      .prepare("SELECT received, rating, reasons FROM order_feedback WHERE order_id = ?")
      .get(orderId) as { received: number; rating: number | null; reasons: string | null } | undefined;
    if (!row) return null;
    return {
      received: row.received === 1,
      rating: row.rating ?? null,
      reasons: row.reasons ? JSON.parse(row.reasons) : [],
    };
  }

  findById(id: string): (Order & { items: Array<{ itemId: string; qty: number; unitPricePaise: number; name?: string }> }) | null {
    const row = this.db.prepare("SELECT * FROM orders WHERE id = ?").get(id) as OrderRow | undefined;
    if (!row) return null;

    const items = this.db
      .prepare(
        `SELECT oi.item_id as itemId, oi.qty, oi.unit_price_paise as unitPricePaise, m.name
         FROM order_items oi
         LEFT JOIN menu_items m ON m.id = oi.item_id
         WHERE oi.order_id = ?`,
      )
      .all(id) as Array<{ itemId: string; qty: number; unitPricePaise: number; name?: string }>;

    return {
      id: row.id,
      pnr: row.pnr,
      coach: row.coach,
      berth: row.berth,
      diners: row.diners,
      track: row.track,
      status: row.status,
      paymentMode: row.payment_mode,
      totalPaise: row.total_paise,
      source: row.source,
      verified: row.verified,
      createdAt: row.created_at,
      updatedAt: row.updated_at,
      deliveryCode: row.delivery_code || undefined,
      feedback: this.getFeedback(id) || undefined,
      items,
    };
  }

  findByPnr(pnr: string): Order[] {
    const rows = this.db
      .prepare("SELECT * FROM orders WHERE pnr = ? ORDER BY created_at DESC")
      .all(pnr) as OrderRow[];

    return rows.map((row) => ({
      id: row.id,
      pnr: row.pnr,
      coach: row.coach,
      berth: row.berth,
      diners: row.diners,
      track: row.track,
      status: row.status,
      paymentMode: row.payment_mode,
      totalPaise: row.total_paise,
      source: row.source,
      verified: row.verified,
      createdAt: row.created_at,
      updatedAt: row.updated_at,
    }));
  }

  insertOrder(order: {
    id: string;
    pnr: string;
    coach: string;
    berth: number;
    diners: number;
    track: "READY" | "COOK";
    status: OrderStatus;
    paymentMode: "COD" | "UPI_QUEUED";
    totalPaise: number;
    source: "WIFI" | "SMS" | "RELAY";
    verified: number;
    createdAt: string;
    updatedAt: string;
  }): boolean {
    const stmt = this.db.prepare(
      `INSERT OR IGNORE INTO orders (
        id, pnr, coach, berth, diners, track, status, payment_mode, total_paise, source, verified, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    );

    const res = stmt.run(
      order.id,
      order.pnr,
      order.coach,
      order.berth,
      order.diners,
      order.track,
      order.status,
      order.paymentMode,
      order.totalPaise,
      order.source,
      order.verified,
      order.createdAt,
      order.updatedAt,
    );
    return res.changes > 0;
  }

  insertOrderItems(
    orderId: string,
    items: Array<{ itemId: string; qty: number; unitPricePaise: number }>,
  ): void {
    const stmt = this.db.prepare(
      `INSERT OR IGNORE INTO order_items (order_id, item_id, qty, unit_price_paise)
       VALUES (?, ?, ?, ?)`,
    );

    for (const item of items) {
      stmt.run(orderId, item.itemId, item.qty, item.unitPricePaise);
    }
  }

  updateStatus(id: string, status: OrderStatus, updatedAt: string): boolean {
    const res = this.db
      .prepare("UPDATE orders SET status = ?, updated_at = ? WHERE id = ?")
      .run(status, updatedAt, id);
    return res.changes > 0;
  }

  createAssignment(orderId: string, captainId: string, attempt: number, assignedAt: string): number {
    const res = this.db
      .prepare(
        "INSERT INTO assignments (order_id, captain_id, attempt, assigned_at) VALUES (?, ?, ?, ?)",
      )
      .run(orderId, captainId, attempt, assignedAt);
    return Number(res.lastInsertRowid);
  }

  updateAssignmentAccepted(orderId: string, captainId: string, acceptedAt: string): void {
    this.db
      .prepare(
        "UPDATE assignments SET accepted_at = ? WHERE order_id = ? AND captain_id = ? AND accepted_at IS NULL",
      )
      .run(acceptedAt, orderId, captainId);
  }

  updateAssignmentRejected(orderId: string, captainId: string, rejectedAt: string): void {
    this.db
      .prepare(
        "UPDATE assignments SET rejected_at = ? WHERE order_id = ? AND captain_id = ? AND accepted_at IS NULL",
      )
      .run(rejectedAt, orderId, captainId);
  }

  updateAssignmentDelivered(orderId: string, captainId: string, deliveredAt: string): void {
    this.db
      .prepare(
        "UPDATE assignments SET delivered_at = ? WHERE order_id = ? AND captain_id = ?",
      )
      .run(deliveredAt, orderId, captainId);
  }

  getLatestAssignment(orderId: string): AssignmentRow | null {
    const row = this.db
      .prepare("SELECT * FROM assignments WHERE order_id = ? ORDER BY id DESC LIMIT 1")
      .get(orderId) as AssignmentRow | undefined;
    return row || null;
  }

  getAssignedOrdersForRecovery(): Array<{ id: string; coach: string; track: "READY" | "COOK"; attempt: number; assignedAt: string; captainId: string }> {
    // Only the LATEST pending assignment per order: re-arming every historical
    // assignment row made recovery re-dispatch N times per order and multiply the
    // assignments table on every server restart (see 1M-row blowup incident).
    return this.db
      .prepare(
        `SELECT o.id, o.coach, o.track, a.attempt, a.assigned_at as assignedAt, a.captain_id as captainId
         FROM orders o
         JOIN assignments a ON a.order_id = o.id
         WHERE o.status = 'ASSIGNED' AND a.accepted_at IS NULL AND a.rejected_at IS NULL
           AND a.id = (SELECT MAX(b.id) FROM assignments b WHERE b.order_id = a.order_id)
         ORDER BY a.id DESC`,
      )
      .all() as any[];
  }

  listKitchenOrders(): any[] {
    return this.db
      .prepare(
        `SELECT o.id as orderId, o.coach, o.berth, o.diners, o.pnr, o.status, o.created_at as createdAt
         FROM orders o
         WHERE o.track = 'COOK' AND o.status IN ('PLACED', 'COOKING')
         ORDER BY o.created_at ASC`,
      )
      .all();
  }
}

