import { z } from "zod";
import { TrainNumberSchema } from "./order.js";

export const StaffLoginSchema = z
  .object({
    staffId: z.string().min(1),
    /** Production picker login (bcrypt-verified). */
    password: z.string().min(4).max(128).optional(),
    /** Legacy 4-8 digit PIN — still accepted until every pantry phone is updated. */
    pin: z.string().min(4).max(8).optional(),
    /** Optional active train number (1-10 chars) set/confirmed at login. */
    trainNumber: TrainNumberSchema.optional(),
  })
  .refine((value) => Boolean(value.password || value.pin), {
    message: "password or pin is required",
  });
export type StaffLogin = z.infer<typeof StaffLoginSchema>;

export const StaffSchema = z.object({
  id: z.string(),
  name: z.string(),
  role: z.enum(["CAPTAIN", "COOK"]),
  zoneCoaches: z.array(z.string()),
  trainNumber: TrainNumberSchema,
  online: z.boolean(),
  deviceToken: z.string().optional(),
});
export type Staff = z.infer<typeof StaffSchema>;

export const SessionTokenSchema = z.object({
  staffId: z.string(),
  role: z.enum(["CAPTAIN", "COOK"]),
  trainNumber: TrainNumberSchema,
  token: z.string(),
  expiresAt: z.string().datetime(),
});

export const PickerStatusUpdateSchema = z.object({
  online: z.boolean(),
  trainNumber: TrainNumberSchema,
});
export type PickerStatusUpdate = z.infer<typeof PickerStatusUpdateSchema>;
export type SessionToken = z.infer<typeof SessionTokenSchema>;

export const LedgerEntrySchema = z.object({
  orderId: z.string().uuid(),
  coach: z.string(),
  berth: z.number().int(),
  paymentMode: z.enum(["COD", "UPI_QUEUED"]),
  totalPaise: z.number().int(),
  collectedPaise: z.number().int(),
  passengerConfirmed: z.boolean(),
  deliveredAt: z.string(),
});
export type LedgerEntry = z.infer<typeof LedgerEntrySchema>;
