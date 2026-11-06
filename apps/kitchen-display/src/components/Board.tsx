import React from "react";
import type { MqttKitchenQueueItem } from "@railbite/shared";

interface BoardProps {
  orders: MqttKitchenQueueItem[];
  onStart: (orderId: string) => void;
  onReady: (orderId: string) => void;
}

export function Board({ orders, onStart, onReady }: BoardProps) {
  const newOrders = orders.filter((o) => o.status === "PLACED");
  const cookingOrders = orders.filter((o) => o.status === "COOKING");
  const readyOrders = orders.filter((o) => o.status === "READY");

  return (
    <div style={styles.board}>
      {/* Column 1: New Orders */}
      <div style={styles.column}>
        <div style={{ ...styles.columnHeader, backgroundColor: "#1e3a8a" }}>
          <h2>NEW ORDERS ({newOrders.length})</h2>
          <span style={styles.headerSub}>Tap to start cooking</span>
        </div>
        <div style={styles.cardList}>
          {newOrders.map((order) => (
            <div key={order.orderId} style={styles.card}>
              <div style={styles.cardTop}>
                <span style={styles.coachBadge}>COACH {order.coach}</span>
                <span style={styles.berthText}>Berth {order.berth}</span>
              </div>
              <div style={styles.itemsList}>
                {order.items.map((it, idx) => (
                  <div key={idx} style={styles.itemRow}>
                    <span style={styles.itemName}>{it.name}</span>
                    <span style={styles.itemQty}>× {it.qty}</span>
                  </div>
                ))}
              </div>
              <div style={styles.cardFooter}>
                <span style={styles.pnrText}>PNR: {order.pnr}</span>
                <button
                  style={styles.startBtn}
                  onClick={() => onStart(order.orderId)}
                >
                  ▶ START COOKING
                </button>
              </div>
            </div>
          ))}
          {newOrders.length === 0 && (
            <div style={styles.emptyCol}>No new meal orders</div>
          )}
        </div>
      </div>

      {/* Column 2: Cooking */}
      <div style={styles.column}>
        <div style={{ ...styles.columnHeader, backgroundColor: "#d97706" }}>
          <h2>COOKING ({cookingOrders.length})</h2>
          <span style={styles.headerSub}>On the stove</span>
        </div>
        <div style={styles.cardList}>
          {cookingOrders.map((order) => (
            <div key={order.orderId} style={{ ...styles.card, borderColor: "#f59e0b" }}>
              <div style={styles.cardTop}>
                <span style={{ ...styles.coachBadge, backgroundColor: "#fef3c7", color: "#92400e" }}>
                  COACH {order.coach}
                </span>
                <span style={styles.berthText}>Berth {order.berth}</span>
              </div>
              <div style={styles.itemsList}>
                {order.items.map((it, idx) => (
                  <div key={idx} style={styles.itemRow}>
                    <span style={styles.itemName}>{it.name}</span>
                    <span style={styles.itemQty}>× {it.qty}</span>
                  </div>
                ))}
              </div>
              <div style={styles.cardFooter}>
                <span style={styles.flameIcon}>🔥 Preparing</span>
                <button
                  style={styles.readyBtn}
                  onClick={() => onReady(order.orderId)}
                >
                  ✓ MARK READY
                </button>
              </div>
            </div>
          ))}
          {cookingOrders.length === 0 && (
            <div style={styles.emptyCol}>Nothing currently cooking</div>
          )}
        </div>
      </div>

      {/* Column 3: Ready */}
      <div style={styles.column}>
        <div style={{ ...styles.columnHeader, backgroundColor: "#15803d" }}>
          <h2>READY ({readyOrders.length})</h2>
          <span style={styles.headerSub}>Awaiting Captain Dispatch</span>
        </div>
        <div style={styles.cardList}>
          {readyOrders.map((order) => (
            <div key={order.orderId} style={{ ...styles.card, borderColor: "#86efac" }}>
              <div style={styles.cardTop}>
                <span style={{ ...styles.coachBadge, backgroundColor: "#dcfce7", color: "#166534" }}>
                  COACH {order.coach}
                </span>
                <span style={styles.berthText}>Berth {order.berth}</span>
              </div>
              <div style={styles.itemsList}>
                {order.items.map((it, idx) => (
                  <div key={idx} style={styles.itemRow}>
                    <span style={styles.itemName}>{it.name}</span>
                    <span style={styles.itemQty}>× {it.qty}</span>
                  </div>
                ))}
              </div>
              <div style={styles.cardFooter}>
                <span style={styles.dispatchedText}>✓ Cooked • Batching</span>
              </div>
            </div>
          ))}
          {readyOrders.length === 0 && (
            <div style={styles.emptyCol}>No trays waiting</div>
          )}
        </div>
      </div>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  board: {
    display: "flex",
    gap: "16px",
    flex: 1,
    overflowX: "auto",
    padding: "16px",
  },
  column: {
    flex: 1,
    minWidth: "320px",
    backgroundColor: "#1e293b",
    borderRadius: "12px",
    display: "flex",
    flexDirection: "column",
    overflow: "hidden",
  },
  columnHeader: {
    padding: "16px",
    color: "#ffffff",
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
  },
  headerSub: {
    fontSize: "12px",
    opacity: 0.8,
  },
  cardList: {
    padding: "14px",
    display: "flex",
    flexDirection: "column",
    gap: "12px",
    overflowY: "auto",
    flex: 1,
  },
  card: {
    backgroundColor: "#0f172a",
    border: "2px solid #334155",
    borderRadius: "8px",
    padding: "14px",
    color: "#ffffff",
  },
  cardTop: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: "10px",
  },
  coachBadge: {
    backgroundColor: "#dbeafe",
    color: "#1e40af",
    padding: "4px 8px",
    borderRadius: "6px",
    fontWeight: "bold",
    fontSize: "14px",
  },
  berthText: {
    fontSize: "16px",
    fontWeight: "bold",
    color: "#94a3b8",
  },
  itemsList: {
    display: "flex",
    flexDirection: "column",
    gap: "6px",
    marginBottom: "12px",
  },
  itemRow: {
    display: "flex",
    justifyContent: "space-between",
    fontSize: "15px",
  },
  itemName: {
    color: "#e2e8f0",
  },
  itemQty: {
    fontWeight: "bold",
    color: "#38bdf8",
  },
  cardFooter: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    borderTop: "1px solid #1e293b",
    paddingTop: "10px",
  },
  pnrText: {
    fontSize: "12px",
    color: "#64748b",
  },
  startBtn: {
    backgroundColor: "#2563eb",
    color: "#ffffff",
    border: "none",
    padding: "10px 16px",
    borderRadius: "6px",
    fontWeight: "bold",
    cursor: "pointer",
    fontSize: "13px",
  },
  readyBtn: {
    backgroundColor: "#16a34a",
    color: "#ffffff",
    border: "none",
    padding: "10px 16px",
    borderRadius: "6px",
    fontWeight: "bold",
    cursor: "pointer",
    fontSize: "13px",
  },
  flameIcon: {
    color: "#f59e0b",
    fontSize: "13px",
    fontWeight: "bold",
  },
  dispatchedText: {
    color: "#22c55e",
    fontSize: "13px",
    fontWeight: "bold",
  },
  emptyCol: {
    color: "#64748b",
    textAlign: "center",
    marginTop: "40px",
    fontSize: "14px",
    fontStyle: "italic",
  },
};
