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

const CHAI = "10000000-0000-0000-0000-000000000001";

describe("Production auth spine", () => {
  let db: Database.Database;
  let aedes: Aedes;
  let app: FastifyInstance;

  beforeEach(async () => {
    db = new Database(":memory:");
    db.pragma("journal_mode = WAL");
    db.pragma("foreign_keys = ON");
    runMigrations(db);
    await seed(db);

    aedes = new Aedes();
    const publisher = new MqttPublisher(aedes);
    const appObj = await createApp({ db, publisher }, { authRequired: true });
    app = appObj.app;
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
    await new Promise<void>((resolve) => aedes.close(() => resolve()));
    db.close();
  });

  function bearer(token: string) {
    return { authorization: `Bearer ${token}` };
  }

  async function devLogin(phone: string) {
    const res = await app.inject({
      method: "POST",
      url: "/auth/dev/login",
      payload: { phone },
    });
    expect(res.statusCode).toBe(200);
    return JSON.parse(res.body) as {
      accessToken: string;
      refreshToken: string;
      user: { id: string; phone: string; role: string };
    };
  }

  it("keeps public routes open and rejects unauthenticated order access", async () => {
    const menu = await app.inject({ method: "GET", url: "/menu" });
    expect(menu.statusCode).toBe(200);

    const orders = await app.inject({ method: "GET", url: "/orders?pnr=8492000001" });
    expect(orders.statusCode).toBe(401);
    expect(JSON.parse(orders.body).code).toBe("AUTH_REQUIRED");

    const create = await app.inject({
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
        items: [{ itemId: CHAI, qty: 1 }],
        createdAt: new Date().toISOString(),
        source: "WIFI",
      },
    });
    expect(create.statusCode).toBe(401);
  });

  it("customer dev login issues a JWT and /auth/me returns the account", async () => {
    const login = await devLogin("9876543210");
    expect(login.user.phone).toBe("+919876543210");
    expect(login.user.role).toBe("customer");

    const me = await app.inject({
      method: "GET",
      url: "/auth/me",
      headers: bearer(login.accessToken),
    });
    expect(me.statusCode).toBe(200);
    expect(JSON.parse(me.body).user.phone).toBe("+919876543210");
  });

  it("customers only see their own orders; another account gets 403", async () => {
    const alice = await devLogin("9876543210");
    const bob = await devLogin("9876543211");

    const orderId = crypto.randomUUID();
    const created = await app.inject({
      method: "POST",
      url: "/orders",
      headers: bearer(alice.accessToken),
      payload: {
        id: orderId,
        trainNumber: "12345",
        pnr: "8492000001",
        coach: "B1",
        berth: 1,
        diners: 1,
        paymentMode: "COD",
        items: [{ itemId: CHAI, qty: 1 }],
        createdAt: new Date().toISOString(),
        source: "WIFI",
      },
    });
    expect(created.statusCode).toBe(201);

    const own = await app.inject({
      method: "GET",
      url: `/orders/${orderId}`,
      headers: bearer(alice.accessToken),
    });
    expect(own.statusCode).toBe(200);

    const other = await app.inject({
      method: "GET",
      url: `/orders/${orderId}`,
      headers: bearer(bob.accessToken),
    });
    expect(other.statusCode).toBe(403);

    const bobList = await app.inject({
      method: "GET",
      url: "/orders?pnr=8492000001",
      headers: bearer(bob.accessToken),
    });
    expect(JSON.parse(bobList.body)).toHaveLength(0);
  });

  it("staff password login works (bcrypt) and pickers can accept orders", async () => {
    const customer = await devLogin("9876543210");
    const orderId = crypto.randomUUID();
    const created = await app.inject({
      method: "POST",
      url: "/orders",
      headers: bearer(customer.accessToken),
      payload: {
        id: orderId,
        trainNumber: "12345",
        pnr: "8492000001",
        coach: "B1",
        berth: 1,
        diners: 1,
        paymentMode: "COD",
        items: [{ itemId: CHAI, qty: 1 }],
        createdAt: new Date().toISOString(),
        source: "WIFI",
      },
    });
    expect(created.statusCode).toBe(201);

    const staff = await app.inject({
      method: "POST",
      url: "/staff/login",
      payload: { staffId: "c1", password: "railbite123" },
    });
    expect(staff.statusCode).toBe(200);
    const staffBody = JSON.parse(staff.body);
    expect(staffBody.role).toBe("CAPTAIN");
    expect(staffBody.accessToken).toBeTruthy();
    expect(staffBody.refreshToken).toBeTruthy();

    // Old PIN still authenticates during the transition.
    const legacy = await app.inject({
      method: "POST",
      url: "/staff/login",
      payload: { staffId: "c2", pin: "1234" },
    });
    expect(legacy.statusCode).toBe(200);

    // A customer token cannot perform picker actions.
    const forbidden = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/accept`,
      headers: bearer(customer.accessToken),
      payload: { captainId: "c1" },
    });
    expect(forbidden.statusCode).toBe(403);

    const accepted = await app.inject({
      method: "POST",
      url: `/orders/${orderId}/accept`,
      headers: bearer(staffBody.accessToken),
      payload: { captainId: "c1" },
    });
    expect(accepted.statusCode).toBe(200);
    expect(JSON.parse(accepted.body).status).toBe("ACCEPTED");
  });

  it("rejects wrong staff passwords and deactivated staff accounts", async () => {
    const bad = await app.inject({
      method: "POST",
      url: "/staff/login",
      payload: { staffId: "c1", password: "wrong-password" },
    });
    expect(bad.statusCode).toBe(401);

    db.prepare("UPDATE staff SET active = 0 WHERE id = 'c2'").run();
    const inactive = await app.inject({
      method: "POST",
      url: "/staff/login",
      payload: { staffId: "c2", password: "railbite123" },
    });
    expect(inactive.statusCode).toBe(401);
  });

  it("rotates refresh tokens and revokes the old one on reuse", async () => {
    const login = await devLogin("9876543210");

    const refreshed = await app.inject({
      method: "POST",
      url: "/auth/refresh",
      payload: { refreshToken: login.refreshToken },
    });
    expect(refreshed.statusCode).toBe(200);
    const next = JSON.parse(refreshed.body);
    expect(next.refreshToken).not.toBe(login.refreshToken);

    const replay = await app.inject({
      method: "POST",
      url: "/auth/refresh",
      payload: { refreshToken: login.refreshToken },
    });
    expect(replay.statusCode).toBe(401);

    const logout = await app.inject({
      method: "POST",
      url: "/auth/logout",
      payload: { refreshToken: next.refreshToken },
    });
    expect(logout.statusCode).toBe(200);

    const afterLogout = await app.inject({
      method: "POST",
      url: "/auth/refresh",
      payload: { refreshToken: next.refreshToken },
    });
    expect(afterLogout.statusCode).toBe(401);
  });

  it("rejects a tampered bearer token", async () => {
    const login = await devLogin("9876543210");
    const [header, payload, signature] = login.accessToken.split(".");
    const tampered = `${header}.${payload}.${(signature ?? "").slice(0, -2)}aa`;
    const res = await app.inject({
      method: "GET",
      url: "/auth/me",
      headers: bearer(tampered),
    });
    expect(res.statusCode).toBe(401);
  });
});
