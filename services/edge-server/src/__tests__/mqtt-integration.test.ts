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

config.logLevel = "silent";

describe("Phase 2 Done-When End-to-End Verification", () => {
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
    registerMqttHandlers(broker.aedes, appObj.orderService, appObj.complaintService);
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
    await broker.close();
    db.close();
  });

  it("POST /orders creates order -> Captain receives MQTT alert -> POST /accept moves status and publishes it", async () => {
    const orderId = crypto.randomUUID();
    const chaiId = "10000000-0000-0000-0000-000000000001";

    const publishedAlerts: any[] = [];
    const publishedStatuses: any[] = [];

    // Intercept MQTT broker publications
    publisher.on("published", (event) => {
      const topic = event.topic;
      if (topic.includes(`/captain/c1/alerts`)) {
        publishedAlerts.push(event.payload);
      }
      if (topic.includes(`/orders/${orderId}/status`)) {
        publishedStatuses.push({
          topic,
          payload: event.payload,
          retain: event.retain,
        });
      }
    });

    // 1. POST /orders to create the order
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
    const orderData = JSON.parse(createRes.body);
    expect(orderData.id).toBe(orderId);

    // Wait a brief tick for async dispatch alert to publish
    await new Promise((resolve) => setTimeout(resolve, 50));

    // Verify captain C1 alert was published
    expect(publishedAlerts.length).toBeGreaterThanOrEqual(1);
    const alert = publishedAlerts[0];
    expect(alert.orderId).toBe(orderId);
    expect(alert.coach).toBe("B1");
    expect(alert.berth).toBe(1);
    expect(alert.track).toBe("READY");
    expect(alert.attempt).toBe(1);

    // 2. POST /orders/:id/accept by Captain C1
    const acceptRes = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/accept`,
      payload: { captainId: "c1" },
    });

    expect(acceptRes.statusCode).toBe(200);
    const acceptedData = JSON.parse(acceptRes.body);
    expect(acceptedData.status).toBe("ACCEPTED");

    // Verify status update was published to MQTT (retained = true)
    const acceptedStatus = publishedStatuses.find((s) => s.payload.status === "ACCEPTED");
    expect(acceptedStatus).toBeDefined();
    expect(acceptedStatus?.payload.captainName).toContain("Captain B1-B2");
    expect(acceptedStatus?.retain).toBe(true);
  });
});
