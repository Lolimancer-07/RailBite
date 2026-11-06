import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
} from "react-native";
import { useRouter } from "expo-router";
import { listLocalOrders, saveLocalOrder } from "../../src/db/queries.js";
import { getOrdersByPnr } from "../../src/net/restClient.js";
import { useSessionStore } from "../../src/store/session.js";

export default function OrdersListScreen() {
  const router = useRouter();
  const session = useSessionStore((s) => s.session);
  const [orders, setOrders] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // 1. Load instantly from local SQLite database
    const local = listLocalOrders();
    setOrders(local);
    setLoading(false);

    // 2. Refresh from server if passenger PNR is available
    if (session?.pnr) {
      getOrdersByPnr(session.pnr)
        .then((serverOrders) => {
          for (const so of serverOrders) {
            saveLocalOrder(so, []);
          }
          setOrders(listLocalOrders());
        })
        .catch(() => {
          // Stay on local
        });
    }
  }, [session]);

  const getStatusColor = (status: string) => {
    switch (status) {
      case "DELIVERED":
        return "#166534";
      case "OUT_FOR_DELIVERY":
        return "#2563eb";
      case "ACCEPTED":
      case "COOKING":
      case "READY":
        return "#ca8a04";
      case "CANCELLED":
      case "EXPIRED":
        return "#dc2626";
      default:
        return "#64748b";
    }
  };

  return (
    <View style={styles.container}>
      {orders.length === 0 ? (
        <View style={styles.emptyContainer}>
          <Text style={styles.emptyIcon}>📦</Text>
          <Text style={styles.emptyTitle}>No Orders Yet</Text>
          <Text style={styles.emptySubtitle}>You haven't placed any pantry car orders on this trip.</Text>
          <TouchableOpacity style={styles.btn} onPress={() => router.push("/menu")}>
            <Text style={styles.btnText}>Order Food</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <FlatList
          data={orders}
          keyExtractor={(item) => item.id}
          contentContainerStyle={{ padding: 16 }}
          renderItem={({ item }) => (
            <TouchableOpacity
              style={styles.orderCard}
              onPress={() => router.push(`/orders/${item.id}`)}
            >
              <View style={styles.cardHeader}>
                <Text style={styles.orderIdText}>Order #{item.id.slice(0, 8)}</Text>
                <View
                  style={[
                    styles.statusBadge,
                    { backgroundColor: `${getStatusColor(item.status)}20` },
                  ]}
                >
                  <Text
                    style={[
                      styles.statusText,
                      { color: getStatusColor(item.status) },
                    ]}
                  >
                    {item.status}
                  </Text>
                </View>
              </View>

              <Text style={styles.seatInfo}>
                Coach {item.coach} • Berth {item.berth}
              </Text>

              <View style={styles.cardFooter}>
                <Text style={styles.totalText}>₹{item.totalPaise / 100}</Text>
                <Text style={styles.arrowText}>View Tracking →</Text>
              </View>
            </TouchableOpacity>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#f8fafc",
  },
  emptyContainer: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    padding: 30,
  },
  emptyIcon: {
    fontSize: 48,
    marginBottom: 16,
  },
  emptyTitle: {
    fontSize: 20,
    fontWeight: "bold",
    color: "#0f172a",
    marginBottom: 8,
  },
  emptySubtitle: {
    fontSize: 14,
    color: "#64748b",
    textAlign: "center",
    marginBottom: 20,
  },
  btn: {
    backgroundColor: "#1e3a8a",
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: 8,
  },
  btnText: {
    color: "#ffffff",
    fontWeight: "bold",
    fontSize: 15,
  },
  orderCard: {
    backgroundColor: "#ffffff",
    padding: 16,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: "#e2e8f0",
    marginBottom: 12,
  },
  cardHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 6,
  },
  orderIdText: {
    fontSize: 14,
    fontWeight: "bold",
    color: "#0f172a",
  },
  statusBadge: {
    paddingVertical: 4,
    paddingHorizontal: 8,
    borderRadius: 4,
  },
  statusText: {
    fontSize: 12,
    fontWeight: "bold",
  },
  seatInfo: {
    fontSize: 13,
    color: "#64748b",
    marginBottom: 12,
  },
  cardFooter: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    borderTopWidth: 1,
    borderTopColor: "#f1f5f9",
    paddingTop: 10,
  },
  totalText: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#1e3a8a",
  },
  arrowText: {
    fontSize: 13,
    fontWeight: "bold",
    color: "#2563eb",
  },
});
