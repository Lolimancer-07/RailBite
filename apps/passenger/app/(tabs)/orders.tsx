import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
} from "react-native";
import { useRouter } from "expo-router";
import { listLocalOrders, saveLocalOrder } from "../../src/db/queries.js";
import { getOrdersByPnr } from "../../src/net/restClient.js";
import { useSessionStore } from "../../src/store/session.js";
import { colors } from "../../src/ui/theme.js";

const STATUS_LABEL: Record<string, string> = {
  PLACED: "Order placed",
  ASSIGNED: "Finding picker",
  ACCEPTED: "Picker accepted",
  PACKING: "Packing",
  OUT_FOR_DELIVERY: "Out for delivery",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled",
  EXPIRED: "Expired",
};

function statusColor(status: string): string {
  switch (status) {
    case "DELIVERED": return colors.primary;
    case "OUT_FOR_DELIVERY": case "PACKING": case "ACCEPTED": case "ASSIGNED": return colors.amber;
    case "CANCELLED": case "EXPIRED": return colors.danger;
    default: return colors.info;
  }
}

export default function OrdersListScreen() {
  const router = useRouter();
  const session = useSessionStore((s) => s.session);
  const [orders, setOrders] = useState<any[]>([]);

  useEffect(() => {
    // 1. Load instantly from local SQLite database
    const local = listLocalOrders();
    setOrders(local);

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

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Your Orders</Text>
      </View>

      {orders.length === 0 ? (
        <View style={styles.emptyContainer}>
          <Text style={styles.emptyIcon}>🧾</Text>
          <Text style={styles.emptyTitle}>No orders yet</Text>
          <Text style={styles.emptySub}>Order food on Home and it will show up here live.</Text>
          <TouchableOpacity style={styles.btn} onPress={() => router.push("/(tabs)/home")}>
            <Text style={styles.btnText}>Browse menu</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <FlatList
          data={orders}
          keyExtractor={(item) => item.id}
          contentContainerStyle={{ padding: 14, paddingBottom: 40 }}
          renderItem={({ item }) => (
            <TouchableOpacity
              style={styles.orderCard}
              onPress={() => router.push(`/orders/${item.id}`)}
            >
              <View style={styles.cardHeader}>
                <Text style={styles.orderIdText}>Order #{item.id.slice(0, 8)}</Text>
                <View style={[styles.statusBadge, { backgroundColor: statusColor(item.status) + "1A" }]}>
                  <Text style={[styles.statusText, { color: statusColor(item.status) }]}>
                    {STATUS_LABEL[item.status] || item.status}
                  </Text>
                </View>
              </View>

              <Text style={styles.seatInfo}>
                Coach {item.coach} • Seat {item.berth} • {(item.items || []).map((i: any) => `${i.name || "Item"} ×${i.qty}`).join(", ") || "Items on the way"}
              </Text>

              <View style={styles.cardFooter}>
                <Text style={styles.totalText}>₹{item.totalPaise / 100}</Text>
                <Text style={styles.arrowText}>Track order →</Text>
              </View>
            </TouchableOpacity>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  header: {
    backgroundColor: colors.primary,
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 14,
    borderBottomLeftRadius: 18,
    borderBottomRightRadius: 18,
  },
  headerTitle: { color: "#ffffff", fontSize: 18, fontWeight: "bold" },
  emptyContainer: { flex: 1, justifyContent: "center", alignItems: "center", padding: 30 },
  emptyIcon: { fontSize: 48, marginBottom: 16 },
  emptyTitle: { fontSize: 18, fontWeight: "bold", color: colors.ink, marginBottom: 6 },
  emptySub: { fontSize: 13, color: colors.inkSoft, textAlign: "center", marginBottom: 20 },
  btn: {
    backgroundColor: colors.primary,
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: 10,
  },
  btnText: { color: "#ffffff", fontWeight: "bold", fontSize: 14 },
  orderCard: {
    backgroundColor: "#ffffff",
    padding: 14,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colors.border,
    marginBottom: 10,
  },
  cardHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 6,
  },
  orderIdText: { fontSize: 14, fontWeight: "bold", color: colors.ink },
  statusBadge: { paddingVertical: 4, paddingHorizontal: 8, borderRadius: 8 },
  statusText: { fontSize: 11, fontWeight: "bold" },
  seatInfo: { fontSize: 12, color: colors.inkSoft, marginBottom: 12 },
  cardFooter: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    borderTopWidth: 1,
    borderTopColor: colors.border,
    paddingTop: 10,
  },
  totalText: { fontSize: 15, fontWeight: "bold", color: colors.ink },
  arrowText: { fontSize: 12, fontWeight: "bold", color: colors.primary },
});
