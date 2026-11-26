import type { FastifyPluginAsync, FastifyReply, FastifyRequest } from "fastify";
import {
  FeedbackCreateSchema,
  OrderCreateSchema,
  OrderMessageCreateSchema,
  type OrderMessageSenderRole,
} from "@railbite/shared";
import {
  OrderService,
  OrderNotFoundError,
  StateTransitionError,
  DeliveryCodeError,
} from "../services/orderService.js";
import { effectiveStaffId, requireRoles } from "../auth/plugin.js";

function customerOwns(
  request: FastifyRequest,
  order: { userId?: string } | null,
): boolean {
  const auth = request.auth;
  if (!auth || auth.role !== "customer") return true;
  if (!order?.userId) return true; // legacy pre-auth order — claimable by any signed-in passenger
  return order.userId === auth.sub;
}

export function createOrderRoutes(orderService: OrderService): FastifyPluginAsync {
  return async (fastify) => {
    // POST /orders
    fastify.post("/orders", async (request, reply) => {
      if (!requireRoles(request, reply, ["customer"])) return;

      const parsed = OrderCreateSchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.status(400).send({
          error: "Invalid order data",
          details: parsed.error.issues,
        });
      }

      try {
        const existing = await orderService.getOrder(parsed.data.id);
        const userId = request.auth?.role === "customer" ? request.auth.sub : undefined;
        const order = await orderService.create(parsed.data, userId);
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

    // GET /orders/:id — passenger view includes the delivery code; pickers never see it
    fastify.get("/orders/:id", async (request, reply) => {
      const { id } = request.params as { id: string };
      const order = await orderService.getOrder(id);
      if (!order) {
        return reply.status(404).send({ error: "Order not found" });
      }
      if (!customerOwns(request, order)) {
        return reply.status(403).send({ error: "This order belongs to another account" });
      }
      const { role } = (request.query || {}) as { role?: string };
      if (role === "picker" || request.auth?.role === "picker") {
        return { ...order, deliveryCode: undefined };
      }
      return order;
    });

    // GET /orders?pnr=... — customers only ever see their own orders on that PNR
    fastify.get("/orders", async (request, reply) => {
      const { pnr } = request.query as { pnr?: string };
      if (!pnr) {
        return reply.status(400).send({ error: "Missing required query parameter: pnr" });
      }
      const userId = request.auth?.role === "customer" ? request.auth.sub : undefined;
      return orderService.getOrdersByPnr(pnr, userId);
    });

    // POST /orders/:id/cancel
    fastify.post("/orders/:id/cancel", async (request, reply) => {
      if (!requireRoles(request, reply, ["customer", "admin"])) return;
      const { id } = request.params as { id: string };
      try {
        const order = await orderService.getOrder(id);
        if (!customerOwns(request, order)) {
          return reply.status(403).send({ error: "This order belongs to another account" });
        }
        return await orderService.cancel(id);
      } catch (err: any) {
        if (err instanceof StateTransitionError) {
          return reply.status(409).send({ error: err.message });
        }
        return reply.status(400).send({ error: err.message });
      }
    });

    // POST /orders/:id/accept
    fastify.post("/orders/:id/accept", async (request, reply) => {
      if (!requireRoles(request, reply, ["picker", "admin"])) return;
      const { id } = request.params as { id: string };
      const { captainId: bodyCaptainId } = (request.body || {}) as { captainId?: string };
      const captainId = effectiveStaffId(request, bodyCaptainId);
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
      if (!requireRoles(request, reply, ["picker", "admin"])) return;
      const { id } = request.params as { id: string };
      const { captainId: bodyCaptainId } = (request.body || {}) as { captainId?: string };
      const captainId = effectiveStaffId(request, bodyCaptainId);
      if (!captainId) {
        return reply.status(400).send({ error: "captainId is required" });
      }
      try {
        return await orderService.pickup(id, captainId);
      } catch (err: any) {
        if (err instanceof StateTransitionError) {
          return reply.status(409).send({ error: err.message });
        }
        return reply.status(400).send({ error: err.message });
      }
    });

    // POST /orders/:id/packing — picker started packing (PACKING status)
    fastify.post("/orders/:id/packing", async (request, reply) => {
      if (!requireRoles(request, reply, ["picker", "admin"])) return;
      const { id } = request.params as { id: string };
      const { captainId: bodyCaptainId } = (request.body || {}) as { captainId?: string };
      try {
        return await orderService.startPacking(id, effectiveStaffId(request, bodyCaptainId));
      } catch (err: any) {
        if (err instanceof StateTransitionError) {
          return reply.status(409).send({ error: err.message });
        }
        return reply.status(400).send({ error: err.message });
      }
    });

    // POST /orders/:id/reject
    fastify.post("/orders/:id/reject", async (request, reply) => {
      if (!requireRoles(request, reply, ["picker", "admin"])) return;
      const { id } = request.params as { id: string };
      const { captainId: bodyCaptainId } = (request.body || {}) as { captainId?: string };
      const captainId = effectiveStaffId(request, bodyCaptainId);
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

    // POST /orders/:id/deliver — requires the passenger's delivery code (FOT-style handover)
    fastify.post("/orders/:id/deliver", async (request, reply) => {
      if (!requireRoles(request, reply, ["picker", "admin"])) return;
      const { id } = request.params as { id: string };
      const {
        captainId: bodyCaptainId,
        cashPaise,
        deliveryCode,
      } = (request.body || {}) as {
        captainId?: string;
        cashPaise?: number;
        deliveryCode?: string;
      };
      const captainId = effectiveStaffId(request, bodyCaptainId);

      if (!captainId) {
        return reply.status(400).send({ error: "captainId is required" });
      }

      try {
        return await orderService.deliver(id, captainId, cashPaise, deliveryCode);
      } catch (err: any) {
        if (err instanceof StateTransitionError) {
          return reply.status(409).send({ error: err.message });
        }
        if (err instanceof DeliveryCodeError) {
          return reply.status(403).send({ error: err.message });
        }
        return reply.status(400).send({ error: err.message });
      }
    });

    // POST /orders/:id/feedback — "Did you receive this order?" + star rating (FOT-style)
    fastify.post("/orders/:id/feedback", async (request, reply) => {
      if (!requireRoles(request, reply, ["customer", "admin"])) return;
      const { id } = request.params as { id: string };
      const parsed = FeedbackCreateSchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.status(400).send({
          error: "Invalid feedback data",
          details: parsed.error.issues,
        });
      }

      try {
        const order = await orderService.getOrder(id);
        if (!customerOwns(request, order)) {
          return reply.status(403).send({ error: "This order belongs to another account" });
        }
        const feedback = await orderService.submitFeedback(id, parsed.data);
        return { ok: true, orderId: id, feedback };
      } catch (err: any) {
        if (err instanceof StateTransitionError) {
          return reply.status(409).send({ error: err.message });
        }
        return reply.status(400).send({ error: err.message });
      }
    });

    // POST /orders/:id/confirm-cash
    fastify.post("/orders/:id/confirm-cash", async (request, reply) => {
      if (!requireRoles(request, reply, ["customer", "admin"])) return;
      const { id } = request.params as { id: string };
      try {
        const order = await orderService.getOrder(id);
        if (!customerOwns(request, order)) {
          return reply.status(403).send({ error: "This order belongs to another account" });
        }
        const confirmed = await orderService.confirmCash(id);
        if (!confirmed) {
          return reply.status(404).send({ error: "No cash ledger record found to confirm" });
        }
        return { ok: true, orderId: id, passengerConfirmed: true };
      } catch (err: any) {
        return reply.status(400).send({ error: err.message });
      }
    });

    // GET /orders/:id/messages — chronological quick-chat history
    fastify.get("/orders/:id/messages", async (request, reply) => {
      if (!requireRoles(request, reply, ["customer", "picker", "admin"])) return;
      const { id } = request.params as { id: string };
      try {
        const order = await orderService.getOrder(id);
        if (!order) {
          return reply.status(404).send({ error: "Order not found" });
        }
        if (!customerOwns(request, order)) {
          return reply.status(403).send({ error: "This order belongs to another account" });
        }
        return await orderService.listMessages(id);
      } catch (err: any) {
        if (err instanceof OrderNotFoundError) {
          return reply.status(404).send({ error: err.message });
        }
        return reply.status(400).send({ error: err.message });
      }
    });

    // POST /orders/:id/messages — idempotent quick-chat message + MQTT QoS 1 broadcast
    fastify.post("/orders/:id/messages", async (request, reply) => {
      if (!requireRoles(request, reply, ["customer", "picker", "admin"])) return;
      const { id } = request.params as { id: string };
      const parsed = OrderMessageCreateSchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.status(400).send({
          error: "Invalid message data",
          details: parsed.error.issues,
        });
      }

      try {
        const order = await orderService.getOrder(id);
        if (!order) {
          return reply.status(404).send({ error: "Order not found" });
        }
        if (!customerOwns(request, order)) {
          return reply.status(403).send({ error: "This order belongs to another account" });
        }

        const auth = request.auth;
        let senderRole: OrderMessageSenderRole = parsed.data.senderRole ?? "CUSTOMER";
        let senderId: string | null = parsed.data.senderId ?? null;
        if (auth?.role === "customer") {
          senderRole = "CUSTOMER";
          senderId = auth.sub;
        } else if (auth?.role === "picker") {
          senderRole = "PICKER";
          senderId = auth.staffId || auth.sub;
        }

        const { duplicate, message } = await orderService.sendMessage(id, parsed.data, {
          senderRole,
          senderId,
        });
        return reply.status(duplicate ? 200 : 201).send(message);
      } catch (err: any) {
        if (err instanceof OrderNotFoundError) {
          return reply.status(404).send({ error: err.message });
        }
        if (err instanceof StateTransitionError) {
          return reply.status(409).send({ error: err.message });
        }
        return reply.status(400).send({ error: err.message });
      }
    });
  };
}
