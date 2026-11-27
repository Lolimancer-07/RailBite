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

/**
 * FOT-inspired prototype features (reverse-engineered from IRCTC Food on Track v2.28.0):
 * - 4-digit delivery code handover: generated on pickup, passenger reveals it, picker must repeat it on /deliver.
 * - Structured post-delivery feedback ("Did you receive this order?" + star rating + reason chips).
 * - Veg / non-veg markers on menu items.
 */
describe("FOT features (delivery code, feedback, veg markers)", () => {
  let db: Database.Database;
  let broker: ReturnType<typeof createBroker>;
  let publisher: MqttPublisher;
  let app: FastifyInstance;

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

  async function createChaiOrder(): Promise<string> {
    const orderId = crypto.randomUUID();
    const createRes = await app.inject({
      method: "POST",
      url: "/orders",
      payload: {
        id: orderId,
        trainNumber: "12345",
        pnr: "8492000001",
        coach: "B1",
        berth: 1,
        diners: 1,
        paymentMode: "COD",
        items: [{ itemId: "10000000-0000-0000-0000-000000000001", qty: 2 }],
        createdAt: new Date().toISOString(),
        source: "WIFI",
      },
    });
    expect(createRes.statusCode).toBe(201);
    return orderId;
  }

  /** Drive a fresh order to OUT_FOR_DELIVERY (accept -> packing -> pickup) */
  async function orderOutForDelivery(): Promise<string> {
    const orderId = await createChaiOrder();
    await app.inject({ method: "POST", url: `/orders/${orderId}/accept`, payload: { captainId: "c1" } });
    await app.inject({ method: "POST", url: `/orders/${orderId}/packing`, payload: { captainId: "c1" } });
    const pickupRes = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/pickup`,
      payload: { captainId: "c1" },
    });
    expect(pickupRes.statusCode).toBe(200);
    return orderId;
  }

  async function deliverWithCode(orderId: string, code?: string, cashPaise = 3000) {
    return app.inject({
      method: "POST",
      url: `/orders/${orderId}/deliver`,
      payload: { captainId: "c1", cashPaise, deliveryCode: code },
    });
  }

  it("pickup generates a 4-digit delivery code; passenger sees it, picker view strips it", async () => {
    const orderId = await orderOutForDelivery();

    const passengerRes = await app.inject({ method: "GET", url: `/orders/${orderId}` });
    const passengerOrder = JSON.parse(passengerRes.body);
    expect(passengerOrder.deliveryCode).toMatch(/^\d{4}$/);
    // code is not the obvious 0000 and is stable across reads
    const secondRead = JSON.parse((await app.inject({ method: "GET", url: `/orders/${orderId}` })).body);
    expect(secondRead.deliveryCode).toBe(passengerOrder.deliveryCode);

    const pickerRes = await app.inject({ method: "GET", url: `/orders/${orderId}?role=picker` });
    const pickerOrder = JSON.parse(pickerRes.body);
    expect(pickerOrder.deliveryCode).toBeUndefined();
  });

  it("deliver rejects a wrong/missing code with 403 and accepts the correct code", async () => {
    const orderId = await orderOutForDelivery();
    const code = JSON.parse((await app.inject({ method: "GET", url: `/orders/${orderId}` })).body)
      .deliveryCode as string;

    const missingRes = await deliverWithCode(orderId, undefined);
    expect(missingRes.statusCode).toBe(403);

    const wrongRes = await deliverWithCode(orderId, "0000" === code ? "1111" : "0000");
    expect(wrongRes.statusCode).toBe(403);
    expect(JSON.parse(wrongRes.body).error).toContain("delivery code");

    const okRes = await deliverWithCode(orderId, code);
    expect(okRes.statusCode).toBe(200);
    expect(JSON.parse(okRes.body).status).toBe("DELIVERED");
  });

  it("feedback is 409 before delivery, accepted after, visible on the order, and rated in picker stats", async () => {
    const orderId = await orderOutForDelivery();

    // Not allowed before DELIVERED
    const earlyRes = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/feedback`,
      payload: { received: true, rating: 5 },
    });
    expect(earlyRes.statusCode).toBe(409);

    await deliverWithCode(
      orderId,
      JSON.parse((await app.inject({ method: "GET", url: `/orders/${orderId}` })).body).deliveryCode,
    );

    const fbRes = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/feedback`,
      payload: { received: true, rating: 5, reasons: ["Food was great", "Staff was nice & humble"] },
    });
    expect(fbRes.statusCode).toBe(200);
    expect(JSON.parse(fbRes.body).feedback).toEqual({
      received: true,
      rating: 5,
      reasons: ["Food was great", "Staff was nice & humble"],
    });

    // Validation: rating out of range
    const badRes = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/feedback`,
      payload: { received: true, rating: 9 },
    });
    expect(badRes.statusCode).toBe(400);

    // Feedback is embedded in the passenger's order view
    const orderRes = await app.inject({ method: "GET", url: `/orders/${orderId}` });
    expect(JSON.parse(orderRes.body).feedback.received).toBe(true);

    // Picker stats include the average rating
    await app.inject({ method: "POST", url: `/orders/${orderId}/confirm-cash` });
    const statsRes = await app.inject({ method: "GET", url: "/staff/c1/stats" });
    const stats = JSON.parse(statsRes.body);
    expect(stats.avgRating).toBe(5);
    expect(stats.feedbackCount).toBe(1);
  });

  it("menu list exposes veg/non-veg markers from the seed", async () => {
    const menuRes = await app.inject({ method: "GET", url: "/menu" });
    const menu = JSON.parse(menuRes.body);

    const chicken = menu.find(
      (m: any) => m.id === "10000000-0000-0000-0000-000000000016",
    );
    expect(chicken).toBeDefined();
    expect(chicken.isVeg).toBe(false);

    const chai = menu.find((m: any) => m.id === "10000000-0000-0000-0000-000000000001");
    expect(chai.isVeg).toBe(true);
  });
});
