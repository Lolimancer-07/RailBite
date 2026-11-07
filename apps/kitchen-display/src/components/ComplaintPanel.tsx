import React from "react";

interface ComplaintItem {
  id: string;
  orderId: string;
  type: string;
  text?: string;
  dueAt?: string;
  createdAt: string;
}

interface ComplaintPanelProps {
  complaints: ComplaintItem[];
}

export function ComplaintPanel({ complaints }: ComplaintPanelProps) {
  const getMinutesLeft = (dueAt?: string) => {
    if (!dueAt) return 15;
    const diff = Math.round((new Date(dueAt).getTime() - Date.now()) / 60000);
    return diff;
  };

  return (
    <div style={styles.container}>
      <h2 style={styles.title}>FOOD QUALITY & REPLACEMENT QUEUE (TIER 1)</h2>
      <p style={styles.sub}>
        Indian Railways 15-Minute Replacement Guarantee. Immediate prep required for passenger seat-side delivery.
      </p>

      <div style={styles.list}>
        {complaints.map((c) => {
          const minsLeft = getMinutesLeft(c.dueAt);
          const isOverdue = minsLeft <= 0;

          return (
            <div
              key={c.id}
              style={{
                ...styles.card,
                borderColor: isOverdue ? "#ef4444" : "#f59e0b",
                backgroundColor: isOverdue ? "#450a0a" : "#1e293b",
              }}
            >
              <div style={styles.cardHeader}>
                <span style={styles.typeText}>{c.type.replace(/_/g, " ")}</span>
                <span
                  style={{
                    ...styles.timerBadge,
                    backgroundColor: isOverdue ? "#991b1b" : "#78350f",
                    color: isOverdue ? "#fecaca" : "#fef3c7",
                  }}
                >
                  {isOverdue ? `OVERDUE (${Math.abs(minsLeft)}m ago)` : `${minsLeft} mins remaining`}
                </span>
              </div>

              {c.text && <p style={styles.comment}>"{c.text}"</p>}

              <div style={styles.cardFooter}>
                <span style={styles.orderRef}>Order Ref: #{c.orderId.slice(0, 8)}</span>
                <span style={styles.priorityText}>⚡ HIGH PRIORITY REPLACEMENT</span>
              </div>
            </div>
          );
        })}

        {complaints.length === 0 && (
          <div style={styles.empty}>✓ No active food replacement complaints. Quality score 100%.</div>
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
  list: {
    display: "flex",
    flexDirection: "column",
    gap: "14px",
    maxWidth: "800px",
  },
  card: {
    borderRadius: "10px",
    padding: "18px",
    border: "2px solid #334155",
  },
  cardHeader: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: "10px",
  },
  typeText: {
    fontSize: "18px",
    fontWeight: "bold",
    color: "#ffffff",
  },
  timerBadge: {
    padding: "4px 10px",
    borderRadius: "6px",
    fontWeight: "bold",
    fontSize: "13px",
  },
  comment: {
    fontSize: "15px",
    color: "#e2e8f0",
    fontStyle: "italic",
    marginBottom: "12px",
  },
  cardFooter: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    borderTop: "1px solid rgba(255,255,255,0.1)",
    paddingTop: "10px",
  },
  orderRef: {
    fontSize: "12px",
    color: "#94a3b8",
  },
  priorityText: {
    fontSize: "12px",
    fontWeight: "bold",
    color: "#f87171",
  },
  empty: {
    color: "#4ade80",
    fontSize: "16px",
    padding: "40px",
    backgroundColor: "#064e3b",
    borderRadius: "8px",
    textAlign: "center",
    fontWeight: "bold",
  },
};

