import * as SQLite from "expo-sqlite";
import { INIT_SQL } from "./schema.js";
import type { MenuItem, Order, OrderStatus } from "@railbite/shared";

let dbInstance: any = null;

// Memory storage fallback if SQLite is not available in test/SSR
const memoryStore = {
  menu: new Map<string, any>(),
  orders: new Map<string, any>(),
  pendingActions: new Map<string, any>(),
};

export function getLocalDb() {
  if (dbInstance) return dbInstance;
  try {
    if (SQLite && typeof SQLite.openDatabaseSync === "function") {
      const db = SQLite.openDatabaseSync("railbite_passenger.db");
      db.execSync(INIT_SQL);
      dbInstance = db;
      return dbInstance;
    }
  } catch {
    // In-memory fallback
  }
  return null;
}

export function saveMenuItems(items: MenuItem[]): void {
  const db = getLocalDb();
  if (db) {
    db.runSync("DELETE FROM menu_items");
    for (const item of items) {
      db.runSync(
        `INSERT OR REPLACE INTO menu_items (id, name, category, track, mrp_paise, stock, prep_minutes, active)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
        [item.id, item.name, item.category, item.track, item.mrpPaise, item.stock, item.prepMinutes, item.active ? 1 : 0],
      );
    }
  } else {
    memoryStore.menu.clear();
    for (const item of items) {
      memoryStore.menu.set(item.id, item);
    }
  }
}

export function getCachedMenuItems(): MenuItem[] {
  const db = getLocalDb();
  if (db) {
    const rows = db.getAllSync(
      "SELECT id, name, category, track, mrp_paise as mrpPaise, stock, prep_minutes as prepMinutes, active FROM menu_items WHERE active = 1",
    ) as any[];
    return rows.map((r) => ({ ...r, active: Boolean(r.active) }));
  }
  return Array.from(memoryStore.menu.values()).filter((i) => i.active);
}

export function saveLocalOrder(order: Order, items: any[]): void {
  const db = getLocalDb();
  const itemsJson = JSON.stringify(items);
  if (db) {
    db.runSync(
      `INSERT OR REPLACE INTO orders (
        id, pnr, coach, berth, diners, track, status, payment_mode, total_paise, source, verified, created_at, updated_at, items_json
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
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
        itemsJson,
      ],
    );
  } else {
    memoryStore.orders.set(order.id, { ...order, items });
  }
}

export function updateLocalOrderStatus(id: string, status: OrderStatus, updatedAt: string): void {
  const db = getLocalDb();
  if (db) {
    db.runSync("UPDATE orders SET status = ?, updated_at = ? WHERE id = ?", [status, updatedAt, id]);
  } else {
    const o = memoryStore.orders.get(id);
    if (o) {
      o.status = status;
      o.updatedAt = updatedAt;
    }
  }
}

export function getLocalOrder(id: string): (Order & { items: any[] }) | null {
  const db = getLocalDb();
  if (db) {
    const row = db.getFirstSync("SELECT * FROM orders WHERE id = ?", [id]) as any;
    if (!row) return null;
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
      items: JSON.parse(row.items_json || "[]"),
    };
  }
  return memoryStore.orders.get(id) || null;
}

export function listLocalOrders(): Array<Order & { items: any[] }> {
  const db = getLocalDb();
  if (db) {
    const rows = db.getAllSync("SELECT * FROM orders ORDER BY created_at DESC") as any[];
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
      items: JSON.parse(row.items_json || "[]"),
    }));
  }
  return Array.from(memoryStore.orders.values());
}

export function queueAction(id: string, type: string, payload: any): void {
  const db = getLocalDb();
  const now = new Date().toISOString();
  const payloadStr = JSON.stringify(payload);
  if (db) {
    db.runSync(
      "INSERT OR REPLACE INTO pending_actions (id, type, payload, attempts, next_try_at, created_at) VALUES (?, ?, ?, 0, ?, ?)",
      [id, type, payloadStr, now, now],
    );
  } else {
    memoryStore.pendingActions.set(id, { id, type, payload, attempts: 0, nextTryAt: now, createdAt: now });
  }
}

export function getPendingActions(): Array<{ id: string; type: string; payload: any; attempts: number; nextTryAt: string }> {
  const db = getLocalDb();
  const now = new Date().toISOString();
  if (db) {
    const rows = db.getAllSync(
      "SELECT id, type, payload, attempts, next_try_at as nextTryAt FROM pending_actions WHERE next_try_at <= ? ORDER BY created_at ASC",
      [now],
    ) as any[];
    return rows.map((r) => ({
      id: r.id,
      type: r.type,
      payload: JSON.parse(r.payload),
      attempts: r.attempts,
      nextTryAt: r.nextTryAt,
    }));
  }
  return Array.from(memoryStore.pendingActions.values())
    .filter((a) => a.nextTryAt <= now)
    .map((a) => ({
      id: a.id,
      type: a.type,
      payload: a.payload,
      attempts: a.attempts,
      nextTryAt: a.nextTryAt,
    }));
}

export function updateActionAttempt(id: string, attempts: number, nextTryAt: string): void {
  const db = getLocalDb();
  if (db) {
    db.runSync("UPDATE pending_actions SET attempts = ?, next_try_at = ? WHERE id = ?", [attempts, nextTryAt, id]);
  } else {
    const a = memoryStore.pendingActions.get(id);
    if (a) {
      a.attempts = attempts;
      a.nextTryAt = nextTryAt;
    }
  }
}

export function removeAction(id: string): void {
  const db = getLocalDb();
  if (db) {
    db.runSync("DELETE FROM pending_actions WHERE id = ?", [id]);
  } else {
    memoryStore.pendingActions.delete(id);
  }
}

export function countPendingActions(): number {
  const db = getLocalDb();
  if (db) {
    const row = db.getFirstSync("SELECT COUNT(*) as cnt FROM pending_actions") as any;
    return row?.cnt || 0;
  }
  return memoryStore.pendingActions.size;
}

