import { describe, it, expect, beforeEach, afterEach } from "vitest";
import Database from "better-sqlite3";
import crypto from "node:crypto";
import { config } from "../config.js";
import { runMigrations } from "../db/connection.js";
import { seed } from "../seed/seed.js";
import { createBroker } from "../mqtt/broker.js";
import { MqttPublisher } from "../mqtt/publisher.js";
import { registerMqttHandlers } from "../mqtt/handlers.js";
import { createApp } from "../app.js";
import type { FastifyInstance } from "fastify";
import type { Dispatcher } from "../dispatch/dispatcher.js";

config.logLevel = "silent";

const CHAI_ID = "10000000-0000-0000-0000-000000000001";

describe("Strict 5-digit Train Number scoping", () => {
  let db: Database.Database;
  let broker: ReturnType<typeof createBroker>;
  let publisher: MqttPublisher;
  let app: FastifyInstance;
  let dispatcher: Dispatcher;

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
    dispatcher = appObj.dispatcher;
    registerMqttHandlers(broker.aedes, appObj.orderService, appObj.complaintService);
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
    await broker.close();
    db.close();
  });

  function collectMqtt() {
    const alerts: Array<{ topic: string; payload: any }> = [];
    const statuses: Array<{ topic: string; payload: any }> = [];
    publisher.on("published", (event) => {
      if (event.topic.includes("/captain/") && event.topic.endsWith("/alerts")) {
        alerts.push({ topic: event.topic, payload: event.payload });
      }
      if (event.topic.includes("/orders/") && event.topic.endsWith("/status")) {
        statuses.push({ topic: event.topic, payload: event.payload });
      }
    });
    return { alerts, statuses };
  }

  it("isolates Train 12345 orders strictly to Train 12345 pickers and Train 12423 orders to Train 12423 pickers", async () => {
    const { alerts } = collectMqtt();

    const order12345 = crypto.randomUUID();
    const res1 = await app.inject({
      method: "POST",
      url: "/orders",
      payload: {
        id: order12345,
        trainNumber: "12345",
        pnr: "8492000001",
        coach: "B1",
        berth: 1,
        diners: 1,
        paymentMode: "COD",
        items: [{ itemId: CHAI_ID, qty: 1 }],
        createdAt: new Date().toISOString(),
        source: "WIFI",
      },
    });
    expect(res1.statusCode).toBe(201);
    expect(JSON.parse(res1.body).trainNumber).toBe("12345");

    const order12423 = crypto.randomUUID();
    const res2 = await app.inject({
      method: "POST",
      url: "/orders",
      payload: {
        id: order12423,
        trainNumber: "12423",
        pnr: "8492000061",
        coach: "B1",
        berth: 2,
        diners: 1,
        paymentMode: "COD",
        items: [{ itemId: CHAI_ID, qty: 1 }],
        createdAt: new Date().toISOString(),
        source: "WIFI",
      },
    });
    expect(res2.statusCode).toBe(201);
    expect(JSON.parse(res2.body).trainNumber).toBe("12423");

    await new Promise((r) => setTimeout(r, 30));

    const alertsFor12345 = alerts.filter((a) => a.payload.orderId === order12345);
    expect(alertsFor12345.length).toBeGreaterThanOrEqual(1);
    expect(alertsFor12345.every((a) => a.topic.startsWith("rb/train/12345/captain/c1/"))).toBe(true);
    expect(alertsFor12345.every((a) => a.payload.trainNumber === "12345")).toBe(true);

    const alertsFor12423 = alerts.filter((a) => a.payload.orderId === order12423);
    expect(alertsFor12423.length).toBeGreaterThanOrEqual(1);
    expect(alertsFor12423.every((a) => a.topic.startsWith("rb/train/12423/captain/c3/"))).toBe(true);
    expect(alertsFor12423.every((a) => a.payload.trainNumber === "12423")).toBe(true);
  });

  it("keeps zone fallback and rejection retries strictly inside the same train and never leaks cross-train", async () => {
    const { alerts } = collectMqtt();
    const orderId = crypto.randomUUID();

    const createRes = await app.inject({
      method: "POST",
      url: "/orders",
      payload: {
        id: orderId,
        trainNumber: "12345",
        pnr: "8492000001",
        coach: "B1",
        berth: 4,
        diners: 1,
        paymentMode: "COD",
        items: [{ itemId: CHAI_ID, qty: 1 }],
        createdAt: new Date().toISOString(),
        source: "WIFI",
      },
    });
    expect(createRes.statusCode).toBe(201);

    // c1 rejects -> fallback must pick c2 on 12345, NEVER c3/c4 on 12423
    const reject1 = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/reject`,
      payload: { captainId: "c1" },
    });
    expect(reject1.statusCode).toBe(200);

    // c2 also rejects -> broadcast escalation must stay strictly within 12345
    const reject2 = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/reject`,
      payload: { captainId: "c2" },
    });
    expect(reject2.statusCode).toBe(200);

    await new Promise((r) => setTimeout(r, 30));
    dispatcher.cancelTimer(orderId);

    expect(alerts.some((a) => a.topic.includes("/captain/c2/"))).toBe(true);
    expect(alerts.some((a) => a.topic.includes("/captain/c3/") || a.topic.includes("/captain/c4/"))).toBe(false);
    expect(alerts.every((a) => a.topic.startsWith("rb/train/12345/"))).toBe(true);
  });

  it("keeps order PLACED and emits NO_PICKER_ON_TRAIN when 0 pickers are online on that train", async () => {
    // Turn off c3 and c4 (Train 12423) while keeping c1 & c2 online on Train 12345
    await app.inject({
      method: "POST",
      url: "/pickers/c3/status",
      payload: { online: false, trainNumber: "12423" },
    });
    await app.inject({
      method: "POST",
      url: "/pickers/c4/status",
      payload: { online: false, trainNumber: "12423" },
    });

    const { alerts, statuses } = collectMqtt();
    const orderId = crypto.randomUUID();
    const createRes = await app.inject({
      method: "POST",
      url: "/orders",
      payload: {
        id: orderId,
        trainNumber: "12423",
        pnr: "8492000061",
        coach: "S1",
        berth: 3,
        diners: 1,
        paymentMode: "COD",
        items: [{ itemId: CHAI_ID, qty: 1 }],
        createdAt: new Date().toISOString(),
        source: "WIFI",
      },
    });
    expect(createRes.statusCode).toBe(201);
    const created = JSON.parse(createRes.body);
    expect(created.status).toBe("PLACED");

    await new Promise((r) => setTimeout(r, 30));

    // Zero alerts sent anywhere (c1/c2 on 12345 must stay silent)
    expect(alerts).toHaveLength(0);

    // Order remains PLACED and NO_PICKER_ON_TRAIN status was emitted
    const fetched = JSON.parse((await app.inject({ method: "GET", url: `/orders/${orderId}` })).body);
    expect(fetched.status).toBe("PLACED");
    expect(
      statuses.some(
        (s) =>
          s.topic === `rb/train/12423/orders/${orderId}/status` &&
          s.payload.code === "NO_PICKER_ON_TRAIN" &&
          s.payload.status === "PLACED",
      ),
    ).toBe(true);
  });

  it("rejects invalid trainNumber values with HTTP 400 across orders, mesh-sync, and picker status", async () => {
    for (const badTrain of ["123", "ABCDE", "123456", ""]) {
      const orderRes = await app.inject({
        method: "POST",
        url: "/orders",
        payload: {
          id: crypto.randomUUID(),
          trainNumber: badTrain,
          pnr: "8492000001",
          coach: "B1",
          berth: 1,
          diners: 1,
          paymentMode: "COD",
          items: [{ itemId: CHAI_ID, qty: 1 }],
          createdAt: new Date().toISOString(),
          source: "WIFI",
        },
      });
      expect(orderRes.statusCode).toBe(400);

      const pickerRes = await app.inject({
        method: "POST",
        url: "/pickers/c1/status",
        payload: { online: true, trainNumber: badTrain },
      });
      expect(pickerRes.statusCode).toBe(400);
    }
  });

  it("exposes GET /manifest/:pnr for both seeded trains and allows POST /pickers/:id/status train switching", async () => {
    const pnr1 = await app.inject({ method: "GET", url: "/manifest/8492000001" });
    expect(pnr1.statusCode).toBe(200);
    expect(JSON.parse(pnr1.body)).toEqual({
      pnr: "8492000001",
      trainNumber: "12345",
      trainName: "Saraighat Express",
      coach: "B1",
      berth: 1,
    });

    const pnr2 = await app.inject({ method: "GET", url: "/manifest/8492000061" });
    expect(pnr2.statusCode).toBe(200);
    expect(JSON.parse(pnr2.body)).toEqual({
      pnr: "8492000061",
      trainNumber: "12423",
      trainName: "Rajdhani Express",
      coach: "S1",
      berth: 1,
    });

    const missing = await app.inject({ method: "GET", url: "/manifest/9999999999" });
    expect(missing.statusCode).toBe(404);

    // Switch c1 from Train 12345 -> Train 12423 via POST /pickers/:id/status
    const switchRes = await app.inject({
      method: "POST",
      url: "/pickers/c1/status",
      payload: { online: true, trainNumber: "12423" },
    });
    expect(switchRes.statusCode).toBe(200);
    expect(JSON.parse(switchRes.body)).toMatchObject({
      ok: true,
      staffId: "c1",
      online: true,
      trainNumber: "12423",
    });

    // Reject wildcard train subscriptions in MQTT ACL
    const canSubWildcard = await new Promise<boolean>((resolve) => {
      broker.aedes.authorizeSubscribe(
        { id: "c-c1" } as any,
        { topic: "rb/train/+/captain/c1/alerts", qos: 1 } as any,
        (err) => resolve(!err),
      );
    });
    expect(canSubWildcard).toBe(false);
  });
});
