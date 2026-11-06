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
| POST | `/orders/:id/accept` | Captain | Body: `{captainId}` |
| POST | `/orders/:id/reject` | Captain | Body: `{captainId}` |
| POST | `/orders/:id/deliver` | Captain | Body: `{captainId, cashPaise}` |
| POST | `/orders/:id/confirm-cash` | None | Passenger confirms cash paid |
| POST | `/kitchen/:id/start` | Cook | Mark cooking started |
| POST | `/kitchen/:id/ready` | Cook | Mark item ready |
| POST | `/staff/login` | None | Returns session token |
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

