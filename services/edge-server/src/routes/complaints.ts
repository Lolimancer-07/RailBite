import type { FastifyPluginAsync } from "fastify";
import { ComplaintCreateSchema } from "@railbite/shared";
import type { ComplaintService } from "../services/complaintService.js";
import { requireRoles } from "../auth/plugin.js";

export function createComplaintRoutes(complaintService: ComplaintService): FastifyPluginAsync {
  return async (fastify) => {
    fastify.post("/complaints", async (request, reply) => {
      if (!requireRoles(request, reply, ["customer", "admin"])) return;
      const parsed = ComplaintCreateSchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.status(400).send({
          error: "Invalid complaint data",
          details: parsed.error.issues,
        });
      }

      try {
        const complaint = await complaintService.create(parsed.data);
        return reply.status(201).send(complaint);
      } catch (err: any) {
        if (err.message?.includes("Rate limit exceeded")) {
          return reply.status(429).send({ error: err.message });
        }
        return reply.status(400).send({ error: err.message });
      }
    });

    fastify.get("/complaints", async (request, reply) => {
      if (!requireRoles(request, reply, ["picker", "kitchen", "admin"])) return;
      return complaintService.listActive();
    });

    fastify.get("/complaints/:id", async (request, reply) => {
      if (!requireRoles(request, reply, ["picker", "kitchen", "admin"])) return;
      const { id } = request.params as { id: string };
      const complaint = await complaintService.getComplaint(id);
      if (!complaint) {
        return reply.status(404).send({ error: "Complaint not found" });
      }
      return complaint;
    });
  };
}
