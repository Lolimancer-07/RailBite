# RailBite SMS Token Protocol Specification (STRETCH)

## 1. Overview

When train pantry Wi-Fi is temporarily lost or out of range, passengers can place orders using GSM SMS fallback. The passenger app pre-fills an SMS message with a compressed, cryptographically signed token sent to the pantry GSM modem (SIM800L / Quectel EC20).

## 2. Inbound Order Token Format

```
RB#<pnrLast4>#<coach>-<berth>#<diners>#<itemCodes>#<nonce>#<hmac8>
```

### Example
```
RB#4829#B2-14#2#CH1,SA2#7f3a#a9c4d2e1
```

### Fields

| Field | Description | Example |
|-------|-------------|---------|
| `RB` | Fixed protocol identifier | `RB` |
| `pnrLast4` | Last 4 digits of 10-digit PNR | `4829` |
| `coach-berth` | Coach and berth separated by hyphen | `B2-14` |
| `diners` | Number of diners (1-8) | `2` |
| `itemCodes` | Comma-separated item short codes + quantity | `CH1,SA2` |
| `nonce` | 4-hex digit unique nonce to prevent replay attacks | `7f3a` |
| `hmac8` | Truncated 8-hex character HMAC-SHA256 signature | `a9c4d2e1` |

### Item Short Codes

| Code | Item Name | Category | Track | MRP |
|------|-----------|----------|-------|-----|
| `CH` | Masala Chai | BEVERAGE | READY | ₹15 |
| `CO` | Filter Coffee | BEVERAGE | READY | ₹20 |
| `BI` | Parle-G Biscuits | SNACKS | READY | ₹10 |
| `CP` | Potato Chips | SNACKS | READY | ₹20 |
| `WA` | Rail Neer Water (1L) | BEVERAGE | READY | ₹15 |
| `SA` | Samosa (2 pcs) | SNACKS | READY | ₹30 |
| `VT` | Standard Veg Thali | MEALS | COOK | ₹80 |
| `CT` | Non-Veg Chicken Thali | MEALS | COOK | ₹130 |
| `ET` | Egg Curry Thali | MEALS | COOK | ₹100 |
| `AP` | Aloo Paratha with Curd | BREAKFAST | COOK | ₹50 |
| `PP` | Paneer Paratha | BREAKFAST | COOK | ₹70 |
| `BO` | Bread Omelette | BREAKFAST | COOK | ₹45 |
| `OM` | Plain Omelette | BREAKFAST | COOK | ₹35 |
| `VB` | Veg Biryani | MEALS | COOK | ₹90 |
| `CB` | Chicken Biryani | MEALS | COOK | ₹140 |

## 3. Cryptographic Verification

1. Key: Pre-shared Trip HMAC Secret (`hmac_secret` from trips table).
2. Data before HMAC: `RB#${pnrLast4}#${coach}-${berth}#${diners}#${itemCodes}#${nonce}`
3. Calculation:
   `hmac8 = HMAC_SHA256(secret, data).substring(0, 8)`
4. The edge server verifies HMAC matching before touching the database. Tampered tokens return HTTP 400.

## 4. Replay Prevention

- Every token includes a random 4-hex-digit nonce.
- The edge server tracks nonces in `sms_nonces`.
- If an already seen nonce arrives, HTTP 409 Conflict is returned (`Replay detected: nonce already processed`).

## 5. Outbound Acknowledgment (ACK) Token

The edge server modem transmits an SMS ACK reply back to the passenger:

```
RB#ACK#<orderIdLast6>#<totalPaise>#<etaMin>
```

### Example
```
RB#ACK#123456#7500#10
```

- `123456`: Last 6 characters of authoritative order ID.
- `7500`: Authoritative total calculated by server MRP table (₹75.00).
- `10`: Estimated preparation/delivery time in minutes.

