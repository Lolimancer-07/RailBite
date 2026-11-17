# RailBite Build Progress

## Phase 0: Repo and Tooling ✅

**Completed:** 2024

**What works:**
- pnpm monorepo with workspace configuration
- TypeScript strict mode base config
- ESLint + Prettier configured
- All package/app/service directories scaffolded
- packages/shared wired with workspace dependency

**What is stubbed:**
- Expo apps are scaffold only (no prebuild yet)
- kitchen-display is scaffold only
- edge-server is scaffold only

---

## Phase 1: Shared Package ✅

**Completed:** 2024

**What works:**
- All enums (OrderStatus, Track, PaymentMode, Role, Source, ComplaintTier)
- Zod schemas: OrderCreate, Order, MenuItem, Complaint, Staff, all MQTT payloads
- MQTT topic builders
- State machine with all legal/illegal transitions
- Pricing helper (integer paise, never floats)
- Vitest tests: state machine (READY + COOK tracks), pricing, topic builders

**What is stubbed:**
- SMS token encode/decode (STRETCH, throws NotImplemented)


---

## Phase 2: Edge Server Core ✅

**Completed:** 2026-11-06

**What works:**
- Environment configuration with `config.ts` (PORT=8080, MQTT_PORT=1883, MQTT_WS_PORT=8883, TRIP_ID, DB_PATH).
- SQLite in WAL mode with auto-migrations on boot (`001_init.sql`).
- Seed script with 15 menu items (6 READY, 9 COOK), 120 passengers in manifest across B1-B4 and S1-S4, 4 captains with zones, 1 cook, and 1 trip.
- Repositories: `orderRepo`, `menuRepo`, `manifestRepo`, `staffRepo`, `ledgerRepo`, `complaintRepo`, `outboxRepo`.
- `orderService.create()` with full idempotency on client-generated UUID, server-authoritative re-pricing, manifest verification, atomic stock check & decrement.
- State machine enforcement rejecting illegal transitions with HTTP 409.
- Aedes MQTT broker with TCP (1883) and WebSocket (8883) listeners, ACL rules for passengers, captains, and kitchen.
- Dispatcher with coach zone mapping, 60s timeout, re-broadcast escalation, Cook batching (up to 4 orders or 90s), and persisted timer recovery on restart.
- Append-only cash ledger with expected vs collected vs confirmed summaries.
- REST endpoints: `/health`, `/menu`, `/staff/login`, `/orders` (POST, GET by id, GET by pnr), `/orders/:id/accept`, `/orders/:id/pickup`, `/orders/:id/deliver`, `/orders/:id/cancel`, `/orders/:id/confirm-cash`, `/kitchen/:id/start`, `/kitchen/:id/ready`, `/complaints`, `/ledger/summary`, `/sync/flush`, `/sms/ingest`.
- PM2 configuration (`pm2.config.js`) and Raspberry Pi hostapd/dnsmasq setup script (`infra/pi-setup.sh`).
- Automated test suite (Fastify inject + MQTT end-to-end integration tests).

**What is stubbed:**
- GSM hardware modem (SIM800L serial port) is stubbed behind `/sms/ingest` endpoint.
- Cloud sync store-and-forward is stubbed behind `/sync/flush` endpoint.

---

## Phase 3: Passenger App (Core Loop) ✅

**Completed:** 2026-11-06

**What works:**
- Offline-first SQLite database (`menu_items` cache, `orders`, `pending_actions` queue).
- Write-Then-Send architecture: every client action (order placement, cash confirmation, complaint) is written locally first before network dispatch.
- `queueWorker.ts`: delivers pending actions with exponential backoff and client-generated UUID idempotency key.
- Live connection state banner: Connected (green) / Searching (orange) / Offline with queued count (red).
- Onboarding screen: validates 10-digit PNR, coach format (e.g. B1, S2), berth (1-100), and diners count (1-8).
- Menu screen: cached offline, refreshed when connected, category filters, instant vs cook-to-order track badges, sold-out status, bottom tray summary bar.
- Tray/Cart screen: item increments/decrements, COD payment mode, write-then-send placement.
- Orders list screen: instant offline access to local order history, remote sync.
- Order details & tracking screen: step progression tracking, live MQTT subscription to `orders/{id}/status`, official MRP receipt, cash confirmation ("Did you pay Rs X?"), order cancellation (before cooking/accepted), file complaint link.
- Complaint screen: Tier 1 (operational 15-min replacement) and Tier 2 (regulatory escalation with photo SHA-256 evidence hashing).
- Tests: local SQLite queue insertion, offline Wi-Fi drop & recovery idempotency, and live status updates via MQTT.

**What is stubbed:**
- Native Kotlin WifiBinder is stubbed in TypeScript transport layer (Phase 5).

---

## Phase 4: Captain App + Alerts ✅

**Completed:** 2026-11-06

**What works:**
- Staff PIN login (staffId, pin) with session token generation.
- Full-screen urgent alert screen (`app/alerts.tsx`) with 60s countdown progress ring, unverified PNR alert (`verified=0`), itemized order summary, and big ACCEPT / REJECT buttons.
- Android foreground service Kotlin module (`AlertForegroundService.kt` + `AlertForegroundServiceModule.kt`) with `IMPORTANCE_HIGH` notification channel and full-screen intent so alerts ring when phone is locked or app is in background.
- Active deliveries screen (`app/active.tsx`): lists orders in captain's coach zone, Mark Picked Up, Mark Delivered + cash collected modal.
- Cash ledger reconciliation screen (`app/cash.tsx`): displays expected cash from delivered orders vs actual collected cash vs passenger-confirmed cash, with mismatch warnings.
- Complaints screen (`app/complaints.tsx`): monitors Tier 1 seat-side replacement requests with 15-minute countdown timers.
- Tests: MQTT alert reception, 60s countdown, accept/reject actions, delivery flow, and cash ledger tracking.

---

## Phase 5: Wi-Fi Binding Native Module ✅

**Completed:** 2026-11-06

**What works:**
- Kotlin native Expo module `wifi-binder` (`WifiBinderModule.kt`):
  - `connectToPantry(ssid, passphrase)` using `WifiNetworkSpecifier` and `ConnectivityManager.requestNetwork` with `NET_CAPABILITY_INTERNET` removed.
  - `bindProcessToNetwork(network)` so Android sockets route all traffic to pantry Wi-Fi even when the OS detects no internet connectivity.
  - Event emitter for `onPantryConnected` and `onPantryLost`.
  - `unbind()` for graceful disconnection.
  - Auto-reconnect loop helper with configurable interval.
- Shared and integrated into both passenger and captain apps.
- Raspberry Pi `hostapd` + `dnsmasq` setup script documented in `infra/pi-setup.sh`.

---

## Phase 6: Kitchen Display + Delivery + Cash ✅

**Completed:** 2026-11-06

**What works:**
- Vite React Kitchen Display tablet app (`apps/kitchen-display`):
  - Built and served directly by the edge server at `/kitchen` via `@fastify/static`.
  - Connects via WebSocket MQTT (`ws://host:8883`) to `kitchen/queue` and `kitchen/actions`.
  - Columns: New Orders (Placed) -> Cooking -> Ready, with high-contrast tablet UI.
  - Audio chime synthesizer on new incoming cook orders using Web Audio API.
  - Coach Batch View (`BatchView.tsx`): visualizes order grouping per coach to minimize captain walking hops through the rake.
  - Action buttons: "▶ START COOKING" and "✓ MARK READY", triggering captain dispatch.
- Cash ledger reconciliation:
  - `POST /orders/:id/deliver` appends to append-only `cash_ledger`.
  - Passenger confirms cash paid ("Did you pay Rs X?").
  - `GET /ledger/summary` verifies expected vs collected vs confirmed totals per captain, flagging mismatches.

---

## Phase 7: Complaints Routing & Escalation ✅

**Completed:** 2026-11-06

**What works:**
- Tier 1 complaints (Cold Food, Missing Item, Bad Taste, Wrong Item):
  - Displays on Kitchen Display `ComplaintPanel` and Captain `complaints.tsx` with 15-minute countdown timers.
  - Operational replacement order flow targeting seat-side delivery within 15 minutes.
- Tier 2 complaints (Overcharging, Stale Food, Hygiene):
  - Requires tamper-evident photo proof (SHA-256 hash + timestamp).
  - Automatically written to `outbox` as `COMPLAINT_ESCALATION` for store-and-forward sync to regulatory bodies (IRCTC / RailMadad).
  - Rate limiting enforced: max 3 Tier 2 complaints per PNR per trip (subsequent requests rejected with HTTP 429).

---

## Phase 8: Simulator & Performance Metrics ✅

**Completed:** 2026-11-06

**What works:**
- High-concurrency load simulator (`tools/simulator/src/simulator.ts`):
  - Spawns 50 concurrent simulated passengers over REST/MQTT across 6 coaches.
  - Measures order-to-captain-alert latency distribution: p50 (18ms), p95 (45ms < 1000ms target), p99 (68ms), max (89ms).
  - Throughput: 42+ orders/second.
- Fault injection:
  - Simulated 30% packet drop rate with client retry queue recovery.
  - 100% recovery with zero lost orders.
- Duplicate detection:
  - 100 duplicate order submissions tested against edge server idempotency.
  - Exactly 0 duplicate charges in append-only cash ledger.
- Coach batching walking hop experiment:
  - Compares staff coach travel distance for 60 orders unbatched vs batched (max 4/coach).
  - Demonstrates a **70% reduction in physical crew walking distance** through crowded passenger coaches.
- Metrics report automatically generated at `docs/metrics.md`.
- Automated test suite in `services/edge-server/src/__tests__/phase8-simulator.test.ts` (4/4 tests passing).

---

## Phase 9: SMS Token Path (STRETCH) ✅

**Completed:** 2026-11-06

**What works:**
- Cryptographic SMS token encoding & decoding (`packages/shared/src/sms-token.ts`):
  - Pure TypeScript zero-dependency implementation of SHA-256 and HMAC-SHA256.
  - Token format: `RB#<pnrLast4>#<coach>-<berth>#<diners>#<itemCodes>#<nonce>#<hmac8>`.
  - Item short code compressor & parser for 15 pantry items (`CH1,SA2`).
  - Outbound SMS ACK message format: `RB#ACK#<orderIdLast6>#<totalPaise>#<etaMin>`.
  - Comprehensive unit test suite in `packages/shared/src/__tests__/sms-token.test.ts` (5/5 tests passing).
- Passenger App offline fallback:
  - Detects no-WiFi connection state in `cart.tsx`.
  - Pre-fills SMS token with `sms:+919876543210?body=...` using React Native `Linking`.
  - Saves order locally in SQLite queue and immediately routes passenger to tracking screen.
- Edge server GSM modem ingestion:
  - `POST /sms/ingest` verifies HMAC signature using authoritative trip secret.
  - Replay prevention: checks unique 4-hex nonce against `sms_nonces` table, rejecting replays with HTTP 409 Conflict.
  - Passenger manifest matching: extracts full PNR and marks `verified = 1` for manifested passengers.
  - Re-prices order using official edge MRP table, deducts inventory stock, creates order with `source: 'SMS'`.
  - Returns calculated total paise and estimated prep/delivery time in SMS ACK format.
- `tools/sms-simulator`:
  - CLI script simulating GSM modem posting valid, tampered, and replay tokens.
- Automated test suite in `services/edge-server/src/__tests__/phase9-sms.test.ts` (4/4 tests passing).
- Protocol documentation written to `docs/sms-token.md`.

---

## Phase 10: Cloud Sync & Admin Dashboard (STRETCH) ✅

**Completed:** 2026-11-07

**What works:**
- Cloud API Service (`services/cloud-api`):
  - Fastify service running on port 8090 with SQLite/Postgres compatibility and `docker-compose.yml`.
  - Ingestion endpoint: `POST /sync/batch` ingests batches of outbox records idempotently with unique constraint on `(trip_id, seq)`.
  - Regulatory License Scoring engine (`scoring.ts`):
    - Starts at 100 points.
    - Automatically penalizes contractor license score on incoming Tier 2 escalations:
      - Overcharging: -10 points
      - Hygiene violations: -15 points
      - Stale/spoiled food: -20 points
    - Flags contractor as `CRITICAL_REVOKE_RISK` when score drops below 70 points.
  - Endpoints: `GET /admin/scores`, `GET /admin/complaints`, `GET /admin/ledger`, `GET /health`.
  - Automated test suite in `services/cloud-api/src/__tests__/cloud-sync.test.ts` (2/2 tests passing).
- Edge Server Store-and-Forward Worker (`services/edge-server/src/sync/outboxWorker.ts`):
  - Polls unsynced outbox records (`synced_at IS NULL`).
  - Pushes batch to `${CLOUD_API_URL}/sync/batch`.
  - On successful acknowledgment (`ackedUpTo`), marks rows as synced with atomic timestamp.
  - Features exponential backoff with jitter on network disconnection or cloud unreachability.
  - Automated test suite in `services/edge-server/src/__tests__/phase10-outbox-worker.test.ts` (1/1 test passing).
- IRCTC Admin Governance Dashboard (`apps/admin-dashboard`):
  - Vite + React + TypeScript web app running on port 5174.
  - Live contractor license governance card with status badges (Green / Yellow Warning / Red Revocation Risk).
  - Regulatory complaints feed with verified SHA-256 photo evidence hashes.
  - Onboard pantry cash ledger reconciliation table (collected vs passenger-confirmed vs IRCTC expected).

---

## Phase 11: Device-to-Device Relay Simulation (STRETCH) ✅

**Completed:** 2026-11-07

**What works:**
- Device-to-Device Mesh Protocol Specification (`docs/relay-protocol.md`):
  - Relay packet envelope: `{ v: 1, orderId, ttl, origin, payloadB64, hmac8, hops }`.
  - Forwarding logic with 500-item LRU deduplication, loop prevention (`hops.includes(self.id)`), and TTL decrement.
  - BLE advertising beacon vs GATT connection profile specs.
  - Fixed ESP32 coach vestibule node hardware architecture.
- 24-Coach Rake Linear Network Simulator (`tools/relay-sim/src/relay.ts`):
  - Scenario A: Models linear graph traversal from Coach 24 (S17) to Pantry (Coach 12), measuring 12 hops and path delivery.
  - Scenario B: Simulates 2 broken coach nodes, verifying partition detection in 1-hop standard phone BLE versus single-node gap bridging in ESP32 2-hop coded PHY.
  - Scenario C: High-concurrency flood (100 simultaneous orders), demonstrating 96% delivery efficiency and suppression of broadcast storms via LRU deduplication.
  - Comprehensive comparison report: Pure phone BLE vs fixed ESP32 coach backbone.
  - Automated test suite in `tools/relay-sim/src/__tests__/relay.test.ts` (5/5 tests passing).





---

## Phase 12: Prototype — Quick-Commerce Customer + Picker Demo ✅

**Completed:** 2026-11-07
**Branch:** `prototype` (commits proto-0 … proto-5)

**Goal:** Demo-ready "Blinkit for train pantry food" on two Android phones + laptop edge server over LAN.

**What was built:**
- `packages/shared`: added `PACKING` order status (ACCEPTED → PACKING → OUT_FOR_DELIVERY) on both READY and COOK tracks; state machine rejects illegal transitions with 409.
- Edge server (`services/edge-server`):
  - `POST /orders/:id/packing`, `POST /staff/:id/online`, `GET /staff/:id/stats` endpoints.
  - Dispatch is now online-only (offline pickers are skipped).
  - Seed rewritten for the prototype: 15 READY (instant) items across 5 categories (Beverages, Water, Snacks, Meals, Breakfast), 4 pickers (c1–c4, PIN 1234), high stock, PNR manifest 8492000001–8492000120. No COOK items in the demo seed.
- Passenger app (`apps/passenger`): quick-commerce home (seat chip, "~10 mins", search, category strip, product grid, ADD→stepper, sold-out overlay), floating cart bar, COD-only cart with bill summary, bottom tabs (Home / Orders), PACKING-aware tracking timeline with "Did you pay ₹X?" cash confirmation, Tier-1-only "Report a problem", server URL Settings screen + "Connected / Cannot reach server" banner, session persistence.
- Picker app (`apps/captain`, UI renamed "Picker"): Staff ID + PIN login with server URL field, ONLINE/OFFLINE toggle, today's stats (delivered / collected / confirmed), full-screen loud new-order alert (reuses AlertForegroundService — rings when locked, high-importance notification backup), picking checklist that marks PACKING and is disabled until all items are ticked, Out for delivery, Delivered + cash collection modal, cash ledger kept.

**Intentionally disabled (hidden, not deleted) for the prototype:**
- "Order via SMS" button hidden in passenger cart (SMS/BLE/relay/cloud sync code untouched).
- Tier 2 complaint flow (photo evidence, regulatory escalation) hidden — only simple Tier 1 reports.
- COOK items removed from seed so no kitchen pipeline appears in the demo; `apps/kitchen-display` left untouched.

**Notes / limitations:**
- Online-only dispatch; picker presence resets on server restart.
- Picker stats are UTC-day based.
- Expo apps pass `tsc` but have not been device-tested; no unit tests for app UI.
- Passenger app `vitest` tests exist in `src/__tests__` but have no `test` script (intentionally excluded from `pnpm -r test`).

---

## Phase 13: FOT-inspired prototype features ✅

**Completed:** 2026-11-07
**Branch:** `prototype` (commits proto-6, proto-7)

**Source:** Reverse-engineered IRCTC Food on Track v2.28.0 APK (`com.irctc.fot`) via apktool decode (resource/manifest/string extraction; code-level JADX blocked pending `REA_JADX_MCP_JAR`). Ported feature patterns: delivery-code/OTP handover, structured feedback & rating, cart upsell + veg markers + How-it-works, richer status subtexts & timeline.

**Backend (`services/edge-server`, proto-6):**
- Migration `002_fot_prototype.sql`: `menu_items.is_veg` (default true), `orders.delivery_code`, new `order_feedback` table (received/rating/reasons/created_at).
- Idempotent migration runner: splits on `/;\s*\n`, strips `--` comment lines before executing, tolerates "duplicate column name" only.
- `OrderRepo`: `setDeliveryCode`/`getDeliveryCode`/`upsertFeedback`/`getFeedback`; `delivery_code` on `OrderRow`.
- `OrderService`: pickup generates a stable 4-digit delivery code (stored, stable across reads) + `captainName` in metadata; `deliver` validates the repeated code → `403 DeliveryCodeError` ("Invalid delivery code — ask the passenger to reveal it") on mismatch, skipped only when no stored code; `submitFeedback` → `409` unless DELIVERED, upsert into `order_feedback`.
- Routes: `GET /orders/:id?role=picker` strips `deliveryCode`; `POST /orders/:id/deliver` accepts `deliveryCode` and maps `DeliveryCodeError` → 403; new `POST /orders/:id/feedback`.
- `menuRepo.map is_veg`; `ledgerRepo.getStaffStats` gains `avgRating` (1-decimal, all-time via assignments.delivered_at join) + `feedbackCount`.
- Seed: 16 items (added "Chicken Frankie Roll" MEALS ₹65 non-veg); all others isVeg=true.
- Docs: `docs/api-contract.md`, `docs/state-machine.md` updated.
- New `__tests__/fot-features.test.ts` (4 tests). 4 existing deliver sites updated to fetch & pass the code; edge-server test asserts wrong code → 403; menu count 15→16. All 83 tests pass; full build green.

**Passenger app (`apps/passenger`, proto-7):**
- Home: veg 🟢/non-veg 🔴 dot marker on every product tile; "How it works" card (permanent entry) + modal that auto-opens once per install (persisted `how_it_works_dismissed` in settings KV).
- Cart: "You might have missed" horizontal upsell strip (3 cached menu items not in cart, quick ADD).
- Tracking: FOT-style named-picker subtexts ("%s is on the way to your seat!" via `displayName()` stripping the "(Picker B1-B2)" suffix), "Order Received" when delivered; blue Delivery Code card with masked `••••` tap-to-reveal; feedback card states prompt→received/not-received→done with Yes/Received, ★ stars (amber selected), reason chips; guards on `order.feedback`; `restClient.submitFeedback` + `FeedbackPayload`.
- Typecheck clean (`tsc --noEmit`).

**Picker app (`apps/captain`, proto-7):**
- `captainRestClient`: `getOrderById` fetches `?role=picker` (code stripped); `deliverOrder` accepts `deliveryCode`; `PickerStats` gains `avgRating`/`feedbackCount`.
- `app/order/[id].tsx` delivery modal: 4-digit code TextInput + inline server-error surfacing on 403.
- `app/active.tsx`: stats row gains avg rating card ("4.5 ★ (3)"); delivery modal gains code input + error; `handleConfirmDelivery` passes the code and surfaces delivery-code mismatch inline.
- Typecheck clean (`tsc --noEmit`).

**Notes / limitations:**
- Delivery code is visible to anyone who can GET `/orders/:id` as passenger role (same endpoint the passenger uses); no online payments; coupons/slots intentionally not ported from FOT.
- Expo apps pass `tsc` but have not been device-tested; no unit tests for app UI.
- Named-picker status subtext only appears after a live MQTT update (which carries `captainName` from the pickup transition); the REST response doesn't include `captainName`.

---

## Phase 14: Standalone Release APKs & Native Module Assembly ✅

**Completed:** 2026-11-13  
**Artifacts Generated:**
- `release/railbite-passenger.apk` (~82 MB, package `com.railbite.passenger`, versionCode 1, versionName 1.0.0)
- `release/railbite-picker.apk` (~82 MB, package `com.railbite.picker`, versionCode 1, versionName 1.0.0)

**What works:**
- **Standalone Offline-Embedded Builds**: Both APKs package full JavaScript bundles and compiled Hermes bytecode. No Metro bundler or dev server is needed at runtime.
- **Expo SDK 51 & Gradle 8.8 Alignment**: Local Gradle assembly configured using Eclipse Temurin JDK 17 (`JAVA_HOME`) and Android SDK (compileSdk 34, minSdk 29, targetSdk 34).
- **Native Modules Preserved & Autolinked**:
  - `alert-foreground-service`: Foreground notification service with wake lock, continuous loud chime/vibration, and full-screen intent for urgent coach order dispatch. Kotlin return types updated to `Boolean` to resolve reified type inference in Expo Modules DSL.
  - `wifi-binder`: Custom `bindProcessToNetwork` module for binding low-level sockets directly to the pantry Wi-Fi without internet connectivity.
- **Cleartext HTTP/WS Traffic**: Enabled via `expo-build-properties` (`usesCleartextTraffic = true`) allowing connection to local prototype server endpoints (`http://` and `ws://`).
- **Dynamic LAN Configuration**: Both apps allow changing the server host and port via in-app UI (Settings screen on passenger app, Login screen on picker app) with fallback to `EXPO_PUBLIC_SERVER_URL`.
- **All Monorepo Unit & Integration Tests Passing**: 83/83 automated tests passed across all services and packages (`pnpm -r test`).

## Phase 15: Cloud deploy + FCM push dispatch (native apps) ✅

**Completed:** 2026-11-16

**Cloud (Render).** The baked-in cloud URL `https://railbite-api.onrender.com` had **no service behind it**
(`x-render-routing: no-server`) — the root cause of the "apps look old / orders don't reach the picker on a real
phone" report. The `railbite-api` web service was created via the Render API mirroring `render.yaml`
(docker runtime, branch `native-android` — pinned in `render.yaml` because the repo's default branch `main`
predates all Android-era server code; free plan, `oregon`, healthCheck `/health`). First deploy went live in
~2 min; env-var updates (bulk `PUT /v1/services/{id}/env-vars`) + `POST /v1/services/{id}/deploys` redeploys
go live in ~20 s. `autoDeploy: yes` redeploys on every push to `native-android`. Free plan: sleeps after
~15 min idle (~30–60 s first-request wake), ephemeral DB (menu/staff/PNR re-seed on boot; orders/ledger reset).

**Cloud UPI E2E (UPI_QUEUED, ₹30).** create → dispatch (ASSIGNED ~1 s) → alert → accept → packing →
out-for-delivery (code 4698) → deliver with `cashPaise: 0` → DELIVERED → confirm-cash (`passengerConfirmed`,
₹0 ledger row). Physical Redmi Note 12 (USB, `c7a216a00000`) received the live-cloud MQTT alert and rendered
AlertActivity; fresh-order dispatch latency measured at ~1.4 s.

**FCM push dispatch.** Firebase project `railbite-7748b`:
- App side: `android/picker/google-services.json` (app `com.railbite.picker`) — the picker's
  `build.gradle.kts` already conditionally applies `com.google.gms.google-services`; release rebuilt,
  installed on the physical phone. Verified in logcat: `FirebaseApp initialization successful`,
  FCM token generated, `POST /staff/device-token` → 200.
- Server side: Firebase Admin SDK service-account JSON set as `GOOGLE_SERVICE_ACCOUNT_JSON` on the Render
  service (never committed; lives only in Render env). Dispatcher's `sendFcmAlert` (broadcast + direct
  dispatch) is now live.
- End-to-end on the physical device: order → dispatcher → **FCM data message received**
  (`PickerFCM: FCM order dispatch data message received: {type=ORDER_DISPATCH, paymentMode=UPI_QUEUED, …}`).
  MQTT remains the primary in-app alert path; FCM adds the backgrounded/wake-up channel.


## Phase 16: Full UI redesign — token-based theme across both Android apps ✅

**Completed:** 2026-11-16 (builds+tests green; versions bumped to 1.2.0/vc4 both apps)

**Token foundation (core module, single source of truth):**
- `core/ui/theme/Tokens.kt` — palette (ink `#141414`, bg `#FFFFFF`, surface `#F4F4F2`, divider
  `#E6E5E0`, muted `#5F6368`, accent `#0B6B3A` actions-only, warning `#F5B942` on dark), type sizes
  (12/14/15/17/24/34/44 only), 4/8 spacing scale (8/12/16/20/24, screen padding 20), radii
  (8 controls / 10 cards / 12 tiles), metrics (44 dp tap target, 56 dp primary button).
- `Type.kt` — Inter variable font (`core/src/main/res/font/inter_var.ttf`, 400/500/600/700 via
  `FontVariation`, `@OptIn(ExperimentalTextApi)`); every M3 role maps onto the seven sizes.
- `Theme.kt` — light/dark schemes derived from tokens (one accent hue; secondary mirrors accent),
  shapes 8/10/12. Public `RailBiteTheme` API unchanged (source-safe for both apps).
- `core/ui/components/Ui.kt` — `RailBiteButton` (56 dp), `RailBiteSecondaryButton` (≥44 dp),
  `RailBiteTextField` (labelled; keyboardActions/minLines supported), `RailBiteCheckboxRow`,
  `RailBiteFilterChip` (≥44 dp), `RailBiteStepper` (44 dp controls), `SkeletonBlock/Row`,
  `EmptyState`, `ErrorBar`, `SectionTitle`, `iconDescription`.
- `core/ui/Common.kt` — `ProductEmoji` removed → flat `ProductThumb` (letter placeholder; real
  photos slot in later); emoji-free `EmptyState(title, subtitle)`.

**Customer app (7 screens):** Tracking (delivery code 44 sp at top + exact hint "Tell this to the
person at your seat. Do not share it earlier."), Home (flat product tiles, ₹ prices via
`Money.rupees` — also fixed a double-₹ bug, ≥44 dp ADD/stepper, no emoji), Cart, Orders, Report,
Settings, Onboarding. All VM/cart/MQTT/state wiring untouched.

**Picker app (7 screens):** AlertActivity (forced dark `#141414` via `RailBiteTheme(darkTheme=true)`,
warning-amber countdown ≤10 s, 56 dp Accept), OrderProgress (packing checklist rows ≥44 dp, deliver
flow still code-before-cash), Home, Login, CashSummary, Permissions, MeshHandover. Notification
title de-emojified.

**Rules enforced:** no gradients/decoration shadows/emoji; no hard-coded colours/sizes in screens
(tokens only); content descriptions on icon-only controls; loading skeletons + empty states +
human-readable errors everywhere; no business-logic changes.

**Validation:** `:core:assembleRelease`, `:customer:assembleRelease`, `:customer:test`,
`:picker:assembleRelease`, `:picker:test` — all green (sequential single-invocation builds).

## Phase 17: Offline BLE mesh ordering on by default ✅

**Problem:** the full offline path already existed (BLE advertise/scan, mesh packets, outbox,
ledger, cloud replay) but was opt-in in hidden settings and gated behind cloud checks — with the
train Wi-Fi dead both apps just waited on the server and errored.

**Change (13 files, commit `1e9055a`):**
- `RailBiteSettings.kt` — mesh relay (customer) and sink (picker) default **ON**; Settings
  toggles remain as explicit opt-outs.
- `ApiClient.reachable(serverUrl, timeoutMs=2000)` — fast `/health` probe used to choose
  between cloud and Bluetooth relay without waiting out full HTTP timeouts.
- `MeshStore.entryOf(orderId)` — outbox lookup so tracking can follow a relayed order locally.
- Picker `RailBiteAlertService` — starts the mesh gateway first, independent of cloud/URL;
  permission-checked so scan never throws. Home shows "Cloud unreachable — listening over
  Bluetooth" chip instead of the retry error while the service is up.
- Customer cart — ~2 s probe; offline COD orders burst over Bluetooth immediately ("Sending
  over Bluetooth…"), relay code shown at once. UPI stays online-only (relay settles in cash).
- Customer tracking — local offline timeline for relayed orders (banner, code block with
  offline hint, items card, "Waiting for a picker nearby"); no cloud polling; flips to the
  cloud timeline automatically once the replay syncs. Cancel hidden while relayed.
- Customer `MainActivity` — outbox auto-sync on app open + on every connectivity return
  (`NetworkCallback`); Bluetooth runtime permissions requested at startup (were never
  requested before — advertise would have failed on Android 12+).
- Both apps 1.2.1 (versionCode 5).

**Server:** no changes — mesh replay reuses existing endpoints (`source:"RELAY"`).

**Validation:** `:core:assembleRelease`, `:picker:assembleRelease :picker:test`,
`:customer:assembleRelease :customer:test` — all green (sequential module-by-module builds;
a 3-module single invocation GC-thrashed the daemon again). APKs copied to `release/` and
installed on the Redmi (Success ×2).

**Pending:** GitHub push of `1e9055a` (no credentials configured on this machine) and the
live offline demo (airplane mode → BT only → order → accept → handover → sync).
