import type Database from "better-sqlite3";
import type { MenuItem } from "@railbite/shared";

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

export class MenuRepo {
  constructor(private db: Database.Database) {}

  listActive(): MenuItem[] {
    const rows = this.db
      .prepare(
        "SELECT id, name, category, track, mrp_paise as mrpPaise, stock, prep_minutes as prepMinutes, active FROM menu_items WHERE active = 1",
      )
      .all() as any[];

    return rows.map((r) => ({
      id: r.id,
      name: r.name,
      category: r.category,
      track: r.track,
      mrpPaise: r.mrpPaise,
      stock: r.stock,
      prepMinutes: r.prepMinutes,
      active: Boolean(r.active),
    }));
  }

  findById(id: string): MenuItem | null {
    const row = this.db
      .prepare(
        "SELECT id, name, category, track, mrp_paise as mrpPaise, stock, prep_minutes as prepMinutes, active FROM menu_items WHERE id = ?",
      )
      .get(id) as any;

    if (!row) return null;
    return {
      id: row.id,
      name: row.name,
      category: row.category,
      track: row.track,
      mrpPaise: row.mrpPaise,
      stock: row.stock,
      prepMinutes: row.prepMinutes,
      active: Boolean(row.active),
    };
  }

  getMenuMap(): Map<string, { mrpPaise: number; track: "READY" | "COOK"; name: string; stock: number }> {
    const rows = this.db
      .prepare("SELECT id, name, track, mrp_paise as mrpPaise, stock FROM menu_items WHERE active = 1")
      .all() as any[];

    const map = new Map();
    for (const r of rows) {
      map.set(r.id, {
        mrpPaise: r.mrpPaise,
        track: r.track,
        name: r.name,
        stock: r.stock,
      });
    }
    return map;
  }

  decrementStock(itemId: string, qty: number): boolean {
    const res = this.db
      .prepare("UPDATE menu_items SET stock = stock - ? WHERE id = ? AND stock >= ?")
      .run(qty, itemId, qty);
    return res.changes > 0;
  }
}

