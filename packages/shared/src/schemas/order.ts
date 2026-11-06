import { z } from "zod";

export const OrderItemSchema = z.object({
  itemId: z.string().uuid(),
  qty: z.number().int().min(1).max(10),
});

export const OrderCreateSchema = z.object({
  id: z.string().uuid(),
  pnr: z.string().regex(/^\d{10}$/, "PNR must be 10 digits"),
  coach: z.string().regex(/^[A-Z]{1,2}\d{1,2}$/, "Invalid coach format, e.g. B3, SL7"),
  berth: z.number().int().min(1).max(100),
  diners: z.number().int().min(1).max(8),
  paymentMode: z.enum(["COD", "UPI_QUEUED"]).default("COD"),
  items: z
    .array(OrderItemSchema)
    .min(1, "At least one item required"),
  createdAt: z.string().datetime(),
  source: z.enum(["WIFI", "SMS", "RELAY"]).default("WIFI"),
});
export type OrderCreate = z.infer<typeof OrderCreateSchema>;

export const OrderStatusUpdateSchema = z.object({
  status: z.string(),
  updatedAt: z.string().datetime(),
  captainName: z.string().optional(),
  etaMin: z.number().int().optional(),
});
export type OrderStatusUpdate = z.infer<typeof OrderStatusUpdateSchema>;

export const OrderSchema = z.object({
  id: z.string().uuid(),
  pnr: z.string(),
  coach: z.string(),
  berth: z.number().int(),
  diners: z.number().int(),
  track: z.enum(["READY", "COOK"]),
  status: z.string(),
  paymentMode: z.enum(["COD", "UPI_QUEUED"]),
  totalPaise: z.number().int(),
  source: z.enum(["WIFI", "SMS", "RELAY"]),
  verified: z.number().int(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
  items: z
    .array(
      z.object({
        itemId: z.string().uuid(),
        qty: z.number().int(),
        unitPricePaise: z.number().int(),
      }),
    )
    .optional(),
});
export type Order = z.infer<typeof OrderSchema>;

