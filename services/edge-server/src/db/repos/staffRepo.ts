import type Database from "better-sqlite3";
import type { Staff } from "@railbite/shared";

export interface StaffRow {
  id: string;
  name: string;
  role: "CAPTAIN" | "COOK";
  zone_coaches: string;
  pin_hash: string;
  device_token: string | null;
  online: number;
}

export class StaffRepo {
  constructor(private db: Database.Database) {}

  findById(id: string): Staff | null {
    const row = this.db
      .prepare(
        "SELECT id, name, role, zone_coaches, pin_hash, device_token, online FROM staff WHERE id = ?",
      )
      .get(id) as StaffRow | undefined;

    if (!row) return null;
    return {
      id: row.id,
      name: row.name,
      role: row.role,
      zoneCoaches: JSON.parse(row.zone_coaches || "[]"),
      online: Boolean(row.online),
      deviceToken: row.device_token || undefined,
    };
  }

  findByPin(staffId: string, pinHash: string): Staff | null {
    const row = this.db
      .prepare(
        "SELECT id, name, role, zone_coaches, pin_hash, device_token, online FROM staff WHERE id = ? AND pin_hash = ?",
      )
      .get(staffId, pinHash) as StaffRow | undefined;

    if (!row) return null;
    return {
      id: row.id,
      name: row.name,
      role: row.role,
      zoneCoaches: JSON.parse(row.zone_coaches || "[]"),
      online: Boolean(row.online),
      deviceToken: row.device_token || undefined,
    };
  }

  listCaptains(): Staff[] {
    const rows = this.db
      .prepare(
        "SELECT id, name, role, zone_coaches, pin_hash, device_token, online FROM staff WHERE role = 'CAPTAIN'",
      )
      .all() as StaffRow[];

    return rows.map((row) => ({
      id: row.id,
      name: row.name,
      role: row.role,
      zoneCoaches: JSON.parse(row.zone_coaches || "[]"),
      online: Boolean(row.online),
      deviceToken: row.device_token || undefined,
    }));
  }

  setOnline(id: string, online: boolean): void {
    this.db.prepare("UPDATE staff SET online = ? WHERE id = ?").run(online ? 1 : 0, id);
  }
}

