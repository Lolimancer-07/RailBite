# Order State Machine

## READY Track (Prototype default)

In the prototype every item is instant ready-to-serve, so every order runs the READY track:

```
PLACED → ASSIGNED → ACCEPTED → PACKING → OUT_FOR_DELIVERY → DELIVERED
   ↓          ↓
CANCELLED  CANCELLED
              ↓
           EXPIRED
```

Simplest demo variant: PLACED → ASSIGNED → ACCEPTED → PACKING → OUT_FOR_DELIVERY → DELIVERED. §Kitchen/COOK plumbing below stays unchanged but has no seeded items.

## COOK Track

For orders containing any COOK track items (thali, paratha, omelette). Mixed orders default to COOK track. No COOK items are seeded in the prototype; editor must set the seed flag for kitchen in the test suite:

```
PLACED → COOKING → READY → ASSIGNED → ACCEPTED → PACKING → OUT_FOR_DELIVERY → DELIVERED
   ↓                                  ↓        
CANCELLED                          CANCELLED
                                       ↓
                                    EXPIRED
```

**Timing:**
- PLACED → COOKING: immediate (kitchen display receives notification)
- Batching: up to 90s or 4 orders per coach before assigning picker
- Picker has 60s to accept before re-broadcast

## Terminal States

- **DELIVERED**: Order complete, cash collected
- **CANCELLED**: Passenger cancelled (only before ACCEPTED/COOKING)
- **EXPIRED**: No picker accepted after 3 re-broadcasts (10 min)

## Implementation

The `canTransition(from, to, track)` function in `packages/shared/src/state-machine.ts` enforces all transitions. The edge server returns HTTP 409 for illegal transitions.

Routing/HTTP map:
- ACCEPTED → PACKING: `POST /orders/:id/packing`
- PACKING → OUT_FOR_DELIVERY: `POST /orders/:id/pickup`
- OUT_FOR_DELIVERY → DELIVERED: `POST /orders/:id/deliver` (requires the 4-digit delivery code generated on pickup; `403` on mismatch — FOT-style handover)
- Post-DELIVERED feedback: `POST /orders/:id/feedback` — `409` if the order is not DELIVERED (FOT-style "Did you receive this order?" + star rating)

