import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
} from "react-native";
import { useRouter } from "expo-router";
import { useAlertsStore } from "../src/store/alerts.js";
import { useAuthStore } from "../src/store/auth.js";
import { useActiveOrdersStore } from "../src/store/orders.js";
import { acceptOrder, rejectOrder } from "../src/net/captainRestClient.js";
import { dismissForegroundAlert } from "../modules/alert-foreground-service/index.js";

export default function AlertScreen() {
  const router = useRouter();
  const staff = useAuthStore((s) => s.staff);
  const { currentAlert, clearAlert } = useAlertsStore();
  const addOrder = useActiveOrdersStore((s) => s.addOrder);

  const [secondsRemaining, setSecondsRemaining] = useState(60);

  useEffect(() => {
    if (!currentAlert) {
      router.replace("/active");
      return;
    }

    // Calculate expiry based on alert.expiresAt
    const expiresTime = new Date(currentAlert.expiresAt).getTime();
    const updateCountdown = () => {
      const diff = Math.max(0, Math.round((expiresTime - Date.now()) / 1000));
      setSecondsRemaining(diff);
      if (diff <= 0) {
        dismissForegroundAlert();
        clearAlert();
        router.replace("/active");
      }
    };

    updateCountdown();
    const interval = setInterval(updateCountdown, 1000);

    return () => {
      clearInterval(interval);
    };
  }, [currentAlert, router, clearAlert]);

  if (!currentAlert) return null;

  const handleAccept = async () => {
    if (!staff?.staffId) return;

    try {
      dismissForegroundAlert();
      const updated = await acceptOrder(currentAlert.orderId, staff.staffId);
      addOrder(updated);
      clearAlert();
      router.replace("/active");
    } catch (err: any) {
      Alert.alert("Error", err.message || "Failed to accept order");
    }
  };

  const handleReject = async () => {
    if (!staff?.staffId) return;

    try {
      dismissForegroundAlert();
      await rejectOrder(currentAlert.orderId, staff.staffId);
      clearAlert();
      router.replace("/active");
    } catch (err: any) {
      Alert.alert("Error", err.message || "Failed to reject order");
    }
  };

  const isUnverified = currentAlert.verified === 0;

  return (
    <View style={styles.container}>
      {/* 60s Countdown Banner */}
      <View style={styles.timerHeader}>
        <View style={styles.ring}>
          <Text style={styles.ringText}>{secondsRemaining}s</Text>
        </View>
        <Text style={styles.timerSub}>Hurry! Auto-reassigns in {secondsRemaining} seconds</Text>
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        {/* Urgent Coach & Berth Card */}
        <View style={styles.locationCard}>
          <Text style={styles.coachLabel}>DELIVER TO SEAT</Text>
          <Text style={styles.coachValue}>
            COACH {currentAlert.coach} • BERTH {currentAlert.berth}
          </Text>

          {isUnverified ? (
            <View style={styles.unverifiedBanner}>
              <Text style={styles.unverifiedText}>
                ⚠️ NOT IN MANIFEST — VERIFY PNR ({currentAlert.pnr}) AT SEAT
              </Text>
            </View>
          ) : (
            <Text style={styles.verifiedText}>✓ Manifest Verified (PNR: {currentAlert.pnr})</Text>
          )}
        </View>

        {/* Order Details */}
        <View style={styles.itemsCard}>
          <Text style={styles.sectionTitle}>Order Items ({currentAlert.track} Track)</Text>
          {currentAlert.items.map((item, idx) => (
            <View key={idx} style={styles.itemRow}>
              <Text style={styles.itemName}>{item.name}</Text>
              <Text style={styles.itemQty}>× {item.qty}</Text>
            </View>
          ))}

          <View style={styles.totalRow}>
            <Text style={styles.totalLabel}>Collect Cash:</Text>
            <Text style={styles.totalValue}>₹{currentAlert.total / 100}</Text>
          </View>
        </View>
      </ScrollView>

      {/* Action Buttons */}
      <View style={styles.buttonContainer}>
        <TouchableOpacity style={styles.rejectBtn} onPress={handleReject}>
          <Text style={styles.rejectBtnText}>✕ REJECT</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.acceptBtn} onPress={handleAccept}>
          <Text style={styles.acceptBtnText}>✓ ACCEPT ORDER</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#18181b", // dark screen for urgent alarm feel
  },
  timerHeader: {
    paddingTop: 40,
    paddingBottom: 16,
    alignItems: "center",
    backgroundColor: "#27272a",
  },
  ring: {
    width: 64,
    height: 64,
    borderRadius: 32,
    borderWidth: 4,
    borderColor: "#ef4444",
    justifyContent: "center",
    alignItems: "center",
    marginBottom: 6,
  },
  ringText: {
    fontSize: 20,
    fontWeight: "bold",
    color: "#ffffff",
  },
  timerSub: {
    color: "#a1a1aa",
    fontSize: 12,
  },
  content: {
    padding: 16,
  },
  locationCard: {
    backgroundColor: "#064e3b",
    padding: 20,
    borderRadius: 12,
    alignItems: "center",
    marginBottom: 16,
    borderWidth: 1,
    borderColor: "#059669",
  },
  coachLabel: {
    color: "#6ee7b7",
    fontSize: 12,
    fontWeight: "bold",
    letterSpacing: 1,
  },
  coachValue: {
    color: "#ffffff",
    fontSize: 26,
    fontWeight: "bold",
    marginTop: 4,
    textAlign: "center",
  },
  unverifiedBanner: {
    backgroundColor: "#7f1d1d",
    padding: 8,
    borderRadius: 6,
    marginTop: 10,
  },
  unverifiedText: {
    color: "#fecaca",
    fontSize: 11,
    fontWeight: "bold",
    textAlign: "center",
  },
  verifiedText: {
    color: "#a7f3d0",
    fontSize: 12,
    marginTop: 8,
  },
  itemsCard: {
    backgroundColor: "#27272a",
    padding: 16,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: "#3f3f46",
  },
  sectionTitle: {
    color: "#a1a1aa",
    fontSize: 12,
    fontWeight: "bold",
    textTransform: "uppercase",
    marginBottom: 12,
  },
  itemRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginBottom: 8,
  },
  itemName: {
    color: "#ffffff",
    fontSize: 16,
    fontWeight: "600",
  },
  itemQty: {
    color: "#6ee7b7",
    fontSize: 16,
    fontWeight: "bold",
  },
  totalRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    borderTopWidth: 1,
    borderTopColor: "#3f3f46",
    paddingTop: 12,
    marginTop: 8,
  },
  totalLabel: {
    color: "#ffffff",
    fontSize: 16,
    fontWeight: "bold",
  },
  totalValue: {
    color: "#22c55e",
    fontSize: 22,
    fontWeight: "bold",
  },
  buttonContainer: {
    flexDirection: "row",
    padding: 16,
    gap: 12,
    backgroundColor: "#27272a",
  },
  rejectBtn: {
    flex: 1,
    backgroundColor: "#991b1b",
    padding: 18,
    borderRadius: 8,
    alignItems: "center",
  },
  rejectBtnText: {
    color: "#ffffff",
    fontSize: 16,
    fontWeight: "bold",
  },
  acceptBtn: {
    flex: 2,
    backgroundColor: "#16a34a",
    padding: 18,
    borderRadius: 8,
    alignItems: "center",
  },
  acceptBtnText: {
    color: "#ffffff",
    fontSize: 16,
    fontWeight: "bold",
  },
});
