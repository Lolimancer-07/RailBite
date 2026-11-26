# RailBite Customer Android UI Audit (Wireframe + Stitch Alignment)

**Target module:** `android/customer` (`com.railbite.customer`)  
**Shared library (read-only boundary):** `android/core` (`com.railbite.core`)  
**Unmodified boundary modules:** `android/picker`, `services/edge-server`, `packages/shared`, `apps/*`  
**Stitch Project:** `2149824161029451542` — *RailBite Train Food Delivery* (Wireframe screens: `acf3d694`, `2291ee04`, `ad2f85d4`, `21ec2fa3`, `5ea7ade2`)

---

## 1. Navigation & Screen Mapping

| App Route / Entry | Wireframe & Stitch Screen | ViewModel & StateFlow | Preserved Mechanics |
|---|---|---|---|
| `Routes.HOME` (`HomeScreen.kt`) | **RailBite - Storefront (From Sketch)** (`acf3d694`) | `HomeViewModel` (`state: StateFlow<HomeUiState>`, `cartLines`, `seatInfo.seat`, `diners`, `mqtt.connected`) | Live `/menu` `READY` feed, offline `menuCache`, category pills, search query, client-side dietary/stock/price filters + sorting, `SeatSetupBottomSheet`, `AccountMenuSheet`, `ItemQuickViewSheet`, `FilterBottomSheet`, `SortBottomSheet`, slim floating `"View Cart"` pill above `Home \| Filter \| Sort` bottom bar |
| Seat pill (`SeatInfoBox` in Home top row) | **RailBite - Edit Berth & PNR** (`2291ee04`) | `HomeViewModel.updateSeat(...)` -> `RailBiteSettings.setSeat(...)` | 10-digit PNR, Coach quick chips + input, Berth number (`1..100`), quick link to `Routes.SETTINGS` |
| Filter / Sort buttons in Home `Scaffold.bottomBar` | **RailBite - Filter & Sort Sheet** (`ad2f85d4`) | `HomeViewModel` (`vegOnly`, `inStockOnly`, `maxPricePaise`, `sortBy: MenuSortOption`) | Pure client-side filtering/sorting over live server `menu` without modifying server API contracts |
| Account button in Home utility row | `AccountMenuSheet` (modal action sheet) | `AuthStore.session`, `AuthRepository.logout()`, `RailBiteSettings.serverUrl`, `mqtt.connected` | 1-tap navigation to `Routes.ORDERS`, `Routes.SETTINGS` (Server IP / BLE relay), `SeatSetupBottomSheet`, and `Routes.LOGIN` sign-out |
| `Routes.CART` (`CartScreen.kt`) | **RailBite - Cart & COD Checkout** (`21ec2fa3`) | `CartViewModel` (`state`, `lines`, `seat`, `itemTotalPaise`, `placeOrder`) | Rounded `16.dp` `CartItemCard`s, Seat Verification card, IRCTC MRP Bill Summary, locked Cash on Delivery (`COD`) payment card + BLE offline mesh relay fallback, sticky cyan Place Order CTA |
| `Routes.TRACKING` (`TrackingScreen.kt`) | **RailBite - Live Order Tracking** (`5ea7ade2`) | `TrackingViewModel` (`state`, `mqttStatuses`, 3s REST poll, BLE relay polling) | 5-step `OrderProgressTimeline` (`PLACED -> ACCEPTED -> PACKING -> OUT_FOR_DELIVERY -> DELIVERED`), tabular `HandoverPinCard`, runner info, `BillCard` + MediaStore PDF download, `CashConfirmSheet` + inline fallback, `Cancel Order`, `Report Issue` -> `Routes.REPORT` |
| `Routes.ORDERS` (`OrdersScreen.kt`) | Orders History (`active`, `past`, `relayPending`) | `OrdersViewModel` | Back navigation to Home, active/past/BLE-relay cards opening `Routes.TRACKING` |
| `Routes.ONBOARDING`, `Routes.LOGIN`, `Routes.SETTINGS`, `Routes.REPORT` | Supporting auth, initial setup, server config, Tier-1 reporting | `OnboardingViewModel`, `CustomerLoginViewModel`, `SettingsViewModel`, `ReportViewModel` | Demo OTP sign-in + dialog, server health test, custom LAN server URL (no autocorrect), BLE mesh relay switch & sync, Tier-1 complaints (`COLD_FOOD`, `MISSING_ITEM`, `BAD_TASTE`, `WRONG_ITEM`) |

---

## 2. Flat Design #2 Palette & Metrics Verification
- **Canvas:** `#F4F6F8` | **Cards/Sheets:** `#FFFFFF` | **Contrast Anchors:** `#0F172A` | **Primary Cyan:** `#0891B2` | **Hairline Border:** `1.dp` `#E2E8F0`
- **Tabular Numerals (`fontFeatureSettings = "tnum"`):** PNR badges, `PricePill`, cart badge/totals, OTP/PIN slots (`HandoverPinCard`), ETA countdowns.
- **Ergonomics:** Zero gradients, zero blur/glassmorphism, zero heavy shadows; minimum `44.dp` hit targets on all steppers, chips, icon buttons, and bottom bar actions; `navigationBarsPadding()` on bottom bars and modal sheets.
