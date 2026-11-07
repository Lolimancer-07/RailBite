import React, { useEffect, useRef, useState } from "react";
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  Modal,
  Alert,
} from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import { getLocalOrder, updateLocalOrderStatus, queueAction } from "../../src/db/queries.js";
import { getOrder, cancelOrder, confirmCashPayment } from "../../src/net/restClient.js";
import { passengerMqtt } from "../../src/net/mqttClient.js";
import { processQueue } from "../../src/sync/queueWorker.js";
import { colors } from "../../src/ui/theme.js";

function rupees(paise: number): string {
  const v = paise / 100;
  return Number.isInteger(v) ? `₹${v}` : `₹${v.toFixed(2)}`;
}

/** Blinkit-like timeline: PLACED → picker assigned/accepted → packing → out for delivery → delivered */
const TIMELINE = [
  { key: "PLACED", label: "Order placed" },
  { key: "ASSIGNED", label: "Picker assigned" },
  { key: "PACKING", label: "Packing" },
  { key: "OUT_FOR_DELIVERY", label: "Out for delivery" },
  { key: "DELIVERED", label: "Delivered" },
] as const;

function statusToIndex(status: string): number {
  switch (status) {
    case "PLACED": return 0;
    case "ASSIGNED": return 1;
    case "ACCEPTED": return 2; // accepted picker => packing phase next
    case "PACKING": return 2;
    case "OUT_FOR_DELIVERY": return 3;
    case "DELIVERED": return 4;
    default: return -1;
  }
}

interface StatusUpdate {
  status: string;
  updatedAt: string;
  captainName?: string | null;
  etaMin?: number | null;
}

export default function OrderDetailScreen() {
  const { id } = useLocalSearchParams() as { id: string };
  const router = useRouter();

  const [order, setOrder] = useState<any>(null);
  const [pickerStatus, setPickerStatus] = useState<StatusUpdate | null>(null);
  const [cashConfirmed, setCashConfirmed] = useState(false);
  const [cancelPrompt, setCancelPrompt] = useState(false);

  // Latest live MQTT update (kept in ref-checkable state for the timeline)

  useEffect(() => {
    // 1. Load immediately from local SQLite
    const local = getLocalOrder(id);
    if (local) {
      setOrder(local);
    }

    // 2. Fetch from edge server
    getOrder(id)
      .then((serverOrder) => {
        setOrder(serverOrder);
        updateLocalOrderStatus(id, serverOrder.status as any, serverOrder.updatedAt);
      })
      .catch(() => {
        // Fallback to local
      });

    // 3. Subscribe to live MQTT status push
    passengerMqtt.subscribeToOrder(id);
    const unsubscribe = passengerMqtt.onStatusChange((orderId, newStatus, data) => {
      if (orderId === id) {
        setOrder((prev: any) => (prev ? { ...prev, status: newStatus, updatedAt: data.updatedAt } : prev));
        setPickerStatus({
          status: newStatus,
          updatedAt: data.updatedAt,
          captainName: data.captainName ?? undefined,
          etaMin: data.etaMin ?? undefined,
        });
      }
    });

    return () => {
      unsubscribe();
    };
  }, [id]);

  const handleCancel = () => {
    setCancelPrompt(false);
    cancelOrder(id)
      .then((cancelled) => {
        setOrder(cancelled);
        updateLocalOrderStatus(id, "CANCELLED" as any, cancelled.updatedAt);
      })
      .catch((err: any) => {
        Alert.alert("Cannot cancel", err.message || "Order cannot be cancelled at this stage.");
      });
  };
  const handleConfirmCash = () => {
    // Write-then-send for cash confirmation
    queueAction(id, "CONFIRM_CASH", { orderId: id });
    setCashConfirmed(true);
    processQueue();
  };

  if (!order) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color={colors.primary} />
        <Text style={styles.loadingText}>Loading your order…</Text>
      </View>
    );
  }

  const currentIndex = statusToIndex(order.status);
  const isDelivered = order.status === "DELIVERED";
  const isCancelled = order.status === "CANCELLED" || order.status === "EXPIRED";
  const isCancellable = order.status === "PLACED" || order.status === "ASSIGNED";
  const pickerName = pickerStatus?.captainName ?? null;
  const etaMin = pickerStatus?.etaMin ?? 10;

  const statusText =
    isCancelled
      ? "Order cancelled"
      : order.status === "ACCEPTED"
        ? "Picker assigned"
        : (TIMELINE[Math.max(currentIndex, 0)]?.label || "Order placed");

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      {/* Header */}
      <View style={styles.headerRow}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backBtn}>
          <Text style={styles.backText}>‹</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Order #{order.id.slice(0, 8)}</Text>
        <View style={{ width: 40 }} />
      </View>

      {/* Status card */}
      <ScrollView contentContainerStyle={{ paddingBottom: 40 }}>
        <View style={styles.statusCard}>
          <Text style={styles.statusBig}>{statusText}</Text>
          {!isCancelled ? (
            <Text style={styles.etaText}>
              {isDelivered
                ? "Enjoy! Paid in cash at your berth."
                : `Arriving in ~${etaMin} min${etaMin === 1 ? "" : "s"}`}
            </Text>
          ) : null}

          {/* Timeline */}
          <View style={styles.timelineRow}>
            {TIMELINE.map((step, idx) => {
              const done = currentIndex >= idx;
              const dotStyle: any[] = [styles.dot];
              if (done) dotStyle.push(styles.dotDone);
              if (currentIndex === idx && !isDelivered) dotStyle.push(styles.dotCurrent);
              return (
                <View key={step.key} style={styles.stepWrap}>
                  <View style={dotStyle} />
                  <Text style={[styles.stepLabel, done && styles.stepLabelDone]} numberOfLines={2}>
                    {step.label}
                  </Text>
                </View>
              );
            })}
          </View>

          {pickerName ? (
            <Text style={styles.pickerText}>
              👤 Your picker: <Text style={styles.pickerName}>{pickerName}</Text>
            </Text>
          ) : null}

          {isCancellable && !isCancelled ? (
            <TouchableOpacity style={styles.cancelBtn} onPress={() => setCancelPrompt(true)}>
              <Text style={styles.cancelBtnText}>Cancel order</Text>
            </TouchableOpacity>
          ) : null}
        </View>

        {/* Cash confirmation */}
        {isDelivered && !cashConfirmed ? (
          <View style={styles.cashBox}>
            <Text style={styles.cashTitle}>Did you pay {rupees(order.totalPaise)}?</Text>
            <Text style={styles.cashSub}>Confirm to close the cash ledger for this order.</Text>
            <View style={{ flexDirection: "row", gap: 10, marginTop: 12 }}>
              <TouchableOpacity
                style={styles.cashNoBtn}
                onPress={() => {
                  setCashConfirmed(true);
                }}
              >
                <Text style={styles.cashNoText}>Not yet</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.cashYesBtn} onPress={handleConfirmCash}>
                <Text style={styles.cashYesText}>Yes, paid</Text>
              </TouchableOpacity>
            </View>
          </View>
        ) : null}
        {isDelivered && cashConfirmed ? (
          <View style={styles.cashConfirmedBox}>
            <Text style={styles.cashConfirmedText}>✓ Thanks — cash payment confirmed.</Text>
          </View>
        ) : null}

        {/* Items */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Order items</Text>
          <View style={{ gap: 6 }}>
            {(order.items || []).map((item: any, idx: number) => (
              <View key={item.itemId || idx} style={styles.itemRow}>
                <Text style={styles.itemText}>
                  {item.name || "Pantry item"} × {item.qty}
                </Text>
                <Text style={styles.itemPrice}>
                  {rupees((item.unitPricePaise || 0) * item.qty)}
                </Text>
              </View>
            ))}
            <View style={[styles.itemRow, styles.totalRow]}>
              <Text style={styles.totalText}>Total (MRP)</Text>
              <Text style={styles.totalValue}>{rupees(order.totalPaise)}</Text>
            </View>
          </View>
        </View>

        {/* Deliver to */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Delivering to</Text>
          <Text style={styles.seatText}>
            Coach {order.coach} • Seat {order.berth} • PNR {order.pnr}
          </Text>
        </View>

        {/* Report a problem */}
        <TouchableOpacity
          style={styles.problemBtn}
          onPress={() => router.push(`/complaint/${order.id}`)}
        >
          <Text style={styles.problemText}>⚠ Report a problem</Text>
        </TouchableOpacity>
      </ScrollView>

      {/* Cancel confirmation */}
      <Modal visible={cancelPrompt} transparent animationType="fade">
        <View style={styles.modalOverlay}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>Cancel this order?</Text>
            <Text style={styles.modalSub}>
              You can only cancel before a picker starts packing.
            </Text>
            <View style={{ flexDirection: "row", gap: 10, marginTop: 16 }}>
              <TouchableOpacity style={styles.modalNoBtn} onPress={() => setCancelPrompt(false)}>
                <Text style={styles.modalNoText}>Keep order</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.modalYesBtn} onPress={handleCancel}>
                <Text style={styles.modalYesText}>Yes, cancel</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, justifyContent: "center", alignItems: "center", backgroundColor: colors.bg },
  loadingText: { marginTop: 12, color: colors.inkSoft, fontSize: 13 },
  headerRow: {
    backgroundColor: colors.primary,
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
  statusCard: {
    backgroundColor: "#ffffff",
    margin: 16,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 18,
  },
  statusBig: { fontSize: 20, fontWeight: "bold", color: colors.ink },
  etaText: { fontSize: 13, color: colors.primary, fontWeight: "600", marginTop: 4 },
  timelineRow: {
    flexDirection: "row",
    marginTop: 18,
  },
  stepWrap: { flex: 1, alignItems: "center" },
  dot: {
    width: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: colors.border,
  },
  dotDone: { backgroundColor: colors.primary },
  dotCurrent: { backgroundColor: colors.amber },
  stepLabel: {
    marginTop: 6,
    fontSize: 10,
    color: colors.inkSoft,
    textAlign: "center",
  },
  stepLabelDone: { color: colors.primary, fontWeight: "600" },
  pickerText: { marginTop: 14, fontSize: 13, color: colors.ink },
  pickerName: { fontWeight: "bold" },
  cancelBtn: {
    marginTop: 14,
    alignSelf: "flex-start",
    borderWidth: 1,
    borderColor: colors.danger,
    borderRadius: 10,
    paddingVertical: 8,
    paddingHorizontal: 14,
  },
  cancelBtnText: { color: colors.danger, fontWeight: "600", fontSize: 13 },
  cashBox: {
    backgroundColor: colors.amberSoft,
    marginHorizontal: 16,
    marginBottom: 14,
    borderRadius: 14,
    padding: 16,
  },
  cashTitle: { fontSize: 16, fontWeight: "bold", color: "#92400e" },
  cashSub: { fontSize: 12, color: "#78350f", marginTop: 4 },
  cashNoBtn: {
    flex: 1,
    borderWidth: 1,
    borderColor: "#f59e0b",
    borderRadius: 10,
    paddingVertical: 10,
    alignItems: "center",
  },
  cashNoText: { color: "#92400e", fontWeight: "600" },
  cashYesBtn: {
    flex: 1,
    backgroundColor: colors.primary,
    borderRadius: 10,
    paddingVertical: 10,
    alignItems: "center",
  },
  cashYesText: { color: "#ffffff", fontWeight: "bold" },
  cashConfirmedBox: {
    backgroundColor: colors.primarySoft,
    marginHorizontal: 16,
    marginBottom: 14,
    borderRadius: 14,
    padding: 12,
  },
  cashConfirmedText: { color: colors.primary, fontWeight: "bold", textAlign: "center", fontSize: 13 },
  section: {
    backgroundColor: "#ffffff",
    marginHorizontal: 16,
    marginBottom: 14,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 16,
  },
  sectionTitle: {
    fontSize: 12,
    fontWeight: "bold",
    color: colors.inkSoft,
    textTransform: "uppercase",
    marginBottom: 10,
  },
  itemRow: {
    flexDirection: "row",
    justifyContent: "space-between",
  },
  itemText: { fontSize: 14, color: colors.ink, flex: 1 },
  itemPrice: { fontSize: 14, fontWeight: "600", color: colors.ink },
  totalRow: {
    borderTopWidth: 1,
    borderTopColor: colors.border,
    paddingTop: 10,
    marginTop: 6,
  },
  totalText: { fontSize: 15, fontWeight: "bold", color: colors.ink },
  totalValue: { fontSize: 16, fontWeight: "bold", color: colors.ink },
  seatText: { fontSize: 14, color: colors.ink, fontWeight: "600" },
  problemBtn: {
    marginHorizontal: 16,
    marginBottom: 20,
    padding: 14,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: "#ffffff",
    alignItems: "center",
  },
  problemText: { color: colors.inkSoft, fontWeight: "600", fontSize: 14 },
  modalOverlay: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.45)",
    justifyContent: "center",
    padding: 24,
  },
  modalCard: {
    backgroundColor: "#ffffff",
    borderRadius: 16,
    padding: 20,
  },
  modalTitle: { fontSize: 17, fontWeight: "bold", color: colors.ink },
  modalSub: { fontSize: 13, color: colors.inkSoft, marginTop: 6 },
  modalNoBtn: {
    flex: 1,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 10,
    paddingVertical: 11,
    alignItems: "center",
  },
  modalNoText: { color: colors.ink, fontWeight: "600" },
  modalYesBtn: {
    flex: 1,
    backgroundColor: colors.danger,
    borderRadius: 10,
    paddingVertical: 11,
    alignItems: "center",
  },
  modalYesText: { color: "#ffffff", fontWeight: "bold" },
});
