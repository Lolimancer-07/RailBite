import { z } from "zod";

export const MenuItemSchema = z.object({
  id: z.string().uuid(),
  name: z.string(),
  category: z.string(),
  track: z.enum(["READY", "COOK"]),
  mrpPaise: z.number().int().positive(),
  stock: z.number().int().min(0),
  prepMinutes: z.number().int().min(0).default(0),
  active: z.boolean().default(true),
});
export type MenuItem = z.infer<typeof MenuItemSchema>;

export const MenuResponseSchema = z.array(MenuItemSchema);
export type MenuResponse = z.infer<typeof MenuResponseSchema>;

