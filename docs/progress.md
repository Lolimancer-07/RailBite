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

