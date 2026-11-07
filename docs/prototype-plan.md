# RailBite Prototype Plan (quick-commerce style demo)

Goal: turn RailBite into a demo-ready "Blinkit for train pantry food" prototype — Customer app + Picker app — with the edge server running on a laptop over LAN. Kitchen/COOK track, offline paths (SMS/BLE/relay), Tier 2 complaints, admin dashboard and cloud sync stay in the repo but are hidden/disabled, and nothing breaks.

## Out of scope handling (disable, don't delete)

| Feature | Action |
|---------|--------|
| Kitchen display / COOK track | Leave `apps/kitchen-display` and all COOK code untouched. Seed no COOK items; the COOK pipeline still exists for kitchen tests (kitchen test seeds its own COOK item). |
| "Order via SMS" button | Hidden in `apps/passenger/app/cart.tsx` (code kept, not rendered). |
| Tier 2 complaints + photo flow | Hidden in `apps/passenger/app/complaint/[orderId].tsx` (only Tier 1 "Report a problem" types offered). |
| Offline queue / BLE / SMS / cloud sync / admin | Untouched; still exists behind the scenes. |

## Backend (services/edge-server + packages/shared)

1. **PACKING status**
   - `packages/shared/src/enums.ts`: add `PACKING` to `OrderStatus`.
   - `packages/shared/src/state-machine.ts`: READY + COOK tracks get `ACCEPTED → PACKING → OUT_FOR_DELIVERY`. Illegal transitions still throw (HTTP 409).
   - Existing `POST /orders/:id/pickup` (ACCEPTED → OUT_FOR_DELIVERY) becomes illegal mid-flow — the picker screens will use `PACKING → OUT_FOR_DELIVERY`. Tests updated accordingly.
2. **New endpoints** (routes/orders.ts, routes/staff.ts, orderService, staffRepo, ledgerRepo)
   - `POST /orders/:id/packing` {captainId} → ACCEPTED → PACKING (mark packing started, enables customer "Packing" step).
   - `POST /staff/:id/online` {online:boolean} → picker online/offline toggle.
   - `GET /staff/:id/stats` → today's orders delivered, cash collected, expected, passenger-confirmed (picker home stats + cash screen).
3. **Dispatch keeps pricing/idempotency/stock/state machine fully authoritative.**
   - `findBestCaptain` now only ever picks ONLINE captains ("only online pickers receive orders").
4. **Seed rewrite** (`src/seed/seed.ts`)
   - 15 instant (READY) products across 5 categories: BEVERAGES, SNACKS, MEALS, BREAKFAST, WATER — believable MRPs, high stock for demos.
   - 4 pickers c1–c4 (PIN `1234`, documented in README) + cook kept for kitchen plumbing.
   - Manifest unchanged: 120 passengers, PNRs `8492000001`–`8492000120` in coaches B1–B4/S1–S4 (test combos documented in README).
5. **Docs**: api-contract.md (new endpoints/status), state-machine.md (PACKING step), progress.md notes.
6. **Tests**: shared state-machine test covers PACKING; edge-server test file for packing endpoint, online toggle and stats; kitchen test seeds its own COOK item since demo seed has none.

## Passenger (customer) app — apps/passenger

- **Server URL setting + indicator**: `EXPO_PUBLIC_SERVER_URL` env default, editable in a Settings screen (persisted locally via a `settings` KV table in expo-sqlite), clear "Connected / Cannot reach server" banner.
- **Session persisted** locally (PNR 10 digits, coach, berth; diners default 1 kept but out of the way).
- **First launch** onboarding stays, restyled + shows test PNR hint.
- **Bottom tabs Home / Orders** via `(tabs)` route group.
- **Home screen (quick-commerce style)**: header with editable seat chip + "Delivery in ~10 mins", search bar, emoji category strip, 2-column product grid, ADD → − 1 + stepper, Sold-out, floating cart bar. Own RailBite palette (green/indigo), no copied brand assets.
- **Cart**: steppers, bill summary (item total, delivery fee ₹0, grand total), COD fixed, Place Order.
- **Tracking**: Blinkit-like timeline Order placed → Picker assigned → Picking → Packing → Out for delivery → Delivered, picker name, ETA, items, total, "Did you pay ₹X?" confirm.
- Skeletons/empty states/friendly error toasts kept; no crash when server unreachable.

## Picker app — apps/captain (folder name kept)

- UI title "RailBite Picker" (folder rename not worth the native-module churn).
- Login (staff ID + 4-digit PIN) kept; server URL field added.
- Home: big ONLINE/OFFLINE toggle, today's stats (orders delivered, cash collected), active order cards.
- New `app/order/[id].tsx`: picking checklist with per-item checkboxes, `POST /orders/:id/packing` on start, "Packed, start delivery" enabled only when all ticked (→ OUT_FOR_DELIVERY), Out for delivery → Deliver + cash modal.
- Alert flow: reuse `AlertForegroundService` full-screen intent + alarm channel + looping vibration (leads with sound while ringtone plays), plus high-importance notification = the FGS notification itself (backup on lock screen). Reject/timeout re-dispatch uses existing dispatcher.

## Order of work (commits)

- proto-1: shared PACKING status + state machine + tests
- proto-2: edge server packing/stats/online endpoints, online-only dispatch, seed rewrite, tests + docs
- proto-3: passenger app quick-commerce UI + tabs + settings + tracking timeline
- proto-4: picker app UI (toggle, stats, checklist, cash)
- proto-5: README "Run the prototype", progress notes, final build+test pass
