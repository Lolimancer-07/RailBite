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
