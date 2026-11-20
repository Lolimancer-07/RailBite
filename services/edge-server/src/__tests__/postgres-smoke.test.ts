import { describe, it, expect, beforeAll, afterAll } from "vitest";
import pg from "pg";
import Aedes from "aedes";
import crypto from "node:crypto";
import type { FastifyInstance } from "fastify";
import { config } from "../config.js";
import { openDb, type Db } from "../db/db.js";
import { seed } from "../seed/seed.js";
import { MqttPublisher } from "../mqtt/publisher.js";
import { createApp } from "../app.js";
import { MenuRepo } from "../db/repos/menuRepo.js";
import { OutboxRepo } from "../db/repos/outboxRepo.js";
import { LedgerRepo } from "../db/repos/ledgerRepo.js";

/**
 * Runs only when TEST_DATABASE_URL points at a disposable Postgres, e.g.
 *   docker run -d --rm --name railbite-pg-test -e POSTGRES_PASSWORD=testpass \
 *     -e POSTGRES_DB=railbite -p 55432:5432 postgres:16-alpine
 *   TEST_DATABASE_URL=postgres://postgres:testpass@127.0.0.1:55432/railbite \
 *     pnpm vitest run src/__tests__/postgres-smoke.test.ts
 *
 * Proves the production driver end-to-end: migrations, idempotent seed, aliases
 * (Postgres folds unquoted identifiers to lowercase), transactions, auth, order
 * lifecycle, serial ids and bigint aggregates.
 */
const TEST_DB_URL = process.env.TEST_DATABASE_URL;
const pgDescribe = TEST_DB_URL ? describe : describe.skip;

const CHAI = "10000000-0000-0000-0000-000000000001";

pgDescribe("Postgres production driver", () => {
  let db: Db;
  let app: FastifyInstance;
  let aedes: Aedes;

  beforeAll(async () => {
    config.logLevel = "silent";

    // Fresh schema every run so the suite is repeatable.
    const admin = new pg.Client({ connectionString: TEST_DB_URL });
    await admin.connect();
    await admin.query("DROP SCHEMA public CASCADE");
    await admin.query("CREATE SCHEMA public");
    await admin.end();

    db = await openDb({ databaseUrl: TEST_DB_URL });
    expect(db.dialect).toBe("postgres");
    await seed(db);
    // Second seed must be a no-op (no duplicate keys, no stock reset).
    await seed(db);

    aedes = new Aedes();
    const publisher = new MqttPublisher(aedes);
    const appObj = await createApp(
      { db, publisher },
      { authRequired: true, auth: { nodeEnv: "test", jwtSecret: "pg-smoke-secret" } },
    );
    app = appObj.app;
    await app.ready();
  }, 180_000);

  afterAll(async () => {
    if (app) await app.close();
    if (aedes) await new Promise<void>((resolve) => aedes.close(() => resolve()));
    if (db) await db.close();
  });

  function bearer(token: string) {
    return { authorization: `Bearer ${token}` };
  }

  it("applies migrations and keeps camelCase aliases intact", async () => {
    const menuRepo = new MenuRepo(db);
    const menu = await menuRepo.listActive();
    expect(menu).toHaveLength(16);
    const chai = menu.find((m) => m.id === CHAI);
    expect(chai?.mrpPaise).toBe(1500);
    expect(chai?.prepMinutes).toBe(0);
    expect(chai?.isVeg).toBe(true);

    const again = await menuRepo.findById(CHAI);
    expect(again?.name).toBe("Masala Chai");
  });

  it("creates an order in a transaction and round-trips it through auth", async () => {
    const login = await app.inject({
      method: "POST",
      url: "/auth/dev/login",
      payload: { phone: "9876543210" },
    });
    expect(login.statusCode, login.body).toBe(200);
    const customer = JSON.parse(login.body) as { accessToken: string; refreshToken: string };

    const orderId = crypto.randomUUID();
    const created = await app.inject({
      method: "POST",
      url: "/orders",
      headers: bearer(customer.accessToken),
      payload: {
        id: orderId,
        pnr: "8492000001",
        coach: "B1",
        berth: 1,
        diners: 1,
        paymentMode: "COD",
        items: [{ itemId: CHAI, qty: 2 }],
        source: "WIFI",
        createdAt: new Date().toISOString(),
      },
    });
    expect(created.statusCode).toBe(201);
    const order = JSON.parse(created.body);
    expect(order.totalPaise).toBe(3000);
    expect(order.status).toBe("PLACED");

    // Stock decremented exactly once (transaction committed).
    const menuRepo = new MenuRepo(db);
    expect((await menuRepo.findById(CHAI))?.stock).toBe(198);

    const fetched = await app.inject({
      method: "GET",
      url: `/orders/${orderId}`,
      headers: bearer(customer.accessToken),
    });
    expect(fetched.statusCode).toBe(200);
    expect(JSON.parse(fetched.body).items[0].unitPricePaise).toBe(1500);

    // Outbox rows come back with a numeric seq after BIGSERIAL.
    const outbox = new OutboxRepo(db);
    const unsynced = await outbox.getUnsynced(10);
    expect(unsynced.length).toBeGreaterThan(0);
    expect(typeof unsynced[0]!.seq).toBe("number");
    expect(unsynced[0]!.entity).toBe("ORDER_CREATED");
  });

  it("runs the full picker lifecycle and returns numeric ledger aggregates", async () => {
    const login = await app.inject({
      method: "POST",
      url: "/auth/dev/login",
      payload: { phone: "9876543211" },
    });
    const customer = JSON.parse(login.body) as { accessToken: string };

    const orderId = crypto.randomUUID();
    await app.inject({
      method: "POST",
      url: "/orders",
      headers: bearer(customer.accessToken),
      payload: {
        id: orderId,
        pnr: "8492000001",
        coach: "B1",
        berth: 2,
        diners: 1,
        paymentMode: "COD",
        items: [{ itemId: CHAI, qty: 1 }],
        source: "WIFI",
        createdAt: new Date().toISOString(),
      },
    });

    const staffLogin = await app.inject({
      method: "POST",
      url: "/staff/login",
      payload: { staffId: "c1", password: "railbite123" },
    });
    expect(staffLogin.statusCode).toBe(200);
    const staff = JSON.parse(staffLogin.body) as { accessToken: string };
    expect(staff.accessToken).toBeTruthy();

    const accept = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/accept`,
      headers: bearer(staff.accessToken),
      payload: { captainId: "c1" },
    });
    expect(accept.statusCode, accept.body).toBe(200);

    const packing = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/packing`,
      headers: bearer(staff.accessToken),
      payload: { captainId: "c1" },
    });
    expect(packing.statusCode, packing.body).toBe(200);

    const pickup = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/pickup`,
      headers: bearer(staff.accessToken),
      payload: { captainId: "c1" },
    });
    expect(pickup.statusCode).toBe(200);

    const passengerView = await app.inject({
      method: "GET",
      url: `/orders/${orderId}`,
      headers: bearer(customer.accessToken),
    });
    const deliveryCode = JSON.parse(passengerView.body).deliveryCode as string;
    expect(deliveryCode).toMatch(/^\d{4}$/);

    const deliver = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/deliver`,
      headers: bearer(staff.accessToken),
      payload: { captainId: "c1", deliveryCode },
    });
    expect(deliver.statusCode).toBe(200);
    expect(JSON.parse(deliver.body).status).toBe("DELIVERED");

    const ledgerRepo = new LedgerRepo(db);
    const summary = await ledgerRepo.getSummary();
    const c1 = summary.find((s) => s.captainId === "c1");
    expect(c1).toBeDefined();
    expect(typeof c1?.expectedPaise).toBe("number");
    expect(c1?.collectedPaise).toBe(1500);

    const stats = await ledgerRepo.getStaffStats("c1", "Ramesh");
    expect(typeof stats.ordersDelivered).toBe("number");
    expect(stats.ordersDelivered).toBe(1);
    expect(stats.feedbackCount).toBe(0);

    const confirm = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/confirm-cash`,
      headers: bearer(customer.accessToken),
    });
    expect(confirm.statusCode).toBe(200);
  });

  it("rotates refresh tokens in Postgres", async () => {
    const login = await app.inject({
      method: "POST",
      url: "/auth/dev/login",
      payload: { phone: "9876543212" },
    });
    const first = JSON.parse(login.body) as { refreshToken: string };

    const refreshed = await app.inject({
      method: "POST",
      url: "/auth/refresh",
      payload: { refreshToken: first.refreshToken },
    });
    expect(refreshed.statusCode, refreshed.body).toBe(200);
    const next = JSON.parse(refreshed.body) as { refreshToken: string };
    expect(next.refreshToken).not.toBe(first.refreshToken);

    const replay = await app.inject({
      method: "POST",
      url: "/auth/refresh",
      payload: { refreshToken: first.refreshToken },
    });
    expect(replay.statusCode).toBe(401);
  });
});
