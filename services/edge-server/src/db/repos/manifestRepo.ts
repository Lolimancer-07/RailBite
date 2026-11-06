import type Database from "better-sqlite3";

export class ManifestRepo {
  constructor(private db: Database.Database) {}

  checkPassenger(pnr: string, coach: string, berth: number): boolean {
    const row = this.db
      .prepare("SELECT 1 FROM manifest WHERE pnr = ? AND coach = ? AND berth = ?")
      .get(pnr, coach, berth);
    return Boolean(row);
  }
}
