import type { Order, OrderStatus } from "@railbite/shared";
import { toDb, type Db } from "../db.js";
import type Database from "better-sqlite3";

export interface OrderRow {
  id: string;
  train_number: string;
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
  user_id: string | null;
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
  private db: Db;

  constructor(db: Db | Database.Database) {
    this.db = toDb(db);
  }

  async setDeliveryCode(orderId: string, code: string): Promise<void> {
    await this.db.run("UPDATE orders SET delivery_code = ? WHERE id = ?", [code, orderId]);
  }

  async getDeliveryCode(orderId: string): Promise<string | null> {
    const row = await this.db.get<{ delivery_code: string | null }>(
      "SELECT delivery_code FROM orders WHERE id = ?",
      [orderId],
    );
    return row?.delivery_code ?? null;
  }

  async upsertFeedback(
    orderId: string,
    feedback: { received: boolean; rating?: number; reasons?: string[]; now: string },
  ): Promise<void> {
    await this.db.run(
      `INSERT INTO order_feedback (order_id, received, rating, reasons, created_at)
       VALUES (?, ?, ?, ?, ?)
       ON CONFLICT (order_id) DO UPDATE SET
         received = excluded.received,
         rating = excluded.rating,
         reasons = excluded.reasons,
         created_at = excluded.created_at`,
      [
        orderId,
        feedback.received ? 1 : 0,
        feedback.rating ?? null,
        feedback.reasons ? JSON.stringify(feedback.reasons) : null,
        feedback.now,
      ],
    );
  }

  async getFeedback(
    orderId: string,
  ): Promise<{ received: boolean; rating: number | null; reasons: string[] } | null> {
    const row = await this.db.get<{
      received: number;
      rating: number | null;
      reasons: string | null;
    }>("SELECT received, rating, reasons FROM order_feedback WHERE order_id = ?", [orderId]);
    if (!row) return null;
    return {
      received: row.received === 1,
      rating: row.rating ?? null,
      reasons: row.reasons ? JSON.parse(row.reasons) : [],
    };
  }

  async findById(
    id: string,
  ): Promise<
    | (Order & {
        userId?: string;
        items: Array<{ itemId: string; qty: number; unitPricePaise: number; name?: string; category?: string }>;
      })
    | null
  > {
    const row = await this.db.get<OrderRow>("SELECT * FROM orders WHERE id = ?", [id]);
    if (!row) return null;

    const [items, feedback] = await Promise.all([
      this.db.all<{ itemId: string; qty: number; unitPricePaise: number; name?: string; category?: string }>(
        `SELECT oi.item_id as "itemId", oi.qty, oi.unit_price_paise as "unitPricePaise", m.name, m.category
         FROM order_items oi
         LEFT JOIN menu_items m ON m.id = oi.item_id
         WHERE oi.order_id = ?`,
        [id],
      ),
      this.getFeedback(id),
    ]);

    return {
      id: row.id,
      trainNumber: row.train_number || "12345",
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
      userId: row.user_id || undefined,
      feedback: feedback || undefined,
      items,
    };
  }

  /** Lists non-terminal orders actively assigned to a picker (ACCEPTED / PACKING / OUT_FOR_DELIVERY). */
  async findActiveByCaptain(
    captainId: string,
  ): Promise<
    Array<
      Order & {
        items: Array<{ itemId: string; qty: number; unitPricePaise: number; name?: string; category?: string }>;
      }
    >
  > {
    const rows = await this.db.all<OrderRow>(
      `SELECT DISTINCT o.*
       FROM orders o
       JOIN assignments a ON a.order_id = o.id
       WHERE a.captain_id = ?
         AND a.accepted_at IS NOT NULL
         AND a.delivered_at IS NULL
         AND o.status IN ('ACCEPTED', 'PACKING', 'OUT_FOR_DELIVERY')
       ORDER BY o.updated_at DESC`,
      [captainId],
    );

    return Promise.all(
      rows.map(async (row) => {
        const items = await this.db.all<{
          itemId: string;
          qty: number;
          unitPricePaise: number;
          name?: string;
          category?: string;
        }>(
          `SELECT oi.item_id as "itemId", oi.qty, oi.unit_price_paise as "unitPricePaise", m.name, m.category
           FROM order_items oi
           LEFT JOIN menu_items m ON m.id = oi.item_id
           WHERE oi.order_id = ?`,
          [row.id],
        );
        return {
          id: row.id,
          trainNumber: row.train_number || "12345",
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
          items,
        };
      }),
    );
  }

  async findByPnr(pnr: string, userId?: string): Promise<Order[]> {
    const rows = userId
      ? await this.db.all<OrderRow>(
          "SELECT * FROM orders WHERE pnr = ? AND user_id = ? ORDER BY created_at DESC",
          [pnr, userId],
        )
      : await this.db.all<OrderRow>(
          "SELECT * FROM orders WHERE pnr = ? ORDER BY created_at DESC",
          [pnr],
        );

    return rows.map((row) => ({
      id: row.id,
      trainNumber: row.train_number || "12345",
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

  async insertOrder(order: {
    id: string;
    trainNumber: string;
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
    userId?: string | null;
    createdAt: string;
    updatedAt: string;
  }): Promise<boolean> {
    const res = await this.db.run(
      `INSERT INTO orders (
        id, train_number, pnr, coach, berth, diners, track, status, payment_mode, total_paise, source, verified, user_id, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT (id) DO NOTHING`,
      [
        order.id,
        order.trainNumber,
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
        order.userId || null,
        order.createdAt,
        order.updatedAt,
      ],
    );
    return res.changes > 0;
  }

  async insertOrderItems(
    orderId: string,
    items: Array<{ itemId: string; qty: number; unitPricePaise: number }>,
  ): Promise<void> {
    for (const item of items) {
      await this.db.run(
        `INSERT INTO order_items (order_id, item_id, qty, unit_price_paise)
         VALUES (?, ?, ?, ?)
         ON CONFLICT (order_id, item_id) DO NOTHING`,
        [orderId, item.itemId, item.qty, item.unitPricePaise],
      );
    }
  }

  async updateStatus(id: string, status: OrderStatus, updatedAt: string): Promise<boolean> {
    const res = await this.db.run("UPDATE orders SET status = ?, updated_at = ? WHERE id = ?", [
      status,
      updatedAt,
      id,
    ]);
    return res.changes > 0;
  }

  async createAssignment(
    orderId: string,
    captainId: string,
    attempt: number,
    assignedAt: string,
  ): Promise<void> {
    await this.db.run(
      "INSERT INTO assignments (order_id, captain_id, attempt, assigned_at) VALUES (?, ?, ?, ?)",
      [orderId, captainId, attempt, assignedAt],
    );
  }

  async updateAssignmentAccepted(
    orderId: string,
    captainId: string,
    acceptedAt: string,
  ): Promise<void> {
    await this.db.run(
      "UPDATE assignments SET accepted_at = ? WHERE order_id = ? AND captain_id = ? AND accepted_at IS NULL",
      [acceptedAt, orderId, captainId],
    );
  }

  async updateAssignmentRejected(
    orderId: string,
    captainId: string,
    rejectedAt: string,
  ): Promise<void> {
    await this.db.run(
      "UPDATE assignments SET rejected_at = ? WHERE order_id = ? AND captain_id = ? AND accepted_at IS NULL",
      [rejectedAt, orderId, captainId],
    );
  }

  async updateAssignmentDelivered(
    orderId: string,
    captainId: string,
    deliveredAt: string,
  ): Promise<void> {
    await this.db.run(
      "UPDATE assignments SET delivered_at = ? WHERE order_id = ? AND captain_id = ?",
      [deliveredAt, orderId, captainId],
    );
  }

  async upsertAcceptedAssignment(
    orderId: string,
    captainId: string,
    acceptedAt: string,
    deliveredAt: string | null,
  ): Promise<void> {
    const existing = await this.db.get<AssignmentRow>(
      "SELECT * FROM assignments WHERE order_id = ? AND captain_id = ? ORDER BY id DESC LIMIT 1",
      [orderId, captainId],
    );
    if (!existing) {
      await this.db.run(
        "INSERT INTO assignments (order_id, captain_id, attempt, assigned_at, accepted_at, delivered_at) VALUES (?, ?, 1, ?, ?, ?)",
        [orderId, captainId, acceptedAt, acceptedAt, deliveredAt],
      );
      return;
    }
    await this.db.run(
      "UPDATE assignments SET accepted_at = COALESCE(accepted_at, ?), delivered_at = COALESCE(delivered_at, ?) WHERE id = ?",
      [acceptedAt, deliveredAt, existing.id],
    );
  }

  async getLatestAssignment(orderId: string): Promise<AssignmentRow | null> {
    return this.db.get<AssignmentRow>(
      "SELECT * FROM assignments WHERE order_id = ? ORDER BY id DESC LIMIT 1",
      [orderId],
    );
  }

  async getAssignedOrdersForRecovery(): Promise<
    Array<{
      id: string;
      coach: string;
      track: "READY" | "COOK";
      attempt: number;
      assignedAt: string;
      captainId: string;
    }>
  > {
    // Only the LATEST pending assignment per order: re-arming every historical
    // assignment row made recovery re-dispatch N times per order and multiply the
    // assignments table on every server restart (see 1M-row blowup incident).
    return this.db.all(
      `SELECT o.id, o.coach, o.track, a.attempt, a.assigned_at as "assignedAt", a.captain_id as "captainId"
       FROM orders o
       JOIN assignments a ON a.order_id = o.id
       WHERE o.status = 'ASSIGNED' AND a.accepted_at IS NULL AND a.rejected_at IS NULL
         AND a.id = (SELECT MAX(b.id) FROM assignments b WHERE b.order_id = a.order_id)
       ORDER BY a.id DESC`,
    );
  }

  async listKitchenOrders(): Promise<any[]> {
    return this.db.all(
      `SELECT o.id as "orderId", o.train_number as "trainNumber", o.coach, o.berth, o.diners, o.pnr, o.status, o.created_at as "createdAt"
       FROM orders o
       WHERE o.track = 'COOK' AND o.status IN ('PLACED', 'COOKING')
       ORDER BY o.created_at ASC`,
    );
  }
}
