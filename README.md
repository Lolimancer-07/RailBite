# RailBite

An offline-first ordering, dispatch and accountability system for Indian Railways onboard pantry cars.

## Quick Start (under 10 commands)

```bash
# 1. Install dependencies
pnpm install

# 2. Seed the edge server database
cp services/edge-server/.env.example services/edge-server/.env
pnpm --filter edge-server seed

# 3. Start the edge server
pnpm --filter edge-server dev

# 4. Open the kitchen display (in a browser)
# Navigate to http://localhost:8080/kitchen

# 5. Build the shared package
pnpm --filter shared build

# 6. Run all tests
pnpm test
```

## Architecture

See [docs/architecture.md](docs/architecture.md) for a full diagram.

## Known Risks

- Pantry Wi-Fi coverage across all coaches needs repeaters; quantify nodes per rake.
- The manifest must be synced before the train enters a dead zone; the `verified=0` fallback covers misses.
- Cash handling needs reconciliation at trip end; the ledger and passenger confirmation mitigate fraud.
- Real deployment needs IRCTC and contractor integration; position this as a single-train pilot.
- BLE relay through steel coaches is unproven; test physically before claiming performance.

## Phase Status

| Phase | Status | Description |
|-------|--------|-------------|
| 0 | ✅ Done | Repo and tooling |
| 1 | ✅ Done | Shared package |
| 2 | 🔄 Next | Edge server core |
| 3-11 | ⏳ Pending | See docs/progress.md |

