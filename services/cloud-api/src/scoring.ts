export interface PenaltyRecord {
  complaintId: string;
  orderId: string;
  type: string;
  pointsDeducted: number;
  reason: string;
  timestamp: string;
}

export function calculatePenalty(type: string): number {
  const normalized = type.toUpperCase().trim();
  if (normalized.includes("OVERCHARG")) {
    return 10;
  }
  if (normalized.includes("HYGIENE")) {
    return 15;
  }
  if (normalized.includes("STALE")) {
    return 20;
  }
  return 10;
}
