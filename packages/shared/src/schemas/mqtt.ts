import { z } from "zod";

export const MqttOrderAlertSchema = z.object({
  orderId: z.string().uuid(),
  coach: z.string(),
  berth: z.number().int(),
  items: z.array(
    z.object({
      name: z.string(),
      qty: z.number().int(),
      unitPricePaise: z.number().int(),
    }),
  ),
  total: z.number().int(),
  track: z.enum(["READY", "COOK"]),
  attempt: z.number().int().min(1),
  expiresAt: z.string().datetime(),
  pnr: z.string(),
  diners: z.number().int(),
  verified: z.number().int(),
});
export type MqttOrderAlert = z.infer<typeof MqttOrderAlertSchema>;

export const MqttCaptainActionSchema = z.object({
  orderId: z.string().uuid(),
  action: z.enum(["ACCEPT", "REJECT", "PICKED_UP", "DELIVERED"]),
  captainId: z.string(),
  cashPaise: z.number().int().optional(),
});
export type MqttCaptainAction = z.infer<typeof MqttCaptainActionSchema>;

export const MqttKitchenActionSchema = z.object({
  orderId: z.string().uuid(),
  action: z.enum(["START", "READY"]),
  cookId: z.string().optional(),
});
export type MqttKitchenAction = z.infer<typeof MqttKitchenActionSchema>;

export const MqttOrderStatusSchema = z.object({
  status: z.string(),
  updatedAt: z.string().datetime(),
  captainName: z.string().optional(),
  etaMin: z.number().int().optional(),
});
export type MqttOrderStatus = z.infer<typeof MqttOrderStatusSchema>;

export const MqttKitchenQueueItemSchema = z.object({
  orderId: z.string().uuid(),
  coach: z.string(),
  berth: z.number().int(),
  status: z.string(),
  items: z.array(
    z.object({
      name: z.string(),
      qty: z.number().int(),
      prepMinutes: z.number().int(),
    }),
  ),
  createdAt: z.string().datetime(),
  pnr: z.string(),
  diners: z.number().int(),
});
export type MqttKitchenQueueItem = z.infer<typeof MqttKitchenQueueItemSchema>;

