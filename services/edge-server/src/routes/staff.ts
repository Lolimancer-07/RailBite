import crypto from "node:crypto";
import type { FastifyPluginAsync } from "fastify";
import { StaffLoginSchema } from "@railbite/shared";
import type { StaffRepo } from "../db/repos/staffRepo.js";
import type { LedgerRepo } from "../db/repos/ledgerRepo.js";

export function createStaffRoutes(
  staffRepo: StaffRepo,
  ledgerRepo?: LedgerRepo,
): FastifyPluginAsync {
  return async (fastify) => {
    fastify.post("/staff/login", async (request, reply) => {
      const parsed = StaffLoginSchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.status(400).send({ error: "Invalid credentials format", details: parsed.error });
      }

      const { staffId, pin } = parsed.data;
      const pinHash = crypto.createHash("sha256").update(pin).digest("hex");
      const staff = staffRepo.findByPin(staffId, pinHash);

      if (!staff) {
        return reply.status(401).send({ error: "Invalid staff ID or PIN" });
      }

      // Mark staff online
      staffRepo.setOnline(staff.id, true);

      // Generate simple session token
      const token = `rb-tok-${staff.id}-${Date.now()}`;
      const expiresAt = new Date(Date.now() + 12 * 60 * 60_000).toISOString();

      return {
        staffId: staff.id,
        name: staff.name,
        role: staff.role,
        zoneCoaches: staff.zoneCoaches,
        token,
        expiresAt,
      };
    });

    // POST /staff/:id/online — picker online/offline toggle
    fastify.post("/staff/:id/online", async (request, reply) => {
      const { id } = request.params as { id: string };
      const { online } = (request.body || {}) as { online?: boolean };

      if (typeof online !== "boolean") {
        return reply.status(400).send({ error: "online boolean is required" });
      }

      const staff = staffRepo.findById(id);
      if (!staff) {
        return reply.status(404).send({ error: "Staff not found" });
      }

      staffRepo.setOnline(id, online);
      return { ok: true, staffId: id, online };
    });

    // GET /staff/:id/stats — today's picker stats (orders delivered, cash collected)
    fastify.get("/staff/:id/stats", async (request, reply) => {
      const { id } = request.params as { id: string };

      const staff = staffRepo.findById(id);
      if (!staff) {
        return reply.status(404).send({ error: "Staff not found" });
      }

      if (!ledgerRepo) {
        return reply.status(500).send({ error: "Ledger repository not configured" });
      }

      return ledgerRepo.getStaffStats(id, staff.name);
    });
  };
}

