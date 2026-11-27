import type { MenuItem } from "@railbite/shared";
import { toDb, type Db } from "../db.js";
import type Database from "better-sqlite3";

export interface MenuItemRow {
  id: string;
  name: string;
  category: string;
  track: "READY" | "COOK";
  mrp_paise: number;
  stock: number;
  prep_minutes: number;
  active: number;
}

function mapRow(r: any): MenuItem {
  return {
    id: r.id,
    name: r.name,
    category: r.category,
    track: r.track,
    mrpPaise: Number(r.mrpPaise),
    stock: Number(r.stock),
    prepMinutes: Number(r.prepMinutes ?? 0),
    isVeg: r.isVeg === undefined || r.isVeg === null ? true : Boolean(r.isVeg),
    active: Boolean(r.active),
  };
}

export class MenuRepo {
  private db: Db;

  constructor(db: Db | Database.Database) {
    this.db = toDb(db);
  }

  async listActive(): Promise<MenuItem[]> {
    const rows = await this.db.all(
      `SELECT id, name, category, track, mrp_paise as "mrpPaise", stock, prep_minutes as "prepMinutes", is_veg as "isVeg", active FROM menu_items WHERE active = 1`,
    );
    return rows.map(mapRow);
  }

  async listTrending(limit = 6): Promise<(MenuItem & { orderCount: number })[]> {
    const safeLimit = Math.max(1, Math.min(20, Math.floor(limit)));
    const rows = await this.db.all(
      `SELECT m.id, m.name, m.category, m.track, m.mrp_paise as "mrpPaise", m.stock,
              m.prep_minutes as "prepMinutes", m.is_veg as "isVeg", m.active,
              COALESCE(SUM(CASE WHEN o.id IS NOT NULL THEN oi.qty ELSE 0 END), 0) as "orderCount"
       FROM menu_items m
       LEFT JOIN order_items oi ON oi.item_id = m.id
       LEFT JOIN orders o ON o.id = oi.order_id AND o.status != 'CANCELLED'
       WHERE m.active = 1
       GROUP BY m.id, m.name, m.category, m.track, m.mrp_paise, m.stock, m.prep_minutes, m.is_veg, m.active
       ORDER BY "orderCount" DESC, m.id ASC
       LIMIT ?`,
      [safeLimit],
    );
    return rows.map((r: any) => ({
      ...mapRow(r),
      orderCount: Number(r.orderCount ?? 0),
    }));
  }

  async findById(id: string): Promise<MenuItem | null> {
    const row = await this.db.get(
      `SELECT id, name, category, track, mrp_paise as "mrpPaise", stock, prep_minutes as "prepMinutes", is_veg as "isVeg", active FROM menu_items WHERE id = ?`,
      [id],
    );
    return row ? mapRow(row) : null;
  }

  async getMenuMap(): Promise<
    Map<string, { mrpPaise: number; track: "READY" | "COOK"; name: string; stock: number }>
  > {
    const rows = await this.db.all<{
      id: string;
      name: string;
      track: "READY" | "COOK";
      mrpPaise: number;
      stock: number;
    }>(`SELECT id, name, track, mrp_paise as "mrpPaise", stock FROM menu_items WHERE active = 1`);

    const map = new Map<string, { mrpPaise: number; track: "READY" | "COOK"; name: string; stock: number }>();
    for (const r of rows) {
      map.set(r.id, {
        mrpPaise: Number(r.mrpPaise),
        track: r.track,
        name: r.name,
        stock: Number(r.stock),
      });
    }
    return map;
  }

  async decrementStock(itemId: string, qty: number): Promise<boolean> {
    const res = await this.db.run(
      "UPDATE menu_items SET stock = stock - ? WHERE id = ? AND stock >= ?",
      [qty, itemId, qty],
    );
    return res.changes > 0;
  }
}
