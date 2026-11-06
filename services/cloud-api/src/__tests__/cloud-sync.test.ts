import { describe, it, expect, beforeEach, afterEach } from "vitest";
import Database from "better-sqlite3";
import { initCloudDb } from "../db.js";
import { createCloudApp } from "../app.js";
import type { FastifyInstance } from "fastify";

describe("Phase 10: Cloud Sync and Contractor Scoring", () => {
  let db: Database.Database;
  let app: FastifyInstance;

  beforeEach(async () => {
    db = initCloudDb(":memory:");
    app = createCloudApp(db);
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
    db.close();
  });

  it("POST /sync/batch writes idempotently on (trip_id, seq) and returns ackedUpTo", async () => {
    const tripId = "TRIP_001";
    const batch = {
      tripId,
      items: [
        {
          seq: 1,
          entity: "ORDER_CREATED",
          payload: JSON.stringify({ orderId: "ord-1", totalPaise: 4500 }),
          createdAt: new Date().toISOString(),
        },
        {
          seq: 2,
          entity: "CASH_DELIVERED",
          payload: JSON.stringify({ orderId: "ord-1", captainId: "c1", amountPaise: 4500 }),
          createdAt: new Date().toISOString(),
        },
      ],
    };

    // First push
    const res1 = await app.inject({
      method: "POST",
      url: "/sync/batch",
      payload: batch,
    });

    expect(res1.statusCode).toBe(200);
    const body1 = JSON.parse(res1.body);
    expect(body1.ok).toBe(true);
    expect(body1.ackedUpTo).toBe(2);

    // Verify 2 outbox rows in DB
    const count1 = (db.prepare("SELECT COUNT(*) as cnt FROM synced_outbox").get() as any).cnt;
    expect(count1).toBe(2);

    // Second push with same seq (idempotency check)
    const res2 = await app.inject({
      method: "POST",
      url: "/sync/batch",
      payload: batch,
    });

    expect(res2.statusCode).toBe(200);
    const body2 = JSON.parse(res2.body);
    expect(body2.ackedUpTo).toBe(2);

    // Count is still 2 - no duplicates created
    const count2 = (db.prepare("SELECT COUNT(*) as cnt FROM synced_outbox").get() as any).cnt;
    expect(count2).toBe(2);
  });

  it("Contractor scoring: Tier 2 complaints deduct points (overcharging = -10, hygiene = -15, stale = -20)", async () => {
    const tripId = "TRIP_SCORING_001";
    const batch = {
      tripId,
      items: [
        {
          seq: 10,
          entity: "COMPLAINT_ESCALATION",
          payload: JSON.stringify({
            id: "comp-1",
            orderId: "ord-1",
            type: "OVERCHARGING",
            text: "Overcharged by 10 rupees",
            photoSha256: "hash123",
          }),
          createdAt: new Date().toISOString(),
        },
        {
          seq: 11,
          entity: "COMPLAINT_ESCALATION",
          payload: JSON.stringify({
            id: "comp-2",
            orderId: "ord-2",
            type: "HYGIENE",
            text: "Dirty tray and hair in food",
            photoSha256: "hash456",
          }),
          createdAt: new Date().toISOString(),
        },
        {
          seq: 12,
          entity: "COMPLAINT_ESCALATION",
          payload: JSON.stringify({
            id: "comp-3",
            orderId: "ord-3",
            type: "STALE_FOOD",
            text: "Rotten curd",
            photoSha256: "hash789",
          }),
          createdAt: new Date().toISOString(),
        },
      ],
    };

    const res = await app.inject({
      method: "POST",
      url: "/sync/batch",
      payload: batch,
    });

    expect(res.statusCode).toBe(200);

    // Verify score deduction: 100 - 10 - 15 - 20 = 55 (< 70 -> CRITICAL_REVOKE_RISK)
    const scoresRes = await app.inject({
      method: "GET",
      url: "/admin/scores",
    });

    expect(scoresRes.statusCode).toBe(200);
    const scores = JSON.parse(scoresRes.body);
    const tripScore = scores.find((s: any) => s.tripId === tripId);
    expect(tripScore).toBeDefined();
    expect(tripScore.score).toBe(55);
    expect(tripScore.status).toBe("CRITICAL_REVOKE_RISK");
    expect(tripScore.penalties.length).toBe(3);

    // Check complaints feed
    const complaintsRes = await app.inject({
      method: "GET",
      url: "/admin/complaints",
    });
    const complaints = JSON.parse(complaintsRes.body);
    expect(complaints.length).toBe(3);
    expect(complaints[0].photo_sha256).toBeDefined();
  });
});
