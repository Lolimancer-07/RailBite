import React, { useEffect, useState, useRef } from "react";
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  StyleSheet,
  TextInput,
  Modal,
  Alert,
  ActivityIndicator,
} from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import type { Order } from "@railbite/shared";
import {
  getOrderById,
  startPacking,
  pickupOrder,
  deliverOrder,
} from "../../src/net/captainRestClient.js";
import { useAuthStore } from "../../src/store/auth.js";
import { useActiveOrdersStore } from "../../src/store/orders.js";
import { colors } from "../../src/ui/theme.js";

type ItemState = { itemId: string; name: string; qty: number; unitPricePaise: number; picked: boolean };

export default function PickingChecklistScreen() {
  const { id } = useLocalSearchParams() as { id: string };
  const router = useRouter();
  const staff = useAuthStore((s) => s.staff);
  const { updateOrderStatus, removeOrder } = useActiveOrdersStore();

  const [items, setItems] = useState<ItemState[]>([]);
  const [orderStatus, setOrderStatus] = useState<string>("ACCEPTED");
  const [totalPaise, setTotalPaise] = useState<number>(0);
  const [coach, setCoach] = useState("");
  const [berth, setBerth] = useState("");
  const [loading, setLoading] = useState(true);
  const [actionBusy, setActionBusy] = useState(false);
  const [deliveryModal, setDeliveryModal] = useState(false);
  const [cashCollected, setCashCollected] = useState("");

  const didMarkPacking = useRef(false);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const order: Order & { items?: any[] } = await getOrderById(id);
        if (cancelled) return;
        setOrderStatus(order.status as string);
        setTotalPaise(order.totalPaise);
        setCoach(order.coach);
        setBerth(String(order.berth));
        setCashCollected(String(order.totalPaise / 100));
        setItems(
          (order.items || []).map((it: any) => ({
            itemId: it.itemId,
            name: it.name || "Item",
            qty: it.qty,
            unitPricePaise: it.unitPricePaise,
            picked: false,
          })),
        );

        // Entering the checklist marks packing started (PLACED..ACCEPTED -> PACKING)
        if (!didMarkPacking.current && order.status === "ACCEPTED" && staff?.staffId) {
          didMarkPacking.current = true;
          try {
            await startPacking(order.id, staff.staffId);
            setOrderStatus("PACKING");
            updateOrderStatus(order.id, "PACKING");
          } catch {
            // e.g. already PACKING (409) — fine
          }
        }
      } catch (err: any) {
        Alert.alert("Error", err.message || "Failed to load order");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, [id]);

  const allPicked = items.length > 0 && items.every((i) => i.picked);

  const toggleItem = (itemId: string) => {
    setItems((prev) => prev.map((i) => (i.itemId === itemId ? { ...i, picked: !i.picked } : i)));
  };

  const handlePackedStartDelivery = async () => {
    if (!staff?.staffId || !allPicked || actionBusy) return;
    setActionBusy(true);
    try {
      await pickupOrder(id, staff.staffId);
      setOrderStatus("OUT_FOR_DELIVERY");
      updateOrderStatus(id as string, "OUT_FOR_DELIVERY");
    } catch (err: any) {
      Alert.alert("Error", err.message || "Failed to mark out for delivery");
    } finally {
      setActionBusy(false);
    }
  };

  const handleConfirmDelivery = async () => {
    if (!staff?.staffId) return;
    const cashAmountPaise = Math.round(parseFloat(cashCollected || "0") * 100);
    setActionBusy(true);
    try {
      await deliverOrder(id as string, staff.staffId, cashAmountPaise);
      removeOrder(id as string);
      setDeliveryModal(false);
      Alert.alert(
        "Delivered + cash collected",
        `Ledger recorded ₹${cashAmountPaise / 100}. Customer will confirm shortly.`,
        [{ text: "Done", onPress: () => router.replace("/active") }],
      );
    } catch (err: any) {
      Alert.alert("Error", err.message || "Failed to record delivery");
    } finally {
      setActionBusy(false);
    }
  };

  if (loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color={colors.primary} />
      </View>
    );
  }

  const isPickPhase = orderStatus === "ACCEPTED" || orderStatus === "PACKING";
  const isOutForDelivery = orderStatus === "OUT_FOR_DELIVERY";

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backBtn}>
          <Text style={styles.backText}>‹</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Order #{id.slice(0, 8)}</Text>
        <View style={{ width: 40 }} />
      </View>

      <ScrollView contentContainerStyle={{ padding: 14, paddingBottom: 130 }}>
        {/* Deliver-to chip */}
        <View style={styles.seatCard}>
          <Text style={styles.seatLabel}>DELIVER TO</Text>
          <Text style={styles.seatValue}>
            Coach {coach} • Seat {berth}
          </Text>
        </View>

        {/* Status pill row */}
        <View style={styles.statusRow}>
          <Text style={styles.statusText}>
            {isPickPhase ? "Phase: PICKING" : isOutForDelivery ? "Phase: OUT FOR DELIVERY" : `Phase: ${orderStatus}`}
          </Text>
        </View>

        {/* Checklist */}
        <View style={styles.checklistCard}>
          <Text style={styles.sectionTitle}>Picking checklist</Text>
          {items.map((item) => (
            <TouchableOpacity
              key={item.itemId}
              style={styles.checkRow}
              onPress={() => toggleItem(item.itemId)}
              disabled={!isPickPhase}
            >
              <View style={[styles.checkbox, item.picked && styles.checkboxDone]}>
                {item.picked ? <Text style={styles.checkMark}>✓</Text> : null}
              </View>
              <Text style={[styles.itemName, item.picked && styles.itemNameDone]}>
                {item.name}
              </Text>
              <Text style={styles.itemQty}>× {item.qty}</Text>
            </TouchableOpacity>
          ))}

          <View style={styles.totalRow}>
            <Text style={styles.totalLabel}>Collect at seat</Text>
            <Text style={styles.totalValue}>₹{totalPaise / 100}</Text>
          </View>
        </View>

        {!isPickPhase && !isOutForDelivery ? (
          <Text style={styles.statusNote}>
            Order status: {orderStatus}. Delivery can be recorded from the Home list.
          </Text>
        ) : null}
      </ScrollView>

      {/* Bottom action bar */}
      <View style={styles.bottomBar}>
        {isPickPhase ? (
          <TouchableOpacity
            style={[styles.primaryBtn, (!allPicked || actionBusy) && styles.primaryBtnDisabled]}
            disabled={!allPicked || actionBusy}
            onPress={handlePackedStartDelivery}
          >
            <Text style={styles.primaryBtnText}>
              {allPicked ? "Packed — start delivery →" : "Tick all items to continue"}
            </Text>
          </TouchableOpacity>
        ) : isOutForDelivery ? (
          <TouchableOpacity style={styles.primaryBtn} disabled={actionBusy} onPress={() => setDeliveryModal(true)}>
            <Text style={styles.primaryBtnText}>Delivered + cash collected ₹{totalPaise / 100}</Text>
          </TouchableOpacity>
        ) : null}
      </View>

      {/* Cash modal */}
      <Modal visible={deliveryModal} transparent animationType="slide">
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <Text style={styles.modalTitle}>Confirm delivery & cash</Text>
            <Text style={styles.modalSub}>
              Delivered to Coach {coach}, Seat {berth}
            </Text>

            <Text style={styles.inputLabel}>Cash amount collected (₹)</Text>
            <TextInput
              style={styles.cashInput}
              keyboardType="numeric"
              value={cashCollected}
              onChangeText={setCashCollected}
            />

            <View style={styles.modalButtons}>
              <TouchableOpacity
                style={styles.cancelModalBtn}
                onPress={() => setDeliveryModal(false)}
              >
                <Text style={styles.cancelModalText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={styles.confirmModalBtn}
                disabled={actionBusy}
                onPress={handleConfirmDelivery}
              >
                <Text style={styles.confirmModalText}>Confirm & complete</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  center: { flex: 1, justifyContent: "center", alignItems: "center" },
  header: {
    backgroundColor: colors.primaryDark,
    paddingHorizontal: 12,
    paddingTop: 12,
    paddingBottom: 14,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  backBtn: { width: 40, height: 40, alignItems: "center", justifyContent: "center" },
  backText: { color: "#ffffff", fontSize: 24, fontWeight: "bold" },
  headerTitle: { color: "#ffffff", fontSize: 16, fontWeight: "bold" },
  seatCard: {
    backgroundColor: colors.primary,
    borderRadius: 14,
    padding: 14,
    alignItems: "center",
  },
  seatLabel: { color: "#d3f4e2", fontSize: 11, fontWeight: "bold", letterSpacing: 1 },
  seatValue: { color: "#ffffff", fontSize: 22, fontWeight: "bold", marginTop: 4 },
  statusRow: { marginTop: 12, alignItems: "center" },
  statusText: { fontSize: 13, fontWeight: "bold", color: colors.inkSoft },
  checklistCard: {
    backgroundColor: "#ffffff",
    borderRadius: 16,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 14,
    marginTop: 12,
  },
  sectionTitle: {
    fontSize: 12,
    fontWeight: "bold",
    color: colors.inkSoft,
    textTransform: "uppercase",
    marginBottom: 8,
  },
  checkRow: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  checkbox: {
    width: 28,
    height: 28,
    borderRadius: 8,
    borderWidth: 2,
    borderColor: colors.border,
    alignItems: "center",
    justifyContent: "center",
    marginRight: 12,
  },
  checkboxDone: { backgroundColor: colors.primary, borderColor: colors.primary },
  checkMark: { color: "#ffffff", fontWeight: "bold", fontSize: 15 },
  itemName: { flex: 1, fontSize: 15, fontWeight: "600", color: colors.ink },
  itemNameDone: { color: colors.inkSoft, textDecorationLine: "line-through" },
  itemQty: { fontSize: 15, fontWeight: "bold", color: colors.ink },
  totalRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    paddingTop: 12,
    marginTop: 6,
  },
  totalLabel: { fontSize: 15, fontWeight: "bold", color: colors.ink },
  totalValue: { fontSize: 18, fontWeight: "bold", color: colors.primary },
  statusNote: { marginTop: 12, fontSize: 12, color: colors.inkSoft, textAlign: "center" },
  bottomBar: {
    position: "absolute",
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: "#ffffff",
    padding: 14,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  primaryBtn: {
    backgroundColor: colors.primary,
    borderRadius: 12,
    padding: 16,
    alignItems: "center",
  },
  primaryBtnDisabled: { backgroundColor: "#c3d2c9" },
  primaryBtnText: { color: "#ffffff", fontWeight: "bold", fontSize: 15 },
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
