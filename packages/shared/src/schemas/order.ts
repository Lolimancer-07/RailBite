import { z } from "zod";

export const TRAIN_NUMBER_REGEX = /^[A-Z0-9]{1,10}$/;

/** Normalizes any train identifier (`trim`, remove whitespace, uppercase). */
export function normalizeTrainNumber(raw: string): string {
  return raw.replace(/\s+/g, "").toUpperCase();
}

export const TrainNumberSchema = z
  .string()
  .transform((val) => normalizeTrainNumber(val))
  .refine(
    (val) => TRAIN_NUMBER_REGEX.test(val),
    "Enter a valid 1-10 character train number",
  );
export type TrainNumber = z.infer<typeof TrainNumberSchema>;

export const OrderItemSchema = z.object({
  itemId: z.string().uuid(),
  qty: z.number().int().min(1).max(10),
});

export const OrderCreateSchema = z.object({
  id: z.string().uuid(),
  trainNumber: TrainNumberSchema,
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

/** Idempotent offline BLE mesh sync payload submitted when LAN returns. */
export const MeshOrderSyncSchema = z.object({
  id: z.string().uuid(),
  trainNumber: TrainNumberSchema,
  pnr: z.string().regex(/^\d{10}$/, "PNR must be 10 digits"),
  coach: z.string().min(1).max(4),
  berth: z.number().int().min(1).max(100),
  diners: z.number().int().min(1).max(8).default(1),
  paymentMode: z.enum(["COD", "UPI_QUEUED"]).default("COD"),
  items: z.array(OrderItemSchema).min(1),
  createdAt: z.string().datetime(),
  status: z
    .enum(["PLACED", "ACCEPTED", "PACKING", "OUT_FOR_DELIVERY", "DELIVERED"])
    .default("PLACED"),
  captainId: z.string().optional(),
  deliveryCode: z.string().regex(/^\d{4}$/).optional(),
  cashPaise: z.number().int().min(0).optional(),
  passengerConfirmed: z.boolean().optional(),
});
export type MeshOrderSync = z.infer<typeof MeshOrderSyncSchema>;

export const ManifestEntrySchema = z.object({
  pnr: z.string(),
  trainNumber: TrainNumberSchema,
  trainName: z.string(),
  coach: z.string(),
  berth: z.number().int(),
});
export type ManifestEntry = z.infer<typeof ManifestEntrySchema>;

export const OrderStatusUpdateSchema = z.object({
  status: z.string(),
  trainNumber: TrainNumberSchema.optional(),
  code: z.string().optional(),
  message: z.string().optional(),
  updatedAt: z.string().datetime(),
  captainName: z.string().optional(),
  etaMin: z.number().int().optional(),
});
export type OrderStatusUpdate = z.infer<typeof OrderStatusUpdateSchema>;

/** FOT-style post-delivery feedback: "Did you receive this order?" + stars + reason chips */
export const FeedbackCreateSchema = z.object({
  received: z.boolean(),
  rating: z.number().int().min(1).max(5).optional(),
  reasons: z.array(z.string()).max(5).optional(),
});
export type FeedbackCreate = z.infer<typeof FeedbackCreateSchema>;

export const FeedbackReadSchema = z.object({
  received: z.boolean(),
  rating: z.number().int().min(1).max(5).nullable().optional(),
  reasons: z.array(z.string()).optional(),
});
export type FeedbackRead = z.infer<typeof FeedbackReadSchema>;

export const OrderSchema = z.object({
  id: z.string().uuid(),
  trainNumber: TrainNumberSchema,
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
  /** 4-digit handover code shown to the passenger; omitted when fetched by the picker (?role=picker) */
  deliveryCode: z.string().optional(),
  feedback: FeedbackReadSchema.optional(),
  items: z
    .array(
      z.object({
        itemId: z.string().uuid(),
        qty: z.number().int(),
        unitPricePaise: z.number().int(),
        name: z.string().optional(),
        category: z.string().optional(),
      }),
    )
    .optional(),
});
export type Order = z.infer<typeof OrderSchema>;

