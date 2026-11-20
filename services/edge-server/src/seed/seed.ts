import crypto from "node:crypto";
import bcrypt from "bcryptjs";
import type Database from "better-sqlite3";
import { getAppDb, openDb, toDb, type Db } from "../db/db.js";
import { config } from "../config.js";

/**
 * Prototype seed: quick-commerce demo.
 * - 16 instant (READY) products across 5 categories, believable MRPs, plenty of stock.
 * - 4 pickers (c1..c4) + 1 cook. Password: `railbite123` (bcrypt); legacy PIN 1234 kept.
 * - 120-passenger manifest: PNRs 8492000001..8492000120, coaches B1..B4 & S1..S4, berths 1..15.
 *
 * Idempotent on purpose: safe to run on every boot and on a durable Postgres database.
 * Existing stock levels, passwords and orders are never overwritten.
 */
export async function seed(target?: Db | Database.Database | string): Promise<void> {
  const db: Db =
    typeof target === "string"
      ? await openDb({ sqlitePath: target })
      : target
        ? toDb(target)
        : await getAppDb();

  const now = new Date().toISOString();

  // 1. Seed Trip
  await db.run(
    `INSERT INTO trips (id, train_no, run_date, hmac_secret, created_at)
     VALUES (?, ?, ?, ?, ?)
     ON CONFLICT (id) DO NOTHING`,
    [config.tripId, "12951", "2026-11-06", config.hmacSecret, now],
  );

  // 2. Seed Menu Items (16 items: 15 instant READY across 5 categories; isVeg markers FOT-style)
  const menuItems = [
    // BEVERAGES
    { id: "10000000-0000-0000-0000-000000000001", name: "Masala Chai", category: "BEVERAGES", mrpPaise: 1500, stock: 200, isVeg: true },
    { id: "10000000-0000-0000-0000-000000000002", name: "Filter Coffee", category: "BEVERAGES", mrpPaise: 2000, stock: 200, isVeg: true },
    { id: "10000000-0000-0000-0000-000000000011", name: "Cola Can (330ml)", category: "BEVERAGES", mrpPaise: 4000, stock: 120, isVeg: true },
    { id: "10000000-0000-0000-0000-000000000012", name: "Masala Buttermilk", category: "BEVERAGES", mrpPaise: 1500, stock: 100, isVeg: true },
    // WATER
    { id: "10000000-0000-0000-0000-000000000005", name: "Rail Neer Water (1L)", category: "WATER", mrpPaise: 1500, stock: 250, isVeg: true },
    { id: "10000000-0000-0000-0000-000000000013", name: "Packaged Water (500ml)", category: "WATER", mrpPaise: 1000, stock: 250, isVeg: true },
    // SNACKS
    { id: "10000000-0000-0000-0000-000000000003", name: "Parle-G Biscuits", category: "SNACKS", mrpPaise: 1000, stock: 150, isVeg: true },
    { id: "10000000-0000-0000-0000-000000000004", name: "Potato Chips", category: "SNACKS", mrpPaise: 2000, stock: 150, isVeg: true },
    { id: "10000000-0000-0000-0000-000000000006", name: "Samosa (2 pcs)", category: "SNACKS", mrpPaise: 3000, stock: 120, isVeg: true },
    { id: "10000000-0000-0000-0000-000000000010", name: "Veg Puff", category: "SNACKS", mrpPaise: 1500, stock: 120, isVeg: true },
    // MEALS
    { id: "10000000-0000-0000-0000-000000000007", name: "Veg Grilled Sandwich", category: "MEALS", mrpPaise: 4500, stock: 100, isVeg: true },
    { id: "10000000-0000-0000-0000-000000000008", name: "Packed Veg Biryani", category: "MEALS", mrpPaise: 9000, stock: 120, isVeg: true },
    { id: "10000000-0000-0000-0000-000000000009", name: "Cup Noodles", category: "MEALS", mrpPaise: 5500, stock: 80, isVeg: true },
    { id: "10000000-0000-0000-0000-000000000016", name: "Chicken Frankie Roll", category: "MEALS", mrpPaise: 6500, stock: 60, isVeg: false },
    // BREAKFAST
    { id: "10000000-0000-0000-0000-000000000014", name: "Aloo Bonda (2 pcs)", category: "BREAKFAST", mrpPaise: 2500, stock: 80, isVeg: true },
    { id: "10000000-0000-0000-0000-000000000015", name: "Honey Cake Slice", category: "BREAKFAST", mrpPaise: 2000, stock: 80, isVeg: true },
  ];

  for (const item of menuItems) {
    await db.run(
      `INSERT INTO menu_items (id, name, category, track, mrp_paise, stock, prep_minutes, is_veg, active)
       VALUES (?, ?, ?, 'READY', ?, ?, 0, ?, 1)
       ON CONFLICT (id) DO UPDATE SET
         name = excluded.name,
         category = excluded.category,
         track = excluded.track,
         mrp_paise = excluded.mrp_paise,
         is_veg = excluded.is_veg`,
      [item.id, item.name, item.category, item.mrpPaise, item.stock, item.isVeg ? 1 : 0],
    );
  }

  // 3. Seed Staff. Password is bcrypt("railbite123"); legacy PIN 1234 remains a fallback.
  const pinHash = crypto.createHash("sha256").update("1234").digest("hex");
  const passwordHash = bcrypt.hashSync("railbite123", 10);
  const staff = [
    { id: "c1", name: "Ramesh Kumar (Picker B1-B2)", role: "CAPTAIN", zone: ["B1", "B2"] },
    { id: "c2", name: "Suresh Sharma (Picker B3-B4)", role: "CAPTAIN", zone: ["B3", "B4"] },
    { id: "c3", name: "Amit Verma (Picker S1-S2)", role: "CAPTAIN", zone: ["S1", "S2"] },
    { id: "c4", name: "Deepak Patel (Picker S3-S4)", role: "CAPTAIN", zone: ["S3", "S4"] },
    { id: "k1", name: "Chef Rajesh (Head Cook)", role: "COOK", zone: ["PANTRY"] },
  ];

  for (const member of staff) {
    await db.run(
      `INSERT INTO staff (id, name, role, zone_coaches, pin_hash, password_hash, online, active)
       VALUES (?, ?, ?, ?, ?, ?, 1, 1)
       ON CONFLICT (id) DO NOTHING`,
      [member.id, member.name, member.role, JSON.stringify(member.zone), pinHash, passwordHash],
    );
    // Upgrade legacy rows (pre-auth deployments) with the demo password.
    await db.run(
      "UPDATE staff SET password_hash = ? WHERE id = ? AND (password_hash IS NULL OR password_hash = '')",
      [passwordHash, member.id],
    );
  }

  // 4. Seed Manifest (120 fake passengers across B1-B4 and S1-S4)
  const coaches = ["B1", "B2", "B3", "B4", "S1", "S2", "S3", "S4"];
  let pnrCounter = 8492000001;

  for (const coach of coaches) {
    for (let berth = 1; berth <= 15; berth++) {
      const pnr = String(pnrCounter++);
      const nameHash = crypto
        .createHash("sha256")
        .update(`Passenger_${coach}_${berth}`)
        .digest("hex");
      await db.run(
        `INSERT INTO manifest (pnr, coach, berth, name_hash)
         VALUES (?, ?, ?, ?)
         ON CONFLICT (pnr, coach, berth) DO NOTHING`,
        [pnr, coach, berth, nameHash],
      );
    }
  }

  console.log(
    "Seeding complete: 1 Trip, 16 instant Menu Items (5 categories, veg/non-veg marked), " +
      "5 Staff (pickers c1-c4 / password railbite123 / legacy PIN 1234, 1 cook), " +
      "120 Passengers (PNR 8492000001-8492000120).",
  );
}

// Run if called directly
if (process.argv[1]?.endsWith("seed.ts") || process.argv[1]?.endsWith("seed.js")) {
  seed()
    .then(() => process.exit(0))
    .catch((err) => {
      console.error("Seed failed:", err);
      process.exit(1);
    });
}
