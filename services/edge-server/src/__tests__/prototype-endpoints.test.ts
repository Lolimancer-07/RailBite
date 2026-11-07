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

describe("Prototype endpoints (PACKING status, picker toggle & stats)", () => {
  let db: Database.Database;
  let broker: ReturnType<typeof createBroker>;
  let publisher: MqttPublisher;
  let app: FastifyInstance;

  beforeEach(async () => {
    db = new Database(":memory:");
    db.pragma("journal_mode = WAL");
    db.pragma("foreign_keys = ON");
    runMigrations(db);
    seed(db);

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

  async function createOrder(status: "ASSIGNED" | "ACCEPTED" | "PACKING") {
    const orderId = crypto.randomUUID();
    const chaiId = "10000000-0000-0000-0000-000000000001";

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

    if (status !== "ASSIGNED") {
      const acceptRes = await app.inject({
        method: "POST",
        url: `/orders/${orderId}/accept`,
        payload: { captainId: "c1" },
      });
      expect(acceptRes.statusCode).toBe(200);
    }

    if (status === "PACKING") {
      const packingRes = await app.inject({
        method: "POST",
        url: `/orders/${orderId}/packing`,
        payload: { captainId: "c1" },
      });
      expect(packingRes.statusCode).toBe(200);
    }

    return orderId;
  }

  it("POST /orders/:id/packing transitions ACCEPTED -> PACKING and matters for customer tracking", async () => {
    const orderId = await createOrder("ACCEPTED");

    const res = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/packing`,
      payload: { captainId: "c1" },
    });

    expect(res.statusCode).toBe(200);
    const order = JSON.parse(res.body);
    expect(order.status).toBe("PACKING");
  });

  it("Packing on a PLACED/ASSIGNED order returns 409 (illegal transition)", async () => {
    const orderId = await createOrder("ASSIGNED");

    const res = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/packing`,
      payload: { captainId: "c1" },
    });

    expect(res.statusCode).toBe(409);
    expect(JSON.parse(res.body).error).toContain("Illegal transition");
  });

  it("Delivery without packing backtracks to 409 (PACKING is mandatory in demo flow)", async () => {
    const orderId = await createOrder("ACCEPTED");

    const res = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/pickup`,
      payload: { captainId: "c1" },
    });

    expect(res.statusCode).toBe(409);
    expect(JSON.parse(res.body).error).toContain("Illegal transition");
  });

  it("POST /staff/:id/online toggles a picker offline and dispatch skips offline pickers", async () => {
    const orderId = crypto.randomUUID();
    const chaiId = "10000000-0000-0000-0000-000000000001";

    // Put c1 (zone B1-B2) offline
    const toggleres = await app.inject({
      method: "POST",
      url: "/staff/c1/online",
      payload: { online: false },
    });
    expect(toggleres.statusCode).toBe(200);
    expect(JSON.parse(toggleres.body)).toMatchObject({ ok: true, staffId: "c1", online: false });

    // Order in B1: no picker is zoned to B1 AND online (c2-c4 cover B3..S4)
    // -> broadcast would alert only online captains c2, c3, c4 (attempt 1 picks c4 nearest? nearest zone is c2 B3)
    // Simplest invariant: NO alert may be published to offline c1
    const alerts: Array<{ topic: string; payload: any }> = [];
    publisher.on("published", (event) => {
      if (event.topic.includes("/captain/") && event.topic.includes("/alerts")) {
        alerts.push({ topic: event.topic, payload: event.payload });
      }
    });

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

    await new Promise((r) => setTimeout(r, 30));

    expect(alerts.length).toBeGreaterThanOrEqual(1);
    expect(alerts.every((a) => !a.topic.includes("/captain/c1/"))).toBe(true);

    // Toggling back on works too
    const onRes = await app.inject({
      method: "POST",
      url: "/staff/c1/online",
      payload: { online: true },
    });
    expect(JSON.parse(onRes.body).online).toBe(true);
  });

  it("POST /staff/:id/online validates body and staff id", async () => {
    const missing = await app.inject({
      method: "POST",
      url: "/staff/c1/online",
      payload: {},
    });
    expect(missing.statusCode).toBe(400);

    const notFound = await app.inject({
      method: "POST",
      url: "/staff/nope/online",
      payload: { online: true },
    });
    expect(notFound.statusCode).toBe(404);
  });

  it("GET /staff/:id/stats returns today's orders delivered and cash collected", async () => {
    const orderId = await createOrder("PACKING");

    const pickupRes = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/pickup`,
      payload: { captainId: "c1" },
    });
    expect(pickupRes.statusCode).toBe(200);

    const deliverRes = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/deliver`,
      payload: { captainId: "c1", cashPaise: 1500 },
    });
    expect(deliverRes.statusCode).toBe(200);

    await app.inject({ method: "POST", url: `/orders/${orderId}/confirm-cash` });

    const statsRes = await app.inject({
      method: "GET",
      url: "/staff/c1/stats",
    });
    expect(statsRes.statusCode).toBe(200);
    const stats = JSON.parse(statsRes.body);
    expect(stats.staffId).toBe("c1");
    expect(stats.ordersDelivered).toBe(1);
    expect(stats.collectedPaise).toBe(1500);
    expect(stats.expectedPaise).toBe(1500);
    expect(stats.passengerConfirmedPaise).toBe(1500);

    // Unknown staff -> 404
    const notFound = await app.inject({ method: "GET", url: "/staff/zzz/stats" });
    expect(notFound.statusCode).toBe(404);
  });
});
