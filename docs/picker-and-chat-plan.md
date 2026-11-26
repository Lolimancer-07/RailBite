# Picker App & Quick-Message Implementation Plan

## Baseline Inventory
- **Backend (`packages/shared` + `services/edge-server`):** Fastify + Aedes MQTT-over-WebSocket (`/mqtt`) + dual SQLite (WAL) / Postgres DB layer. Already implements orders, dispatcher, 4-digit delivery code handover, `PACKING` step, ledger summary, and staff PIN/password login (`c1`–`c4`, PIN `1234` / password `railbite123`). Missing order-scoped chat schema, endpoints, broker ACL permission for pickers, and tests.
- **Customer App (`android/customer`):** Jetpack Compose Material 3 app using Retrofit (`RailBiteApi`), `CustomerMqtt` (`MqttConnector` with `p-...` client ID), DataStore (`RailBiteSettings`), and `TrackingScreen`/`TrackingViewModel`. Minimal addition needed on `TrackingScreen` + `TrackingViewModel` + `CustomerMqtt` without redesigning existing screens.
- **Picker App (`android/picker`):** Jetpack Compose Material 3 module already containing `LoginScreen`, `HomeScreen`, `RailBiteAlertService` + full-screen lock-screen `AlertActivity`, `OrderProgressScreen` (checklist -> `PACKING` -> `OUT_FOR_DELIVERY` -> 4-digit handover code + cash -> `DELIVERED`), and `CashSummaryScreen`. Needs:
  1. Legacy 4-digit PIN forwarding alongside password in `AuthRepository.staffLogin`.
  2. Live server connection badge + in-app Server URL editor on Picker Home.
  3. Order-scoped Quick-Message integration (`PickerChatMqtt` using non-colliding `c-<staffId>-chat` clientId, unread badge + preview, chat sheet with presets + 120-char custom input, subtle chime + haptic buzz on incoming customer message, locked on terminal order states).

## Commit Plan
1. `feat-1: quick-chat backend schema, REST, MQTT, and tests`
   - `packages/shared`: `OrderMessageCreateSchema`, `OrderMessageSchema`, `CUSTOMER_CHAT_PRESETS`, `PICKER_CHAT_PRESETS`, `topics().orderChat(orderId)`.
   - `services/edge-server`: `004_order_messages.sql`, Postgres epoch translation in `db.ts`, `MessageRepo`, `MqttPublisher.publishOrderChat`, Aedes ACL update in `broker.ts`, `GET/POST /orders/:id/messages` in `routes/orders.ts`, `docs/api-contract.md`, and `order-messages.test.ts`.
2. `feat-2: customer app quick-chat integration`
   - `android/core`: `RailBiteTopics.orderChat`, `Models.kt` chat models & presets, `RailBiteApi` endpoints.
   - `android/customer`: `CustomerMqtt` chat subscription, `TrackingViewModel` message/unread/toast state, minimal `"Message Picker"` button + chat sheet on `TrackingScreen`.
3. `feat-3: kotlin picker app core (login, online toggle, lock-screen alert service)`
   - `AuthRepository.staffLogin` 4-digit PIN support + Picker `LoginScreen` label clarity; Picker `HomeScreen` / `HomeViewModel` live server Connected/Disconnected indicator + Server URL settings modal.
4. `feat-4: kotlin picker app checklist, delivery, cash ledger, and quick-chat`
   - `PickerChatMqtt`, `OrderProgressViewModel` chat + incoming buzz/tone event, `OrderProgressScreen` `"Quick Message"` button with unread badge/preview + chat dialog/sheet.
5. **Verification & Docs:** `pnpm -r build && pnpm -r test`, `./gradlew assembleDebug` + `assembleRelease`, copy to `/release/railbite-customer.apk` & `/release/railbite-picker.apk`, `aapt` package check, and `README.md` LAN two-phone walkthrough.
