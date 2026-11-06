import type Aedes from "aedes";
import pino from "pino";
import {
  OrderCreateSchema,
  ComplaintCreateSchema,
  MqttCaptainActionSchema,
  MqttKitchenActionSchema,
} from "@railbite/shared";
import { config } from "../config.js";
import type { OrderService } from "../services/orderService.js";
import type { ComplaintService } from "../services/complaintService.js";

const logger = pino({ name: "mqtt-handlers" });

export function registerMqttHandlers(
  aedes: Aedes,
  orderService: OrderService,
  complaintService: ComplaintService,
): void {
  const tripPrefix = `rb/${config.tripId}`;

  aedes.on("publish", async (packet, client) => {
    // Skip if no payload or topic
    if (!packet || !packet.topic || !packet.payload) return;
    const topic = packet.topic;

    // Ignore internal or outgoing server publications to prevent loops
    if (
      topic.endsWith("/status") ||
      topic.endsWith("/alerts") ||
      topic.endsWith("/queue")
    ) {
      return;
    }

    try {
      const payloadStr = packet.payload.toString("utf-8");
      if (!payloadStr) return;
      const data = JSON.parse(payloadStr);

      // 1. New Order
      if (topic === `${tripPrefix}/orders/new`) {
        const parsed = OrderCreateSchema.safeParse(data);
        if (parsed.success) {
          logger.info({ orderId: parsed.data.id }, "MQTT incoming new order");
          orderService.create(parsed.data);
        } else {
          logger.warn({ errors: parsed.error }, "Invalid OrderCreate MQTT payload");
        }
      }

      // 2. Captain Actions
      else if (topic.startsWith(`${tripPrefix}/captain/`) && topic.endsWith("/actions")) {
        const parsed = MqttCaptainActionSchema.safeParse(data);
        if (parsed.success) {
          const action = parsed.data;
          logger.info({ action }, "MQTT incoming captain action");
          if (action.action === "ACCEPT") {
            await orderService.accept(action.orderId, action.captainId);
          } else if (action.action === "PICKED_UP") {
            await orderService.pickup(action.orderId, action.captainId);
          } else if (action.action === "REJECT") {
            await orderService.reject(action.orderId, action.captainId);
          } else if (action.action === "DELIVERED") {
            await orderService.deliver(action.orderId, action.captainId, action.cashPaise);
          }
        }
      }

      // 3. Kitchen Actions
      else if (topic === `${tripPrefix}/kitchen/actions`) {
        const parsed = MqttKitchenActionSchema.safeParse(data);
        if (parsed.success) {
          const action = parsed.data;
          logger.info({ action }, "MQTT incoming kitchen action");
          if (action.action === "START") {
            await orderService.startCooking(action.orderId);
          } else if (action.action === "READY") {
            await orderService.readyCooking(action.orderId);
          }
        }
      }

      // 4. Complaints
      else if (topic === `${tripPrefix}/complaints/new`) {
        const parsed = ComplaintCreateSchema.safeParse(data);
        if (parsed.success) {
          logger.info({ complaintId: parsed.data.id }, "MQTT incoming complaint");
          await complaintService.create(parsed.data);
        }
      }
    } catch (err) {
      logger.error({ err, topic }, "Error processing MQTT message");
    }
  });
}
