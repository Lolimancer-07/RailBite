import React, { useEffect, useState, useCallback } from "react";
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  Alert,
  Switch,
  Modal,
  TextInput,
} from "react-native";
import { useRouter } from "expo-router";
import { useAuthStore } from "../src/store/auth.js";
import { useActiveOrdersStore } from "../src/store/orders.js";
import { deliverOrder, setPickerOnline, getPickerStats } from "../src/net/captainRestClient.js";
import { colors } from "../src/ui/theme.js";

const STATUS_LABEL: Record<string, string> = {
  ACCEPTED: "Accepted — start picking",
  PACKING: "Packing",
  OUT_FOR_DELIVERY: "Out for delivery",
};

export default function PickerHomeScreen() {
  const router = useRouter();
  const staff = useAuthStore((s) => s.staff);
  const { activeOrders, updateOrderStatus, removeOrder } = useActiveOrdersStore();

  const [online, setOnline] = useState(true);
  const [stats, setStats] = useState<{ ordersDelivered: number; collectedPaise: number; confirmedPaise: number } | null>(null);

  const loadStats = useCallback(() => {
    if (!staff?.staffId) return;
    getPickerStats(staff.staffId)
      .then((s) =>
        setStats({
          ordersDelivered: s.ordersDelivered,
          collectedPaise: s.collectedPaise,
          confirmedPaise: s.passengerConfirmedPaise,
        }),
      )
      .catch(() => setStats(null));
  }, [staff]);

  useEffect(() => {
    loadStats();
  }, [loadStats]);

  const toggleOnline = async (value: boolean) => {
    if (!staff?.staffId) return;
    setOnline(value);
    try {
      await setPickerOnline(staff.staffId, value);
    } catch (err: any) {
      setOnline(!value);
      Alert.alert("Error", err.message || "Could not update presence");
    }
  };

  const handleOpenChecklist = (orderId: string) => {
    router.push(`/order/${orderId}`);
  };

  const handleOpenDeliveryModal = (order: any) => {
    setDeliveryModalOrder(order);
    setCashCollected(String(order.totalPaise / 100));
  };

  const [deliveryModalOrder, setDeliveryModalOrder] = useState<any>(null);
  const [cashCollected, setCashCollected] = useState("");

  const handleConfirmDelivery = async () => {
    if (!staff?.staffId || !deliveryModalOrder) return;

    const cashAmountPaise = Math.round(parseFloat(cashCollected || "0") * 100);
    try {
      await deliverOrder(deliveryModalOrder.id, staff.staffId, cashAmountPaise);
      removeOrder(deliveryModalOrder.id);
      setDeliveryModalOrder(null);
      Alert.alert("Delivered", `Order marked delivered. Cash ledger recorded ₹${cashAmountPaise / 100}`);
      loadStats();
    } catch (err: any) {
      Alert.alert("Error", err.message || "Failed to record delivery");
    }
  };

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <View>
          <Text style={styles.staffName}>{staff?.name || "Picker"}</Text>
          <Text style={styles.zoneCoaches}>
            Zone: {(staff?.zoneCoaches || []).join(", ") || "All"}
          </Text>
        </View>
        <View style={styles.onlineRow}>
          <Text style={[styles.onlineLabel, online && styles.onlineLabelOn]}>
            {online ? "ONLINE" : "OFFLINE"}
          </Text>
          <Switch
            value={online}
            onValueChange={toggleOnline}
            trackColor={{ true: colors.primary, false: "#94a3b8" }}
            thumbColor="#ffffff"
          />
        </View>
      </View>

      {/* Today's stats */}
      <View style={styles.statsRow}>
        <View style={styles.statCard}>
          <Text style={styles.statValue}>{stats?.ordersDelivered ?? "—"}</Text>
          <Text style={styles.statLabel}>Delivered today</Text>
        </View>
        <View style={styles.statCard}>
          <Text style={styles.statValue}>₹{(stats?.collectedPaise ?? 0) / 100}</Text>
          <Text style={styles.statLabel}>Cash collected</Text>
        </View>
        <View style={styles.statCard}>
          <Text style={styles.statValue}>₹{(stats?.confirmedPaise ?? 0) / 100}</Text>
          <Text style={styles.statLabel}>Confirmed</Text>
        </View>
      </View>

      {/* Nav */}
      <View style={styles.navRow}>
        <TouchableOpacity style={styles.navBtn} onPress={() => router.push("/cash")}>
          <Text style={styles.navBtnText}>💰 Cash ledger</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.navBtn} onPress={() => router.push("/complaints")}>
          <Text style={styles.navBtnText}>⚠ Issues</Text>
        </TouchableOpacity>
      </View>

      {/* Active orders */}
      {activeOrders.length === 0 ? (
        <View style={styles.emptyContainer}>
          <Text style={styles.emptyIcon}>📦</Text>
          <Text style={styles.emptyTitle}>No active orders</Text>
          <Text style={styles.emptySub}>
            {online
              ? "You will ring instantly when a new order lands in your zone."
              : "Go ONLINE to start receiving orders."}
          </Text>
        </View>
      ) : (
        <FlatList
          data={activeOrders}
          keyExtractor={(item) => item.id}
          contentContainerStyle={{ padding: 14, paddingBottom: 30 }}
          renderItem={({ item }) => (
            <View style={styles.orderCard}>
              <View style={styles.cardHeader}>
                <Text style={styles.seatTitle}>
                  Coach {item.coach} • Seat {item.berth}
                </Text>
                <View style={styles.statusPill}>
                  <Text style={styles.statusPillText}>{STATUS_LABEL[item.status] || item.status}</Text>
                </View>
              </View>

              <View style={styles.itemsBlock}>
                {(item.items || []).map((it: any, i: number) => (
                  <Text key={i} style={styles.itemText}>
                    • {it.name || "Item"} × {it.qty}
                  </Text>
                ))}
              </View>

              <View style={styles.cardFooter}>
                <Text style={styles.collectText}>
                  Collect: <Text style={styles.amount}>₹{item.totalPaise / 100}</Text>
                </Text>

                {item.status === "ACCEPTED" ? (
                  <TouchableOpacity style={styles.pickupBtn} onPress={() => handleOpenChecklist(item.id)}>
                    <Text style={styles.pickupBtnText}>Picking checklist →</Text>
                  </TouchableOpacity>
                ) : item.status === "PACKING" ? (
                  <TouchableOpacity style={styles.pickupBtn} onPress={() => handleOpenChecklist(item.id)}>
                    <Text style={styles.pickupBtnText}>Continue pick →</Text>
                  </TouchableOpacity>
                ) : (
                  <TouchableOpacity style={styles.deliverBtn} onPress={() => handleOpenDeliveryModal(item)}>
                    <Text style={styles.deliverBtnText}>Delivered + cash →</Text>
                  </TouchableOpacity>
                )}
              </View>
            </View>
          )}
        />
      )}

      {/* Cash collection modal */}
      {deliveryModalOrder ? (
        <Modal visible transparent animationType="slide">
          <View style={styles.modalOverlay}>
            <View style={styles.modalContent}>
              <Text style={styles.modalTitle}>Confirm delivery & cash</Text>
              <Text style={styles.modalSub}>
                Delivered to Coach {deliveryModalOrder.coach}, Seat {deliveryModalOrder.berth}
              </Text>

              <Text style={styles.inputLabel}>Cash amount collected (₹)</Text>
              <TextInput
                style={styles.cashInput}
                keyboardType="numeric"
                value={cashCollected}
                onChangeText={setCashCollected}
              />

              <View style={styles.modalButtons}>
                <TouchableOpacity style={styles.cancelModalBtn} onPress={() => setDeliveryModalOrder(null)}>
                  <Text style={styles.cancelModalText}>Cancel</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.confirmModalBtn} onPress={handleConfirmDelivery}>
                  <Text style={styles.confirmModalText}>Confirm & complete</Text>
                </TouchableOpacity>
              </View>
            </View>
          </View>
        </Modal>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  header: {
    backgroundColor: colors.primaryDark,
    padding: 16,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  staffName: { fontSize: 16, fontWeight: "bold", color: "#ffffff" },
  zoneCoaches: { fontSize: 12, color: "#a7f3d0", marginTop: 2 },
  onlineRow: { flexDirection: "row", alignItems: "center", gap: 8 },
  onlineLabel: { fontSize: 12, fontWeight: "bold", color: "#94a3b8" },
  onlineLabelOn: { color: "#6ee7b7" },
  statsRow: { flexDirection: "row", gap: 10, padding: 14 },
  statCard: {
    flex: 1,
    backgroundColor: "#ffffff",
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 12,
    alignItems: "center",
  },
  statValue: { fontSize: 17, fontWeight: "bold", color: colors.ink },
  statLabel: { fontSize: 11, color: colors.inkSoft, marginTop: 3, textAlign: "center" },
  navRow: { flexDirection: "row", gap: 10, paddingHorizontal: 14, marginBottom: 4 },
  navBtn: {
    flex: 1,
    backgroundColor: "#ffffff",
    borderWidth: 1,
    borderColor: colors.border,
    paddingVertical: 10,
    borderRadius: 12,
    alignItems: "center",
  },
  navBtnText: { color: colors.ink, fontSize: 12, fontWeight: "bold" },
  emptyContainer: { flex: 1, justifyContent: "center", alignItems: "center", padding: 30 },
  emptyIcon: { fontSize: 44, marginBottom: 12 },
  emptyTitle: { fontSize: 19, fontWeight: "bold", color: colors.ink, marginBottom: 6 },
  emptySub: { fontSize: 13, color: colors.inkSoft, textAlign: "center" },
  orderCard: {
    backgroundColor: "#ffffff",
    borderRadius: 14,
    padding: 14,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: colors.border,
    elevation: 2,
  },
  cardHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 8,
  },
  seatTitle: { fontSize: 17, fontWeight: "bold", color: colors.ink },
  statusPill: {
    backgroundColor: colors.primarySoft,
    paddingVertical: 4,
    paddingHorizontal: 8,
    borderRadius: 8,
  },
  statusPillText: { color: colors.primaryDark, fontSize: 10, fontWeight: "bold" },
  itemsBlock: { marginVertical: 8 },
  itemText: { fontSize: 14, color: colors.ink, marginBottom: 2 },
  cardFooter: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginTop: 8,
    paddingTop: 10,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  collectText: { fontSize: 13, color: colors.inkSoft },
  amount: { fontWeight: "bold", fontSize: 16, color: colors.ink },
  pickupBtn: {
    backgroundColor: colors.info,
    paddingVertical: 9,
    paddingHorizontal: 14,
    borderRadius: 10,
  },
  pickupBtnText: { color: "#ffffff", fontWeight: "bold", fontSize: 12 },
  deliverBtn: {
    backgroundColor: colors.primary,
    paddingVertical: 9,
    paddingHorizontal: 14,
    borderRadius: 10,
  },
  deliverBtnText: { color: "#ffffff", fontWeight: "bold", fontSize: 12 },
  modalOverlay: { flex: 1, backgroundColor: "rgba(0,0,0,0.5)", justifyContent: "center", padding: 24 },
  modalContent: { backgroundColor: "#ffffff", borderRadius: 16, padding: 20 },
  modalTitle: { fontSize: 18, fontWeight: "bold", color: colors.ink, marginBottom: 4 },
  modalSub: { fontSize: 13, color: colors.inkSoft, marginBottom: 16 },
  inputLabel: { fontSize: 13, fontWeight: "600", color: colors.ink, marginBottom: 6 },
  cashInput: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 10,
    padding: 12,
    fontSize: 20,
    fontWeight: "bold",
    marginBottom: 16,
    color: colors.ink,
  },
  modalButtons: { flexDirection: "row", gap: 12 },
  cancelModalBtn: {
    flex: 1,
    padding: 12,
    alignItems: "center",
    borderRadius: 10,
    borderWidth: 1,
    borderColor: colors.border,
  },
  cancelModalText: { color: colors.inkSoft, fontWeight: "600" },
  confirmModalBtn: {
    flex: 1,
    backgroundColor: colors.primary,
    padding: 12,
    alignItems: "center",
    borderRadius: 10,
  },
  confirmModalText: { color: "#ffffff", fontWeight: "bold" },
});
