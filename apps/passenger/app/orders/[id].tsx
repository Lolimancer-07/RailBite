import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ActivityIndicator,
} from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import { getLocalOrder, updateLocalOrderStatus, queueAction } from "../../src/db/queries.js";
import { getOrder, cancelOrder, confirmCashPayment } from "../../src/net/restClient.js";
import { passengerMqtt } from "../../src/net/mqttClient.js";
import { processQueue } from "../../src/sync/queueWorker.js";

const READY_STEPS = ["PLACED", "ASSIGNED", "ACCEPTED", "OUT_FOR_DELIVERY", "DELIVERED"];
const COOK_STEPS = ["PLACED", "COOKING", "READY", "ASSIGNED", "ACCEPTED", "OUT_FOR_DELIVERY", "DELIVERED"];

export default function OrderDetailScreen() {
  const { id } = useLocalSearchParams() as { id: string };
  const router = useRouter();

  const [order, setOrder] = useState<any>(null);
  const [captainName, setCaptainName] = useState<string | null>(null);
  const [etaMin, setEtaMin] = useState<number | null>(null);
  const [cashConfirmed, setCashConfirmed] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // 1. Load immediately from local SQLite
    const local = getLocalOrder(id);
    if (local) {
      setOrder(local);
      setLoading(false);
    }

    // 2. Fetch from edge server
    getOrder(id)
      .then((serverOrder) => {
        setOrder(serverOrder);
        updateLocalOrderStatus(id, serverOrder.status as any, serverOrder.updatedAt);
      })
      .catch(() => {
        // Fallback to local
      })
      .finally(() => setLoading(false));

    // 3. Subscribe to live MQTT status push
    passengerMqtt.subscribeToOrder(id);
    const unsubscribe = passengerMqtt.onStatusChange((orderId, newStatus, data) => {
      if (orderId === id) {
        setOrder((prev: any) => (prev ? { ...prev, status: newStatus, updatedAt: data.updatedAt } : prev));
        if (data.captainName) setCaptainName(data.captainName);
        if (data.etaMin) setEtaMin(data.etaMin);
      }
    });

    return () => {
      unsubscribe();
    };
  }, [id]);

  const handleCancel = () => {
    Alert.alert("Cancel Order", "Are you sure you want to cancel this order?", [
      { text: "No", style: "cancel" },
      {
        text: "Yes, Cancel",
        style: "destructive",
        onPress: async () => {
          try {
            const cancelled = await cancelOrder(id);
            setOrder(cancelled);
            updateLocalOrderStatus(id, "CANCELLED" as any, cancelled.updatedAt);
            Alert.alert("Cancelled", "Your order has been cancelled.");
          } catch (err: any) {
            Alert.alert("Cannot Cancel", err.message || "Order cannot be cancelled at this stage.");
          }
        },
      },
    ]);
  };

  const handleConfirmCash = () => {
    // Write-then-send for cash confirmation
    queueAction(id, "CONFIRM_CASH", { orderId: id });
    setCashConfirmed(true);
    processQueue();
    Alert.alert("Cash Confirmed", "Thank you for confirming your payment!");
  };

  if (loading && !order) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#1e3a8a" />
      </View>
    );
  }

  if (!order) {
    return (
      <View style={styles.center}>
        <Text style={styles.errorText}>Order not found.</Text>
      </View>
    );
  }

  const steps = order.track === "COOK" ? COOK_STEPS : READY_STEPS;
  const currentStepIndex = steps.indexOf(order.status);
  const isDelivered = order.status === "DELIVERED";
  const isCancellable = order.status === "PLACED" || order.status === "ASSIGNED";

  return (
    <ScrollView style={styles.container} contentContainerStyle={{ paddingBottom: 60 }}>
      {/* Status Card */}
      <View style={styles.statusCard}>
        <Text style={styles.orderNumber}>Order #{order.id.slice(0, 8)}</Text>
        <Text style={styles.currentStatus}>{order.status.replace(/_/g, " ")}</Text>

        {captainName ? (
          <Text style={styles.captainText}>Assigned Captain: {captainName}</Text>
        ) : null}
        {etaMin ? <Text style={styles.etaText}>Estimated Delivery: ~{etaMin} mins</Text> : null}

        {/* Progress Tracker */}
        <View style={styles.stepperContainer}>
          {steps.map((step, idx) => {
            const isCompleted = currentStepIndex >= idx;
            const isCurrent = currentStepIndex === idx;

            return (
              <View key={step} style={styles.stepItem}>
                <View
                  style={[
                    styles.stepCircle,
                    isCompleted && styles.stepCircleCompleted,
                    isCurrent && styles.stepCircleCurrent,
                  ]}
                >
                  <Text style={styles.stepNumber}>{idx + 1}</Text>
                </View>
                <Text
                  style={[
                    styles.stepLabel,
                    isCurrent && styles.stepLabelCurrent,
                  ]}
                  numberOfLines={1}
                >
                  {step.replace(/_/g, " ")}
                </Text>
              </View>
            );
          })}
        </View>
      </View>

      {/* Cash Confirmation Prompt when Delivered */}
      {isDelivered && !cashConfirmed ? (
        <View style={styles.cashConfirmBox}>
          <Text style={styles.cashConfirmTitle}>Cash Payment Confirmation</Text>
          <Text style={styles.cashConfirmText}>
            Did you pay ₹{order.totalPaise / 100} in cash to the delivery captain?
          </Text>
          <TouchableOpacity style={styles.confirmCashBtn} onPress={handleConfirmCash}>
            <Text style={styles.confirmCashBtnText}>Yes, I Paid ₹{order.totalPaise / 100} ✓</Text>
          </TouchableOpacity>
        </View>
      ) : isDelivered && cashConfirmed ? (
        <View style={styles.confirmedBox}>
          <Text style={styles.confirmedText}>✓ Cash payment confirmed by you.</Text>
        </View>
      ) : null}

      {/* Seat Details */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Delivery Location</Text>
        <View style={styles.infoRow}>
          <Text style={styles.infoLabel}>Coach</Text>
          <Text style={styles.infoValue}>{order.coach}</Text>
        </View>
        <View style={styles.infoRow}>
          <Text style={styles.infoLabel}>Berth Number</Text>
          <Text style={styles.infoValue}>{order.berth}</Text>
        </View>
        <View style={styles.infoRow}>
          <Text style={styles.infoLabel}>PNR</Text>
          <Text style={styles.infoValue}>{order.pnr}</Text>
        </View>
        <View style={styles.infoRow}>
          <Text style={styles.infoLabel}>Track</Text>
          <Text style={styles.infoValue}>
            {order.track === "READY" ? "Instant (Ready)" : "Cook to Order"}
          </Text>
        </View>
      </View>

      {/* Bill / Receipt */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Official Receipt (MRP)</Text>
        {(order.items || []).map((item: any, idx: number) => (
          <View key={item.itemId || idx} style={styles.receiptRow}>
            <Text style={styles.receiptItem}>
              {item.name || "Pantry Item"} × {item.qty}
            </Text>
            <Text style={styles.receiptPrice}>
              ₹{((item.unitPricePaise || 0) * item.qty) / 100}
            </Text>
          </View>
        ))}

        <View style={[styles.receiptRow, styles.totalRow]}>
          <Text style={styles.totalLabel}>Total Amount (Paid via {order.paymentMode})</Text>
          <Text style={styles.totalValue}>₹{order.totalPaise / 100}</Text>
        </View>
      </View>

      {/* Actions: Cancel & Complaints */}
      <View style={styles.actionContainer}>
        {isCancellable ? (
          <TouchableOpacity style={styles.cancelBtn} onPress={handleCancel}>
            <Text style={styles.cancelBtnText}>Cancel Order</Text>
          </TouchableOpacity>
        ) : null}

        <TouchableOpacity
          style={styles.complaintBtn}
          onPress={() => router.push(`/complaint/${order.id}`)}
        >
          <Text style={styles.complaintBtnText}>Report Issue / File Complaint</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#f8fafc",
  },
  center: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
  },
  errorText: {
    fontSize: 16,
    color: "#64748b",
  },
  statusCard: {
    backgroundColor: "#ffffff",
    padding: 20,
    margin: 16,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: "#e2e8f0",
  },
  orderNumber: {
    fontSize: 12,
    color: "#64748b",
    fontWeight: "bold",
    textTransform: "uppercase",
  },
  currentStatus: {
    fontSize: 22,
    fontWeight: "bold",
    color: "#1e3a8a",
    marginTop: 4,
  },
  captainText: {
    fontSize: 14,
    color: "#334155",
    fontWeight: "600",
    marginTop: 6,
  },
  etaText: {
    fontSize: 14,
    color: "#ca8a04",
    fontWeight: "600",
    marginTop: 2,
  },
  stepperContainer: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginTop: 20,
    paddingTop: 16,
    borderTopWidth: 1,
    borderTopColor: "#f1f5f9",
  },
  stepItem: {
    alignItems: "center",
    flex: 1,
  },
  stepCircle: {
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: "#e2e8f0",
    justifyContent: "center",
    alignItems: "center",
    marginBottom: 4,
  },
  stepCircleCompleted: {
    backgroundColor: "#2563eb",
  },
  stepCircleCurrent: {
    backgroundColor: "#166534",
  },
  stepNumber: {
    color: "#ffffff",
    fontSize: 11,
    fontWeight: "bold",
  },
  stepLabel: {
    fontSize: 8,
    color: "#94a3b8",
    textAlign: "center",
  },
  stepLabelCurrent: {
    color: "#166534",
    fontWeight: "bold",
  },
  cashConfirmBox: {
    backgroundColor: "#fef3c7",
    padding: 16,
    marginHorizontal: 16,
    marginBottom: 16,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: "#fde68a",
  },
  cashConfirmTitle: {
    fontSize: 15,
    fontWeight: "bold",
    color: "#92400e",
    marginBottom: 4,
  },
  cashConfirmText: {
    fontSize: 13,
    color: "#78350f",
    marginBottom: 12,
  },
  confirmCashBtn: {
    backgroundColor: "#d97706",
    paddingVertical: 12,
    borderRadius: 6,
    alignItems: "center",
  },
  confirmCashBtnText: {
    color: "#ffffff",
    fontWeight: "bold",
    fontSize: 14,
  },
  confirmedBox: {
    backgroundColor: "#dcfce7",
    padding: 12,
    marginHorizontal: 16,
    marginBottom: 16,
    borderRadius: 8,
  },
  confirmedText: {
    color: "#166534",
    fontWeight: "bold",
    fontSize: 13,
    textAlign: "center",
  },
  section: {
    backgroundColor: "#ffffff",
    padding: 16,
    marginHorizontal: 16,
    marginBottom: 14,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: "#e2e8f0",
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: "bold",
    color: "#334155",
    marginBottom: 12,
    textTransform: "uppercase",
  },
  infoRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginBottom: 8,
  },
  infoLabel: {
    fontSize: 14,
    color: "#64748b",
  },
  infoValue: {
    fontSize: 14,
    fontWeight: "600",
    color: "#0f172a",
  },
  receiptRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginBottom: 8,
  },
  receiptItem: {
    fontSize: 14,
    color: "#334155",
  },
  receiptPrice: {
    fontSize: 14,
    fontWeight: "600",
    color: "#0f172a",
  },
  totalRow: {
    borderTopWidth: 1,
    borderTopColor: "#e2e8f0",
    paddingTop: 10,
    marginTop: 6,
  },
  totalLabel: {
    fontSize: 15,
    fontWeight: "bold",
    color: "#0f172a",
  },
  totalValue: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#1e3a8a",
  },
  actionContainer: {
    paddingHorizontal: 16,
    gap: 10,
    marginTop: 8,
  },
  cancelBtn: {
    backgroundColor: "#fee2e2",
    padding: 14,
    borderRadius: 8,
    alignItems: "center",
    borderWidth: 1,
    borderColor: "#fca5a5",
  },
  cancelBtnText: {
    color: "#991b1b",
    fontWeight: "bold",
    fontSize: 14,
  },
  complaintBtn: {
    backgroundColor: "#ffffff",
    padding: 14,
    borderRadius: 8,
    alignItems: "center",
    borderWidth: 1,
    borderColor: "#cbd5e1",
  },
  complaintBtnText: {
    color: "#475569",
    fontWeight: "600",
    fontSize: 14,
  },
});

