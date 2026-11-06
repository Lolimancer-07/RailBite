import type { FastifyPluginAsync } from "fastify";
import { OrderCreateSchema } from "@railbite/shared";
import { OrderService, StateTransitionError } from "../services/orderService.js";

export function createOrderRoutes(orderService: OrderService): FastifyPluginAsync {
  return async (fastify) => {
    // POST /orders
    fastify.post("/orders", async (request, reply) => {
      const parsed = OrderCreateSchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.status(400).send({
          error: "Invalid order data",
          details: parsed.error.issues,
        });
      }

      try {
        const existing = orderService.getOrder(parsed.data.id);
        const order = orderService.create(parsed.data);
        if (existing) {
          return reply.status(200).send(order);
        }
        return reply.status(201).send(order);
      } catch (err: any) {
        if (err.message?.includes("Insufficient stock")) {
          return reply.status(422).send({ error: err.message });
        }
        return reply.status(400).send({ error: err.message });
      }
    });

    // GET /orders/:id
    fastify.get("/orders/:id", async (request, reply) => {
      const { id } = request.params as { id: string };
      const order = orderService.getOrder(id);
      if (!order) {
        return reply.status(404).send({ error: "Order not found" });
      }
      return order;
    });

    // GET /orders?pnr=...
    fastify.get("/orders", async (request, reply) => {
      const { pnr } = request.query as { pnr?: string };
      if (!pnr) {
        return reply.status(400).send({ error: "Missing required query parameter: pnr" });
      }
      return orderService.getOrdersByPnr(pnr);
    });

    // POST /orders/:id/cancel
    fastify.post("/orders/:id/cancel", async (request, reply) => {
      const { id } = request.params as { id: string };
      try {
        const order = await orderService.cancel(id);
        return order;
      } catch (err: any) {
        if (err instanceof StateTransitionError) {
          return reply.status(409).send({ error: err.message });
        }
        return reply.status(400).send({ error: err.message });
      }
    });

    // POST /orders/:id/accept
    fastify.post("/orders/:id/accept", async (request, reply) => {
      const { id } = request.params as { id: string };
      const { captainId } = (request.body || {}) as { captainId: string };
      if (!captainId) {
        return reply.status(400).send({ error: "captainId is required" });
      }

      try {
        const order = await orderService.accept(id, captainId);
        return order;
      } catch (err: any) {
        if (err instanceof StateTransitionError) {
          return reply.status(409).send({ error: err.message });
        }
        return reply.status(400).send({ error: err.message });
      }
    });

    // POST /orders/:id/pickup
    fastify.post("/orders/:id/pickup", async (request, reply) => {
      const { id } = request.params as { id: string };
      const { captainId } = (request.body || {}) as { captainId: string };
      try {
        const order = await orderService.pickup(id, captainId);
        return order;
      } catch (err: any) {
        if (err instanceof StateTransitionError) {
          return reply.status(409).send({ error: err.message });
        }
        return reply.status(400).send({ error: err.message });
      }
    });

    // POST /orders/:id/reject
    fastify.post("/orders/:id/reject", async (request, reply) => {
      const { id } = request.params as { id: string };
      const { captainId } = (request.body || {}) as { captainId: string };
      if (!captainId) {
        return reply.status(400).send({ error: "captainId is required" });
      }

      try {
        await orderService.reject(id, captainId);
        return { ok: true, orderId: id, rejectedBy: captainId };
      } catch (err: any) {
        return reply.status(400).send({ error: err.message });
      }
    });

    // POST /orders/:id/deliver
    fastify.post("/orders/:id/deliver", async (request, reply) => {
      const { id } = request.params as { id: string };
      const { captainId, cashPaise } = (request.body || {}) as {
        captainId: string;
        cashPaise?: number;
      };

      if (!captainId) {
        return reply.status(400).send({ error: "captainId is required" });
      }

      try {
        const order = await orderService.deliver(id, captainId, cashPaise);
        return order;
      } catch (err: any) {
        if (err instanceof StateTransitionError) {
          return reply.status(409).send({ error: err.message });
        }
        return reply.status(400).send({ error: err.message });
      }
    });

    // POST /orders/:id/confirm-cash
    fastify.post("/orders/:id/confirm-cash", async (request, reply) => {
      const { id } = request.params as { id: string };
      try {
        const confirmed = await orderService.confirmCash(id);
        if (!confirmed) {
          return reply.status(404).send({ error: "No cash ledger record found to confirm" });
        }
        return { ok: true, orderId: id, passengerConfirmed: true };
      } catch (err: any) {
        return reply.status(400).send({ error: err.message });
      }
    });
  };
}
