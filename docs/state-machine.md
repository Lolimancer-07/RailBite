# Order State Machine

## READY Track

For orders containing only READY track items (chai, coffee, biscuits, chips, water, samosa, packed biryani).

```
PLACED → ASSIGNED → ACCEPTED → OUT_FOR_DELIVERY → DELIVERED
   ↓          ↓
CANCELLED  CANCELLED
              ↓
           EXPIRED
```

**Timing:**
- PLACED → ASSIGNED: immediate on order creation
- Captain has 60s to accept before re-broadcast
- After 3 failed attempts (10 min total): EXPIRED

## COOK Track

For orders containing any COOK track items (thali, paratha, omelette). Mixed orders default to COOK track.

```
PLACED → COOKING → READY → ASSIGNED → ACCEPTED → OUT_FOR_DELIVERY → DELIVERED
   ↓                           ↓          
CANCELLED                   CANCELLED
                                ↓
                             EXPIRED
```

**Timing:**
- PLACED → COOKING: immediate (kitchen display receives notification)
- Batching: up to 90s or 4 orders per coach before assigning captain
- Captain has 60s to accept before re-broadcast

## Terminal States

- **DELIVERED**: Order complete, cash collected
- **CANCELLED**: Passenger cancelled (only before ACCEPTED/COOKING)
- **EXPIRED**: No captain accepted after 3 re-broadcasts (10 min)

## Implementation

The `canTransition(from, to, track)` function in `packages/shared/src/state-machine.ts` enforces all transitions. The edge server returns HTTP 409 for illegal transitions.

