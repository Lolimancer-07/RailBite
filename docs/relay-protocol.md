# RailBite Device-to-Device Mesh Relay Protocol (STRETCH)

## 1. Overview

Indian Railways long-distance rakes typically span 22 to 24 coaches (approx. 550 to 600 meters in length). Metal coach shells, vestibule doors, and crowded passenger bodies create significant RF attenuation for 2.4 GHz and 5 GHz Wi-Fi signals.

When a passenger's phone cannot reach the pantry car Wi-Fi hotspot directly, orders can hop down the train rake via:
1. **Passenger Phone Mesh**: Opportunistic BLE (Bluetooth Low Energy) / Wi-Fi Aware peer-to-peer relay.
2. **ESP32 Coach Node Backbone**: Low-cost (₹350 / $4) fixed ESP32 microcontrollers mounted in coach electrical vestibules.

---

## 2. Relay Packet Structure

Every relayed packet is encapsulated in a compact JSON / CBOR frame:

```ts
interface RelayPacket {
  v: 1;                     // Protocol version
  orderId: string;          // Authoritative order UUID (or truncated 16-byte hex)
  ttl: number;              // Time-to-live hop counter (default: 6)
  origin: string;           // Node ID of the originating passenger phone / coach
  payloadB64: string;       // Base64-encoded encrypted order payload (or SMS token)
  hmac8: string;            // 8-character truncated HMAC-SHA256 signature
  hops: string[];           // Breadcrumb list of node IDs that forwarded this packet
}
```

### Example
```json
{
  "v": 1,
  "orderId": "32f16c2c-6941-4136-b530-98270bbcc13d",
  "ttl": 5,
  "origin": "PHONE_B5_4829",
  "payloadB64": "UkIjNDgyOSNCNy0xNCMyI0NIMSwgU0EyIzdmM2EjMzk4MjA3Yzc=",
  "hmac8": "a9c4d2e1",
  "hops": ["ESP32_B5", "ESP32_B4", "ESP32_B3", "ESP32_PC"]
}
```

---

## 3. Forwarding & Deduplication Rules

Each relay node (whether a passenger's phone running the app in background or a fixed ESP32 node) executes the following forwarding loop upon receiving a packet:

1. **Deduplication Check (LRU Cache)**:
   - Each node maintains a bounded LRU cache of `seenOrderIds` (capacity: 500 items, TTL: 10 minutes).
   - If `orderId` is in `seenOrderIds`, **DROP** the packet immediately to prevent network broadcast storms.
   - Insert `orderId` into `seenOrderIds`.

2. **Loop & Self-Origin Prevention**:
   - If `packet.origin === self.id`, **DROP**.
   - If `packet.hops.includes(self.id)`, **DROP** (loop detected).

3. **Time-To-Live (TTL) Decrement**:
   - Decrement TTL: `packet.ttl = packet.ttl - 1`.
   - If `packet.ttl <= 0`, **DROP** (packet expired).

4. **Destination Check**:
   - If `self.isPantryGateway === true` (Edge Server or connected gateway):
     - Unpack `payloadB64` and inject into `orderService.create()`.
     - Generate response ACK token and route reverse ACK.
   - Else:
     - Append `self.id` to `packet.hops`.
     - Re-broadcast to neighboring nodes with jitter (10ms - 50ms) to avoid simultaneous transmit collisions.

---

## 4. Physical Layer: BLE Advertising vs GATT Transfer

Standard BLE 4.2 / 5.0 advertising packets carry a maximum payload of 31 bytes (or 254 bytes with BLE 5 Extended Advertising):

| Mode | Mechanism | Overhead | Latency | Use Case |
|------|-----------|----------|---------|----------|
| **BLE Advertisement Beacon** | Order hash + truncated token broadcasted in manufacturer data | Minimal | ~20 ms | Notification of pending order down the rake |
| **GATT Characteristic Read/Write** | Point-to-point connection established between adjacent nodes | Handshake required | ~150 ms | Transferring full multi-item order payload |

---

## 5. ESP32 Coach Node Architecture

Each coach in the rake is fitted with one fixed ESP32-WROOM-32D node:
- **Location**: Coach vestibule electrical cabinet (powered by coach 110V DC / 24V DC auxiliary converter).
- **RF Range**: External 2.4 GHz dipole antenna mounted near vestibule gangway bellows, achieving 25m reliable range through metal vestibules.
- **Relay Mesh Topology**: Linear bucket brigade topology ($C_1 \leftrightarrow C_2 \leftrightarrow \dots \leftrightarrow C_{\text{pantry}} \leftrightarrow \dots \leftrightarrow C_{24}$).
- **Failure Tolerance**: If a node $C_i$ fails, high-power BLE 5.0 coded PHY allows $C_{i-1}$ to communicate directly with $C_{i+1}$ across a single gap.
