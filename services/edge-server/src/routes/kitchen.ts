import type { FastifyPluginAsync } from "fastify";
import { OrderService, StateTransitionError } from "../services/orderService.js";

export function createKitchenRoutes(orderService: OrderService): FastifyPluginAsync {
  return async (fastify) => {
    // POST /kitchen/:id/start
    fastify.post("/kitchen/:id/start", async (request, reply) => {
      const { id } = request.params as { id: string };
      try {
        const order = await orderService.startCooking(id);
        return order;
      } catch (err: any) {
        if (err instanceof StateTransitionError) {
          return reply.status(409).send({ error: err.message });
        }
        return reply.status(400).send({ error: err.message });
      }
    });

    // POST /kitchen/:id/ready
    fastify.post("/kitchen/:id/ready", async (request, reply) => {
      const { id } = request.params as { id: string };
      try {
        const order = await orderService.readyCooking(id);
        return order;
      } catch (err: any) {
        if (err instanceof StateTransitionError) {
          return reply.status(409).send({ error: err.message });
        }
        return reply.status(400).send({ error: err.message });
      }
    });
  };
}

