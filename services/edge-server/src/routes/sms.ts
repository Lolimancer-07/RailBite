import type { FastifyPluginAsync } from "fastify";
import type Database from "better-sqlite3";
import crypto from "node:crypto";
import { decodeSmsToken, formatSmsAck } from "@railbite/shared";
import { OrderService } from "../services/orderService.js";
import { config } from "../config.js";

export function createSmsRoutes(db: Database.Database, orderService: OrderService): FastifyPluginAsync {
  return async (fastify) => {
    fastify.post("/sms/ingest", async (request, reply) => {
      const { from, text } = (request.body || {}) as { from?: string; text?: string };

      if (!text) {
        return reply.status(400).send({ error: "Missing required text field containing SMS token" });
      }

      // 1. Fetch trip HMAC secret from database (fallback to config.hmacSecret)
      const trip = db.prepare("SELECT hmac_secret FROM trips WHERE id = ?").get(config.tripId) as any;
      const secret = trip?.hmac_secret || config.hmacSecret;

      // 2. Decode and verify SMS token
      let decoded;
      try {
        decoded = decodeSmsToken(text, secret);
      } catch (err: any) {
        return reply.status(400).send({ error: `SMS verification failed: ${err.message}` });
      }

      // 3. Replay prevention: check nonce in sms_nonces table
      const existingNonce = db.prepare("SELECT nonce FROM sms_nonces WHERE nonce = ?").get(decoded.nonce);
      if (existingNonce) {
        return reply.status(409).send({ error: `Replay detected: nonce ${decoded.nonce} already processed` });
      }

      // Record nonce
      db.prepare("INSERT INTO sms_nonces (nonce, pnr_last4, created_at) VALUES (?, ?, ?)").run(
        decoded.nonce,
        decoded.pnrLast4,
        new Date().toISOString()
      );

      // 4. Manifest match: find full PNR if passenger in manifest
      const manifestRow = db
        .prepare(
          "SELECT pnr FROM manifest WHERE pnr LIKE ? AND coach = ? AND berth = ?"
        )
        .get(`%${decoded.pnrLast4}`, decoded.coach, decoded.berth) as any;

      const pnr = manifestRow?.pnr || `PNR_SMS_${decoded.pnrLast4}`;

      // 5. Create Order via orderService
      const orderId = crypto.randomUUID();
      try {
        const order = orderService.create({
          id: orderId,
          pnr,
          coach: decoded.coach,
          berth: decoded.berth,
          diners: decoded.diners,
          paymentMode: "COD",
          items: decoded.items,
          source: "SMS",
          createdAt: new Date().toISOString(),
        });

        const etaMin = order.track === "COOK" ? 20 : 10;
        const ackToken = formatSmsAck(order.id, order.totalPaise, etaMin);

        return reply.status(201).send({
          ok: true,
          orderId: order.id,
          source: "SMS",
          from: from || "MODEM",
          ackToken,
          totalPaise: order.totalPaise,
          track: order.track,
          etaMin,
        });
      } catch (err: any) {
        return reply.status(400).send({ error: `Failed to create order from SMS: ${err.message}` });
      }
    });
  };
}
