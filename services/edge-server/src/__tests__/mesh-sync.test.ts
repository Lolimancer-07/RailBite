import { describe, it, expect, beforeEach, afterEach } from "vitest";
import Database from "better-sqlite3";
import crypto from "node:crypto";
import { config } from "../config.js";
import { runMigrations } from "../db/connection.js";
import { seed } from "../seed/seed.js";
import { createBroker } from "../mqtt/broker.js";
import { MqttPublisher } from "../mqtt/publisher.js";
import { createApp } from "../app.js";
import type { FastifyInstance } from "fastify";

config.logLevel = "silent";

describe("POST /orders/mesh-sync idempotent offline BLE reconciliation", () => {
  let db: Database.Database;
  let broker: ReturnType<typeof createBroker>;
  let publisher: MqttPublisher;
  let app: FastifyInstance;

  const chaiId = "10000000-0000-0000-0000-000000000001";
  const biryaniId = "10000000-0000-0000-0000-000000000008";

  beforeEach(async () => {
    db = new Database(":memory:");
    db.pragma("journal_mode = WAL");
    db.pragma("foreign_keys = ON");
    runMigrations(db);
    await seed(db);

    broker = createBroker();
    publisher = new MqttPublisher(broker.aedes);
    const appObj = await createApp({ db, publisher });
    app = appObj.app;
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
    await broker.close();
    db.close();
  });

  it("syncs a BLE-completed order idempotently without duplicate cash ledger entries", async () => {
    const orderId = "52420000-0000-4000-8000-00001234abcd";
    const createdAt = new Date().toISOString();

    // First Customer sync (PLACED)
    const first = await app.inject({
      method: "POST",
      url: "/orders/mesh-sync",
      payload: {
        id: orderId,
        pnr: "8492000001",
        coach: "B1",
        berth: 4,
        diners: 1,
        paymentMode: "COD",
        items: [
          { itemId: chaiId, qty: 2 },
          { itemId: biryaniId, qty: 1 },
        ],
        createdAt,
        status: "PLACED",
        deliveryCode: "4821",
      },
    });
    expect(first.statusCode).toBe(201);
    const body1 = JSON.parse(first.body);
    expect(body1.totalPaise).toBe(12000);
    expect(body1.status).toBe("PLACED");

    // Second sync by Picker (DELIVERED + cashPaise)
    const second = await app.inject({
      method: "POST",
      url: "/orders/mesh-sync",
      payload: {
        id: orderId,
        pnr: "8492000001",
        coach: "B1",
        berth: 4,
        diners: 1,
        paymentMode: "COD",
        items: [
          { itemId: chaiId, qty: 2 },
          { itemId: biryaniId, qty: 1 },
        ],
        createdAt,
        status: "DELIVERED",
        captainId: "c1",
        deliveryCode: "4821",
        cashPaise: 12000,
      },
    });
    expect(second.statusCode).toBe(200);
    expect(JSON.parse(second.body).status).toBe("DELIVERED");

    // Duplicate retry of DELIVERED sync — must NOT create a second cash_ledger row
    const retry = await app.inject({
      method: "POST",
      url: "/orders/mesh-sync",
      payload: {
        id: orderId,
        pnr: "8492000001",
        coach: "B1",
        berth: 4,
        diners: 1,
        paymentMode: "COD",
        items: [
          { itemId: chaiId, qty: 2 },
          { itemId: biryaniId, qty: 1 },
        ],
        createdAt,
        status: "DELIVERED",
        captainId: "c1",
        deliveryCode: "4821",
        cashPaise: 12000,
      },
    });
    expect(retry.statusCode).toBe(200);

    const ledgerRes = await app.inject({ method: "GET", url: "/staff/c1/ledger" });
    expect(ledgerRes.statusCode).toBe(200);
    const ledger = JSON.parse(ledgerRes.body);
    expect(ledger.entries).toHaveLength(1);
    expect(ledger.summary.collectedPaise).toBe(12000);
  });

  it("ignores stale PLACED sync when server already recorded DELIVERED", async () => {
    const orderId = crypto.randomUUID();
    const createdAt = new Date().toISOString();

    const pickerFirst = await app.inject({
      method: "POST",
      url: "/orders/mesh-sync",
      payload: {
        id: orderId,
        pnr: "8492000002",
        coach: "B1",
        berth: 5,
        diners: 1,
        paymentMode: "COD",
        items: [{ itemId: chaiId, qty: 1 }],
        createdAt,
        status: "DELIVERED",
        captainId: "c1",
        deliveryCode: "9191",
        cashPaise: 1500,
      },
    });
    expect(pickerFirst.statusCode).toBe(201);

    const lateCustomer = await app.inject({
      method: "POST",
      url: "/orders/mesh-sync",
      payload: {
        id: orderId,
        pnr: "8492000002",
        coach: "B1",
        berth: 5,
        diners: 1,
        paymentMode: "COD",
        items: [{ itemId: chaiId, qty: 1 }],
        createdAt,
        status: "PLACED",
        deliveryCode: "9191",
      },
    });
    expect(lateCustomer.statusCode).toBe(200);
    expect(JSON.parse(lateCustomer.body).status).toBe("DELIVERED");
  });
});
