# RailBite Picker UI & Quick-Message Plan

## 1. Repository & Module Audit

### 1.1 Workspace & Backend (`packages/shared`, `services/edge-server`)
- **Shared Contracts (`packages/shared`)**:
  - `src/enums.ts`, `src/state-machine.ts`: Order lifecycle `PLACED → ASSIGNED → ACCEPTED → PACKING → OUT_FOR_DELIVERY → DELIVERED` (with `CANCELLED` / `EXPIRED` terminal exits).
  - `src/topics.ts`: MQTT topics under `rb/{tripId}`, including `orders/{orderId}/status`, `orders/{orderId}/chat`, and `captain/{captainId}/alerts`.
  - `src/schemas/message.ts`: `OrderMessageCreateSchema`, `OrderMessageSchema`, `CUSTOMER_CHAT_PRESETS` (`WHERE_ARE_YOU`, `AT_SEAT`, `NEAR_DOOR`, `NEED_CHANGE`), `PICKER_CHAT_PRESETS` (`IM_HERE`, `ENTERING_COACH`, `TWO_MINS`, `PACKING`), max body length 120 chars.
  - `src/schemas/order.ts`, `src/schemas/staff.ts`: Zod contracts for orders (`paymentMode: COD | UPI_QUEUED`), staff login (`password` or legacy 4–8 digit `pin`), and ledger entries.
- **Edge Server (`services/edge-server`)**:
  - Fastify HTTP + Aedes MQTT-over-WebSocket (`/mqtt` on the same HTTP port `8080`, plus optional TCP `1883`) + SQLite WAL (`PRAGMA journal_mode = WAL; PRAGMA foreign_keys = ON`) / Postgres dual layer.
  - Seed (`src/seed/seed.ts`): Pickers `c1`–`c4` (`B1–B2`, `B3–B4`, `S1–S2`, `S3–S4`, PIN `1234`, password `railbite123`), 16 instant `READY` items across 5 categories (`BEVERAGES`, `WATER`, `SNACKS`, `MEALS`, `BREAKFAST`), 120 manifested PNRs (`8492000001`–`8492000120`).
  - Migration `004_order_messages.sql`: `order_messages(id TEXT PRIMARY KEY, order_id TEXT NOT NULL, sender_role TEXT NOT NULL, sender_id TEXT, preset_code TEXT, body TEXT NOT NULL, created_at INTEGER NOT NULL)` + `idx_order_messages_order_created(order_id, created_at)`.
  - Quick-Chat REST & MQTT: `GET /orders/:id/messages` and idempotent `POST /orders/:id/messages` (rejects with `409` outside `ACCEPTED` / `PACKING` / `OUT_FOR_DELIVERY`; publishes QoS 1 to `rb/{tripId}/orders/{orderId}/chat`). Aedes ACL (`src/mqtt/broker.ts`) allows both `p-*` and `c-*` clients to subscribe to `rb/{tripId}/orders/*/chat`.
  - Picker Ops Read Endpoints added in Phase 1:
    - `GET /staff/:id/orders`: Returns active non-terminal orders (`ACCEPTED`, `PACKING`, `OUT_FOR_DELIVERY`) assigned to picker `:id` with categorized `items` and `deliveryCode` stripped, backing multi-order Active Queue on `PickerDashboardScreen`.
    - `GET /staff/:id/ledger`: Returns `{ staffId, summary, entries }` joining `cash_ledger` + `orders` chronologically (newest first), backing the filterable `All` / `Confirmed` / `Awaiting Passenger` transaction list on `CashLedgerScreen`.

### 1.2 Android Gradle & Native Modules (`android/`)
- **Gradle (`android/gradle/libs.versions.toml`, `gradle.properties`)**:
  - AGP `8.6.1`, Kotlin `2.0.21`, Compose BOM `2024.12.01`, `minSdk 29`, `compileSdk/targetSdk 35`, Java 17 toolchain (`org.gradle.java.home`).
  - `android.enableR8.fullMode=false` with explicit HiveMQ/Netty/jctools keep rules (`netty-codec-http:4.1.118.Final` pinned in `:core`).
- **`:core` (`com.railbite.core`)**:
  - `ApiClient` + `RailBiteApi` (Retrofit + `kotlinx.serialization`), `RailBiteClient` + `AuthInterceptor` + `EncryptedAuthStore`.
  - `RailBiteSettings` (DataStore): stores `serverUrl`, optional `mqttWsUrl`, `staffId`, `staffName`, `staffZone`, `online`, `pickerOrderId`.
  - `MqttConnector`: HiveMQ MQTT v3 over WebSocket (`/mqtt`), auto-reconnect, QoS 1, supporting both `ws://` (LAN) and `wss://` (TLS cloud).
- **`:customer` (`com.railbite.customer`)**:
  - Preserved UI untouched except minimal `"Message Picker"` button + `OrderChatBottomSheet` on `TrackingScreen` (`TrackingViewModel` + `CustomerMqtt`).
- **`:picker` (`com.railbite.picker`)**:
  - Co-installable distinct `applicationId = "com.railbite.picker"`, `minSdk 29`, `usesCleartextTraffic = true` + cleartext-permitting `network_security_config.xml` for LAN server & MQTT WS testing in both debug and release builds.
  - Services & Receivers: `AlertForegroundService` (`foregroundServiceType="dataSync"`), `IncomingOrderActivity` (`showWhenLocked`, `turnScreenOn`, `USE_FULL_SCREEN_INTENT`), `PickerFirebaseMessagingService`, `PickerChatMqtt` (`c-<staffId>-chat`).

## 2. Stitch Project Mapping (`2149824161029451542` — *RailBite Train Food Delivery*)

| # | Stitch Screen ID | Stitch Title | Kotlin Implementation | Key Mechanics |
|---|---|---|---|---|
| 1 | `0db36cb0bd0e492d9dd4da7b2e94a312` | Staff Shift Login | `LoginScreen` + `ServerSettingsSheet` | Staff ID + 4-digit PIN (or password), zone preview, runtime Server URL + MQTT WS URL config, live `/health` latency check |
| 2 | `db3495c5ba6a4523a9e314a3835e5941` | Ops Dashboard | `PickerDashboardScreen` | Hero ONLINE/OFFLINE switch (`AlertForegroundService` + `POST /staff/:id/online`), 3-col KPI strip, Active Queue cards with unread chat badge + preview, empty state, `Orders \| Cash` bottom bar |
| 3 | `9e2ed81e16204e7e8eb308729685dc9b` | Incoming Order Alert | `IncomingOrderActivity` + `AlertForegroundService` | Dark `#0F172A` lock-screen takeover, 60s ring + bar, giant Coach/Seat header, item list, COD/UPI total, `REJECT` + green `ACCEPT ORDER` |
| 4 | `7001963e28c449b887d457375060c2ea` | Picking Checklist | `PickingChecklistScreen` | Sticky Seat pill + SLA timer + `Message` badge, unread customer banner, `X of Y picked` cyan bar, category-grouped cards with local drawables, cyan `×N` badges, `48.dp` checkboxes, sticky `56.dp` `"Packed — Start Delivery"` CTA |
| 5 | `d28dadfcf92a420c931c53494c043857` & `41ab9eb55c9647fca345c97b5c05601a` | Delivery & Handover + Quick-Chat Sheet | `DeliveryAndHandoverScreen` + `OrderQuickChatSheet` + `HandoverConfirmSheet` | 1-tap Picker quick-message chips, live chat preview, full Quick-Chat sheet with haptic/tone on incoming customer MQTT, 4-digit customer PIN + exact COD verification -> `DELIVERED`, read-only lock on terminal state |
| 6 | `64546e68b506450094973ce1e9ef23b5` | Cash Ledger | `CashLedgerScreen` | Dark-slate 3-metric hero card (`Expected`, `Collected`, `Confirmed`), `All / Confirmed / Awaiting Passenger` filter pills, live transaction list from `GET /staff/:id/ledger` |

## 3. Design System Tokens (Flat Utilitarianism)
- **Zero gradients**, **zero blur**, **zero heavy elevation drop shadows**.
- Canvas `#F4F6F8`, Cards `#FFFFFF`, Image Tiles `#F1F5F9`, Dark Anchor `#0F172A`, Cyan Primary `#0891B2` (`#ECFEFF` tint, `#0E7490` pressed), Semantic Green `#16A34A`, Semantic Amber `#D97706`, Semantic Red `#DC2626`, Hairline Border `1.dp` `#E2E8F0`.
- Radii: `8.dp` PIN slots, `12.dp` buttons/inputs, `16.dp` cards, `18.dp` food tiles, `24.dp` top corners on `ModalBottomSheet`, `999.dp` pills.
- Tabular numerals (`fontFeatureSettings = "tnum"`) across all `₹` currency amounts, SLA countdowns, timers, order tags, and 4-digit PINs.
