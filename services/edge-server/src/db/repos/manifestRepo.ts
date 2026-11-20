import { toDb, type Db } from "../db.js";
import type Database from "better-sqlite3";

export class ManifestRepo {
  private db: Db;

  constructor(db: Db | Database.Database) {
    this.db = toDb(db);
  }

  async checkPassenger(pnr: string, coach: string, berth: number): Promise<boolean> {
    const row = await this.db.get(
      "SELECT 1 as hit FROM manifest WHERE pnr = ? AND coach = ? AND berth = ?",
      [pnr, coach, berth],
    );
    return Boolean(row);
  }
}
