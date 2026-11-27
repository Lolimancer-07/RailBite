import { describe, it, expect, beforeEach, afterEach } from "vitest";
import Database from "better-sqlite3";
import crypto from "node:crypto";
import { config } from "../config.js";
import { runMigrations } from "../db/connection.js";
import { seed } from "../seed/seed.js";
import { createBroker } from "../mqtt/broker.js";
import { MqttPublisher } from "../mqtt/publisher.js";
import { createApp } from "../app.js";
import { CUSTOMER_CHAT_PRESETS, PICKER_CHAT_PRESETS } from "@railbite/shared";
import type { FastifyInstance } from "fastify";

config.logLevel = "silent";

describe("Order quick-chat REST & MQTT endpoints", () => {
  let db: Database.Database;
  let broker: ReturnType<typeof createBroker>;
  let publisher: MqttPublisher;
  let app: FastifyInstance;

  const chaiId = "10000000-0000-0000-0000-000000000001";

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

  async function createOrder(): Promise<string> {
    const orderId = crypto.randomUUID();
    const res = await app.inject({
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
        items: [{ itemId: chaiId, qty: 1 }],
        createdAt: new Date().toISOString(),
        source: "WIFI",
      },
    });
    expect(res.statusCode).toBe(201);
    return orderId;
  }

  it("rejects quick-chat with 409 when order is PLACED/ASSIGNED, DELIVERED, or CANCELLED", async () => {
    const placedId = crypto.randomUUID();
    // Force an offline/unassigned PLACED row (avoid dispatcher transition) cleanly via SQL + API test
    const now = new Date().toISOString();
    db.prepare(
      `INSERT INTO orders (id, pnr, coach, berth, diners, track, status, payment_mode, total_paise, source, verified, created_at, updated_at)
       VALUES (?, '8492000001', 'B1', 4, 1, 'READY', 'PLACED', 'COD', 1500, 'WIFI', 1, ?, ?)`,
    ).run(placedId, now, now);

    const onPlaced = await app.inject({
      method: "POST",
      url: `/orders/${placedId}/messages`,
      payload: { id: crypto.randomUUID(), senderRole: "CUSTOMER", body: "Where are you?" },
    });
    expect(onPlaced.statusCode).toBe(409);

    const cancelOrder = await createOrder();
    await app.inject({ method: "POST", url: `/orders/${cancelOrder}/cancel` });
    const onCancelled = await app.inject({
      method: "POST",
      url: `/orders/${cancelOrder}/messages`,
      payload: { id: crypto.randomUUID(), senderRole: "CUSTOMER", body: "Still coming?" },
    });
    expect(onCancelled.statusCode).toBe(409);

    const deliveredOrder = await createOrder();
    await app.inject({ method: "POST", url: `/orders/${deliveredOrder}/accept`, payload: { captainId: "c1" } });
    await app.inject({ method: "POST", url: `/orders/${deliveredOrder}/packing`, payload: { captainId: "c1" } });
    await app.inject({ method: "POST", url: `/orders/${deliveredOrder}/pickup`, payload: { captainId: "c1" } });
    const fetched = await app.inject({ method: "GET", url: `/orders/${deliveredOrder}` });
    const code = JSON.parse(fetched.body).deliveryCode;
    await app.inject({
      method: "POST",
      url: `/orders/${deliveredOrder}/deliver`,
      payload: { captainId: "c1", deliveryCode: code },
    });

    const onDelivered = await app.inject({
      method: "POST",
      url: `/orders/${deliveredOrder}/messages`,
      payload: { id: crypto.randomUUID(), senderRole: "PICKER", body: "Enjoy!" },
    });
    expect(onDelivered.statusCode).toBe(409);
  });

  it("sends preset and custom messages, preserves chronological order, publishes QoS 1 MQTT, and deduplicates UUID retries", async () => {
    const orderId = await createOrder();
    await app.inject({ method: "POST", url: `/orders/${orderId}/accept`, payload: { captainId: "c1" } });

    const mqttEvents: Array<{ topic: string; qos: number; retain: boolean; payload: any }> = [];
    publisher.on("published", (ev) => {
      if (ev.topic.endsWith(`/orders/${orderId}/chat`)) {
        mqttEvents.push(ev);
      }
    });

    const firstId = crypto.randomUUID();
    const customerPreset = CUSTOMER_CHAT_PRESETS[0];
    const send1 = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/messages`,
      payload: {
        id: firstId,
        senderRole: "CUSTOMER",
        presetCode: customerPreset.code,
        body: customerPreset.body,
      },
    });
    expect(send1.statusCode).toBe(201);
    const msg1 = JSON.parse(send1.body);
    expect(msg1).toMatchObject({
      id: firstId,
      orderId,
      senderRole: "CUSTOMER",
      presetCode: "WHERE_ARE_YOU",
      body: "Where are you?",
    });

    // Duplicate UUID retry: returns 200 with the exact same record and does NOT re-publish MQTT
    const retry1 = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/messages`,
      payload: {
        id: firstId,
        senderRole: "CUSTOMER",
        presetCode: customerPreset.code,
        body: customerPreset.body,
      },
    });
    expect(retry1.statusCode).toBe(200);
    expect(JSON.parse(retry1.body)).toEqual(msg1);
    expect(mqttEvents).toHaveLength(1);

    // Advance to PACKING & send picker preset + custom text
    await app.inject({ method: "POST", url: `/orders/${orderId}/packing`, payload: { captainId: "c1" } });
    const pickerPreset = PICKER_CHAT_PRESETS[0];
    const send2 = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/messages`,
      payload: {
        id: crypto.randomUUID(),
        senderRole: "PICKER",
        senderId: "c1",
        presetCode: pickerPreset.code,
        body: `  ${pickerPreset.body}  `,
      },
    });
    expect(send2.statusCode).toBe(201);

    // Validation bounds: blank or >120 chars fail with 400
    const badBlank = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/messages`,
      payload: { id: crypto.randomUUID(), senderRole: "CUSTOMER", body: "   " },
    });
    expect(badBlank.statusCode).toBe(400);

    const badTooLong = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/messages`,
      payload: { id: crypto.randomUUID(), senderRole: "CUSTOMER", body: "a".repeat(121) },
    });
    expect(badTooLong.statusCode).toBe(400);

    // Fetch chronological history
    const listRes = await app.inject({ method: "GET", url: `/orders/${orderId}/messages` });
    expect(listRes.statusCode).toBe(200);
    const messages = JSON.parse(listRes.body);
    expect(messages).toHaveLength(2);
    expect(messages[0].body).toBe("Where are you?");
    expect(messages[1].body).toBe("I'm here at your seat");
    expect(messages[1].senderRole).toBe("PICKER");

    // Verify MQTT broadcasts
    expect(mqttEvents).toHaveLength(2);
    expect(mqttEvents[0]).toMatchObject({
      topic: `rb/train/12345/orders/${orderId}/chat`,
      qos: 1,
      retain: false,
    });
  });

  it("allows both p- passenger and c- picker MQTT clients to subscribe to order chat topics", async () => {
    const orderId = crypto.randomUUID();
    const chatTopic = `rb/train/12345/orders/${orderId}/chat`;

    const checkSubscribe = (clientId: string, topic: string) =>
      new Promise<boolean>((resolve) => {
        broker.aedes.authorizeSubscribe(
          { id: clientId } as any,
          { topic, qos: 1 } as any,
          (err) => resolve(!err),
        );
      });

    expect(await checkSubscribe("p-demo123", chatTopic)).toBe(true);
    expect(await checkSubscribe("c-c1-chat", chatTopic)).toBe(true);
    expect(await checkSubscribe("c-c1", chatTopic)).toBe(true);
  });

  it("exposes GET /staff/:id/orders (with item categories, no deliveryCode) and GET /staff/:id/ledger", async () => {
    const orderId = await createOrder();
    await app.inject({ method: "POST", url: `/orders/${orderId}/accept`, payload: { captainId: "c1" } });

    const queueRes = await app.inject({ method: "GET", url: "/staff/c1/orders" });
    expect(queueRes.statusCode).toBe(200);
    const activeOrders = JSON.parse(queueRes.body);
    expect(activeOrders).toHaveLength(1);
    expect(activeOrders[0].id).toBe(orderId);
    expect(activeOrders[0].deliveryCode).toBeUndefined();
    expect(activeOrders[0].items[0].category).toBe("BEVERAGES");

    // Advance to DELIVERED and check ledger entry transitions from Awaiting -> Confirmed
    await app.inject({ method: "POST", url: `/orders/${orderId}/packing`, payload: { captainId: "c1" } });
    await app.inject({ method: "POST", url: `/orders/${orderId}/pickup`, payload: { captainId: "c1" } });
    const full = JSON.parse((await app.inject({ method: "GET", url: `/orders/${orderId}` })).body);
    await app.inject({
      method: "POST",
      url: `/orders/${orderId}/deliver`,
      payload: { captainId: "c1", deliveryCode: full.deliveryCode },
    });

    const queueAfter = JSON.parse((await app.inject({ method: "GET", url: "/staff/c1/orders" })).body);
    expect(queueAfter).toHaveLength(0);

    const ledger1Res = await app.inject({ method: "GET", url: "/staff/c1/ledger" });
    expect(ledger1Res.statusCode).toBe(200);
    const ledger1 = JSON.parse(ledger1Res.body);
    expect(ledger1.summary.ordersDelivered).toBe(1);
    expect(ledger1.entries).toHaveLength(1);
    expect(ledger1.entries[0]).toMatchObject({
      orderId,
      coach: "B1",
      berth: 4,
      paymentMode: "COD",
      collectedPaise: 1500,
      passengerConfirmed: false,
    });

    await app.inject({ method: "POST", url: `/orders/${orderId}/confirm-cash` });
    const ledger2 = JSON.parse((await app.inject({ method: "GET", url: "/staff/c1/ledger" })).body);
    expect(ledger2.entries[0].passengerConfirmed).toBe(true);
    expect(ledger2.summary.passengerConfirmedPaise).toBe(1500);
  });
});

