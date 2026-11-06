export const Track = { READY: "READY", COOK: "COOK" } as const;
export type Track = (typeof Track)[keyof typeof Track];

export const PaymentMode = { COD: "COD", UPI_QUEUED: "UPI_QUEUED" } as const;
export type PaymentMode = (typeof PaymentMode)[keyof typeof PaymentMode];

export const Role = { CAPTAIN: "CAPTAIN", COOK: "COOK" } as const;
export type Role = (typeof Role)[keyof typeof Role];

export const Source = { WIFI: "WIFI", SMS: "SMS", RELAY: "RELAY" } as const;
export type Source = (typeof Source)[keyof typeof Source];

export const ComplaintTier = { TIER1: 1, TIER2: 2 } as const;
export type ComplaintTier = (typeof ComplaintTier)[keyof typeof ComplaintTier];

export const OrderStatus = {
  PLACED: "PLACED",
  ASSIGNED: "ASSIGNED",
  ACCEPTED: "ACCEPTED",
  COOKING: "COOKING",
  READY: "READY",
  OUT_FOR_DELIVERY: "OUT_FOR_DELIVERY",
  DELIVERED: "DELIVERED",
  CANCELLED: "CANCELLED",
  EXPIRED: "EXPIRED",
} as const;
export type OrderStatus = (typeof OrderStatus)[keyof typeof OrderStatus];

export const ComplaintStatus = {
  OPEN: "OPEN",
  REPLACEMENT_SENT: "REPLACEMENT_SENT",
  RESOLVED: "RESOLVED",
  ESCALATED: "ESCALATED",
} as const;
export type ComplaintStatus = (typeof ComplaintStatus)[keyof typeof ComplaintStatus];

export const ComplaintType = {
  // Tier 1
  COLD_FOOD: "COLD_FOOD",
  MISSING_ITEM: "MISSING_ITEM",
  BAD_TASTE: "BAD_TASTE",
  WRONG_ITEM: "WRONG_ITEM",
  // Tier 2
  OVERCHARGED: "OVERCHARGED",
  STALE_FOOD: "STALE_FOOD",
  HYGIENE: "HYGIENE",
} as const;
export type ComplaintType = (typeof ComplaintType)[keyof typeof ComplaintType];

export const KitchenAction = { START: "START", READY: "READY" } as const;
export type KitchenAction = (typeof KitchenAction)[keyof typeof KitchenAction];

export const CaptainAction = {
  ACCEPT: "ACCEPT",
  REJECT: "REJECT",
  PICKED_UP: "PICKED_UP",
  DELIVERED: "DELIVERED",
} as const;
export type CaptainAction = (typeof CaptainAction)[keyof typeof CaptainAction];

