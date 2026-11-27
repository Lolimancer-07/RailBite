import { toDb, type Db } from "../db.js";
import type Database from "better-sqlite3";
import type { ManifestEntry } from "@railbite/shared";

interface ManifestLookupRow {
  pnr: string;
  train_number: string;
  train_name: string;
  coach: string;
  berth: number;
}

export class ManifestRepo {
  private db: Db;

  constructor(db: Db | Database.Database) {
    this.db = toDb(db);
  }

  async checkPassenger(
    pnr: string,
    coach: string,
    berth: number,
    trainNumber?: string,
  ): Promise<boolean> {
    const row = trainNumber
      ? await this.db.get(
          "SELECT 1 as hit FROM manifest WHERE pnr = ? AND coach = ? AND berth = ? AND train_number = ?",
          [pnr, coach, berth, trainNumber],
        )
      : await this.db.get(
          "SELECT 1 as hit FROM manifest WHERE pnr = ? AND coach = ? AND berth = ?",
          [pnr, coach, berth],
        );
    return Boolean(row);
  }

  async findByPnr(pnr: string): Promise<ManifestEntry | null> {
    const row = await this.db.get<ManifestLookupRow>(
      "SELECT pnr, train_number, train_name, coach, berth FROM manifest WHERE pnr = ? LIMIT 1",
      [pnr],
    );
    if (!row) return null;
    return {
      pnr: row.pnr,
      trainNumber: row.train_number,
      trainName: row.train_name,
      coach: row.coach,
      berth: row.berth,
    };
  }
}
