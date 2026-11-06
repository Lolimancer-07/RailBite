import type { Staff } from "@railbite/shared";

// Coach ordering along a typical Indian Railways rake
const COACH_ORDER = [
  "ENG",
  "GEN1",
  "SL1", "SL2", "SL3", "SL4", "SL5", "SL6", "SL7",
  "S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8",
  "PC", // Pantry car
  "B1", "B2", "B3", "B4", "B5", "B6",
  "A1", "A2",
  "H1",
  "GEN2",
];

function getCoachIndex(coach: string): number {
  const norm = coach.trim().toUpperCase();
  const idx = COACH_ORDER.indexOf(norm);
  if (idx !== -1) return idx;

  // Fallback heuristic: parse prefix and number
  const match = norm.match(/^([A-Z]+)(\d+)$/);
  if (match) {
    const [, prefix, numStr] = match;
    const num = Number(numStr);
    if (prefix === "B") return 20 + num;
    if (prefix === "A") return 30 + num;
    if (prefix === "S" || prefix === "SL") return 10 + num;
    if (prefix === "H") return 40 + num;
  }
  return 999;
}

export function coachDistance(coachA: string, coachB: string): number {
  return Math.abs(getCoachIndex(coachA) - getCoachIndex(coachB));
}

export function findBestCaptain(
  orderCoach: string,
  captains: Staff[],
  excludeCaptainIds: string[] = [],
): Staff | null {
  const candidates = captains.filter((c) => !excludeCaptainIds.includes(c.id));
  if (candidates.length === 0) return null;

  // 1. Direct match: online captain whose zone covers this coach
  const direct = candidates.find(
    (c) => c.online && c.zoneCoaches.map((z) => z.toUpperCase()).includes(orderCoach.toUpperCase()),
  );
  if (direct) return direct;

  // 2. Any captain whose zone covers this coach (even if currently marked offline, fallback)
  const directAny = candidates.find((c) =>
    c.zoneCoaches.map((z) => z.toUpperCase()).includes(orderCoach.toUpperCase()),
  );

  // 3. Nearest captain by distance to any coach in captain's zone (prefer online)
  let bestCaptain: Staff | null = null;
  let bestScore = Infinity;

  for (const c of candidates) {
    let minDistanceToZone = Infinity;
    for (const zc of c.zoneCoaches) {
      const d = coachDistance(orderCoach, zc);
      if (d < minDistanceToZone) minDistanceToZone = d;
    }
    // Give heavy preference to online captains (subtract 1000 from score or add 1000 penalty for offline)
    const score = minDistanceToZone + (c.online ? 0 : 1000);
    if (score < bestScore) {
      bestScore = score;
      bestCaptain = c;
    }
  }

  return bestCaptain || directAny || candidates[0] || null;
}
