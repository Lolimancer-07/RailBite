# API Contract

## Base URL
`http://192.168.50.1:8080`

## Authentication
Staff endpoints require a session token obtained from `POST /staff/login`.
Passenger endpoints are unauthenticated but PNR-scoped.

## Endpoints

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/health` | None | `{ok, tripId, time, version}` |
| GET | `/menu` | None | Active menu items with stock |
| POST | `/orders` | None | Idempotent on `id`. Server reprices. |
| GET | `/orders/:id` | None | Single order with items |
| GET | `/orders?pnr=...` | None | Passenger's orders |
| POST | `/orders/:id/cancel` | None | Only before ACCEPTED/COOKING |
| POST | `/orders/:id/accept` | Picker | Body: `{captainId}` |
| POST | `/orders/:id/reject` | Picker | Body: `{captainId}` |
| POST | `/orders/:id/packing` | Picker | Body: `{captainId}` — ACCEPTED → PACKING (picker started packing) |
| POST | `/orders/:id/pickup` | Picker | Body: `{captainId}` — PACKING → OUT_FOR_DELIVERY. Server generates a 4-digit delivery code 🔸PROTOTYPE |
| POST | `/orders/:id/deliver` | Picker | Body: `{captainId, cashPaise?, deliveryCode}` — picker must repeat the passenger's delivery code; `403` on mismatch 🔸PROTOTYPE |
| POST | `/orders/:id/feedback` | Passenger | Body: `{received, rating?, reasons?}` — FOT-style post-delivery feedback; only for DELIVERED orders, else `409` 🔸PROTOTYPE |
| POST | `/orders/:id/confirm-cash` | None | Passenger confirms cash paid |
| GET | `/orders/:id/messages` | Customer / Picker | Chronological quick-chat messages for an order |
| POST | `/orders/:id/messages` | Customer / Picker | Send preset or 120-char custom message (idempotent on `id`; `409` outside `ACCEPTED`/`PACKING`/`OUT_FOR_DELIVERY`; publishes MQTT `rb/{trip}/orders/{orderId}/chat` QoS 1) |
| GET | `/orders/:id` | None | Passenger view includes `deliveryCode` + `feedback`; append `?role=picker` to strip the code |
| POST | `/kitchen/:id/start` | Cook | Mark cooking started |
| POST | `/kitchen/:id/ready` | Cook | Mark item ready |
| POST | `/staff/login` | None | Returns session token |
| POST | `/staff/:id/online` | Picker | Body: `{online:boolean}` — online/offline toggle 🔸PROTOTYPE |
| GET | `/staff/:id/stats` | Picker | Today's orders delivered, cash collected, expected, confirmed, + `avgRating`/`feedbackCount` (passenger star feedback) 🔸PROTOTYPE |
| GET | `/staff/:id/orders` | Picker | Active non-terminal orders (`ACCEPTED`/`PACKING`/`OUT_FOR_DELIVERY`) assigned to `:id` (`deliveryCode` stripped) |
| GET | `/staff/:id/ledger` | Picker | Today's summary + chronological per-order cash ledger entries (`Confirmed` vs `Awaiting Passenger`) |
| GET | `/menu` | None | Active items; each includes `isVeg` (FOT-style veg/non-veg marker) |
| GET | `/menu/trending` | None | Top active `menu_items` ordered by real `order_items` quantity (query `?limit=6`, falls back to catalog order) |
| POST | `/complaints` | None | Tier determined by type |
| GET | `/ledger/summary` | Captain | Expected vs collected cash |
| POST | `/sync/flush` | Admin | STRETCH: triggers outbox sync |
| POST | `/sms/ingest` | Internal | STRETCH: SMS token ingest |

## Error Responses

- `400 Bad Request` — Validation failure (Zod schema)
- `404 Not Found` — Resource not found
- `409 Conflict` — Illegal state transition
- `422 Unprocessable Entity` — Business rule violation (e.g., out of stock)
- `500 Internal Server Error` — Unexpected error

## Money
All prices and totals are in **paise** (1 INR = 100 paise). Never use floats.

## Timestamps
All timestamps in ISO 8601 UTC format, e.g. `2024-01-15T10:30:00.000Z`.

