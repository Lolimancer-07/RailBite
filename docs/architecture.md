# RailBite Architecture

## Overview

RailBite is an offline-first ordering and dispatch system for Indian Railways pantry cars. The system operates primarily on a local Wi-Fi network created by the edge server (a Raspberry Pi or laptop in the pantry car), with optional cloud sync when connectivity is available.

## Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     Pantry Car Wi-Fi Network                     │
│                      (192.168.50.0/24)                           │
│                                                                   │
│  ┌──────────────┐    REST+MQTT    ┌─────────────────────────┐   │
│  │ Passenger App│ ◄──────────────► │   Edge Server           │   │
│  │ (Android)    │                  │   (Raspberry Pi 4)      │   │
│  └──────────────┘                  │                         │   │
│                                    │  • Fastify REST API     │   │
│  ┌──────────────┐    REST+MQTT    │  • Aedes MQTT Broker    │   │
│  │ Captain App  │ ◄──────────────► │  • SQLite (WAL mode)   │   │
│  │ (Android)    │                  │  • PM2 process mgmt    │   │
│  └──────────────┘                  └────────────┬────────────┘   │
│                                                  │               │
│  ┌──────────────┐    MQTT/WS                     │               │
│  │Kitchen Tablet│ ◄──────────────────────────────┘               │
│  │ (Web App)    │                                                 │
│  └──────────────┘                                                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ HTTPS (when available)
                              ▼
                    ┌─────────────────┐
                    │  Cloud API      │
                    │  (STRETCH)      │
                    │  PostgreSQL     │
                    └─────────────────┘
```

## Transport Fallback Order

1. **Pantry Wi-Fi** (REST + MQTT) — Primary, MVP
2. **SMS Token** — STRETCH, via GSM modem on edge server
3. **BLE/Device Relay** — STRETCH, simulation only
4. **Local retry queue** — Always on, MVP

## Data Flow

1. Passenger places order → written to local SQLite queue
2. Queue worker delivers via REST to edge server
3. Edge server validates, reprices, and stores in SQLite
4. Edge server dispatches via MQTT to captain
5. Captain accepts via MQTT action or REST
6. Status updates flow back via retained MQTT messages
7. For COOK track: kitchen display receives via MQTT, marks ready, triggers captain dispatch

## Key Design Decisions

- **Offline-first**: All client actions queue locally first
- **Server repricing**: Edge server always recomputes totals from its own MRP table
- **Idempotent orders**: Client-generated UUIDs serve as idempotency keys
- **Money in paise**: Integer arithmetic only, no floats
- **State machine enforced server-side**: HTTP 409 on illegal transitions

