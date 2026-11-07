import { describe, it, expect, beforeEach, afterEach } from "vitest";
import Database from "better-sqlite3";
import { config } from "../config.js";
import { runMigrations } from "../db/connection.js";
import { seed } from "../seed/seed.js";
import { createBroker } from "../mqtt/broker.js";
import { MqttPublisher } from "../mqtt/publisher.js";
import { registerMqttHandlers } from "../mqtt/handlers.js";
import { createApp } from "../app.js";
import type { FastifyInstance } from "fastify";
import { encodeSmsToken, parseSmsAck } from "@railbite/shared";

config.logLevel = "silent";

describe("Phase 9: SMS Token Path (STRETCH)", () => {
  let db: Database.Database;
  let broker: ReturnType<typeof createBroker>;
  let publisher: MqttPublisher;
  let app: FastifyInstance;
  const secret = config.hmacSecret;

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

  it("Done when 1 & 3: Valid SMS token creates order, re-prices from DB, sets source=SMS, and returns ACK token", async () => {
    // Chai (₹15) + Samosa 2 pcs (₹30) -> total ₹45 (4500 paise)
    const token = encodeSmsToken(
      {
        pnrLast4: "0001",
        coach: "B1",
        berth: 1,
        diners: 1,
        itemCodes: "CH1,SA1",
        nonce: "1001",
      },
      secret
    );

    const res = await app.inject({
      method: "POST",
      url: "/sms/ingest",
      payload: {
        from: "+919876543210",
        text: token,
      },
    });

    expect(res.statusCode).toBe(201);
    const body = JSON.parse(res.body);
    expect(body.ok).toBe(true);
    expect(body.source).toBe("SMS");
    expect(body.totalPaise).toBe(4500); // 1500 + 3000
    expect(body.ackToken).toMatch(/^RB#ACK#[0-9a-f]{6}#4500#\d+$/);

    const ack = parseSmsAck(body.ackToken);
    expect(ack.totalPaise).toBe(4500);
    expect(ack.etaMin).toBe(10); // READY track = 10 mins

    // Verify in DB
    const order = db.prepare("SELECT * FROM orders WHERE id = ?").get(body.orderId) as any;
    expect(order).toBeDefined();
    expect(order.source).toBe("SMS");
    expect(order.coach).toBe("B1");
    expect(order.berth).toBe(1);
    expect(order.total_paise).toBe(4500);
  });

  it("Done when 3: Rejects tampered HMAC with HTTP 400", async () => {
    const validToken = encodeSmsToken(
      {
        pnrLast4: "0001",
        coach: "B1",
        berth: 1,
        diners: 1,
        itemCodes: "CH1",
        nonce: "2001",
      },
      secret
    );

    // Tamper the berth from 1 to 2 without updating HMAC
    const tampered = validToken.replace("B1-1", "B1-2");

    const res = await app.inject({
      method: "POST",
      url: "/sms/ingest",
      payload: {
        from: "+919876543210",
        text: tampered,
      },
    });

    expect(res.statusCode).toBe(400);
    const body = JSON.parse(res.body);
    expect(body.error).toContain("SMS verification failed");
  });

  it("Done when 3: Rejects duplicate nonce replay attack with HTTP 409", async () => {
    const token = encodeSmsToken(
      {
        pnrLast4: "0001",
        coach: "B1",
        berth: 1,
        diners: 1,
        itemCodes: "CH1",
        nonce: "3001",
      },
      secret
    );

    // First attempt succeeds
    const res1 = await app.inject({
      method: "POST",
      url: "/sms/ingest",
      payload: { from: "+919876543210", text: token },
    });
    expect(res1.statusCode).toBe(201);

    // Second attempt with exact same nonce fails with 409
    const res2 = await app.inject({
      method: "POST",
      url: "/sms/ingest",
      payload: { from: "+919876543210", text: token },
    });
    expect(res2.statusCode).toBe(409);
    const body = JSON.parse(res2.body);
    expect(body.error).toContain("Replay detected");
  });

  it("Done when 3: Checks manifest matching for verified flag", async () => {
    // 8492000001 in seed data is B1 berth 1
    const token = encodeSmsToken(
      {
        pnrLast4: "0001",
        coach: "B1",
        berth: 1,
        diners: 1,
        itemCodes: "CO1",
        nonce: "4001",
      },
      secret
    );

    const res = await app.inject({
      method: "POST",
      url: "/sms/ingest",
      payload: { from: "+919876543210", text: token },
    });

    expect(res.statusCode).toBe(201);
    const body = JSON.parse(res.body);
    const order = db.prepare("SELECT * FROM orders WHERE id = ?").get(body.orderId) as any;
    expect(order.verified).toBe(1);
    expect(order.pnr).toBe("8492000001");
  });
});

