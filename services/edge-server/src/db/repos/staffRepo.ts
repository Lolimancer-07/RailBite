import { normalizeTrainNumber, type Staff } from "@railbite/shared";
import { config } from "../../config.js";
import { toDb, type Db } from "../db.js";
import type Database from "better-sqlite3";

export interface StaffRow {
  id: string;
  name: string;
  role: "CAPTAIN" | "COOK";
  zone_coaches: string;
  train_number: string;
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
    trainNumber: normalizeTrainNumber(row.train_number || "12345"),
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
      "SELECT id, name, role, zone_coaches, train_number, pin_hash, device_token, online FROM staff WHERE id = ?",
      [id],
    );
    return row ? mapStaff(row) : null;
  }

  /** Legacy PIN lookup (sha256). Kept for old pantry APKs during rollout. */
  async findByPin(staffId: string, pinHash: string): Promise<Staff | null> {
    const row = await this.db.get<StaffRow>(
      "SELECT id, name, role, zone_coaches, train_number, pin_hash, device_token, online FROM staff WHERE id = ? AND pin_hash = ?",
      [staffId, pinHash],
    );
    return row ? mapStaff(row) : null;
  }

  async listCaptains(): Promise<Staff[]> {
    const rows = await this.db.all<StaffRow>(
      "SELECT id, name, role, zone_coaches, train_number, pin_hash, device_token, online FROM staff WHERE role = 'CAPTAIN'",
    );
    return rows.map(mapStaff);
  }

  async listOnlineCaptainsByTrain(trainNumber: string): Promise<Staff[]> {
    const normalized = normalizeTrainNumber(trainNumber);
    const rows = await this.db.all<StaffRow>(
      `SELECT id, name, role, zone_coaches, train_number, pin_hash, device_token, online
       FROM staff
       WHERE role IN ('CAPTAIN', 'PICKER')
         AND online = 1
         AND REPLACE(UPPER(TRIM(train_number)), ' ', '') = ?`,
      [normalized],
    );
    return rows.map(mapStaff);
  }

  async ensureTripExists(trainNumber: string): Promise<void> {
    const normalized = normalizeTrainNumber(trainNumber);
    if (!normalized) return;
    const now = new Date().toISOString();
    await this.db.run(
      `INSERT INTO trips (id, train_no, run_date, hmac_secret, created_at)
       VALUES (?, ?, ?, ?, ?)
       ON CONFLICT (id) DO NOTHING`,
      [normalized, normalized, now.slice(0, 10), config.hmacSecret, now],
    );
  }

  async setOnline(id: string, online: boolean, trainNumber?: string): Promise<string | undefined> {
    if (trainNumber) {
      const normalized = normalizeTrainNumber(trainNumber);
      await this.ensureTripExists(normalized);
      await this.db.run("UPDATE staff SET online = ?, train_number = ? WHERE id = ?", [
        online ? 1 : 0,
        normalized,
        id,
      ]);
      return normalized;
    }
    await this.db.run("UPDATE staff SET online = ? WHERE id = ?", [online ? 1 : 0, id]);
    return undefined;
  }

  async setDeviceToken(id: string, token: string): Promise<void> {
    await this.db.run("UPDATE staff SET device_token = ? WHERE id = ?", [token, id]);
  }
}
