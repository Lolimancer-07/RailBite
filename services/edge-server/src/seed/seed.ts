import crypto from "node:crypto";
import type Database from "better-sqlite3";
import { getDb } from "../db/connection.js";
import { config } from "../config.js";

export function seed(target?: Database.Database | string) {
  const db: Database.Database =
    typeof target === "object" && target !== null
      ? target
      : getDb(typeof target === "string" ? target : undefined);

  // 1. Seed Trip
  const tripStmt = db.prepare(
    "INSERT OR REPLACE INTO trips (id, train_no, run_date, hmac_secret, created_at) VALUES (?, ?, ?, ?, ?)",
  );
  const now = new Date().toISOString();
  tripStmt.run(config.tripId, "12951", "2026-11-06", config.hmacSecret, now);

  // 2. Seed Menu Items (15 items: 6 READY, 9 COOK)
  const menuStmt = db.prepare(
    `INSERT OR REPLACE INTO menu_items (id, name, category, track, mrp_paise, stock, prep_minutes, active)
     VALUES (?, ?, ?, ?, ?, ?, ?, 1)`,
  );

  const menuItems = [
    // 6 READY items
    { id: "10000000-0000-0000-0000-000000000001", name: "Masala Chai", category: "BEVERAGE", track: "READY", mrpPaise: 1500, stock: 150, prep: 0 },
    { id: "10000000-0000-0000-0000-000000000002", name: "Filter Coffee", category: "BEVERAGE", track: "READY", mrpPaise: 2000, stock: 100, prep: 0 },
    { id: "10000000-0000-0000-0000-000000000003", name: "Parle-G Biscuits", category: "SNACKS", track: "READY", mrpPaise: 1000, stock: 80, prep: 0 },
    { id: "10000000-0000-0000-0000-000000000004", name: "Potato Chips", category: "SNACKS", track: "READY", mrpPaise: 2000, stock: 60, prep: 0 },
    { id: "10000000-0000-0000-0000-000000000005", name: "Rail Neer Water (1L)", category: "BEVERAGE", track: "READY", mrpPaise: 1500, stock: 250, prep: 0 },
    { id: "10000000-0000-0000-0000-000000000006", name: "Samosa (2 pcs)", category: "SNACKS", track: "READY", mrpPaise: 3000, stock: 75, prep: 0 },
    // 9 COOK items
    { id: "10000000-0000-0000-0000-000000000007", name: "Standard Veg Thali", category: "MEALS", track: "COOK", mrpPaise: 8000, stock: 50, prep: 12 },
    { id: "10000000-0000-0000-0000-000000000008", name: "Non-Veg Chicken Thali", category: "MEALS", track: "COOK", mrpPaise: 13000, stock: 40, prep: 15 },
    { id: "10000000-0000-0000-0000-000000000009", name: "Egg Curry Thali", category: "MEALS", track: "COOK", mrpPaise: 10000, stock: 35, prep: 12 },
    { id: "10000000-0000-0000-0000-000000000010", name: "Aloo Paratha with Curd", category: "BREAKFAST", track: "COOK", mrpPaise: 5000, stock: 45, prep: 10 },
    { id: "10000000-0000-0000-0000-000000000011", name: "Paneer Paratha", category: "BREAKFAST", track: "COOK", mrpPaise: 7000, stock: 40, prep: 10 },
    { id: "10000000-0000-0000-0000-000000000012", name: "Bread Omelette", category: "BREAKFAST", track: "COOK", mrpPaise: 4500, stock: 50, prep: 8 },
    { id: "10000000-0000-0000-0000-000000000013", name: "Plain Omelette", category: "BREAKFAST", track: "COOK", mrpPaise: 3500, stock: 50, prep: 6 },
    { id: "10000000-0000-0000-0000-000000000014", name: "Veg Biryani", category: "MEALS", track: "COOK", mrpPaise: 9000, stock: 40, prep: 15 },
    { id: "10000000-0000-0000-0000-000000000015", name: "Chicken Biryani", category: "MEALS", track: "COOK", mrpPaise: 14000, stock: 35, prep: 15 },
  ];

  for (const item of menuItems) {
    menuStmt.run(item.id, item.name, item.category, item.track, item.mrpPaise, item.stock, item.prep);
  }

  // 3. Seed Staff (4 Captains with zones, 1 Cook)
  const pinHash = crypto.createHash("sha256").update("1234").digest("hex");
  const staffStmt = db.prepare(
    "INSERT OR REPLACE INTO staff (id, name, role, zone_coaches, pin_hash, online) VALUES (?, ?, ?, ?, ?, ?)",
  );

  staffStmt.run("c1", "Ramesh Kumar (Captain B1-B2)", "CAPTAIN", JSON.stringify(["B1", "B2"]), pinHash, 1);
  staffStmt.run("c2", "Suresh Sharma (Captain B3-B4)", "CAPTAIN", JSON.stringify(["B3", "B4"]), pinHash, 1);
  staffStmt.run("c3", "Amit Verma (Captain S1-S2)", "CAPTAIN", JSON.stringify(["S1", "S2"]), pinHash, 1);
  staffStmt.run("c4", "Deepak Patel (Captain S3-S4)", "CAPTAIN", JSON.stringify(["S3", "S4"]), pinHash, 1);
  staffStmt.run("k1", "Chef Rajesh (Head Cook)", "COOK", JSON.stringify(["PANTRY"]), pinHash, 1);

  // 4. Seed Manifest (120 fake passengers across B1-B4 and S1-S4)
  const manifestStmt = db.prepare(
    "INSERT OR REPLACE INTO manifest (pnr, coach, berth, name_hash) VALUES (?, ?, ?, ?)",
  );

  const coaches = ["B1", "B2", "B3", "B4", "S1", "S2", "S3", "S4"];
  let pnrCounter = 8492000001;

  for (const coach of coaches) {
    for (let berth = 1; berth <= 15; berth++) {
      const pnr = String(pnrCounter++);
      const nameHash = crypto.createHash("sha256").update(`Passenger_${coach}_${berth}`).digest("hex");
      manifestStmt.run(pnr, coach, berth, nameHash);
    }
  }

  console.log("Seeding complete: 1 Trip, 15 Menu Items, 5 Staff (4 captains, 1 cook), 120 Passengers.");
}

// Run if called directly
if (process.argv[1]?.endsWith("seed.ts") || process.argv[1]?.endsWith("seed.js")) {
  seed();
}
