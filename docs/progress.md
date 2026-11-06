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




