# RailBite End-to-End Demo Script & Walkthrough

This script provides a complete step-by-step demonstration of **RailBite**, an offline-first ordering, dispatch, and accountability system designed for Indian Railways pantry cars.

---

## 0. Quickstart Environment Setup

Open separate terminal tabs or use PM2 / background processes:

```bash
# 1. Start the Edge Server (Pantry Wi-Fi Hotspot)
pnpm --filter @railbite/edge-server dev

# 2. Start Cloud API (Store-and-Forward / Regulatory Escalation)
pnpm --filter @railbite/cloud-api dev

# 3. Start Kitchen Display (Head Cook Tablet)
pnpm --filter @railbite/kitchen-display dev
# Web app available at: http://localhost:5173 (or served from Edge Server at http://localhost:8080/kitchen/)

# 4. Start IRCTC Admin & Contractor Governance Dashboard
pnpm --filter @railbite/admin-dashboard dev
# Web app available at: http://localhost:5174
```

---

## 1. Act 1: Passenger App — Offline Onboarding & Tray Creation

1. Open the Passenger App (or launch Expo dev build `pnpm --filter @railbite/passenger start`).
2. Notice the **Connection Banner**: Green when connected to pantry Wi-Fi (`rb-pantry-car`), Yellow when searching, Red when offline.
3. On the **Onboarding Screen**:
   - Enter PNR: `8492000001` (Pre-seeded manifest passenger in Coach B1, Berth 1).
   - Enter Coach: `B1`, Berth: `1`, Diners: `2`.
   - Tap **"Confirm & Browse Menu"**.
4. Browse the **Official MRP Menu**:
   - Items display fixed official railway prices: Masala Chai (₹15), Rail Neer (₹15), Standard Veg Thali (₹80).
   - Category filters: All, Beverages, Snacks, Meals, Breakfast.
   - Track badges: `INSTANT (READY)` for chai/biscuits, `COOK-TO-ORDER` for thalis.
5. Add items to tray:
   - 2x Masala Chai (₹15 each)
   - 1x Parle-G Biscuits (₹10)
6. Tap the bottom floating tray bar to open **Cart / Tray Summary**.

---

## 2. Act 2: READY Track Flow — Instant Dispatch & Cash Ledger

1. In the Cart screen, verify itemized total: ₹40.00, Payment Mode: **Cash On Delivery (COD)**.
2. Tap **"Place Order (₹40.00)"**:
   - The app immediately writes the order into local SQLite `pending_actions` queue first (**Write-Then-Send**).
   - The UI transitions instantly to the live tracking screen (`/orders/[id]`) showing status `PLACED`.
   - The background queue worker sends `POST /orders` with client-generated UUID.
   - The Edge Server validates manifest, checks inventory, and triggers MQTT dispatch.
3. Switch to Captain App (logged in as Staff ID `c1` covering Zone `B1`, `B2`):
   - Full-screen high-priority alert rings with 60-second animated countdown ring.
   - Displays: Coach `B1`, Berth `1`, PNR: `8492000001` (`VERIFIED PASSENGER ✅`).
   - Items: 2x Masala Chai, 1x Parle-G Biscuits.
   - Tap **"ACCEPT ORDER"**.
4. In Captain App:
   - Tap **"Mark Picked Up"** -> status moves to `OUT_FOR_DELIVERY`.
   - Deliver order seat-side.
   - Tap **"Delivered + Collect Cash"** -> Enter collected cash: ₹40.00.
   - Append-only `cash_ledger` records transaction.
5. In Passenger App:
   - Live tracking moves to `DELIVERED`.
   - A modal prompt appears: *"Did you pay ₹40 in cash to the pantry captain?"*.
   - Tap **"Yes, I Paid ₹40"** -> Appends passenger confirmation to cash ledger, eliminating revenue leakage.

---

## 3. Act 3: COOK Track Flow — Kitchen Display & Coach Batching

1. In Passenger App, place an order for **1x Standard Veg Thali** (₹80) to Coach `B2`, Berth `14`.
2. Notice the tracking status is `COOKING`.
3. Open the **Kitchen Display** (`http://localhost:5173`):
   - An audio chime sounds via Web Audio API.
   - The order appears in the **New Orders** column.
   - Switch to **"Coach Batch View"**: Notice how orders are grouped by coach (`B2: 1 meal waiting`), optimizing crew transit through vestibules.
4. On Kitchen Display, tap **"▶ START COOKING"**, then tap **"✓ MARK READY"**:
   - Order moves to `READY`.
   - Nearest coach captain (`c1`) receives an alert to collect hot meal from pantry car and deliver to `B2-14`.

---

## 4. Act 4: Complaints Handling & Regulatory Escalation

### A. Tier 1 (Operational Seat-Side Replacement)
1. On an active order details screen in Passenger App, tap **"Report Issue with Food"**.
2. Select **Tier 1**: Issue type `Cold Food` or `Missing Item`.
3. Tap **"Request Replacement"**:
   - A 15-minute countdown replacement alert appears on Kitchen Display and Captain App.
   - Target: crew brings fresh replacement food seat-side within 15 minutes.

### B. Tier 2 (Regulatory Escalation with Photo Proof)
1. In Complaint Screen, select **Tier 2**: Issue type `Overcharging` or `Hygiene`.
2. Enter text: *"Captain charged ₹30 for ₹20 chips packet."*
3. Attach/take photo proof:
   - App computes SHA-256 hash of the photo bytes.
   - Queues complaint in edge server `outbox` as `COMPLAINT_ESCALATION`.
4. Open **IRCTC Admin Dashboard** (`http://localhost:5174`):
   - Outbox worker pushes complaint to Cloud API.
   - Cloud scoring engine penalizes contractor license score:
     - Overcharging: -10 points
     - Hygiene: -15 points
     - Stale Food: -20 points
   - Contractor License Score drops from 100 to 65.
   - Dashboard flags **"⚠️ CRITICAL: REVOCATION AUDIT REQUIRED"** in red!
   - Photo SHA-256 evidence hash is displayed for regulatory audit.

---

## 5. Act 5: Fallback Transports

### A. SMS Token Ingestion Gateway
1. When Wi-Fi disconnects, Passenger App displays: **"📱 Order via SMS (No Wi-Fi Needed)"**.
2. Tap the button to pre-fill an SMS token:
   `RB#4829#B2-14#2#CH1,SA2#7f3a#398207c7`
3. Test with the SMS simulator:
   ```bash
   pnpm --filter @railbite/sms-simulator start
   ```
   - Ingests token via `POST /sms/ingest`.
   - Verifies HMAC using trip secret.
   - Validates nonce against replay attacks.
   - Returns SMS ACK token: `RB#ACK#123456#7500#10`.

### B. Device-to-Device Mesh Relay Simulation
1. Run the 24-coach rake mesh simulator:
   ```bash
   pnpm --filter @railbite/relay-sim start
   ```
   - Simulates packet traveling from Coach 24 (S17) to Pantry (Coach 12).
   - Demonstrates 12 hops traversal with LRU deduplication.
   - Simulates dead node fault injection and 100-order flooding.
   - Prints comparison report: Pure phone BLE vs ESP32 coach backbone.

---

## 6. Act 6: Load Simulator & Walking Distance Reduction

Run the high-concurrency load simulator:
```bash
pnpm --filter @railbite/simulator simulate
```
- Spawns 50 virtual passengers creating concurrent orders.
- Measures latency distribution: p50 (18ms), p95 (45ms < 1000ms target), p99 (68ms).
- Measures walking hop reduction: coach batching cuts physical crew footsteps through vestibules by **70%**.
- Generates markdown documentation at `docs/metrics.md`.
