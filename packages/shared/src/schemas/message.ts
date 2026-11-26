import { z } from "zod";

export const OrderMessageSenderRole = z.enum(["CUSTOMER", "PICKER"]);
export type OrderMessageSenderRole = z.infer<typeof OrderMessageSenderRole>;

export interface ChatPreset {
  readonly code: string;
  readonly body: string;
}

export const CUSTOMER_CHAT_PRESETS = [
  { code: "WHERE_ARE_YOU", body: "Where are you?" },
  { code: "AT_SEAT", body: "I'm at my seat" },
  { code: "NEAR_DOOR", body: "Near coach door" },
  { code: "NEED_CHANGE", body: "Please bring change" },
] as const satisfies readonly ChatPreset[];

export const PICKER_CHAT_PRESETS = [
  { code: "IM_HERE", body: "I'm here at your seat" },
  { code: "ENTERING_COACH", body: "Entering your coach" },
  { code: "TWO_MINS", body: "2 mins away" },
  { code: "PACKING", body: "Packing your order" },
] as const satisfies readonly ChatPreset[];

export const OrderMessageCreateSchema = z.object({
  id: z.string().uuid(),
  senderRole: OrderMessageSenderRole.optional(),
  senderId: z.string().max(120).optional(),
  presetCode: z.string().max(64).nullable().optional(),
  body: z
    .string()
    .transform((s) => s.trim())
    .pipe(
      z
        .string()
        .min(1, "Message cannot be empty")
        .max(120, "Message must be at most 120 characters"),
    ),
});
export type OrderMessageCreate = z.infer<typeof OrderMessageCreateSchema>;

export const OrderMessageSchema = z.object({
  id: z.string().uuid(),
  orderId: z.string().uuid(),
  senderRole: OrderMessageSenderRole,
  senderId: z.string().nullable().optional(),
  presetCode: z.string().nullable().optional(),
  body: z.string(),
  createdAt: z.number().int(),
});
export type OrderMessage = z.infer<typeof OrderMessageSchema>;
