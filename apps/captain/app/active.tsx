import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  Alert,
  TextInput,
  Modal,
} from "react-native";
import { useRouter } from "expo-router";
import { useAuthStore } from "../src/store/auth.js";
import { useActiveOrdersStore } from "../src/store/orders.js";
import { useAlertsStore } from "../src/store/alerts.js";
import { pickupOrder, deliverOrder } from "../src/net/captainRestClient.js";

export default function ActiveDeliveriesScreen() {
  const router = useRouter();
  const staff = useAuthStore((s) => s.staff);
  const { activeOrders, updateOrderStatus, removeOrder } = useActiveOrdersStore();
  const currentAlert = useAlertsStore((s) => s.currentAlert);

  const [deliveryModalOrder, setDeliveryModalOrder] = useState<any>(null);
  const [cashCollected, setCashCollected] = useState("");

  // If a new urgent alert comes in, jump to full-screen alert
  useEffect(() => {
    if (currentAlert) {
      router.push("/alerts");
    }
  }, [currentAlert, router]);

  const handlePickup = async (orderId: string) => {
    if (!staff?.staffId) return;
    try {
      await pickupOrder(orderId, staff.staffId);
      updateOrderStatus(orderId, "OUT_FOR_DELIVERY");
    } catch (err: any) {
      Alert.alert("Error", err.message || "Failed to update status to Picked Up");
    }
  };

  const handleOpenDeliveryModal = (order: any) => {
    setDeliveryModalOrder(order);
    setCashCollected(String(order.totalPaise / 100));
  };

  const handleConfirmDelivery = async () => {
    if (!staff?.staffId || !deliveryModalOrder) return;

    const cashAmountPaise = Math.round(parseFloat(cashCollected || "0") * 100);
    try {
      await deliverOrder(deliveryModalOrder.id, staff.staffId, cashAmountPaise);
      removeOrder(deliveryModalOrder.id);
      setDeliveryModalOrder(null);
      Alert.alert("Delivered", `Order marked delivered. Cash ledger recorded ₹${cashAmountPaise / 100}`);
    } catch (err: any) {
      Alert.alert("Error", err.message || "Failed to record delivery");
    }
  };

  return (
    <View style={styles.container}>
      {/* Captain Profile Top Bar */}
      <View style={styles.topBar}>
        <View>
          <Text style={styles.staffName}>{staff?.name || "Captain"}</Text>
          <Text style={styles.zoneCoaches}>
            Assigned Zone: {(staff?.zoneCoaches || []).join(", ") || "All"}
          </Text>
        </View>

        <View style={styles.topActions}>
          <TouchableOpacity
            style={styles.navBtn}
            onPress={() => router.push("/cash")}
          >
            <Text style={styles.navBtnText}>💰 Cash Ledger</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.navBtn}
            onPress={() => router.push("/complaints")}
          >
            <Text style={styles.navBtnText}>⚠️ Issues</Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* Active Orders List */}
      {activeOrders.length === 0 ? (
        <View style={styles.emptyContainer}>
          <Text style={styles.emptyIcon}>☕</Text>
          <Text style={styles.emptyTitle}>No Active Deliveries</Text>
          <Text style={styles.emptySubtitle}>
            Waiting for passenger orders in your coach zone...
          </Text>
          <Text style={styles.listeningText}>● Listening on MQTT broker</Text>
        </View>
      ) : (
        <FlatList
          data={activeOrders}
          keyExtractor={(item) => item.id}
          contentContainerStyle={{ padding: 14 }}
          renderItem={({ item }) => (
            <View style={styles.orderCard}>
              <View style={styles.cardHeader}>
                <Text style={styles.seatTitle}>
                  COACH {item.coach} • BERTH {item.berth}
                </Text>
                <View style={styles.statusPill}>
                  <Text style={styles.statusPillText}>{item.status}</Text>
                </View>
              </View>

              {item.verified === 0 ? (
                <View style={styles.unverifiedBanner}>
                  <Text style={styles.unverifiedText}>
                    ⚠️ Unverified PNR ({item.pnr}) — Check passenger ticket
                  </Text>
                </View>
              ) : null}

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
                  <TouchableOpacity
                    style={styles.pickupBtn}
                    onPress={() => handlePickup(item.id)}
                  >
                    <Text style={styles.pickupBtnText}>Mark Picked Up →</Text>
                  </TouchableOpacity>
                ) : (
                  <TouchableOpacity
                    style={styles.deliverBtn}
                    onPress={() => handleOpenDeliveryModal(item)}
                  >
                    <Text style={styles.deliverBtnText}>Delivered + Cash →</Text>
                  </TouchableOpacity>
                )}
              </View>
            </View>
          )}
        />
      )}

      {/* Cash Collection Modal */}
      {deliveryModalOrder ? (
        <Modal transparent animationType="slide">
          <View style={styles.modalOverlay}>
            <View style={styles.modalContent}>
              <Text style={styles.modalTitle}>Confirm Delivery & Cash</Text>
              <Text style={styles.modalSub}>
                Delivered to Coach {deliveryModalOrder.coach}, Berth {deliveryModalOrder.berth}
              </Text>

              <Text style={styles.inputLabel}>Cash Amount Collected (₹)</Text>
              <TextInput
                style={styles.cashInput}
                keyboardType="numeric"
                value={cashCollected}
                onChangeText={setCashCollected}
              />

              <View style={styles.modalButtons}>
                <TouchableOpacity
                  style={styles.cancelModalBtn}
                  onPress={() => setDeliveryModalOrder(null)}
                >
                  <Text style={styles.cancelModalText}>Cancel</Text>
                </TouchableOpacity>

                <TouchableOpacity
                  style={styles.confirmModalBtn}
                  onPress={handleConfirmDelivery}
                >
                  <Text style={styles.confirmModalText}>Confirm & Complete</Text>
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
  container: {
    flex: 1,
    backgroundColor: "#f0fdf4",
  },
  topBar: {
    backgroundColor: "#065f46",
    padding: 16,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  staffName: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#ffffff",
  },
  zoneCoaches: {
    fontSize: 12,
    color: "#a7f3d0",
    marginTop: 2,
  },
  topActions: {
    flexDirection: "row",
    gap: 8,
  },
  navBtn: {
    backgroundColor: "#047857",
    paddingVertical: 8,
    paddingHorizontal: 10,
    borderRadius: 6,
  },
  navBtnText: {
    color: "#ffffff",
    fontSize: 11,
    fontWeight: "bold",
  },
  emptyContainer: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    padding: 30,
  },
  emptyIcon: {
    fontSize: 48,
    marginBottom: 12,
  },
  emptyTitle: {
    fontSize: 20,
    fontWeight: "bold",
    color: "#064e3b",
    marginBottom: 6,
  },
  emptySubtitle: {
    fontSize: 14,
    color: "#047857",
    textAlign: "center",
    marginBottom: 16,
  },
  listeningText: {
    fontSize: 12,
    color: "#10b981",
    fontWeight: "600",
  },
  orderCard: {
    backgroundColor: "#ffffff",
    borderRadius: 10,
    padding: 16,
    marginBottom: 14,
    borderWidth: 1,
    borderColor: "#bbf7d0",
    elevation: 2,
  },
  cardHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 8,
  },
  seatTitle: {
    fontSize: 18,
    fontWeight: "bold",
    color: "#064e3b",
  },
  statusPill: {
    backgroundColor: "#dcfce7",
    paddingVertical: 4,
    paddingHorizontal: 8,
    borderRadius: 6,
  },
  statusPillText: {
    color: "#166534",
    fontSize: 11,
    fontWeight: "bold",
  },
  unverifiedBanner: {
    backgroundColor: "#fef2f2",
    padding: 8,
    borderRadius: 6,
    marginBottom: 8,
    borderWidth: 1,
    borderColor: "#fecaca",
  },
  unverifiedText: {
    color: "#991b1b",
    fontSize: 11,
    fontWeight: "bold",
  },
  itemsBlock: {
    marginVertical: 8,
  },
  itemText: {
    fontSize: 14,
    color: "#334155",
    marginBottom: 2,
  },
  cardFooter: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginTop: 10,
    paddingTop: 10,
    borderTopWidth: 1,
    borderTopColor: "#f1f5f9",
  },
  collectText: {
    fontSize: 14,
    color: "#475569",
  },
  amount: {
    fontWeight: "bold",
    fontSize: 16,
    color: "#065f46",
  },
  pickupBtn: {
    backgroundColor: "#0284c7",
    paddingVertical: 8,
    paddingHorizontal: 14,
    borderRadius: 6,
  },
  pickupBtnText: {
    color: "#ffffff",
    fontWeight: "bold",
    fontSize: 13,
  },
  deliverBtn: {
    backgroundColor: "#16a34a",
    paddingVertical: 8,
    paddingHorizontal: 14,
    borderRadius: 6,
  },
  deliverBtnText: {
    color: "#ffffff",
    fontWeight: "bold",
    fontSize: 13,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.5)",
    justifyContent: "center",
    padding: 24,
  },
  modalContent: {
    backgroundColor: "#ffffff",
    borderRadius: 12,
    padding: 20,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: "bold",
    color: "#0f172a",
    marginBottom: 4,
  },
  modalSub: {
    fontSize: 13,
    color: "#64748b",
    marginBottom: 16,
  },
  inputLabel: {
    fontSize: 13,
    fontWeight: "600",
    color: "#334155",
    marginBottom: 6,
  },
  cashInput: {
    borderWidth: 1,
    borderColor: "#cbd5e1",
    borderRadius: 8,
    padding: 12,
    fontSize: 20,
    fontWeight: "bold",
    marginBottom: 16,
  },
  modalButtons: {
    flexDirection: "row",
    gap: 12,
  },
  cancelModalBtn: {
    flex: 1,
    padding: 12,
    alignItems: "center",
    borderRadius: 6,
    borderWidth: 1,
    borderColor: "#cbd5e1",
  },
  cancelModalText: {
    color: "#475569",
    fontWeight: "600",
  },
  confirmModalBtn: {
    flex: 1,
    backgroundColor: "#16a34a",
    padding: 12,
    alignItems: "center",
    borderRadius: 6,
  },
  confirmModalText: {
    color: "#ffffff",
    fontWeight: "bold",
  },
});
