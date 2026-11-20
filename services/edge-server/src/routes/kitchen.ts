import type { FastifyPluginAsync } from "fastify";
import { OrderService, StateTransitionError } from "../services/orderService.js";
import { requireRoles } from "../auth/plugin.js";

export function createKitchenRoutes(orderService: OrderService): FastifyPluginAsync {
  return async (fastify) => {
    // POST /kitchen/:id/start
    fastify.post("/kitchen/:id/start", async (request, reply) => {
      if (!requireRoles(request, reply, ["kitchen", "admin"])) return;
      const { id } = request.params as { id: string };
      try {
        return await orderService.startCooking(id);
      } catch (err: any) {
        if (err instanceof StateTransitionError) {
          return reply.status(409).send({ error: err.message });
        }
        return reply.status(400).send({ error: err.message });
      }
    });

    // POST /kitchen/:id/ready
    fastify.post("/kitchen/:id/ready", async (request, reply) => {
      if (!requireRoles(request, reply, ["kitchen", "admin"])) return;
      const { id } = request.params as { id: string };
      try {
        return await orderService.readyCooking(id);
      } catch (err: any) {
        if (err instanceof StateTransitionError) {
          return reply.status(409).send({ error: err.message });
        }
        return reply.status(400).send({ error: err.message });
      }
    });
  };
}
