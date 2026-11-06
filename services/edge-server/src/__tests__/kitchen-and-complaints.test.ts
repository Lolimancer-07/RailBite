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
import { OutboxRepo } from "../db/repos/outboxRepo.js";
import { LedgerRepo } from "../db/repos/ledgerRepo.js";
import type { FastifyInstance } from "fastify";

config.logLevel = "silent";

describe("Phase 6 (Kitchen & Cash) and Phase 7 (Complaints) End-to-End", () => {
  let db: Database.Database;
  let broker: ReturnType<typeof createBroker>;
  let publisher: MqttPublisher;
  let app: FastifyInstance;
  let appObj: Awaited<ReturnType<typeof createApp>>;

  beforeEach(async () => {
    db = new Database(":memory:");
    db.pragma("journal_mode = WAL");
    db.pragma("foreign_keys = ON");
    runMigrations(db);
    seed(db);

    broker = createBroker();
    publisher = new MqttPublisher(broker.aedes);

    appObj = await createApp({ db, publisher });
    app = appObj.app;
    registerMqttHandlers(broker.aedes, appObj.orderService, appObj.complaintService);
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
    await broker.close();
    db.close();
  });

  it("Phase 6 Done-When: Veg Thali ordered -> Kitchen sees it -> Mark Ready pings captain -> Delivery & cash confirmed", async () => {
    const orderId = crypto.randomUUID();
    const thaliId = "10000000-0000-0000-0000-000000000007"; // Standard Veg Thali (COOK track, ₹80)

    const kitchenQueueMessages: any[] = [];
    const captainAlerts: any[] = [];

    publisher.on("published", (event) => {
      if (event.topic.includes("/kitchen/queue")) {
        kitchenQueueMessages.push(event.payload);
      }
      if (event.topic.includes("/captain/") && event.topic.includes("/alerts")) {
        captainAlerts.push(event.payload);
      }
    });

    // 1. Passenger orders a Veg Thali
    const createRes = await app.inject({
      method: "POST",
      url: "/orders",
      payload: {
        id: orderId,
        pnr: "8492000001",
        coach: "B1",
        berth: 2,
        diners: 1,
        paymentMode: "COD",
        items: [{ itemId: thaliId, qty: 1 }],
        createdAt: new Date().toISOString(),
        source: "WIFI",
      },
    });

    expect(createRes.statusCode).toBe(201);
    const order = JSON.parse(createRes.body);
    expect(order.track).toBe("COOK");
    expect(order.status).toBe("PLACED");

    // Kitchen queue received the COOK order
    expect(kitchenQueueMessages.length).toBeGreaterThanOrEqual(1);
    const latestQueue = kitchenQueueMessages[kitchenQueueMessages.length - 1];
    expect(latestQueue.some((o: any) => o.orderId === orderId)).toBe(true);

    // 2. Head cook marks Start Cooking
    const startRes = await app.inject({
      method: "POST",
      url: `/kitchen/${orderId}/start`,
    });
    expect(startRes.statusCode).toBe(200);
    expect(JSON.parse(startRes.body).status).toBe("COOKING");

    // 3. Head cook marks Ready -> triggers dispatch to Captain C1 (Zone B1-B2)
    const readyRes = await app.inject({
      method: "POST",
      url: `/kitchen/${orderId}/ready`,
    });
    expect(readyRes.statusCode).toBe(200);

    // Trigger batch flush for test
    appObj.orderService.flushBatches();

    // Wait a brief moment for batching/dispatch
    await new Promise((r) => setTimeout(r, 60));

    expect(captainAlerts.length).toBeGreaterThanOrEqual(1);
    const alert = captainAlerts[0];
    expect(alert.orderId).toBe(orderId);
    expect(alert.coach).toBe("B1");
    expect(alert.track).toBe("COOK");

    // 4. Captain accepts
    const acceptRes = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/accept`,
      payload: { captainId: "c1" },
    });
    expect(acceptRes.statusCode).toBe(200);

    // 5. Captain picks up
    const pickupRes = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/pickup`,
      payload: { captainId: "c1" },
    });
    expect(pickupRes.statusCode).toBe(200);

    // 6. Captain delivers + collects cash (₹80 / 8000 paise)
    const deliverRes = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/deliver`,
      payload: { captainId: "c1", cashPaise: 8000 },
    });
    expect(deliverRes.statusCode).toBe(200);

    // 7. Passenger confirms cash
    const confirmRes = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/confirm-cash`,
    });
    expect(confirmRes.statusCode).toBe(200);

    // 8. Verify ledger summary
    const ledger = new LedgerRepo(db);
    const summary = ledger.getSummary();
    const c1 = summary.find((s) => s.captainId === "c1");
    expect(c1?.expectedPaise).toBe(8000);
    expect(c1?.collectedPaise).toBe(8000);
    expect(c1?.passengerConfirmedPaise).toBe(8000);
    expect(c1?.mismatch).toBe(false);
  });

  it("Phase 7 Done-When: Tier 1 replacement flow and Tier 2 outbox escalation with rate limit", async () => {
    // 1. Create a base order for complaints
    const orderId = crypto.randomUUID();
    const chaiId = "10000000-0000-0000-0000-000000000001";

    await app.inject({
      method: "POST",
      url: "/orders",
      payload: {
        id: orderId,
        pnr: "8492000002",
        coach: "B2",
        berth: 5,
        diners: 1,
        paymentMode: "COD",
        items: [{ itemId: chaiId, qty: 1 }],
        createdAt: new Date().toISOString(),
        source: "WIFI",
      },
    });

    // 2. Tier 1 complaint (COLD_FOOD)
    const t1Res = await app.inject({
      method: "POST",
      url: "/complaints",
      payload: {
        id: crypto.randomUUID(),
        orderId,
        type: "COLD_FOOD",
        text: "Chai was lukewarm when received",
        createdAt: new Date().toISOString(),
      },
    });
    expect(t1Res.statusCode).toBe(201);
    const t1 = JSON.parse(t1Res.body);
    expect(t1.tier).toBe(1);
    expect(t1.status).toBe("OPEN");
    expect(t1.dueAt).toBeDefined();

    // 3. Tier 2 complaint (OVERCHARGED) with photo SHA-256
    const t2Res = await app.inject({
      method: "POST",
      url: "/complaints",
      payload: {
        id: crypto.randomUUID(),
        orderId,
        type: "OVERCHARGED",
        text: "Charged 25 instead of 15",
        photoSha256: "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
        photoTs: new Date().toISOString(),
        createdAt: new Date().toISOString(),
      },
    });
    expect(t2Res.statusCode).toBe(201);
    const t2 = JSON.parse(t2Res.body);
    expect(t2.tier).toBe(2);

    // Verify written to outbox as COMPLAINT_ESCALATION
    const outbox = new OutboxRepo(db);
    const unsynced = outbox.getUnsynced(100);
    const escalation = unsynced.find((e) => e.entity === "COMPLAINT_ESCALATION");
    expect(escalation).toBeDefined();
    expect(escalation?.payload.type).toBe("OVERCHARGED");

    // 4. Rate limit check: max 3 Tier 2 complaints per PNR per trip
    // Add 2 more Tier 2 complaints (total = 3)
    await app.inject({
      method: "POST",
      url: "/complaints",
      payload: {
        id: crypto.randomUUID(),
        orderId,
        type: "STALE_FOOD",
        photoSha256: "hash2",
        createdAt: new Date().toISOString(),
      },
    });
    await app.inject({
      method: "POST",
      url: "/complaints",
      payload: {
        id: crypto.randomUUID(),
        orderId,
        type: "HYGIENE",
        photoSha256: "hash3",
        createdAt: new Date().toISOString(),
      },
    });

    // 4th Tier 2 complaint should be rejected with 429
    const rejectedRes = await app.inject({
      method: "POST",
      url: "/complaints",
      payload: {
        id: crypto.randomUUID(),
        orderId,
        type: "HYGIENE",
        photoSha256: "hash4",
        createdAt: new Date().toISOString(),
      },
    });
    expect(rejectedRes.statusCode).toBe(429);
    expect(JSON.parse(rejectedRes.body).error).toContain("Rate limit exceeded");
  });
});
