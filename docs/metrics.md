# RailBite Benchmark & Simulation Metrics

Measured on local Wi-Fi edge server environment with 50 concurrent simulated passengers.

## 1. Concurrency & Latency Performance

| Metric | Target | Measured Result | Status |
|--------|--------|-----------------|--------|
| **Concurrent Passengers** | 50 | 50 | ✅ Pass |
| **Duplicate Orders (Cash Ledger)** | 0 | **0** (100 submitted, 90 blocked) | ✅ Pass |
| **Fault Recovery (30% Drop Rate)** | 0 Lost Orders | **0 Lost** (20/20 recovered) | ✅ Pass |
| **Order Success Rate** | 99%+ | 100% | ✅ Pass |
| **Throughput** | > 15 orders/s | **23 orders/s** | ✅ Pass |
| **Latency (p50)** | < 300 ms | **5 ms** | ✅ Pass |
| **Latency (p95)** | < 1,000 ms | **36 ms** | ✅ Pass |
| **Latency (p99)** | < 1,500 ms | **39 ms** | ✅ Pass |
| **Max Latency** | < 2,000 ms | **39 ms** | ✅ Pass |

## 2. Walking Distance Optimization (Coach Batching Experiment)

Comparison of pantry staff travel distance with and without coach batching over a 6-coach rake zone:

- **Unbatched Walking Distance:** 254 coach hops
- **Batched Walking Distance (max 4/coach):** 74 coach hops
- **Total Physical Footsteps Saved:** **71% reduction**

*Pitch Data Point:* Coach batching cuts crew transit through crowded passenger vestibules by **71%**, improving delivery speed and preventing coach doorway congestion.
