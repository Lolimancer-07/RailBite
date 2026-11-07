import type { FastifyPluginAsync } from "fastify";
import { ComplaintCreateSchema } from "@railbite/shared";
import type { ComplaintService } from "../services/complaintService.js";

export function createComplaintRoutes(complaintService: ComplaintService): FastifyPluginAsync {
  return async (fastify) => {
    fastify.post("/complaints", async (request, reply) => {
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

    fastify.get("/complaints", async () => {
      return complaintService.listActive();
    });

    fastify.get("/complaints/:id", async (request, reply) => {
      const { id } = request.params as { id: string };
      const complaint = complaintService.getComplaint(id);
      if (!complaint) {
        return reply.status(404).send({ error: "Complaint not found" });
      }
      return complaint;
    });
  };
}

