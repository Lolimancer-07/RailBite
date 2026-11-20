import type { Staff } from "@railbite/shared";
import { toDb, type Db } from "../db.js";
import type Database from "better-sqlite3";

export interface StaffRow {
  id: string;
  name: string;
  role: "CAPTAIN" | "COOK";
  zone_coaches: string;
  pin_hash: string;
  device_token: string | null;
  online: number;
}

function mapStaff(row: StaffRow): Staff {
  return {
    id: row.id,
    name: row.name,
    role: row.role,
    zoneCoaches: JSON.parse(row.zone_coaches || "[]"),
    online: Boolean(row.online),
    deviceToken: row.device_token || undefined,
  };
}

export class StaffRepo {
  private db: Db;

  constructor(db: Db | Database.Database) {
    this.db = toDb(db);
  }

  async findById(id: string): Promise<Staff | null> {
    const row = await this.db.get<StaffRow>(
      "SELECT id, name, role, zone_coaches, pin_hash, device_token, online FROM staff WHERE id = ?",
      [id],
    );
    return row ? mapStaff(row) : null;
  }

  /** Legacy PIN lookup (sha256). Kept for old pantry APKs during rollout. */
  async findByPin(staffId: string, pinHash: string): Promise<Staff | null> {
    const row = await this.db.get<StaffRow>(
      "SELECT id, name, role, zone_coaches, pin_hash, device_token, online FROM staff WHERE id = ? AND pin_hash = ?",
      [staffId, pinHash],
    );
    return row ? mapStaff(row) : null;
  }

  async listCaptains(): Promise<Staff[]> {
    const rows = await this.db.all<StaffRow>(
      "SELECT id, name, role, zone_coaches, pin_hash, device_token, online FROM staff WHERE role = 'CAPTAIN'",
    );
    return rows.map(mapStaff);
  }

  async setOnline(id: string, online: boolean): Promise<void> {
    await this.db.run("UPDATE staff SET online = ? WHERE id = ?", [online ? 1 : 0, id]);
  }

  async setDeviceToken(id: string, token: string): Promise<void> {
    await this.db.run("UPDATE staff SET device_token = ? WHERE id = ?", [token, id]);
  }
}
