# RailBite Customer Android UI Audit

**Target module:** `android/customer` (`com.railbite.customer`)  
**Shared library (read-only boundary):** `android/core` (`com.railbite.core`)  
**Unmodified boundary modules:** `android/picker`, `services/edge-server`, `packages/shared`, `apps/*`  
**Stitch Project:** `2149824161029451542` — *RailBite Train Food Delivery*

---

## 1. Entry Points, Container & Navigation

| File | Symbol | Role & State | Preserved Behaviour |
|---|---|---|---|
| `android/customer/build.gradle.kts` | `:customer` Gradle build | `minSdk = 29`, `targetSdk = 35`, `versionCode = 6`, Compose BOM + Material 3 + Navigation + DataStore + Firebase BOM | Keep pure Compose Material 3 + `DEFAULT_SERVER_URL` build config |
| `android/customer/src/main/AndroidManifest.xml` | `RailBiteCustomerApp` / `MainActivity` | `networkSecurityConfig`, BLE permissions (`BLUETOOTH_ADVERTISE/CONNECT/SCAN`, `ACCESS_FINE_LOCATION`) | Unchanged (cleartext LAN traffic preserved) |
| `android/customer/src/main/java/com/railbite/customer/RailBiteCustomerApp.kt` | `AppContainer` | Holds `settings` (`RailBiteSettings`), `authStore` (`EncryptedAuthStore`), `client` (`RailBiteClient`), `auth` (`AuthRepository`), `cart` (`CartStore`), `mqtt` (`CustomerMqtt`), `meshTransport` (`BleMeshTransport`), `meshSender` (`MeshOrderSender`) | Unchanged |
| `android/customer/src/main/java/com/railbite/customer/MainActivity.kt` | `MainActivity` | Requests BLE runtime permissions, starts/stops `meshSender`, watches `ConnectivityManager` to auto-sync pending mesh orders, hosts `CustomerRoot(container)` | Update root theme wrapper from `RailBiteTheme` to `RailBiteCustomerTheme` (Customer-only theme) |
| `android/customer/src/main/java/com/railbite/customer/CustomerGraph.kt` | `CustomerGraph` | Resolves `startDestination` (`Routes.LOGIN`, `Routes.ONBOARDING`, or `Routes.HOME`) from `authStore.session` & `settings.onboardingDone`; exposes `activeOrderId` | Unchanged |
| `android/customer/src/main/java/com/railbite/customer/CustomerApp.kt` | `CustomerRoot`, `CustomerNavHost`, `TabScaffold`, `Routes` | Routes: `login`, `onboarding`, `home`, `orders`, `cart`, `settings`, `tracking/{orderId}`, `report/{orderId}`; session-loss `LaunchedEffect`; bottom nav tabs (`Home`, `Orders`) | Restyle `TabScaffold` bottom navigation bar to flat Design #2 (`#FFFFFF` + `1.dp` `#E2E8F0` top border, `#0891B2` active tint); wire `onEditSeat` -> `Routes.ONBOARDING` & `onOpenSettings` -> `Routes.SETTINGS` |
| `android/customer/src/main/java/com/railbite/customer/CartStore.kt` | `CartStore`, `CartLine` | In-memory `StateFlow<Map<String, CartLine>>`, max 10 qty per item, `add`, `decrement`, `clear`, `itemCount`, `totalPaise` | Preserve existing methods; optionally add `setQuantity(item, qty)` helper for item detail sheet |
| `android/customer/src/main/java/com/railbite/customer/CustomerMqtt.kt` | `CustomerMqtt` | `orderStatuses: StateFlow<Map<String, MqttOrderStatus>>`, `connected: StateFlow<Boolean>`, `ensureConnected`, `watchOrder`, `unwatchOrder` | Unchanged; expose `connected` stream via `SeatHeaderBar` status dot |

---

## 2. Screen-to-Stitch Mapping & ViewModel Contracts

### Screen 1: Stitch `379b7a451f2b4f7f978a6dd06dc66fa0` (*RailBite - Seat Setup*)
- **Kotlin Screens:**
  - `ui/onboarding/OnboardingScreen.kt` + `OnboardingViewModel` (`state: StateFlow<OnboardingUiState>` — 10-digit `pnr`, `coach`, `berth`, `diners` 1..8, `saving`, `error`, `serverUrl`, `connection` `ConnectionTestState` with `testConnection()` and `complete(onDone)`).
  - Pre-auth gate: `ui/login/LoginScreen.kt` (`CustomerLoginScreen`) + `CustomerLoginViewModel` (`state: StateFlow<CustomerLoginUiState>` — 10-digit Indian phone, on-device demo OTP generation & verification, dev-login JWT exchange with demo session fallback, inline Server URL quick-change `AlertDialog`).
- **Rebuild Notes:** Flat `#F4F6F8` canvas with crisp `#FFFFFF` setup card (`16.dp` radius, `1.dp` `#E2E8F0` border), clear 10-digit PNR / Coach / Berth inputs, tactile 44dp+ diners stepper, server connection status pill/dialog, primary `#0891B2` CTA (`Save & Browse Menu` / `Send OTP` / `Verify & continue`). Keep `CustomerLoginViewModel` dummy OTP and `OnboardingViewModel` validation untouched.

### Screen 2: Stitch `68c94a3f8eab4bbd8d630d0c232e3396` (*RailBite - Storefront*)
- **Kotlin Screen:** `ui/home/HomeScreen.kt` + `HomeViewModel`
- **Observed `StateFlow`s & REST/Prefs:**
  - `vm.state: StateFlow<HomeUiState>` (`loading`, `menu` filtered to `track == "READY"`, `categories`, `selectedCategory`, `search`, `error`; falls back to `settings.menuCache` offline)
  - `vm.cartLines: StateFlow<Map<String, CartLine>>`
  - `vm.seatInfo.seat` (`Coach • Berth` chip)
  - `graph.activeOrderId` + `graph.mqtt.orderStatuses` (inline live order banner)
  - `graph.mqtt.connected` (`Boolean` — drives `StatusDot` in `SeatHeaderBar`)
- **Rebuild Notes:**
  - `SeatHeaderBar`: tappable cyan-tinted seat chip (`Coach B2 • Berth 14`) opening seat edit (`Routes.ONBOARDING`), `~10 min` delivery badge, live `StatusDot` + settings icon button (`Routes.SETTINGS`).
  - Flat search bar + pill `CategoryChip` row (`#ECFEFF`/`#0891B2` active, `#FFFFFF`/`#64748B` inactive).
  - 2-column `LazyVerticalGrid` of `FoodProductCard`s using local offline food assets (`res/drawable/food_*.jpg`), floating `PricePill` (`#0F172A`, `tnum` ₹ MRP), veg indicator, and `ADD` <-> `QuantityStepper` (`44.dp` targets) or dimmed `Sold Out` state.
  - Floating dark-slate `#0F172A` `FloatingCartBar` anchored above bottom navigation with `110.dp` grid bottom padding.

### Screen 3: Stitch `835c5a160f584bde8cd8371246b2eb12` (*RailBite - Item Detail Sheet*)
- **Kotlin Integration:** New `ItemQuickViewSheet` (`ModalBottomSheet`) hosted in `HomeScreen.kt` when a passenger taps a `FoodProductCard` body/image.
- **State Wiring:** Reads live quantity from `cartLines[item.id]?.qty ?: 0` and dispatches `vm.addToCart(item)` / `vm.decrement(item.id)` (or updates quantity to the selected stepper count on `Add to Cart`), while preserving max-10 cart cap and `stock <= 0` guard.
- **Rebuild Notes:** `24.dp` top corner radius, `#FFFFFF` sheet over dimmed scrim, rounded `18.dp` food hero artwork (`food_*.jpg`), veg indicator, category pill, `tnum` ₹ MRP & `IRCTC MRP` green badge, remaining stock badge, inline `QuantityStepper`, and full-width `#0891B2` CTA (`Add to Cart • ₹...` / `Update Cart`).

### Screen 4: Stitch `d5c3ffc9755042d5bdf6ed3c28617c22` (*RailBite - Cart & Checkout*)
- **Kotlin Screen:** `ui/cart/CartScreen.kt` + `CartViewModel`
- **Observed `StateFlow`s & Actions:**
  - `vm.state: StateFlow<CartUiState>` (`placing`, `sendingRelay`, `error`, `paymentMode` `"COD"` / `"UPI_QUEUED"`, `relayedCode`)
  - `vm.lines: StateFlow<Map<String, CartLine>>`
  - `vm.seat: StateFlow<SeatSnapshot.Seat?>`
  - `vm.placeOrder(onPlaced)`: checks `ApiClient.reachable(...)`, supports online `POST /orders` + optional dual-path BLE mesh burst and automatic offline BLE relay fallback for COD.
- **Rebuild Notes:** `#F4F6F8` canvas, individual `16.dp` `#FFFFFF` `CartItemCard`s with `1.dp` `#E2E8F0` border, local food thumbnail, `tnum` prices, `QuantityStepper`, seat summary chip, Bill Summary card (Subtotal, Convenience Fee ₹0 / Free, Total Payable in `tnum`), flat COD vs UPI Queued selection cards, and pinned bottom CTA (`#0891B2`) with `navigationBarsPadding()`.

### Screen 5: Stitch `b94229514bd542e3b6703f1c983d921d` (*RailBite - Live Tracking & Cash Confirmation*)
- **Kotlin Screens:**
  - `ui/tracking/TrackingScreen.kt` + `TrackingViewModel` (`state: StateFlow<TrackingUiState>` merged with `vm.mqttStatuses(orderId)` via `refreshMerged(mqtt)` + 3s REST fallback poll + BLE mesh relay tracking loop).
    - Preserves: 5-step status timeline (`PLACED/ASSIGNED` -> `ACCEPTED` -> `PACKING` -> `OUT_FOR_DELIVERY` -> `DELIVERED`, plus terminal `CANCELLED`/`EXPIRED`), `HandoverPinCard` (4-digit `deliveryCode` / `relayCode`), `UpiPayCard` (with external UPI app launch via `Upi.buildUri`), `ItemsCard`, `BillCard` + `generateBillPdf()` MediaStore export + Snackbar confirmation, `Cancel order` button, `Report a problem` navigation (`Routes.report(orderId)`), and post-delivery cash confirmation (`vm.confirmCash()`).
    - Rebuilds cash confirmation prompt as `CashConfirmSheet` (`ModalBottomSheet`) replacing the legacy `AlertDialog` while keeping the same auto-open condition (`state.delivered && !state.cashConfirmed && !state.relayed`) and exact button actions (`confirmCash()` / dismiss).
  - `ui/orders/OrdersScreen.kt` + `OrdersViewModel` (`active`, `past`, `relayPending` refreshed every 3s while active) reachable via the `Orders` tab in `TabScaffold`.

### Supporting Routes Preserved
- **Issue Reporting:** `ui/report/ReportScreen.kt` + `ReportViewModel` (`TIER1_TYPES`: `COLD_FOOD`, `MISSING_ITEM`, `BAD_TASTE`, `WRONG_ITEM`; `POST /complaints`). Restyled onto flat Design #2 cards on `#F4F6F8`.
- **Runtime Server & Seat Settings:** `ui/settings/SettingsScreen.kt` + `SettingsViewModel` (`serverUrl`, `testConnection()`, `meshRelayEnabled`, `syncMeshOrders()`, `pnr`/`coach`/`berth`/`diners`, `save()`, `logout()`). Restyled with grouped flat cards, `StatusDot`, zero hardcoded colors, keeping the exact Uri keyboard without autocorrect for `http://` LAN servers.

---

## 3. Existing Tests & Verification Scope

- `:customer` currently has no `src/test` unit tests.
- `:core` unit tests exist under `android/core/src/test/java/com/railbite/core/`:
  - `AuthInterceptorTest.kt`
  - `ModelDeserializationTest.kt`
  - `MoneyAndValidatorsTest.kt`
  - `TopicsTest.kt`
  - `mesh/{MeshChunkerTest,MeshCodecTest,MeshRelayTest}.kt`
- Build & test verification command (run after implementation):
  `./gradlew :core:testDebugUnitTest :customer:assembleDebug :customer:assembleRelease :picker:compileDebugKotlin --no-daemon`
# RailBite Customer Android UI Audit

**Target module:** `android/customer` (`com.railbite.customer`)  
**Shared library (read-only boundary):** `android/core` (`com.railbite.core`)  
**Unmodified boundary modules:** `android/picker`, `services/edge-server`, `packages/shared`, `apps/*`  
**Stitch Project:** `2149824161029451542` — *RailBite Train Food Delivery*

---

## 1. Entry Points, Container & Navigation

| File | Symbol | Role & State | Preserved Behaviour |
|---|---|---|---|
| `android/customer/build.gradle.kts` | `:customer` Gradle build | `minSdk = 29`, `targetSdk = 35`, `versionCode = 6`, Compose BOM + Material 3 + Navigation + DataStore + Firebase BOM | Keep pure Compose Material 3 + `DEFAULT_SERVER_URL` build config |
| `android/customer/src/main/AndroidManifest.xml` | `RailBiteCustomerApp` / `MainActivity` | `networkSecurityConfig`, BLE permissions (`BLUETOOTH_ADVERTISE/CONNECT/SCAN`, `ACCESS_FINE_LOCATION`) | Unchanged (cleartext LAN traffic preserved) |
| `android/customer/src/main/java/com/railbite/customer/RailBiteCustomerApp.kt` | `AppContainer` | Holds `settings` (`RailBiteSettings`), `authStore` (`EncryptedAuthStore`), `client` (`RailBiteClient`), `auth` (`AuthRepository`), `cart` (`CartStore`), `mqtt` (`CustomerMqtt`), `meshTransport` (`BleMeshTransport`), `meshSender` (`MeshOrderSender`) | Unchanged |
| `android/customer/src/main/java/com/railbite/customer/MainActivity.kt` | `MainActivity` | Requests BLE runtime permissions, starts/stops `meshSender`, watches `ConnectivityManager` to auto-sync pending mesh orders, hosts `CustomerRoot(container)` | Update root theme wrapper from `RailBiteTheme` to `RailBiteCustomerTheme` (Customer-only theme) |
| `android/customer/src/main/java/com/railbite/customer/CustomerGraph.kt` | `CustomerGraph` | Resolves `startDestination` (`Routes.LOGIN`, `Routes.ONBOARDING`, or `Routes.HOME`) from `authStore.session` & `settings.onboardingDone`; exposes `activeOrderId` | Unchanged |
| `android/customer/src/main/java/com/railbite/customer/CustomerApp.kt` | `CustomerRoot`, `CustomerNavHost`, `TabScaffold`, `Routes` | Routes: `login`, `onboarding`, `home`, `orders`, `cart`, `settings`, `tracking/{orderId}`, `report/{orderId}`; session-loss `LaunchedEffect`; bottom nav tabs (`Home`, `Orders`) | Restyle `TabScaffold` bottom navigation bar to flat Design #2 (`#FFFFFF` + `1.dp` `#E2E8F0` top border, `#0891B2` active tint); wire `onEditSeat` -> `Routes.ONBOARDING` & `onOpenSettings` -> `Routes.SETTINGS` |
| `android/customer/src/main/java/com/railbite/customer/CartStore.kt` | `CartStore`, `CartLine` | In-memory `StateFlow<Map<String, CartLine>>`, max 10 qty per item, `add`, `decrement`, `clear`, `itemCount`, `totalPaise` | Preserve existing methods; optionally add `setQuantity(item, qty)` helper for item detail sheet |
| `android/customer/src/main/java/com/railbite/customer/CustomerMqtt.kt` | `CustomerMqtt` | `orderStatuses: StateFlow<Map<String, MqttOrderStatus>>`, `connected: StateFlow<Boolean>`, `ensureConnected`, `watchOrder`, `unwatchOrder` | Unchanged; expose `connected` stream via `SeatHeaderBar` status dot |

---

## 2. Screen-to-Stitch Mapping & ViewModel Contracts

### Screen 1: Stitch `379b7a451f2b4f7f978a6dd06dc66fa0` (*RailBite - Seat Setup*)
- **Kotlin Screens:**
  - `ui/onboarding/OnboardingScreen.kt` + `OnboardingViewModel` (`state: StateFlow<OnboardingUiState>` — 10-digit `pnr`, `coach`, `berth`, `diners` 1..8, `saving`, `error`, `serverUrl`, `connection` `ConnectionTestState` with `testConnection()` and `complete(onDone)`).
  - Pre-auth gate: `ui/login/LoginScreen.kt` (`CustomerLoginScreen`) + `CustomerLoginViewModel` (`state: StateFlow<CustomerLoginUiState>` — 10-digit Indian phone, on-device demo OTP generation & verification, dev-login JWT exchange with demo session fallback, inline Server URL quick-change `AlertDialog`).
- **Rebuild Notes:** Flat `#F4F6F8` canvas with crisp `#FFFFFF` setup card (`16.dp` radius, `1.dp` `#E2E8F0` border), clear 10-digit PNR / Coach / Berth inputs, tactile 44dp+ diners stepper, server connection status pill/dialog, primary `#0891B2` CTA (`Save & Browse Menu` / `Send OTP` / `Verify & continue`). Keep `CustomerLoginViewModel` dummy OTP and `OnboardingViewModel` validation untouched.

### Screen 2: Stitch `68c94a3f8eab4bbd8d630d0c232e3396` (*RailBite - Storefront*)
- **Kotlin Screen:** `ui/home/HomeScreen.kt` + `HomeViewModel`
- **Observed `StateFlow`s & REST/Prefs:**
  - `vm.state: StateFlow<HomeUiState>` (`loading`, `menu` filtered to `track == "READY"`, `categories`, `selectedCategory`, `search`, `error`; falls back to `settings.menuCache` offline)
  - `vm.cartLines: StateFlow<Map<String, CartLine>>`
  - `vm.seatInfo.seat` (`Coach • Berth` chip)
  - `graph.activeOrderId` + `graph.mqtt.orderStatuses` (inline live order banner)
  - `graph.mqtt.connected` (`Boolean` — drives `StatusDot` in `SeatHeaderBar`)
- **Rebuild Notes:**
  - `SeatHeaderBar`: tappable cyan-tinted seat chip (`Coach B2 • Berth 14`) opening seat edit (`Routes.ONBOARDING`), `~10 min` delivery badge, live `StatusDot` + settings icon button (`Routes.SETTINGS`).
  - Flat search bar + pill `CategoryChip` row (`#ECFEFF`/`#0891B2` active, `#FFFFFF`/`#64748B` inactive).
  - 2-column `LazyVerticalGrid` of `FoodProductCard`s using local offline food assets (`res/drawable/food_*.jpg`), floating `PricePill` (`#0F172A`, `tnum` ₹ MRP), veg indicator, and `ADD` <-> `QuantityStepper` (`44.dp` targets) or dimmed `Sold Out` state.
  - Floating dark-slate `#0F172A` `FloatingCartBar` anchored above bottom navigation with `110.dp` grid bottom padding.

### Screen 3: Stitch `835c5a160f584bde8cd8371246b2eb12` (*RailBite - Item Detail Sheet*)
- **Kotlin Integration:** New `ItemQuickViewSheet` (`ModalBottomSheet`) hosted in `HomeScreen.kt` when a passenger taps a `FoodProductCard` body/image.
- **State Wiring:** Reads live quantity from `cartLines[item.id]?.qty ?: 0` and dispatches `vm.addToCart(item)` / `vm.decrement(item.id)` (or updates quantity to the selected stepper count on `Add to Cart`), while preserving max-10 cart cap and `stock <= 0` guard.
- **Rebuild Notes:** `24.dp` top corner radius, `#FFFFFF` sheet over dimmed scrim, rounded `18.dp` food hero artwork (`food_*.jpg`), veg indicator, category pill, `tnum` ₹ MRP & `IRCTC MRP` green badge, remaining stock badge, inline `QuantityStepper`, and full-width `#0891B2` CTA (`Add to Cart • ₹...` / `Update Cart`).

### Screen 4: Stitch `d5c3ffc9755042d5bdf6ed3c28617c22` (*RailBite - Cart & Checkout*)
- **Kotlin Screen:** `ui/cart/CartScreen.kt` + `CartViewModel`
- **Observed `StateFlow`s & Actions:**
  - `vm.state: StateFlow<CartUiState>` (`placing`, `sendingRelay`, `error`, `paymentMode` `"COD"` / `"UPI_QUEUED"`, `relayedCode`)
  - `vm.lines: StateFlow<Map<String, CartLine>>`
  - `vm.seat: StateFlow<SeatSnapshot.Seat?>`
  - `vm.placeOrder(onPlaced)`: checks `ApiClient.reachable(...)`, supports online `POST /orders` + optional dual-path BLE mesh burst and automatic offline BLE relay fallback for COD.
- **Rebuild Notes:** `#F4F6F8` canvas, individual `16.dp` `#FFFFFF` `CartItemCard`s with `1.dp` `#E2E8F0` border, local food thumbnail, `tnum` prices, `QuantityStepper`, seat summary chip, Bill Summary card (Subtotal, Convenience Fee ₹0 / Free, Total Payable in `tnum`), flat COD vs UPI Queued selection cards, and pinned bottom CTA (`#0891B2`) with `navigationBarsPadding()`.

### Screen 5: Stitch `b94229514bd542e3b6703f1c983d921d` (*RailBite - Live Tracking & Cash Confirmation*)
- **Kotlin Screens:**
  - `ui/tracking/TrackingScreen.kt` + `TrackingViewModel` (`state: StateFlow<TrackingUiState>` merged with `vm.mqttStatuses(orderId)` via `refreshMerged(mqtt)` + 3s REST fallback poll + BLE mesh relay tracking loop).
    - Preserves: 5-step status timeline (`PLACED/ASSIGNED` -> `ACCEPTED` -> `PACKING` -> `OUT_FOR_DELIVERY` -> `DELIVERED`, plus terminal `CANCELLED`/`EXPIRED`), `HandoverPinCard` (4-digit `deliveryCode` / `relayCode`), `UpiPayCard` (with external UPI app launch via `Upi.buildUri`), `ItemsCard`, `BillCard` + `generateBillPdf()` MediaStore export + Snackbar confirmation, `Cancel order` button, `Report a problem` navigation (`Routes.report(orderId)`), and post-delivery cash confirmation (`vm.confirmCash()`).
    - Rebuilds cash confirmation prompt as `CashConfirmSheet` (`ModalBottomSheet`) replacing the legacy `AlertDialog` while keeping the same auto-open condition (`state.delivered && !state.cashConfirmed && !state.relayed`) and exact button actions (`confirmCash()` / dismiss).
  - `ui/orders/OrdersScreen.kt` + `OrdersViewModel` (`active`, `past`, `relayPending` refreshed every 3s while active) reachable via the `Orders` tab in `TabScaffold`.

### Supporting Routes Preserved
- **Issue Reporting:** `ui/report/ReportScreen.kt` + `ReportViewModel` (`TIER1_TYPES`: `COLD_FOOD`, `MISSING_ITEM`, `BAD_TASTE`, `WRONG_ITEM`; `POST /complaints`). Restyled onto flat Design #2 cards on `#F4F6F8`.
- **Runtime Server & Seat Settings:** `ui/settings/SettingsScreen.kt` + `SettingsViewModel` (`serverUrl`, `testConnection()`, `meshRelayEnabled`, `syncMeshOrders()`, `pnr`/`coach`/`berth`/`diners`, `save()`, `logout()`). Restyled with grouped flat cards, `StatusDot`, zero hardcoded colors, keeping the exact Uri keyboard without autocorrect for `http://` LAN servers.

---

## 3. Existing Tests & Verification Scope

- `:customer` currently has no `src/test` unit tests.
- `:core` unit tests exist under `android/core/src/test/java/com/railbite/core/`:
  - `AuthInterceptorTest.kt`
  - `ModelDeserializationTest.kt`
  - `MoneyAndValidatorsTest.kt`
  - `TopicsTest.kt`
  - `mesh/{MeshChunkerTest,MeshCodecTest,MeshRelayTest}.kt`
- Build & test verification command (run after implementation):
  `./gradlew :core:testDebugUnitTest :customer:assembleDebug :customer:assembleRelease :picker:compileDebugKotlin --no-daemon`

