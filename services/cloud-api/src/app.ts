import fastify, { type FastifyInstance } from "fastify";
import cors from "@fastify/cors";
import type Database from "better-sqlite3";
import { calculatePenalty, type PenaltyRecord } from "./scoring.js";

export function createCloudApp(db: Database.Database): FastifyInstance {
  const app = fastify({ logger: false });

  app.register(cors, { origin: "*" });

  app.get("/health", async () => {
    return { status: "ok", service: "cloud-api", version: "0.0.1" };
  });

  // POST /sync/batch
  app.post("/sync/batch", async (request, reply) => {
    const body = (request.body || {}) as {
      tripId: string;
      items: Array<{
        seq: number;
        entity: string;
        payload: string;
        createdAt: string;
      }>;
    };

    if (!body.tripId || !Array.isArray(body.items)) {
      return reply.status(400).send({ error: "Missing required fields: tripId and items array" });
    }

    // Ensure trip exists
    const existingTrip = db.prepare("SELECT id FROM trips WHERE id = ?").get(body.tripId);
    if (!existingTrip) {
      db.prepare(
        "INSERT INTO trips (id, train_no, contractor_name, created_at) VALUES (?, ?, ?, ?)"
      ).run(body.tripId, "12951", "IRCTC Catering Licensee Corp", new Date().toISOString());
    }

    // Ensure score entry exists
    const existingScore = db.prepare("SELECT * FROM contractor_scores WHERE trip_id = ?").get(body.tripId) as any;
    if (!existingScore) {
      db.prepare(
        "INSERT INTO contractor_scores (trip_id, score, penalties_json, updated_at) VALUES (?, 100, '[]', ?)"
      ).run(body.tripId, new Date().toISOString());
    }

    let maxSeq = 0;
    const now = new Date().toISOString();

    const insertOutboxStmt = db.prepare(`
      INSERT OR IGNORE INTO synced_outbox (trip_id, seq, entity, payload, created_at, received_at)
      VALUES (?, ?, ?, ?, ?, ?)
    `);

    const updateScoreStmt = db.prepare(`
      UPDATE contractor_scores
      SET score = ?, penalties_json = ?, updated_at = ?
      WHERE trip_id = ?
    `);

    const insertComplaintStmt = db.prepare(`
      INSERT OR REPLACE INTO synced_complaints (id, trip_id, order_id, type, text, photo_sha256, photo_path, penalty_points, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);

    const insertLedgerStmt = db.prepare(`
      INSERT INTO synced_ledger (trip_id, order_id, captain_id, amount_paise, passenger_confirmed, ts)
      VALUES (?, ?, ?, ?, ?, ?)
    `);

    const syncTx = db.transaction(() => {
      let currentScoreRow = db.prepare("SELECT * FROM contractor_scores WHERE trip_id = ?").get(body.tripId) as any;
      let currentScore = currentScoreRow?.score ?? 100;
      let penalties: PenaltyRecord[] = JSON.parse(currentScoreRow?.penalties_json || "[]");

      for (const item of body.items) {
        if (item.seq > maxSeq) {
          maxSeq = item.seq;
        }

        const info = insertOutboxStmt.run(body.tripId, item.seq, item.entity, item.payload, item.createdAt, now);
        // Only process newly inserted outbox rows to ensure idempotency
        if (info.changes > 0) {
          try {
            const data = JSON.parse(item.payload);

            if (item.entity === "COMPLAINT_ESCALATION") {
              const penalty = calculatePenalty(data.type || "OTHER");
              currentScore = Math.max(0, currentScore - penalty);

              penalties.push({
                complaintId: data.id,
                orderId: data.orderId,
                type: data.type,
                pointsDeducted: penalty,
                reason: data.text || data.type,
                timestamp: now,
              });

              insertComplaintStmt.run(
                data.id,
                body.tripId,
                data.orderId,
                data.type,
                data.text || null,
                data.photoSha256 || null,
                data.photoPath || null,
                penalty,
                data.createdAt || now
              );
            } else if (item.entity === "CASH_DELIVERED" || item.entity === "CASH_CONFIRMED") {
              insertLedgerStmt.run(
                body.tripId,
                data.orderId,
                data.captainId,
                data.amountPaise || data.cashPaise || 0,
                item.entity === "CASH_CONFIRMED" ? 1 : 0,
                now
              );
            }
          } catch (err) {
            console.error("Failed to parse synced item payload:", err);
          }
        }
      }

      updateScoreStmt.run(currentScore, JSON.stringify(penalties), now, body.tripId);
    });

    syncTx();

    return reply.status(200).send({
      ok: true,
      tripId: body.tripId,
      ackedUpTo: maxSeq,
      itemsProcessed: body.items.length,
    });
  });

  // GET /admin/scores
  app.get("/admin/scores", async (request, reply) => {
    const scores = db.prepare(`
      SELECT cs.trip_id, t.train_no, t.contractor_name, cs.score, cs.penalties_json, cs.updated_at
      FROM contractor_scores cs
      JOIN trips t ON cs.trip_id = t.id
    `).all() as any[];

    const result = scores.map((s) => {
      const score = s.score;
      let status = "GOOD";
      if (score < 70) status = "CRITICAL_REVOKE_RISK";
      else if (score < 85) status = "WARNING";

      return {
        tripId: s.trip_id,
        trainNo: s.train_no,
        contractorName: s.contractor_name,
        score,
        status,
        penalties: JSON.parse(s.penalties_json || "[]"),
        updatedAt: s.updated_at,
      };
    });

    return result;
  });

  // GET /admin/complaints
  app.get("/admin/complaints", async () => {
    return db.prepare("SELECT * FROM synced_complaints ORDER BY created_at DESC").all();
  });

  // GET /admin/ledger
  app.get("/admin/ledger", async () => {
    const rows = db.prepare("SELECT * FROM synced_ledger ORDER BY ts DESC").all() as any[];
    const totalCollectedPaise = rows.reduce((acc, r) => acc + (r.amount_paise || 0), 0);
    const confirmedCount = rows.filter((r) => r.passenger_confirmed === 1).length;

    return {
      totalCollectedPaise,
      totalCollectedRupees: totalCollectedPaise / 100,
      totalEntries: rows.length,
      confirmedCount,
      entries: rows,
    };
  });

  return app;
}

