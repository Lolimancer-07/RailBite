import { OrderStatus, Track } from "./enums.js";

type OS = typeof OrderStatus;
type StatusValue = OS[keyof OS];

/** All legal transitions per track */
const READY_TRACK_TRANSITIONS: Array<[StatusValue, StatusValue]> = [
  [OrderStatus.PLACED, OrderStatus.ASSIGNED],
  [OrderStatus.ASSIGNED, OrderStatus.ACCEPTED],
  [OrderStatus.ACCEPTED, OrderStatus.OUT_FOR_DELIVERY],
  [OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED],
  // exits
  [OrderStatus.PLACED, OrderStatus.CANCELLED],
  [OrderStatus.ASSIGNED, OrderStatus.CANCELLED],
  [OrderStatus.ASSIGNED, OrderStatus.EXPIRED],
];

const COOK_TRACK_TRANSITIONS: Array<[StatusValue, StatusValue]> = [
  [OrderStatus.PLACED, OrderStatus.COOKING],
  [OrderStatus.COOKING, OrderStatus.READY],
  [OrderStatus.READY, OrderStatus.ASSIGNED],
  [OrderStatus.ASSIGNED, OrderStatus.ACCEPTED],
  [OrderStatus.ACCEPTED, OrderStatus.OUT_FOR_DELIVERY],
  [OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED],
  // exits
  [OrderStatus.PLACED, OrderStatus.CANCELLED],
  [OrderStatus.ASSIGNED, OrderStatus.CANCELLED],
  [OrderStatus.ASSIGNED, OrderStatus.EXPIRED],
];

function buildSet(pairs: Array<[StatusValue, StatusValue]>): Set<string> {
  return new Set(pairs.map(([from, to]) => `${from}->${to}`));
}

const READY_SET = buildSet(READY_TRACK_TRANSITIONS);
const COOK_SET = buildSet(COOK_TRACK_TRANSITIONS);

export function canTransition(from: StatusValue, to: StatusValue, track: string): boolean {
  const key = `${from}->${to}`;
  if (track === Track.READY) return READY_SET.has(key);
  if (track === Track.COOK) return COOK_SET.has(key);
  return false;
}

export function assertTransition(from: StatusValue, to: StatusValue, track: string): void {
  if (!canTransition(from, to, track)) {
    throw new Error(
      `Illegal transition: ${from} -> ${to} on track ${track}`,
    );
  }
}

/** Returns the next expected status(es) for a given status and track */
export function nextStatuses(current: StatusValue, track: string): StatusValue[] {
  const transitions =
    track === Track.READY ? READY_TRACK_TRANSITIONS : COOK_TRACK_TRANSITIONS;
  return transitions.filter(([from]) => from === current).map(([, to]) => to);
}

/** Determine track: any COOK item makes the whole order COOK track */
export function determineTrack(itemTracks: string[]): typeof Track[keyof typeof Track] {
  return itemTracks.some((t) => t === Track.COOK) ? Track.COOK : Track.READY;
}

