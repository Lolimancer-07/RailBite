import React from "react";
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
} from "react-native";
import { useRouter } from "expo-router";
import { useCartStore } from "../src/store/cart.js";
import { useSessionStore } from "../src/store/session.js";
import { saveLocalOrder, queueAction } from "../src/db/queries.js";
import { processQueue } from "../src/sync/queueWorker.js";
import { passengerMqtt } from "../src/net/mqttClient.js";
import { determineTrack, type OrderCreate } from "@railbite/shared";
import { colors } from "../src/ui/theme.js";

function rupees(paise: number): string {
  const v = paise / 100;
  return Number.isInteger(v) ? `₹${v}` : `₹${v.toFixed(2)}`;
}

function generateUUID(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export default function CartScreen() {
  const router = useRouter();
  const session = useSessionStore((s) => s.session);
  const { items: cartMap, addItem, removeItem, clearCart, totalPaise } = useCartStore();

  const cartList = Array.from(cartMap.values());
  const totalAmount = totalPaise();
  const paymentMode = "COD" as const;

  const handlePlaceOrder = () => {
    if (!session || !session.pnr) {
      router.push("/onboarding");
      return;
    }

    if (cartList.length === 0) {
      return;
    }

    const orderId = generateUUID();
    const now = new Date().toISOString();

    const orderItems = cartList.map(({ item, qty }) => ({
      itemId: item.id,
      qty,
      unitPricePaise: item.mrpPaise,
      name: item.name,
    }));

    const track = determineTrack(cartList.map(({ item }) => item.track));

    const orderCreatePayload: OrderCreate = {
      id: orderId,
      pnr: session.pnr,
      coach: session.coach,
      berth: session.berth,
      diners: session.diners,
      paymentMode,
      items: orderItems.map(({ itemId, qty }) => ({ itemId, qty })),
      createdAt: now,
      source: "WIFI",
    };

    // 1. Write to local SQLite database first (Offline-First)
    saveLocalOrder(
      {
        id: orderId,
        pnr: session.pnr,
        coach: session.coach,
        berth: session.berth,
        diners: session.diners,
        track,
        status: "PLACED",
        paymentMode,
        totalPaise: totalAmount,
        source: "WIFI",
        verified: 1,
        createdAt: now,
        updatedAt: now,
      },
      orderItems,
    );

    // 2. Queue in pending_actions for delivery
    queueAction(orderId, "PLACE_ORDER", orderCreatePayload);

    // 3. Clear cart
    clearCart();

    // 4. Trigger delivery queue in background (REST + retry)
    processQueue();

    // 5. Subscribe to live status via MQTT
    passengerMqtt.subscribeToOrder(orderId);

    // 6. Navigate to live order tracking screen immediately
    router.replace(`/orders/${orderId}`);
  };

  if (cartList.length === 0) {
    return (
      <View style={styles.emptyContainer}>
        <Text style={styles.emptyIcon}>🛒</Text>
        <Text style={styles.emptyTitle}>Your cart is empty</Text>
        <Text style={styles.emptySubtitle}>Add some snacks, chai or meals from the menu.</Text>
        <TouchableOpacity style={styles.browseBtn} onPress={() => router.push("/(tabs)/home")}>
          <Text style={styles.browseBtnText}>Browse menu</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {/* Delivery Seat Summary */}
      <View style={styles.seatSummary}>
        <Text style={styles.seatSummaryLabel}>Delivering to</Text>
        <Text style={styles.seatSummaryValue}>
          Coach {session?.coach} • Seat {session?.berth} • PNR {session?.pnr}
        </Text>
      </View>

      <FlatList
        data={cartList}
        keyExtractor={({ item }) => item.id}
        contentContainerStyle={{ paddingBottom: 130 }}
        renderItem={({ item: cartEntry }) => {
          const { item, qty } = cartEntry;
          return (
            <View style={styles.itemRow}>
              <View style={{ flex: 1 }}>
                <Text style={styles.itemName}>{item.name}</Text>
                <Text style={styles.itemPrice}>{rupees(item.mrpPaise)} each</Text>
              </View>

              <View style={styles.stepper}>
                <TouchableOpacity style={styles.stepperBtn} onPress={() => removeItem(item.id)}>
                  <Text style={styles.stepperBtnText}>−</Text>
                </TouchableOpacity>
                <Text style={styles.stepperQty}>{qty}</Text>
                <TouchableOpacity style={styles.stepperBtn} onPress={() => addItem(item)}>
                  <Text style={styles.stepperBtnText}>+</Text>
                </TouchableOpacity>
              </View>

              <Text style={styles.lineTotal}>{rupees(item.mrpPaise * qty)}</Text>
            </View>
          );
        }}
        ListFooterComponent={
          <View style={styles.footerContainer}>
            {/* Payment Method — fixed COD in prototype */}
            <View style={styles.paymentRow}>
              <Text style={styles.paymentEmoji}>💵</Text>
              <View style={{ flex: 1 }}>
                <Text style={styles.paymentTitle}>Cash on Delivery</Text>
                <Text style={styles.paymentSub}>Pay the picker at your berth</Text>
              </View>
            </View>

            {/* Bill Summary */}
            <View style={styles.billBox}>
              <View style={styles.billRow}>
                <Text style={styles.billLabel}>Item total</Text>
                <Text style={styles.billValue}>{rupees(totalAmount)}</Text>
              </View>
              <View style={styles.billRow}>
                <Text style={styles.billLabel}>Delivery fee</Text>
                <Text style={[styles.billValue, { color: colors.primary }]}>FREE</Text>
              </View>
              <View style={[styles.billRow, styles.grandTotalRow]}>
                <Text style={styles.grandTotalLabel}>To pay</Text>
                <Text style={styles.grandTotalValue}>{rupees(totalAmount)}</Text>
              </View>
            </View>
          </View>
        }
      />

      {/* Place Order Button */}
      <View style={styles.placeOrderBar}>
        <TouchableOpacity style={styles.placeOrderBtn} onPress={handlePlaceOrder}>
          <Text style={styles.placeOrderText}>Place Order • {rupees(totalAmount)}</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.bg,
  },
  emptyContainer: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    padding: 30,
    backgroundColor: "#ffffff",
  },
  emptyIcon: { fontSize: 48, marginBottom: 16 },
  emptyTitle: { fontSize: 20, fontWeight: "bold", color: colors.ink, marginBottom: 8 },
  emptySubtitle: { fontSize: 14, color: colors.inkSoft, textAlign: "center", marginBottom: 24 },
  browseBtn: {
    backgroundColor: colors.primary,
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: 10,
  },
  browseBtnText: { color: "#ffffff", fontWeight: "bold", fontSize: 15 },
  seatSummary: {
    backgroundColor: colors.primarySoft,
    padding: 14,
  },
  seatSummaryLabel: {
    fontSize: 11,
    color: colors.inkSoft,
    fontWeight: "bold",
    textTransform: "uppercase",
  },
  seatSummaryValue: {
    fontSize: 14,
    color: colors.ink,
    fontWeight: "bold",
    marginTop: 2,
  },
  itemRow: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#ffffff",
    padding: 14,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  itemName: { fontSize: 15, fontWeight: "600", color: colors.ink },
  itemPrice: { fontSize: 13, color: colors.inkSoft, marginTop: 2 },
  stepper: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: colors.primary,
    borderRadius: 10,
    marginHorizontal: 12,
  },
  stepperBtn: { paddingHorizontal: 12, paddingVertical: 5 },
  stepperBtnText: { color: "#ffffff", fontSize: 16, fontWeight: "bold" },
  stepperQty: { color: "#ffffff", fontSize: 14, fontWeight: "bold", paddingHorizontal: 4 },
  lineTotal: {
    fontSize: 15,
    fontWeight: "bold",
    color: colors.ink,
    width: 56,
    textAlign: "right",
  },
  footerContainer: { padding: 16 },
  paymentRow: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#ffffff",
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 14,
    marginBottom: 14,
  },
  paymentEmoji: { fontSize: 22, marginRight: 12 },
  paymentTitle: { fontSize: 14, fontWeight: "bold", color: colors.ink },
  paymentSub: { fontSize: 12, color: colors.inkSoft, marginTop: 2 },
  billBox: {
    backgroundColor: "#ffffff",
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: colors.border,
  },
  billRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginBottom: 8,
  },
  billLabel: { fontSize: 14, color: colors.inkSoft },
  billValue: { fontSize: 14, fontWeight: "600", color: colors.ink },
  grandTotalRow: {
    borderTopWidth: 1,
    borderTopColor: colors.border,
    paddingTop: 8,
    marginTop: 4,
    marginBottom: 0,
  },
  grandTotalLabel: { fontSize: 16, fontWeight: "bold", color: colors.ink },
  grandTotalValue: { fontSize: 18, fontWeight: "bold", color: colors.ink },
  placeOrderBar: {
    position: "absolute",
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: "#ffffff",
    padding: 16,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  placeOrderBtn: {
    backgroundColor: colors.primary,
    padding: 16,
    borderRadius: 12,
    alignItems: "center",
  },
  placeOrderText: {
    color: "#ffffff",
    fontSize: 16,
    fontWeight: "bold",
  },
});
