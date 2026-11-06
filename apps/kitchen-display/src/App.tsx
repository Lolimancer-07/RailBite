import React, { useEffect, useState } from "react";
import type { MqttKitchenQueueItem } from "@railbite/shared";
import { kitchenMqtt } from "./mqtt.js";
import { playKitchenChime } from "./audio.js";
import { Board } from "./components/Board.js";
import { BatchView } from "./components/BatchView.js";
import { ComplaintPanel } from "./components/ComplaintPanel.js";

type Tab = "BOARD" | "BATCH" | "COMPLAINTS";

export function App() {
  const [tab, setTab] = useState<Tab>("BOARD");
  const [orders, setOrders] = useState<MqttKitchenQueueItem[]>([]);
  const [complaints, setComplaints] = useState<any[]>([]);
  const [time, setTime] = useState<string>(new Date().toLocaleTimeString());

  useEffect(() => {
    // Clock tick
    const clockInterval = setInterval(() => {
      setTime(new Date().toLocaleTimeString());
    }, 1000);

    // Connect MQTT
    kitchenMqtt.connect();

    const unsubscribeQueue = kitchenMqtt.onQueueUpdate((incomingOrders) => {
      setOrders((prev) => {
        // If a new order arrived that wasn't in prev, chime!
        const prevIds = new Set(prev.map((o) => o.orderId));
        const hasNewOrder = incomingOrders.some((o) => !prevIds.has(o.orderId) && o.status === "PLACED");
        if (hasNewOrder) {
          playKitchenChime();
        }
        return incomingOrders;
      });
    });

    const unsubscribeComplaints = kitchenMqtt.onComplaint((complaint) => {
      if (complaint.tier === 1) {
        playKitchenChime();
        setComplaints((prev) => [complaint, ...prev]);
      }
    });

    // Also fetch active complaints via REST on mount
    const host = window.location.hostname || "localhost";
    fetch(`http://${host}:8080/complaints`)
      .then((res) => res.json())
      .then((list) => {
        if (Array.isArray(list)) {
          setComplaints(list.filter((c) => c.tier === 1));
        }
      })
      .catch(() => {});

    return () => {
      clearInterval(clockInterval);
      unsubscribeQueue();
      unsubscribeComplaints();
    };
  }, []);

  const handleStartCooking = (orderId: string) => {
    kitchenMqtt.sendAction(orderId, "START");
    setOrders((prev) =>
      prev.map((o) => (o.orderId === orderId ? { ...o, status: "COOKING" } : o)),
    );
  };

  const handleMarkReady = (orderId: string) => {
    kitchenMqtt.sendAction(orderId, "READY");
    setOrders((prev) =>
      prev.map((o) => (o.orderId === orderId ? { ...o, status: "READY" } : o)),
    );
  };

  return (
    <div style={styles.appContainer}>
      {/* Top Header Bar */}
      <header style={styles.header}>
        <div style={styles.logoSection}>
          <span style={styles.trainIcon}>🚆</span>
          <div>
            <h1 style={styles.title}>RailBite Pantry Car Kitchen Display</h1>
            <span style={styles.subtitle}>Head Cook Station • Train 12951</span>
          </div>
        </div>

        {/* Tab Switcher */}
        <nav style={styles.navTabs}>
          <button
            style={{
              ...styles.tabBtn,
              ...(tab === "BOARD" ? styles.tabBtnActive : {}),
            }}
            onClick={() => setTab("BOARD")}
          >
            📋 KITCHEN BOARD ({orders.length})
          </button>

          <button
            style={{
              ...styles.tabBtn,
              ...(tab === "BATCH" ? styles.tabBtnActive : {}),
            }}
            onClick={() => setTab("BATCH")}
          >
            🚋 COACH BATCH VIEW
          </button>

          <button
            style={{
              ...styles.tabBtn,
              ...(tab === "COMPLAINTS" ? styles.tabBtnActive : {}),
              ...(complaints.length > 0 ? styles.tabBtnAlert : {}),
            }}
            onClick={() => setTab("COMPLAINTS")}
          >
            ⚠️ REPLACEMENTS ({complaints.length})
          </button>
        </nav>

        {/* Clock & Status */}
        <div style={styles.statusSection}>
          <span style={styles.onlineBadge}>● LIVE MQTT</span>
          <span style={styles.clock}>{time}</span>
        </div>
      </header>

      {/* Main Content Area */}
      <main style={styles.main}>
        {tab === "BOARD" && (
          <Board
            orders={orders}
            onStart={handleStartCooking}
            onReady={handleMarkReady}
          />
        )}
        {tab === "BATCH" && <BatchView orders={orders} />}
        {tab === "COMPLAINTS" && <ComplaintPanel complaints={complaints} />}
      </main>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  appContainer: {
    display: "flex",
    flexDirection: "column",
    height: "100vh",
    width: "100vw",
    backgroundColor: "#0f172a",
    fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif",
    overflow: "hidden",
  },
  header: {
    height: "70px",
    backgroundColor: "#1e293b",
    borderBottom: "2px solid #334155",
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    padding: "0 20px",
  },
  logoSection: {
    display: "flex",
    alignItems: "center",
    gap: "12px",
  },
  trainIcon: {
    fontSize: "28px",
  },
  title: {
    fontSize: "18px",
    fontWeight: "bold",
    color: "#f8fafc",
    margin: 0,
  },
  subtitle: {
    fontSize: "12px",
    color: "#94a3b8",
  },
  navTabs: {
    display: "flex",
    gap: "8px",
  },
  tabBtn: {
    backgroundColor: "#0f172a",
    color: "#94a3b8",
    border: "1px solid #334155",
    padding: "10px 18px",
    borderRadius: "8px",
    fontWeight: "bold",
    fontSize: "13px",
    cursor: "pointer",
    transition: "all 0.2s ease",
  },
  tabBtnActive: {
    backgroundColor: "#2563eb",
    color: "#ffffff",
    borderColor: "#3b82f6",
  },
  tabBtnAlert: {
    backgroundColor: "#991b1b",
    color: "#fee2e2",
    borderColor: "#ef4444",
  },
  statusSection: {
    display: "flex",
    alignItems: "center",
    gap: "14px",
  },
  onlineBadge: {
    color: "#22c55e",
    fontWeight: "bold",
    fontSize: "13px",
  },
  clock: {
    color: "#f8fafc",
    fontWeight: "bold",
    fontSize: "16px",
    fontVariantNumeric: "tabular-nums",
  },
  main: {
    flex: 1,
    display: "flex",
    overflow: "hidden",
  },
};

export default App;
