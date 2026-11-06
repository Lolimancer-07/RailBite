import { z } from "zod";

const TIER1_TYPES = ["COLD_FOOD", "MISSING_ITEM", "BAD_TASTE", "WRONG_ITEM"] as const;
const TIER2_TYPES = ["OVERCHARGED", "STALE_FOOD", "HYGIENE"] as const;
const ALL_TYPES = [...TIER1_TYPES, ...TIER2_TYPES] as const;

export const ComplaintCreateSchema = z.object({
  id: z.string().uuid(),
  orderId: z.string().uuid(),
  type: z.enum(ALL_TYPES),
  text: z.string().max(500).optional(),
  // Tier 2 fields
  photoBase64: z.string().optional(),
  photoSha256: z.string().optional(),
  photoTs: z.string().datetime().optional(),
  createdAt: z.string().datetime(),
});
export type ComplaintCreate = z.infer<typeof ComplaintCreateSchema>;

export const ComplaintSchema = z.object({
  id: z.string().uuid(),
  orderId: z.string().uuid(),
  tier: z.number().int().refine((v) => v === 1 || v === 2),
  type: z.enum(ALL_TYPES),
  text: z.string().optional(),
  photoPath: z.string().optional(),
  photoSha256: z.string().optional(),
  photoTs: z.string().datetime().optional(),
  status: z.enum(["OPEN", "REPLACEMENT_SENT", "RESOLVED", "ESCALATED"]),
  dueAt: z.string().datetime().optional(),
  createdAt: z.string().datetime(),
});
export type Complaint = z.infer<typeof ComplaintSchema>;

export const TIER1_COMPLAINT_TYPES = TIER1_TYPES;
export const TIER2_COMPLAINT_TYPES = TIER2_TYPES;

export function getTier(type: string): 1 | 2 {
  if (TIER1_TYPES.includes(type as (typeof TIER1_TYPES)[number])) return 1;
  if (TIER2_TYPES.includes(type as (typeof TIER2_TYPES)[number])) return 2;
  throw new Error(`Unknown complaint type: ${type}`);
}

