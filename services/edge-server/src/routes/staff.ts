import crypto from "node:crypto";
import type { FastifyPluginAsync } from "fastify";
import { StaffLoginSchema } from "@railbite/shared";
import type { StaffRepo } from "../db/repos/staffRepo.js";

export function createStaffRoutes(staffRepo: StaffRepo): FastifyPluginAsync {
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
  };
}

