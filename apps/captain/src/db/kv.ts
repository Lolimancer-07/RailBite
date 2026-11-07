import * as SQLite from "expo-sqlite";

const INIT_SQL = `
CREATE TABLE IF NOT EXISTS settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);
`;

let dbInstance: any = null;
const memorySettings = new Map<string, string>();

function getDb() {
  if (dbInstance) return dbInstance;
  try {
    if (SQLite && typeof SQLite.openDatabaseSync === "function") {
      const db = SQLite.openDatabaseSync("railbite_picker.db");
      db.execSync(INIT_SQL);
      dbInstance = db;
      return dbInstance;
    }
  } catch {
    // In-memory fallback
  }
  return null;
}

export function setSetting(key: string, value: string): void {
  const db = getDb();
  if (db) {
    db.runSync("INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)", [key, value]);
  } else {
    memorySettings.set(key, value);
  }
}

export function getSetting(key: string): string | null {
  const db = getDb();
  if (db) {
    const row = db.getFirstSync("SELECT value FROM settings WHERE key = ?", [key]) as any;
    return row?.value ?? null;
  }
  return memorySettings.get(key) ?? null;
}
