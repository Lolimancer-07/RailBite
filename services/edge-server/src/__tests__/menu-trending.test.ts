import { describe, it, expect, beforeEach, afterEach } from "vitest";
import Database from "better-sqlite3";
import Aedes from "aedes";
import crypto from "node:crypto";
import { config } from "../config.js";
import { runMigrations } from "../db/connection.js";
import { seed } from "../seed/seed.js";
import { MqttPublisher } from "../mqtt/publisher.js";
import { createApp } from "../app.js";
import type { FastifyInstance } from "fastify";

config.logLevel = "silent";

describe("GET /menu/trending", () => {
  let db: Database.Database;
  let aedes: Aedes;
  let publisher: MqttPublisher;
  let app: FastifyInstance;

  beforeEach(async () => {
    db = new Database(":memory:");
    db.pragma("journal_mode = WAL");
    db.pragma("foreign_keys = ON");

    runMigrations(db);
    await seed(db);

    aedes = new Aedes();
    publisher = new MqttPublisher(aedes);

    const appObj = await createApp({ db, publisher });
    app = appObj.app;
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
    await new Promise<void>((resolve) => aedes.close(() => resolve()));
    db.close();
  });

  it("falls back to default catalog order with orderCount 0 when no orders exist", async () => {
    const res = await app.inject({
      method: "GET",
      url: "/menu/trending",
    });
    expect(res.statusCode).toBe(200);
    const items = JSON.parse(res.body);
    expect(items).toHaveLength(6);
    expect(items[0].id).toBe("10000000-0000-0000-0000-000000000001");
    expect(items[0].orderCount).toBe(0);
  });

  it("ranks active items by non-cancelled order_items qty and honors limit query param", async () => {
    const noodlesId = "10000000-0000-0000-0000-000000000009";
    const biryaniId = "10000000-0000-0000-0000-000000000008";
    const chipsId = "10000000-0000-0000-0000-000000000004";

    await app.inject({
      method: "POST",
      url: "/orders",
      payload: {
        id: crypto.randomUUID(),
        trainNumber: "12345",
        pnr: "8492000001",
        coach: "B1",
        berth: 1,
        diners: 1,
        paymentMode: "COD",
        items: [
          { itemId: noodlesId, qty: 4 },
          { itemId: biryaniId, qty: 2 },
        ],
        createdAt: new Date().toISOString(),
        source: "WIFI",
      },
    });

    // Cancelled order should not inflate trending counts
    const cancelTargetId = crypto.randomUUID();
    await app.inject({
      method: "POST",
      url: "/orders",
      payload: {
        id: cancelTargetId,
        trainNumber: "12345",
        pnr: "8492000002",
        coach: "B1",
        berth: 2,
        diners: 1,
        paymentMode: "COD",
        items: [{ itemId: chipsId, qty: 9 }],
        createdAt: new Date().toISOString(),
        source: "WIFI",
      },
    });
    await app.inject({
      method: "POST",
      url: `/orders/${cancelTargetId}/cancel`,
    });

    const res = await app.inject({
      method: "GET",
      url: "/menu/trending?limit=3",
    });
    expect(res.statusCode).toBe(200);
    const trending = JSON.parse(res.body);
    expect(trending).toHaveLength(3);
    expect(trending[0].id).toBe(noodlesId);
    expect(trending[0].orderCount).toBe(4);
    expect(trending[1].id).toBe(biryaniId);
    expect(trending[1].orderCount).toBe(2);
    expect(trending.find((i: any) => i.id === chipsId)).toBeUndefined();
  });
});
