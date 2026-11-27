import type { FastifyPluginAsync } from "fastify";
import { PickerStatusUpdateSchema, StaffLoginSchema, TrainNumberSchema } from "@railbite/shared";
import type { StaffRepo } from "../db/repos/staffRepo.js";
import type { LedgerRepo } from "../db/repos/ledgerRepo.js";
import type { OrderRepo } from "../db/repos/orderRepo.js";
import { AuthError, type AuthService } from "../auth/authService.js";
import { requireRoles } from "../auth/plugin.js";

export function createStaffRoutes(
  staffRepo: StaffRepo,
  ledgerRepo: LedgerRepo | undefined,
  authService: AuthService,
  orderRepo?: OrderRepo,
): FastifyPluginAsync {
  return async (fastify) => {
    fastify.post(
      "/staff/login",
      { config: { rateLimit: { max: 10, timeWindow: "1 minute" } } },
      async (request, reply) => {
        const parsed = StaffLoginSchema.safeParse(request.body);
        if (!parsed.success) {
          return reply.status(400).send({ error: "Invalid credentials format", details: parsed.error });
        }

        const { staffId, password, pin, trainNumber } = parsed.data;
        try {
          const result = await authService.staffLogin(staffId, { password, pin, trainNumber });
          // Legacy keys (`token`, `expiresAt`) keep the pre-auth pantry APK working.
          return {
            staffId: result.user.staffId || result.user.id,
            name: result.user.name,
            role: result.user.role === "picker" ? "CAPTAIN" : result.user.role === "kitchen" ? "COOK" : "ADMIN",
            zoneCoaches: result.user.zoneCoaches || [],
            trainNumber: result.user.trainNumber || "12345",
            token: result.accessToken,
            accessToken: result.accessToken,
            refreshToken: result.refreshToken,
            expiresIn: result.expiresIn,
            expiresAt: result.expiresAt,
          };
        } catch (err) {
          const message = err instanceof AuthError ? err.message : "Invalid staff ID or password";
          return reply.status(401).send({ error: message });
        }
      },
    );

    // POST /pickers/:id/status — toggle picker online/offline with required 5-digit trainNumber
    fastify.post("/pickers/:id/status", async (request, reply) => {
      if (!requireRoles(request, reply, ["picker", "kitchen", "admin"])) return;
      const { id } = request.params as { id: string };
      const parsed = PickerStatusUpdateSchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.status(400).send({
          error: parsed.error.issues[0]?.message || "online boolean and 5-digit trainNumber are required",
          details: parsed.error.issues,
        });
      }

      if (request.auth && request.auth.role !== "admin" && request.auth.staffId !== id) {
        return reply.status(403).send({ error: "You can only change your own status" });
      }

      const staff = await staffRepo.findById(id);
      if (!staff) {
        return reply.status(404).send({ error: "Staff not found" });
      }

      await staffRepo.setOnline(id, parsed.data.online, parsed.data.trainNumber);
      return {
        ok: true,
        staffId: id,
        online: parsed.data.online,
        trainNumber: parsed.data.trainNumber,
      };
    });

    // POST /staff/:id/online — legacy online/offline toggle (supports optional trainNumber)
    fastify.post("/staff/:id/online", async (request, reply) => {
      if (!requireRoles(request, reply, ["picker", "kitchen", "admin"])) return;
      const { id } = request.params as { id: string };
      const { online, trainNumber } = (request.body || {}) as {
        online?: boolean;
        trainNumber?: string;
      };

      if (typeof online !== "boolean") {
        return reply.status(400).send({ error: "online boolean is required" });
      }
      if (trainNumber !== undefined) {
        const validTrain = TrainNumberSchema.safeParse(trainNumber);
        if (!validTrain.success) {
          return reply.status(400).send({
            error: validTrain.error.issues[0]?.message || "Enter a valid 5-digit train number",
          });
        }
      }
      if (request.auth && request.auth.role !== "admin" && request.auth.staffId !== id) {
        return reply.status(403).send({ error: "You can only change your own status" });
      }

      const staff = await staffRepo.findById(id);
      if (!staff) {
        return reply.status(404).send({ error: "Staff not found" });
      }

      await staffRepo.setOnline(id, online, trainNumber);
      return { ok: true, staffId: id, online, trainNumber: trainNumber ?? staff.trainNumber };
    });

    // POST /staff/device-token — register FCM / APNs device token for staff alerts
    fastify.post("/staff/device-token", async (request, reply) => {
      if (!requireRoles(request, reply, ["picker", "kitchen", "admin"])) return;
      const body = (request.body || {}) as {
        staffId?: string;
        deviceToken?: string;
        fcmToken?: string;
      };
      const staffId =
        request.auth && request.auth.role !== "admin" ? request.auth.staffId : body.staffId;
      const token = body.fcmToken || body.deviceToken;

      if (!staffId || !token) {
        return reply.status(400).send({ error: "staffId and deviceToken (or fcmToken) are required" });
      }

      const staff = await staffRepo.findById(staffId);
      if (!staff) {
        return reply.status(404).send({ error: "Staff not found" });
      }

      await staffRepo.setDeviceToken(staffId, token);
      return { ok: true, staffId, registered: true };
    });

    // GET /staff/:id/stats — today's picker stats (orders delivered, cash collected)
    fastify.get("/staff/:id/stats", async (request, reply) => {
      if (!requireRoles(request, reply, ["picker", "kitchen", "admin"])) return;
      const { id } = request.params as { id: string };
      if (request.auth && request.auth.role !== "admin" && request.auth.staffId !== id) {
        return reply.status(403).send({ error: "You can only view your own stats" });
      }

      const staff = await staffRepo.findById(id);
      if (!staff) {
        return reply.status(404).send({ error: "Staff not found" });
      }

      if (!ledgerRepo) {
        return reply.status(500).send({ error: "Ledger repository not configured" });
      }

      return ledgerRepo.getStaffStats(id, staff.name);
    });

    // GET /staff/:id/orders — active queue for a picker (ACCEPTED / PACKING / OUT_FOR_DELIVERY)
    fastify.get("/staff/:id/orders", async (request, reply) => {
      if (!requireRoles(request, reply, ["picker", "kitchen", "admin"])) return;
      const { id } = request.params as { id: string };
      if (request.auth && request.auth.role !== "admin" && request.auth.staffId !== id) {
        return reply.status(403).send({ error: "You can only view your own active queue" });
      }

      const staff = await staffRepo.findById(id);
      if (!staff) {
        return reply.status(404).send({ error: "Staff not found" });
      }
      if (!orderRepo) {
        return reply.status(500).send({ error: "Order repository not configured" });
      }

      return orderRepo.findActiveByCaptain(id);
    });

    // GET /staff/:id/ledger — today's summary + per-order cash ledger entries
    fastify.get("/staff/:id/ledger", async (request, reply) => {
      if (!requireRoles(request, reply, ["picker", "kitchen", "admin"])) return;
      const { id } = request.params as { id: string };
      if (request.auth && request.auth.role !== "admin" && request.auth.staffId !== id) {
        return reply.status(403).send({ error: "You can only view your own cash ledger" });
      }

      const staff = await staffRepo.findById(id);
      if (!staff) {
        return reply.status(404).send({ error: "Staff not found" });
      }
      if (!ledgerRepo) {
        return reply.status(500).send({ error: "Ledger repository not configured" });
      }

      const [summary, entries] = await Promise.all([
        ledgerRepo.getStaffStats(id, staff.name),
        ledgerRepo.getStaffLedgerEntries(id),
      ]);
      return { staffId: id, summary, entries };
    });
  };
}
