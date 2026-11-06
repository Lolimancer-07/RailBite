import React from "react";
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  Alert,
  Linking,
} from "react-native";
import { useRouter } from "expo-router";
import { useCartStore } from "../src/store/cart.js";
import { useSessionStore } from "../src/store/session.js";
import { useConnectionStore } from "../src/store/connection.js";
import { saveLocalOrder, queueAction } from "../src/db/queries.js";
import { processQueue } from "../src/sync/queueWorker.js";
import { passengerMqtt } from "../src/net/mqttClient.js";
import { determineTrack, encodeSmsToken, encodeItemCodes, type OrderCreate } from "@railbite/shared";

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
  const connState = useConnectionStore((s) => s.state);
  const { items: cartMap, addItem, removeItem, clearCart, totalPaise, paymentMode, setPaymentMode } = useCartStore();

  const cartList = Array.from(cartMap.values());
  const totalAmount = totalPaise();

  const handlePlaceOrder = () => {
    if (!session || !session.pnr) {
      Alert.alert("Missing Details", "Please enter your PNR and seat details first.");
      router.push("/onboarding");
      return;
    }

    if (cartList.length === 0) {
      Alert.alert("Empty Tray", "Please add items to your tray first.");
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

  const handlePlaceOrderSms = () => {
    if (!session || !session.pnr) {
      Alert.alert("Missing Details", "Please enter your PNR and seat details first.");
      router.push("/onboarding");
      return;
    }

    if (cartList.length === 0) {
      Alert.alert("Empty Tray", "Please add items to your tray first.");
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

    try {
      const itemCodes = encodeItemCodes(orderItems.map(({ itemId, qty }) => ({ itemId, qty })));
      const token = encodeSmsToken(
        {
          pnrLast4: session.pnr.slice(-4),
          coach: session.coach,
          berth: session.berth,
          diners: session.diners,
          itemCodes,
        },
        "railbite_secret_pantry_trip_2026"
      );

      saveLocalOrder(
        {
          id: orderId,
          pnr: session.pnr,
          coach: session.coach,
          berth: session.berth,
          diners: session.diners,
          track,
          status: "PLACED",
          paymentMode: "COD",
          totalPaise: totalAmount,
          source: "SMS",
          verified: 1,
          createdAt: now,
          updatedAt: now,
        },
        orderItems
      );

      clearCart();

      const pantryPhone = "+919876543210";
      const smsUrl = `sms:${pantryPhone}?body=${encodeURIComponent(token)}`;
      Linking.openURL(smsUrl).catch(() => {
        Alert.alert("SMS Ready", `Send this code to ${pantryPhone}:\n\n${token}`);
      });

      router.replace(`/orders/${orderId}`);
    } catch (err: any) {
      Alert.alert("Error generating SMS", err.message);
    }
  };

  if (cartList.length === 0) {
    return (
      <View style={styles.emptyContainer}>
        <Text style={styles.emptyIcon}>🍽️</Text>
        <Text style={styles.emptyTitle}>Your tray is empty</Text>
        <Text style={styles.emptySubtitle}>Explore the pantry car menu to order food at official MRP.</Text>
        <TouchableOpacity style={styles.browseBtn} onPress={() => router.push("/menu")}>
          <Text style={styles.browseBtnText}>Browse Menu</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {/* Delivery Seat Summary */}
      <View style={styles.seatSummary}>
        <Text style={styles.seatSummaryLabel}>Delivering to Seat:</Text>
        <Text style={styles.seatSummaryValue}>
          Coach {session?.coach}, Berth {session?.berth} (PNR: {session?.pnr})
        </Text>
      </View>

      <FlatList
        data={cartList}
        keyExtractor={({ item }) => item.id}
        renderItem={({ item: cartEntry }) => {
          const { item, qty } = cartEntry;
          return (
            <View style={styles.itemRow}>
              <View style={{ flex: 1 }}>
                <Text style={styles.itemName}>{item.name}</Text>
                <Text style={styles.itemPrice}>₹{item.mrpPaise / 100} each</Text>
              </View>

              <View style={styles.qtyControls}>
                <TouchableOpacity style={styles.qtyBtn} onPress={() => removeItem(item.id)}>
                  <Text style={styles.qtyBtnText}>-</Text>
                </TouchableOpacity>
                <Text style={styles.qtyText}>{qty}</Text>
                <TouchableOpacity style={styles.qtyBtn} onPress={() => addItem(item)}>
                  <Text style={styles.qtyBtnText}>+</Text>
                </TouchableOpacity>
              </View>

              <Text style={styles.lineTotal}>₹{(item.mrpPaise * qty) / 100}</Text>
            </View>
          );
        }}
        ListFooterComponent={
          <View style={styles.footerContainer}>
            {/* Payment Mode Selector */}
            <Text style={styles.sectionHeader}>Payment Method</Text>
            <View style={styles.paymentRow}>
              <TouchableOpacity
                style={[styles.paymentOption, paymentMode === "COD" && styles.paymentOptionActive]}
                onPress={() => setPaymentMode("COD")}
              >
                <Text style={[styles.paymentText, paymentMode === "COD" && styles.paymentTextActive]}>
                  💵 Cash on Delivery (Standard)
                </Text>
              </TouchableOpacity>
            </View>

            {/* Bill Summary */}
            <View style={styles.billBox}>
              <View style={styles.billRow}>
                <Text style={styles.billLabel}>Item Total</Text>
                <Text style={styles.billValue}>₹{totalAmount / 100}</Text>
              </View>
              <View style={styles.billRow}>
                <Text style={styles.billLabel}>Delivery Charges</Text>
                <Text style={[styles.billValue, { color: "#166534" }]}>FREE (Seat-side)</Text>
              </View>
              <View style={[styles.billRow, styles.grandTotalRow]}>
                <Text style={styles.grandTotalLabel}>To Pay</Text>
                <Text style={styles.grandTotalValue}>₹{totalAmount / 100}</Text>
              </View>
            </View>
          </View>
        }
      />

      {/* Place Order Button */}
      <View style={styles.placeOrderBar}>
        <TouchableOpacity style={styles.placeOrderBtn} onPress={handlePlaceOrder}>
          <Text style={styles.placeOrderText}>Place Order (₹{totalAmount / 100}) →</Text>
        </TouchableOpacity>
        {connState !== "CONNECTED" && (
          <TouchableOpacity style={styles.smsOrderBtn} onPress={handlePlaceOrderSms}>
            <Text style={styles.smsOrderBtnText}>📱 Order via SMS (No Wi-Fi Needed)</Text>
          </TouchableOpacity>
        )}
      </View>
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
    backgroundColor: "#ffffff",
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
    marginBottom: 24,
  },
  browseBtn: {
    backgroundColor: "#1e3a8a",
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: 8,
  },
  browseBtnText: {
    color: "#ffffff",
    fontWeight: "bold",
    fontSize: 15,
  },
  seatSummary: {
    backgroundColor: "#e2e8f0",
    padding: 14,
    borderBottomWidth: 1,
    borderBottomColor: "#cbd5e1",
  },
  seatSummaryLabel: {
    fontSize: 11,
    color: "#64748b",
    fontWeight: "bold",
    textTransform: "uppercase",
  },
  seatSummaryValue: {
    fontSize: 14,
    color: "#0f172a",
    fontWeight: "bold",
    marginTop: 2,
  },
  itemRow: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#ffffff",
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: "#f1f5f9",
  },
  itemName: {
    fontSize: 15,
    fontWeight: "600",
    color: "#0f172a",
  },
  itemPrice: {
    fontSize: 13,
    color: "#64748b",
    marginTop: 2,
  },
  qtyControls: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#f1f5f9",
    borderRadius: 6,
    borderWidth: 1,
    borderColor: "#cbd5e1",
    marginHorizontal: 12,
  },
  qtyBtn: {
    paddingHorizontal: 10,
    paddingVertical: 4,
  },
  qtyBtnText: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#1e3a8a",
  },
  qtyText: {
    fontSize: 14,
    fontWeight: "bold",
    paddingHorizontal: 4,
  },
  lineTotal: {
    fontSize: 15,
    fontWeight: "bold",
    color: "#0f172a",
    width: 60,
    textAlign: "right",
  },
  footerContainer: {
    padding: 16,
    paddingBottom: 100,
  },
  sectionHeader: {
    fontSize: 14,
    fontWeight: "bold",
    color: "#334155",
    marginBottom: 8,
    marginTop: 8,
  },
  paymentRow: {
    marginBottom: 16,
  },
  paymentOption: {
    borderWidth: 1.5,
    borderColor: "#cbd5e1",
    borderRadius: 8,
    padding: 14,
    backgroundColor: "#ffffff",
  },
  paymentOptionActive: {
    borderColor: "#1e3a8a",
    backgroundColor: "#eff6ff",
  },
  paymentText: {
    fontSize: 14,
    fontWeight: "600",
    color: "#334155",
  },
  paymentTextActive: {
    color: "#1e3a8a",
    fontWeight: "bold",
  },
  billBox: {
    backgroundColor: "#ffffff",
    borderRadius: 8,
    padding: 16,
    borderWidth: 1,
    borderColor: "#e2e8f0",
  },
  billRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginBottom: 8,
  },
  billLabel: {
    fontSize: 14,
    color: "#64748b",
  },
  billValue: {
    fontSize: 14,
    fontWeight: "600",
    color: "#0f172a",
  },
  grandTotalRow: {
    borderTopWidth: 1,
    borderTopColor: "#e2e8f0",
    paddingTop: 8,
    marginTop: 4,
    marginBottom: 0,
  },
  grandTotalLabel: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#0f172a",
  },
  grandTotalValue: {
    fontSize: 18,
    fontWeight: "bold",
    color: "#1e3a8a",
  },
  placeOrderBar: {
    position: "absolute",
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: "#ffffff",
    padding: 16,
    borderTopWidth: 1,
    borderTopColor: "#e2e8f0",
  },
  placeOrderBtn: {
    backgroundColor: "#166534",
    padding: 16,
    borderRadius: 8,
    alignItems: "center",
  },
  placeOrderText: {
    color: "#ffffff",
    fontSize: 16,
    fontWeight: "bold",
  },
  smsOrderBtn: {
    backgroundColor: "#475569",
    padding: 12,
    borderRadius: 8,
    alignItems: "center",
    marginTop: 8,
  },
  smsOrderBtnText: {
    color: "#ffffff",
    fontSize: 14,
    fontWeight: "600",
  },
});
