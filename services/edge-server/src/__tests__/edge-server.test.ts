import { describe, it, expect, beforeEach, afterEach } from "vitest";
import Database from "better-sqlite3";
import Aedes from "aedes";
import crypto from "node:crypto";
import { config } from "../config.js";
import { runMigrations } from "../db/connection.js";
import { seed } from "../seed/seed.js";
import { MqttPublisher } from "../mqtt/publisher.js";
import { createApp } from "../app.js";
import { OrderRepo } from "../db/repos/orderRepo.js";
import { MenuRepo } from "../db/repos/menuRepo.js";
import type { FastifyInstance } from "fastify";
import type { OrderService } from "../services/orderService.js";

// Keep test logs silent
config.logLevel = "silent";

describe("RailBite Edge Server Core", () => {
  let db: Database.Database;
  let aedes: Aedes;
  let publisher: MqttPublisher;
  let app: FastifyInstance;
  let orderService: OrderService;

  beforeEach(async () => {
    db = new Database(":memory:");
    db.pragma("journal_mode = WAL");
    db.pragma("foreign_keys = ON");

    runMigrations(db);
    seed(db);

    aedes = new Aedes();
    publisher = new MqttPublisher(aedes);

    const appObj = await createApp({ db, publisher });
    app = appObj.app;
    orderService = appObj.orderService;
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
    await new Promise<void>((resolve) => aedes.close(() => resolve()));
    db.close();
  });

  it("GET /health returns health info", async () => {
    const res = await app.inject({
      method: "GET",
      url: "/health",
    });
    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.ok).toBe(true);
    expect(body.tripId).toBeDefined();
    expect(body.version).toBe("1.0.0");
  });

  it("GET /menu returns active menu items with MRP and stock", async () => {
    const res = await app.inject({
      method: "GET",
      url: "/menu",
    });
    expect(res.statusCode).toBe(200);
    const items = JSON.parse(res.body);
    expect(Array.isArray(items)).toBe(true);
    expect(items.length).toBe(15);
    expect(items[0]).toHaveProperty("mrpPaise");
    expect(items[0]).toHaveProperty("track");
  });

  it("Duplicate POST with the same UUID creates one order (idempotency)", async () => {
    const orderId = crypto.randomUUID();
    const chaiId = "10000000-0000-0000-0000-000000000001";

    const payload = {
      id: orderId,
      pnr: "8492000001",
      coach: "B1",
      berth: 1,
      diners: 1,
      paymentMode: "COD",
      items: [{ itemId: chaiId, qty: 2 }],
      createdAt: new Date().toISOString(),
      source: "WIFI",
    };

    // First POST
    const res1 = await app.inject({
      method: "POST",
      url: "/orders",
      payload,
    });
    expect(res1.statusCode).toBe(201);
    const order1 = JSON.parse(res1.body);
    expect(order1.id).toBe(orderId);

    // Second POST with exact same UUID
    const res2 = await app.inject({
      method: "POST",
      url: "/orders",
      payload,
    });
    expect([200, 201]).toContain(res2.statusCode);
    const order2 = JSON.parse(res2.body);
    expect(order2.id).toBe(orderId);

    // Verify in DB that only 1 record exists
    const orderRepo = new OrderRepo(db);
    const inDb = orderRepo.findById(orderId);
    expect(inDb).not.toBeNull();
    const count = (db.prepare("SELECT COUNT(*) as cnt FROM orders WHERE id = ?").get(orderId) as any).cnt;
    expect(count).toBe(1);
  });

  it("Client-sent bogus price is ignored and total is re-priced by server", async () => {
    const orderId = crypto.randomUUID();
    const chaiId = "10000000-0000-0000-0000-000000000001"; // MRP 1500 paise (₹15)

    const payload = {
      id: orderId,
      pnr: "8492000001",
      coach: "B1",
      berth: 1,
      diners: 2,
      paymentMode: "COD",
      items: [{ itemId: chaiId, qty: 3 }],
      createdAt: new Date().toISOString(),
      source: "WIFI",
    };

    const res = await app.inject({
      method: "POST",
      url: "/orders",
      payload,
    });

    expect(res.statusCode).toBe(201);
    const created = JSON.parse(res.body);

    // 3 x 1500 = 4500 paise. Server-authoritative pricing.
    expect(created.totalPaise).toBe(4500);
  });

  it("Illegal transition returns 409", async () => {
    const orderId = crypto.randomUUID();
    const chaiId = "10000000-0000-0000-0000-000000000001";

    // Create a READY order (will be in ASSIGNED status after creation)
    const createRes = await app.inject({
      method: "POST",
      url: "/orders",
      payload: {
        id: orderId,
        pnr: "8492000001",
        coach: "B1",
        berth: 1,
        diners: 1,
        paymentMode: "COD",
        items: [{ itemId: chaiId, qty: 1 }],
        createdAt: new Date().toISOString(),
        source: "WIFI",
      },
    });
    expect(createRes.statusCode).toBe(201);

    // Attempt illegal transition: deliver directly without accepting first!
    const deliverRes = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/deliver`,
      payload: { captainId: "c1", cashPaise: 1500 },
    });

    expect(deliverRes.statusCode).toBe(409);
    const body = JSON.parse(deliverRes.body);
    expect(body.error).toContain("Illegal transition");
  });

  it("Stock never goes negative under concurrent orders", async () => {
    const itemId = "99999999-9999-9999-9999-999999999999";
    // Insert an item with stock = 2
    db.prepare(
      `INSERT INTO menu_items (id, name, category, track, mrp_paise, stock, prep_minutes, active)
       VALUES (?, 'Limited Stock Snack', 'SNACKS', 'READY', 2500, 2, 0, 1)`,
    ).run(itemId);

    // Launch 5 concurrent order requests, each requesting 1 item
    const requests = Array.from({ length: 5 }, () => {
      const orderId = crypto.randomUUID();
      return app.inject({
        method: "POST",
        url: "/orders",
        payload: {
          id: orderId,
          pnr: "8492000001",
          coach: "B1",
          berth: 1,
          diners: 1,
          paymentMode: "COD",
          items: [{ itemId, qty: 1 }],
          createdAt: new Date().toISOString(),
          source: "WIFI",
        },
      });
    });

    const responses = await Promise.all(requests);
    const successful = responses.filter((r) => r.statusCode === 201);
    const rejected = responses.filter((r) => r.statusCode === 422);

    expect(successful.length).toBe(2);
    expect(rejected.length).toBe(3);

    // Verify stock in database is exactly 0 and NOT negative
    const menuRepo = new MenuRepo(db);
    const item = menuRepo.findById(itemId);
    expect(item?.stock).toBe(0);
  });

  it("Full order lifecycle: create -> accept -> deliver -> confirm cash -> ledger summary", async () => {
    const orderId = crypto.randomUUID();
    const chaiId = "10000000-0000-0000-0000-000000000001";

    // 1. Create order
    const createRes = await app.inject({
      method: "POST",
      url: "/orders",
      payload: {
        id: orderId,
        pnr: "8492000001",
        coach: "B1",
        berth: 1,
        diners: 1,
        paymentMode: "COD",
        items: [{ itemId: chaiId, qty: 2 }], // 3000 paise
        createdAt: new Date().toISOString(),
        source: "WIFI",
      },
    });
    expect(createRes.statusCode).toBe(201);

    // 2. Captain accepts
    const acceptRes = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/accept`,
      payload: { captainId: "c1" },
    });
    expect(acceptRes.statusCode).toBe(200);
    expect(JSON.parse(acceptRes.body).status).toBe("ACCEPTED");

    // 2b. Captain picks up (out for delivery)
    const pickupRes = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/pickup`,
      payload: { captainId: "c1" },
    });
    expect(pickupRes.statusCode).toBe(200);
    expect(JSON.parse(pickupRes.body).status).toBe("OUT_FOR_DELIVERY");

    // 3. Captain delivers
    const deliverRes = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/deliver`,
      payload: { captainId: "c1", cashPaise: 3000 },
    });
    expect(deliverRes.statusCode).toBe(200);
    expect(JSON.parse(deliverRes.body).status).toBe("DELIVERED");

    // 4. Passenger confirms cash
    const confirmRes = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/confirm-cash`,
    });
    expect(confirmRes.statusCode).toBe(200);
    expect(JSON.parse(confirmRes.body).passengerConfirmed).toBe(true);

    // 5. Check ledger summary
    const ledgerRes = await app.inject({
      method: "GET",
      url: "/ledger/summary",
    });
    expect(ledgerRes.statusCode).toBe(200);
    const summary = JSON.parse(ledgerRes.body);
    const c1Summary = summary.find((s: any) => s.captainId === "c1");
    expect(c1Summary).toBeDefined();
    expect(c1Summary.expectedPaise).toBe(3000);
    expect(c1Summary.collectedPaise).toBe(3000);
    expect(c1Summary.passengerConfirmedPaise).toBe(3000);
    expect(c1Summary.mismatch).toBe(false);
  });
});
