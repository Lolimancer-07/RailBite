# Android Apps Plan — Native Kotlin/Compose rewrite

Goal: two **native Android** apps (Kotlin, Jetpack Compose Material 3, Gradle Kotlin DSL) in
`/android`, replacing the Expo prototypes for the demo loop. The existing Fastify + Aedes + SQLite
edge server (`services/edge-server`) is reused as-is; `apps/passenger`, `apps/captain`,
`apps/kitchen-display` remain untouched.

## Module layout

```
/android
├── settings.gradle.kts        # includes :core, :customer, :picker
├── build.gradle.kts           # root: plugin versions from catalog
├── gradle.properties          # JVM args, AndroidX, machine-local org.gradle.java.home
├── gradle/libs.versions.toml  # version catalog
├── local.properties           # sdk.dir (gitignored)
├── keystore.properties        # release signing config (gitignored)
├── keystore/railbite-release.jks (gitignored)
├── scripts/copy-release.sh    # copies APKs to /release
├── core/                      # Android library
│   ├── models (kotlinx.serialization, mirror of packages/shared JSON)
│   ├── Retrofit/OkHttp API client (per server URL)
│   ├── DataStore settings store (server URL, seat, login, online flag)
│   ├── HiveMQ MQTT client wrapper (TCP 1883, auto-reconnect, QoS 1, WS 8883 fallback)
│   ├── money helpers (integer paise → ₹ display)
│   ├── mesh/ (offline Bluetooth relay: MeshPacket/Codec/Relay/Chunker, outbox+ledger stores,
│   │          BLE transport, unit-tested port of tools/relay-sim)
│   └── shared Compose theme + common components
├── customer/                  # app, applicationId com.railbite.customer, name "RailBite"
└── picker/                    # app, applicationId com.railbite.picker, name "RailBite Picker"
```

minSdk 29, compileSdk/targetSdk 35, Java 17 toolchain, Kotlin 2.0.21 + Compose compiler plugin,
Retrofit 2.11 + OkHttp 4.12 + kotlinx-serialization 1.7.x, HiveMQ MQTT client 1.3.5, Navigation
Compose, ViewModel + StateFlow, DataStore Preferences. No DI framework (manual singletons), no Coil
(emoji/Compose-drawn icons only). R8 minify is enabled for release builds with full mode disabled:
full mode's class merging breaks the reflective field lookups inside jctools queues used by the
HiveMQ MQTT client (`NoSuchFieldException: consumerIndex` on first connect), so
`org.jctools.**`, `io.netty.**` and `com.hivemq.**` are kept by name in `proguard-rules.pro`
and `android.enableR8.fullMode=false`. Minified releases were verified end-to-end on an emulator.

## Server contract (already exists — verified against source)

Everything section 6 of the brief asks for was already shipped in the Phase 12 prototype and is
covered by `services/edge-server/src/__tests__/prototype-endpoints.test.ts` (29 server tests pass):

- `OrderStatus`: PLACED → ASSIGNED → ACCEPTED → **PACKING** → OUT_FOR_DELIVERY → DELIVERED
  (409 on illegal transitions; PACKING enforced before pickup).
- `POST /orders/:id/packing`, `POST /orders/:id/accept|reject|pickup|deliver|cancel|confirm-cash|feedback`.
- `POST /staff/login` → `{staffId, name, role, zoneCoaches, token, expiresAt}`.
- `POST /staff/:id/online` (toggle) and `GET /staff/:id/stats` (delivered count, expected/collected/confirmed
  paise today, avg rating).
- `paymentMode: "COD" | "UPI_QUEUED"` on orders (Phase 3): UPI orders are queued at the pantry,
  delivered like COD (same 4-digit code), then the passenger confirms payment in-app; they never
  inflate the cash ledger (a `₹0` ledger row is written so the passenger-confirmation state
  machine stays uniform). Covered by `services/edge-server/src/__tests__/prototype-endpoints.test.ts`
  (31 server tests pass).
- Demo seed: 16 instant READY items / 5 categories (no COOK items), pickers c1–c4 (PIN 1234),
  manifest PNR 8492000001–8492000120 (coach/berth = `8492000000 + 15*coachIndex + berth-1`,
  coaches B1–B4, S1–S4, berths 1–15).

Therefore: **no server code changes required.** Only docs (`android-plan.md`, README section).

### REST endpoints the apps call

| App | Call |
|---|---|
| both | `GET /health` → `{ok, tripId, time, version}` (connection test; `tripId` seeds MQTT topic prefix `rb/{tripId}`) |
| both | `GET /menu` → `{id, name, category, track, mrpPaise, stock, prepMinutes, isVeg, active}` |
| customer | `POST /orders` `{id(UUID idempotency), pnr, coach, berth, diners, paymentMode:"COD"|"UPI_QUEUED", items:[{itemId,qty}], createdAt, source:"WIFI"}` — client never sends prices |
| customer | `GET /orders?pnr=`, `GET /orders/:id`, `POST /orders/:id/confirm-cash`, `POST /orders/:id/cancel`, `POST /orders/:id/feedback` (Tier-1 report uses `POST /complaints`) |
| picker | `POST /staff/login`, `POST /staff/:id/online`, `GET /staff/:id/stats`, `GET /ledger/summary`, `POST /orders/:id/accept|packing|pickup|deliver|reject` (`deliver` needs the passenger's 4-digit `deliveryCode`) |

### MQTT topics (Aedes broker, no auth, client-id prefix ACL)

- Customer: client id `p-<uuid>`; subscribes `rb/{tripId}/orders/{orderId}/status` (retained, QoS 1)
  → `{status, updatedAt, captainName?, etaMin?}`.
- Picker: client id `c-{staffId}`; subscribes `rb/{tripId}/captain/{staffId}/alerts` (QoS 1)
  → `{orderId, coach, berth, items[{name,qty,unitPricePaise}], total, track, attempt, expiresAt, pnr, diners, verified, paymentMode}`.

## Customer app screens

1. **Onboarding** (first launch): seat step only (PNR 10 digits, coach `B2`, berth 1–100,
   diners 1–8). The cloud server URL is baked in (`BuildConfig.DEFAULT_SERVER_URL`,
   fallback chain: stored override → baked default) and auto-tested with a non-blocking
   status line. Editable later in Settings; header chip `Coach B2 · Seat 14`.
2. **Home**: seat chip + "~10 mins", search bar, category chips, 2-col product grid (emoji icon,
   veg dot, MRP ₹, ADD → − 1 + stepper, Sold out state), floating cart bar (`2 items · ₹45 · View Cart`).
3. **Cart**: steppers, bill summary (item total, delivery fee ₹0, grand total), payment toggle
   COD / UPI (UPI_QUEUED: pay digitally after delivery — relayed/offline orders stay COD),
   Place Order (client UUID, idempotent POST).
4. **Tracking**: 5-step timeline (Order placed → Picker accepted → Packing → Out for delivery →
   Delivered), picker name once accepted (from MQTT), ETA, items + total ("Total (UPI)" /
   "Total (COD)"), delivery-code reveal card while out for delivery; UPI orders also get the
   **UpiPayCard** ("Pay ₹X via UPI" opens any installed UPI app with amount + note prefilled)
   until DELIVERED, then a **"Confirm your UPI payment"** dialog (`POST /orders/:id/confirm-cash`
   on the ₹0 row) so the ledger records the passenger's confirmation. COD orders get the
   "Did you pay ₹X?" dialog instead. Cancel before accept.
   Live = MQTT subscription + 3 s REST polling while order active. Tier-1 "Report a problem" sheet.
5. **Orders** (bottom nav Home/Orders): active + past, empty states, skeletons, snackbar errors.
6. **Settings**: seat editor + optional advanced server-URL override (empty = built-in cloud).

## Picker app screens

1. **Login**: staff ID, 4-digit PIN → token persisted in DataStore. Baked-in cloud server
   (fallback chain as above) with an optional override behind "Change".
2. **Home**: big ONLINE/OFFLINE toggle (drives the foreground service), today's stats cards
   (delivered / collected / confirmed / avg rating), active orders list, **Test alert** button,
   permissions card, **Bluetooth relay** card (sink toggle + BLE permission row + pending-sync
   count).
3. **Permission flow**: POST_NOTIFICATIONS runtime prompt, full-screen-intent permission
   (`canUseFullScreenIntent` on Android 14+), ignore-battery-optimizations, DND/overlay notes.
4. **AlertActivity** (the core feature): full-screen, `showWhenLocked` + `turnScreenOn`,
   keyguard dismiss, high-contrast coach/seat/items/total with a payment badge
   ("UPI — passenger pays digitally" / "Cash on delivery" — the heads-up notification reflects
   `paymentMode` too), 60 s circular countdown, big ACCEPT / REJECT. Loud looping alarm
   (`USAGE_ALARM` MediaPlayer) + repeating vibration + partial wake lock
   until action or timeout (timeout = reject → server re-dispatches).
5. **Order in progress**: picking checklist (qty + checkbox per item); entering the screen calls
   `/packing`; "Packed — start delivery" enabled only when every box is ticked (`/pickup` →
   server generates the delivery code shown on the customer's phone); then delivery dialog asking
   for the passenger's 4-digit code → `/deliver` (403 on wrong code) → "Delivered + cash collected ₹X".
   The cash field is prefilled with the order total for COD and **0 for UPI_QUEUED**
   (label "Cash collected (₹) — UPI order", helper "Passenger pays ₹X digitally via UPI — leave 0"),
   and the delivered card swaps the cash handover line for a digital-payment line.
6. **Cash summary**: today (stats) + all-time ledger row (expected / collected / customer-confirmed).

### Alert pipeline

`RailBiteAlertService` (foreground, `foregroundServiceType="dataSync"`, started when ONLINE):
persistent "You are online" notification + Stop action; owns the MQTT connection so alerts ring
with the app closed / screen locked; on new alert → high-importance notification with full-screen
intent → AlertActivity (sound + vibration handled there via a shared AlertBus singleton).

## Offline feature: Bluetooth mesh relay (Phase 2)

The apps stay cloud-first; the mesh is an optional transport that only engages when the cloud is
unreachable and the user turned it on:

- **Engine (`core/mesh/`)** — Kotlin port of `tools/relay-sim`: envelope `{v, orderId, ttl,
  origin, payloadB64, hmac8, hops}`, LRU-500 dedup by orderId, loop prevention (origin + hops),
  TTL 16, sink consumes; chunker frames `[hash4][idx1][total1][data≤14]` (20-byte manufacturer
  AD, company id 0x0822, max 64 chunks). HMAC-SHA256-8 over `orderId|origin|payloadB64` under
  the shared trip secret (`MeshTrip.SECRET`, pinned in `render.yaml` as `HMAC_SECRET` — redeploy
  with a different secret and the phones' tags break).
- **Payload** — `MeshOrderPayload` is richer than REST on purpose: item names/prices (pantry can
  alert offline) and the passenger-generated 4-digit delivery code (offline handover
  verification; cloud orders keep the server-generated code).
- **Customer** — cloud `createOrder` failure + relay ON → order queued in `MeshOutbox`
  (DataStore `railbite_mesh`), burst over BLE, "Sent via Bluetooth relay" card on Orders with the
  code; `MeshOrderSender.syncPending` POSTs queued orders (idempotent client UUID) when
  reachable; menu cache (`menu_cache`) keeps browsing/cart working offline.
- **Picker** — `MeshGateway` scans while ONLINE (sink toggle on Home); completed packets →
  same `AlertBus`/AlertActivity path (`source = MESH`), ledger entry PENDING; accept works
  offline (routes to `MeshHandoverScreen`); deliver verifies the passenger's code and marks
  DELIVERED; a 2-minute replay loop pushes outcomes to the cloud (create → accept → deliver,
  409/404 tolerated) so the server record and customer timeline catch up. Relayed orders are
  always COD (no digital payments over the mesh).
- **Replay source** — orders are created with `source: "RELAY"` (shared schema enum
  `WIFI | SMS | RELAY`).
- **Tests** — 22 unit tests in `:core` port the simulator's scenarios (12-hop delivery, 2-dead-
  node partition, dedup, TTL death, loops, HMAC tamper, LRU eviction, 96-order flood) plus
  chunker round-trips/interleaving and codec sign/verify round-trips. BLE radio itself can't be
  exercised in the emulator; queue → sync → cloud-replay was verified E2E there.

## Phase 3: UPI_QUEUED payment flow (native apps)

Passengers can choose **UPI** at checkout. The order is still cooked/delivered exactly like COD —
the pantry never waits for money on the track:

1. **Place** — customer app Cart: payment toggle COD / UPI. UPI orders POST `paymentMode:
   "UPI_QUEUED"` and are dispatched through the same alert pipeline. Offline/relayed orders stay
   COD (the mesh can't carry a digital payment).
2. **Alert** — picker notification + AlertActivity show the payment badge ("UPI — passenger pays
   digitally" instead of "Cash on delivery" / "(COD)") so the picker knows before accepting.
3. **Delivery** — identical 4-digit code handover. The cash field is prefilled **0** for UPI
   orders (helper: "Passenger pays ₹X digitally via UPI — leave 0"); the delivered card swaps
   "hand over after cash" for "passenger pays digitally — no cash to collect".
4. **Confirm** — after DELIVERED the customer's tracking screen pops "Confirm your UPI payment";
   confirming POSTs `confirm-cash`, which flips the ledger row's `passenger_confirmed` flag. The
   UpiPayCard ("Pay ₹X via UPI", `upi://pay` deep link) hides once DELIVERED.

**Honest cash accounting.** A `₹0` cash-ledger row is written at delivery for UPI orders (vs the
order total for COD). `expectedPaise` sums only **DELIVERED + COD** orders, so a ₹15 UPI delivery
adds ₹0 to expected/collected — the picker's "Today: 1 delivered · ₹0 collected · ₹0 confirmed"
and the ledger mismatch flag stay truthful. Verified E2E: order delivered under c1 with
expected/collected/confirmed all ₹0 and `passenger_confirmed=1` on the ₹0 row.

**HiveMQ WebSocket on Android — the one dependency that matters.** HiveMQ's POM marks
`netty-codec-http` (plus `netty-handler-proxy`/epoll) `<optional>`, so Gradle never propagates it;
without it `MqttWebSocketInitializer.initChannel` throws `NoClassDefFoundError:
io/netty/handler/codec/http/websocketx/WebSocketVersion`, Netty swallows it as a pipeline
exception, and the MQTT connect "hangs" forever with no error. Fix: explicit
`netty-codec-http:4.1.118.Final` in `android/core/build.gradle.kts` (keep it aligned with the
netty version HiveMQ 1.3.5 resolves).

**Emulator tip.** SLiRP (emulator NAT) can stall the HiveMQ WebSocket handshake even when the
server is fine — REST keeps working, MQTT never completes. Workaround on the emulator: run
`adb reverse tcp:8080 tcp:8080` after every emulator restart and point the picker at
`http://127.0.0.1:8080` via the login screen's server override (reverse tunnel carries both REST
and MQTT). Physical phones over Wi-Fi/cloud don't need this.

## Phase 4: FCM push dispatch (native apps)

Firebase project `railbite-7748b` (app `com.railbite.picker`):

- **App** — `android/picker/google-services.json` activates the already-wired conditional
  `com.google.gms.google-services` plugin + Firebase BOM/Messaging. `FcmTokenRegistrar` generates the FCM
  token at app start and `POST`s it to `/staff/device-token` (server stores it on the captain row). Since the
  cloud DB is ephemeral, re-registration happens on every app start (after any server restart the app
  re-registers automatically).
- **Server** — Firebase Admin SDK service-account JSON is set as `GOOGLE_SERVICE_ACCOUNT_JSON` on the Render
  `railbite-api` service (fetched at runtime by `fcm.ts`; **never committed**). The dispatcher fires
  `sendFcmAlert` on every direct assignment and broadcast (`ORDER_DISPATCH` data message with coach/berth/
  items/total/track/paymentMode payload).
- **Verified** — physical device received the FCM dispatch (logcat `PickerFCM: FCM order dispatch data
  message received`), payload carried `paymentMode=UPI_QUEUED`. MQTT (wss, `rb/TRIP_001/captain/<id>/alerts`)
  stays the primary foreground alert channel; FCM covers backgrounded/wake-up delivery.

## Risks

- **Java 27 system JVM** is too new for Gradle/AGP → pinned local JDK 17 (`org.gradle.java.home`),
  documented install steps.
- **HiveMQ client on Android** — pure JVM client, used with Aedes before; fallback documented
  (Paho) if runtime issues appear on the emulator.
- **Battery-killer OEMs** can still kill the service → permission flow + battery-optimization
  exemption requested; documented limitation.
- **Full-screen alerts** depend on notification permission + full-screen-intent permission on
  Android 14+; the permission flow makes this explicit.
- No physical devices in this environment: emulator (API 37 AVD, KVM available) is used for
  install/launch/logcat verification; real-phone behaviour (wake locks, DND) listed as untested.

## Commit plan

`android-1` plan + gradle scaffold → `android-2` :core → `android-3` customer app →
`android-4` picker app + alert service → `android-5` build/release scripts + docs →
`android-6` emulator verification + final docs → `android-7` UPI_QUEUED flow + netty-codec-http
fix (emulator E2E: alert → accept → pick → deliver code → ₹0 ledger → passenger confirm).
