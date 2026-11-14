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
- Demo seed: 16 instant READY items / 5 categories (no COOK items), pickers c1–c4 (PIN 1234),
  manifest PNR 8492000001–8492000120 (coach/berth = `8492000000 + 15*coachIndex + berth-1`,
  coaches B1–B4, S1–S4, berths 1–15).

Therefore: **no server code changes required.** Only docs (`android-plan.md`, README section).

### REST endpoints the apps call

| App | Call |
|---|---|
| both | `GET /health` → `{ok, tripId, time, version}` (connection test; `tripId` seeds MQTT topic prefix `rb/{tripId}`) |
| both | `GET /menu` → `{id, name, category, track, mrpPaise, stock, prepMinutes, isVeg, active}` |
| customer | `POST /orders` `{id(UUID idempotency), pnr, coach, berth, diners, paymentMode:"COD", items:[{itemId,qty}], createdAt, source:"WIFI"}` — client never sends prices |
| customer | `GET /orders?pnr=`, `GET /orders/:id`, `POST /orders/:id/confirm-cash`, `POST /orders/:id/cancel`, `POST /orders/:id/feedback` (Tier-1 report uses `POST /complaints`) |
| picker | `POST /staff/login`, `POST /staff/:id/online`, `GET /staff/:id/stats`, `GET /ledger/summary`, `POST /orders/:id/accept|packing|pickup|deliver|reject` (`deliver` needs the passenger's 4-digit `deliveryCode`) |

### MQTT topics (Aedes broker, no auth, client-id prefix ACL)

- Customer: client id `p-<uuid>`; subscribes `rb/{tripId}/orders/{orderId}/status` (retained, QoS 1)
  → `{status, updatedAt, captainName?, etaMin?}`.
- Picker: client id `c-{staffId}`; subscribes `rb/{tripId}/captain/{staffId}/alerts` (QoS 1)
  → `{orderId, coach, berth, items[{name,qty,unitPricePaise}], total, track, attempt, expiresAt, pnr, diners, verified}`.

## Customer app screens

1. **Onboarding** (first launch): server URL step (BuildConfig default, "Test connection" →
   Connected / Cannot reach server) → seat step (PNR 10 digits, coach `B2`, berth 1–100, diners 1–8).
   Editable later in Settings; header chip `Coach B2 · Seat 14`.
2. **Home**: seat chip + "~10 mins", search bar, category chips, 2-col product grid (emoji icon,
   veg dot, MRP ₹, ADD → − 1 + stepper, Sold out state), floating cart bar (`2 items · ₹45 · View Cart`).
3. **Cart**: steppers, bill summary (item total, delivery fee ₹0, grand total), COD fixed,
   Place Order (client UUID, idempotent POST).
4. **Tracking**: 5-step timeline (Order placed → Picker accepted → Packing → Out for delivery →
   Delivered), picker name once accepted (from MQTT), ETA, items + total, delivery-code reveal card
   while out for delivery, **"Did you pay ₹X?"** dialog → confirm-cash. Cancel before accept.
   Live = MQTT subscription + 3 s REST polling while order active. Tier-1 "Report a problem" sheet.
5. **Orders** (bottom nav Home/Orders): active + past, empty states, skeletons, snackbar errors.
6. **Settings**: server URL + test, seat editor.

## Picker app screens

1. **Login**: server URL + test, staff ID, 4-digit PIN → token persisted in DataStore.
2. **Home**: big ONLINE/OFFLINE toggle (drives the foreground service), today's stats cards
   (delivered / collected / confirmed / avg rating), active orders list, **Test alert** button,
   permissions card.
3. **Permission flow**: POST_NOTIFICATIONS runtime prompt, full-screen-intent permission
   (`canUseFullScreenIntent` on Android 14+), ignore-battery-optimizations, DND/overlay notes.
4. **AlertActivity** (the core feature): full-screen, `showWhenLocked` + `turnScreenOn`,
   keyguard dismiss, high-contrast coach/seat/items/total, 60 s circular countdown, big ACCEPT /
   REJECT. Loud looping alarm (`USAGE_ALARM` MediaPlayer) + repeating vibration + partial wake lock
   until action or timeout (timeout = reject → server re-dispatches).
5. **Order in progress**: picking checklist (qty + checkbox per item); entering the screen calls
   `/packing`; "Packed — start delivery" enabled only when every box is ticked (`/pickup` →
   server generates the delivery code shown on the customer's phone); then delivery dialog asking
   for the passenger's 4-digit code → `/deliver` (403 on wrong code) → "Delivered + cash collected ₹X".
6. **Cash summary**: today (stats) + all-time ledger row (expected / collected / customer-confirmed).

### Alert pipeline

`RailBiteAlertService` (foreground, `foregroundServiceType="dataSync"`, started when ONLINE):
persistent "You are online" notification + Stop action; owns the MQTT connection so alerts ring
with the app closed / screen locked; on new alert → high-importance notification with full-screen
intent → AlertActivity (sound + vibration handled there via a shared AlertBus singleton).

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
`android-6` emulator verification + final docs.
