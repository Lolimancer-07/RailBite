# RailBite Offline BLE Multi-Hop Flood Mesh Spec

## 1. Overview
RailBite operates **100% offline** in Airplane Mode (Bluetooth ON) with zero internet and zero Wi-Fi router. Whenever `TransportRouter` cannot reach `edge-server` within `1500ms`, order placement, status progression, handover PIN verification, and preset Quick Messages fail over automatically to a connectionless **Legacy BLE Advertisement Multi-Hop Flood Mesh**.

- **Zero Pairing / Zero GATT:** All frames ride inside Legacy BLE Manufacturer Specific Data (Company ID `0x5242`, `'R' 'B'`).
- **RF Budget:** `3B Flags + 4B Manufacturer Header (len, 0xFF, 0x5242) + 23B Payload = 30 bytes ≤ 31 bytes` (compatible with all Android 10–15 devices).
- **Offline Catalog:** Both `com.railbite.customer` and `com.railbite.picker` bundle `assets/default_menu.json` (deterministic numeric IDs `1..16`, IRCTC MRPs in paise, categories `BEVERAGES`, `WATER`, `SNACKS`, `MEALS`, `BREAKFAST`), seeded into DataStore on first launch and refreshed from `GET /menu` only when LAN is reachable.

---

## 2. 23-Byte Binary Wire Format (`BlePacketCodec.kt`)

All multi-byte integers are **big-endian**.

| Byte(s) | Field | Type / Encoding | Notes |
|---|---|---|---|
| `0` | `verType` | `UByte` | Upper nibble version (`0x1`), lower nibble type: `0x11`=`ORDER_NEW`, `0x12`=`STATUS_ACK`, `0x13`=`QUICK_MSG` |
| `1..4` | `orderId` | `UInt32` | Deterministic 32-bit short ID mapped bidirectionally to canonical UUID `52420000-0000-4000-8000-0000xxxxxxxx` |
| `5` | `hopTtl` | `UByte` | Upper 4 bits `hopCount` (`0..15`), lower 4 bits `ttl` (`0..15`, default `6`) |
| `6..7` | `coach` | `2 × ASCII` | Uppercase, space-padded on right (e.g. `"B1"`, `"S2"`, `"A "`) |
| `8` | `seat` | `UByte` | Berth / seat number (`0..255`) |
| `9..10` | `pnrLast4` | `UInt16` | Last 4 digits of PNR (`0..9999`) |
| `11..12` | `handoverPin` | `UInt16` | Customer-generated 4-digit delivery PIN (`0..9999`) |
| `13..20` | `payload` | `8 bytes` | Type-specific fixed binary layout (see below) |
| `21..22` | `hmac16` | `2 bytes` | First 2 bytes of `HMAC-SHA256(bytes[0..20], RAILBITE_MESH_KEY)` |

### Payload Layouts (`Bytes 13..20`)

#### `0x11` — `ORDER_NEW`
- 4 item slots × `[itemId: UByte, qty: UByte]`.
- `itemId = 0` denotes an unused slot; valid catalog IDs map to `default_menu.json` (`1..16`).
- Carts with `> 4` distinct items offline trigger a clear UI notice to split or trim to 4 items.

#### `0x12` — `STATUS_ACK`
- `byte 13`: `status` (`1=ACCEPTED`, `2=PACKING`, `3=OUT_FOR_DELIVERY`, `4=DELIVERED`, `5=REJECTED`)
- `bytes 14..15`: `pickerId` (2 ASCII chars, e.g. `"c1"`, `"c2"`, `"c3"`, `"c4"`)
- `byte 16`: `etaMins` (`UByte`, `0..255`)
- `bytes 17..18`: `cashRs` (`UInt16`, collected/expected rupees)
- `bytes 19..20`: `reserved` (`0x0000`)

#### `0x13` — `QUICK_MSG`
- `byte 13`: `senderRole` (`1=CUSTOMER`, `2=PICKER`)
- `byte 14`: `presetCode` (`1..8`):
  - Customer: `1=WHERE_ARE_YOU`, `2=AT_SEAT`, `3=NEAR_DOOR`, `4=NEED_CHANGE`
  - Picker: `5=IM_HERE`, `6=ENTERING_COACH`, `7=TWO_MINS`, `8=PACKING`
- `bytes 15..16`: `msgSeq` (`UInt16`, per-order monotonic sequence)
- `bytes 17..20`: `reserved` (`0x00000000`)

---

## 3. Relay Engine, Dedup & Time-Sliced Advertising
- **Foreground Service (`BleMeshService`):** Uses `foregroundServiceType="connectedDevice"` and `ScanFilter` matching Manufacturer ID `0x5242` (`SCAN_MODE_LOW_LATENCY` while active/online; `SCAN_MODE_BALANCED` while idle).
- **Dedup Cache (`BleDedupCache`):** Thread-safe LRU cache (max `500` entries, `10`-minute TTL) keyed by:
  - `ORDER_NEW`: `(0x11, orderId)`
  - `STATUS_ACK`: `(0x12, orderId, status)`
  - `QUICK_MSG`: `(0x13, orderId, senderRole, msgSeq)`
- **Tx Scheduler (`BleTxScheduler`):** Round-robin FIFO queue rotating each packet for `1500ms` per slice (`ADVERTISE_MODE_LOW_LATENCY`, `ADVERTISE_TX_POWER_HIGH`, non-connectable):
  - Customer `ORDER_NEW` re-broadcasts periodically up to `90s` until `STATUS_ACK (ACCEPTED)` arrives or user cancels.
  - Relays: when `ttl > 0`, increment `hopCount`, decrement `ttl`, recompute `hmac16`, wait `150..600ms` random jitter, and transmit for `4` slices (~`6s`).
  - Picker `STATUS_ACK` / `QUICK_MSG`: broadcasts with `ttl = 6` for `15s`.

---

## 4. Nearest-Picker Arbitration & LAN Re-Sync
- **Arbitration Delay:**
  - `inZone && hopCount == 0`: `0ms`
  - `inZone && hopCount > 0`: `1000ms * hopCount`
  - `outOfZone`: `3000ms + (1000ms * hopCount) + rssiPenalty` (`((−65 − rssi).coerceIn(0, 40) * 50)ms`)
  - Arrival of `STATUS_ACK (ACCEPTED+)` cancels pending delayed alerts and dismisses ringing alerts on other pickers for that `orderId`.
- **Idempotent LAN Re-Sync:** When `edge-server` becomes reachable again, offline BLE orders and handover ledger entries replay idempotently using the canonical UUID `orderId`.
