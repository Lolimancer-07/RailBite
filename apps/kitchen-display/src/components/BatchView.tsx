import React from "react";
import type { MqttKitchenQueueItem } from "@railbite/shared";

interface BatchViewProps {
  orders: MqttKitchenQueueItem[];
}

export function BatchView({ orders }: BatchViewProps) {
  // Group orders by coach
  const coachMap = new Map<string, MqttKitchenQueueItem[]>();
  for (const o of orders) {
    const list = coachMap.get(o.coach) || [];
    list.push(o);
    coachMap.set(o.coach, list);
  }

  const coachList = Array.from(coachMap.entries()).sort(([cA], [cB]) => cA.localeCompare(cB));

  return (
    <div style={styles.container}>
      <h2 style={styles.title}>COACH BATCHING OVERVIEW</h2>
      <p style={styles.sub}>
        Orders are batched by coach (max 4 trays or 90 seconds) to minimize captain walking distance through the train rake.
      </p>

      <div style={styles.grid}>
        {coachList.map(([coach, coachOrders]) => {
          const totalTrays = coachOrders.reduce(
            (sum, o) => sum + o.items.reduce((s, it) => s + it.qty, 0),
            0,
          );

          return (
            <div key={coach} style={styles.batchCard}>
              <div style={styles.batchHeader}>
                <span style={styles.coachTitle}>COACH {coach}</span>
                <span style={styles.orderCount}>
                  {coachOrders.length} Order(s) • {totalTrays} Tray(s)
                </span>
              </div>

              {/* Progress bar towards batch limit (4 orders) */}
              <div style={styles.progressTrack}>
                <div
                  style={{
                    ...styles.progressBar,
                    width: `${Math.min(100, (coachOrders.length / 4) * 100)}%`,
                  }}
                />
              </div>

              <div style={styles.ordersList}>
                {coachOrders.map((ord) => (
                  <div key={ord.orderId} style={styles.orderRow}>
                    <span style={styles.berthTag}>Berth {ord.berth}</span>
                    <span style={styles.statusTag}>{ord.status}</span>
                    <span style={styles.itemSummary}>
                      {ord.items.map((it) => `${it.name} (${it.qty})`).join(", ")}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          );
        })}

        {coachList.length === 0 && (
          <div style={styles.empty}>No cook orders waiting in batch queues.</div>
        )}
      </div>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    padding: "24px",
    flex: 1,
    overflowY: "auto",
    color: "#ffffff",
  },
  title: {
    fontSize: "22px",
    fontWeight: "bold",
    marginBottom: "4px",
  },
  sub: {
    fontSize: "14px",
    color: "#94a3b8",
    marginBottom: "24px",
  },
  grid: {
    display: "grid",
    gridTemplateColumns: "repeat(auto-fill, minmax(360px, 1fr))",
    gap: "16px",
  },
  batchCard: {
    backgroundColor: "#1e293b",
    borderRadius: "10px",
    padding: "16px",
    border: "1px solid #334155",
  },
  batchHeader: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: "12px",
  },
  coachTitle: {
    fontSize: "20px",
    fontWeight: "bold",
    color: "#38bdf8",
  },
  orderCount: {
    fontSize: "13px",
    color: "#94a3b8",
    fontWeight: "600",
  },
  progressTrack: {
    height: "6px",
    backgroundColor: "#0f172a",
    borderRadius: "3px",
    overflow: "hidden",
    marginBottom: "14px",
  },
  progressBar: {
    height: "100%",
    backgroundColor: "#22c55e",
  },
  ordersList: {
    display: "flex",
    flexDirection: "column",
    gap: "8px",
  },
  orderRow: {
    backgroundColor: "#0f172a",
    padding: "8px 12px",
    borderRadius: "6px",
    fontSize: "13px",
    display: "flex",
    alignItems: "center",
    gap: "8px",
  },
  berthTag: {
    fontWeight: "bold",
    color: "#f8fafc",
  },
  statusTag: {
    backgroundColor: "#334155",
    color: "#e2e8f0",
    padding: "2px 6px",
    borderRadius: "4px",
    fontSize: "11px",
    fontWeight: "bold",
  },
  itemSummary: {
    color: "#cbd5e1",
    overflow: "hidden",
    textOverflow: "ellipsis",
    whiteSpace: "nowrap",
    flex: 1,
  },
  empty: {
    color: "#64748b",
    fontSize: "16px",
    fontStyle: "italic",
    padding: "40px",
    textAlign: "center",
  },
};

