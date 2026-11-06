import { z } from "zod";

export const StaffLoginSchema = z.object({
  staffId: z.string(),
  pin: z.string().min(4).max(8),
});
export type StaffLogin = z.infer<typeof StaffLoginSchema>;

export const StaffSchema = z.object({
  id: z.string(),
  name: z.string(),
  role: z.enum(["CAPTAIN", "COOK"]),
  zoneCoaches: z.array(z.string()),
  online: z.boolean(),
  deviceToken: z.string().optional(),
});
export type Staff = z.infer<typeof StaffSchema>;

export const SessionTokenSchema = z.object({
  staffId: z.string(),
  role: z.enum(["CAPTAIN", "COOK"]),
  token: z.string(),
  expiresAt: z.string().datetime(),
});
export type SessionToken = z.infer<typeof SessionTokenSchema>;

