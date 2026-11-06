import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
} from "react-native";
import { getLedgerSummary } from "../src/net/captainRestClient.js";
import { useAuthStore } from "../src/store/auth.js";

export default function CashLedgerScreen() {
  const staff = useAuthStore((s) => s.staff);
  const [summaries, setSummaries] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  const loadSummary = () => {
    setLoading(true);
    getLedgerSummary()
      .then((data) => setSummaries(data))
      .catch(() => {})
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadSummary();
  }, []);

  const mySummary = summaries.find((s) => s.captainId === staff?.staffId);

  return (
    <View style={styles.container}>
      {/* My Shift Summary Card */}
      {mySummary ? (
        <View style={styles.summaryCard}>
          <Text style={styles.cardHeader}>MY SHIFT CASH RECONCILIATION</Text>

          <View style={styles.metricRow}>
            <Text style={styles.metricLabel}>Expected Cash (Delivered Orders):</Text>
            <Text style={styles.metricValue}>₹{mySummary.expectedPaise / 100}</Text>
          </View>

          <View style={styles.metricRow}>
            <Text style={styles.metricLabel}>Cash Actually Collected:</Text>
            <Text style={[styles.metricValue, { color: "#16a34a" }]}>
              ₹{mySummary.collectedPaise / 100}
            </Text>
          </View>

          <View style={styles.metricRow}>
            <Text style={styles.metricLabel}>Passenger Confirmed:</Text>
            <Text style={styles.metricValue}>₹{mySummary.passengerConfirmedPaise / 100}</Text>
          </View>

          {mySummary.mismatch ? (
            <View style={styles.mismatchBanner}>
              <Text style={styles.mismatchText}>
                ⚠️ MISMATCH DETECTED: Collected cash does not equal expected or passenger-confirmed total.
              </Text>
            </View>
          ) : (
            <View style={styles.balancedBanner}>
              <Text style={styles.balancedText}>✓ Shift cash perfectly balanced.</Text>
            </View>
          )}
        </View>
      ) : null}

      {/* All Captains Shift Table */}
      <View style={styles.tableSection}>
        <Text style={styles.tableTitle}>ALL CAPTAINS CASH SUMMARY</Text>
        {loading ? (
          <ActivityIndicator color="#065f46" />
        ) : (
          <FlatList
            data={summaries}
            keyExtractor={(item) => item.captainId}
            renderItem={({ item }) => (
              <View style={styles.captainRow}>
                <View>
                  <Text style={styles.captainName}>{item.captainName}</Text>
                  <Text style={styles.captainSub}>
                    Expected: ₹{item.expectedPaise / 100} • Confirmed: ₹{item.passengerConfirmedPaise / 100}
                  </Text>
                </View>
                <Text
                  style={[
                    styles.captainCash,
                    item.mismatch ? styles.mismatchCash : styles.balancedCash,
                  ]}
                >
                  ₹{item.collectedPaise / 100}
                </Text>
              </View>
            )}
          />
        )}
      </View>

      <TouchableOpacity style={styles.refreshBtn} onPress={loadSummary}>
        <Text style={styles.refreshBtnText}>🔄 Refresh Ledger</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#f0fdf4",
    padding: 16,
  },
  summaryCard: {
    backgroundColor: "#ffffff",
    borderRadius: 10,
    padding: 18,
    borderWidth: 1,
    borderColor: "#bbf7d0",
    marginBottom: 16,
  },
  cardHeader: {
    fontSize: 12,
    fontWeight: "bold",
    color: "#065f46",
    letterSpacing: 1,
    marginBottom: 14,
  },
  metricRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginBottom: 10,
  },
  metricLabel: {
    fontSize: 14,
    color: "#475569",
  },
  metricValue: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#0f172a",
  },
  mismatchBanner: {
    backgroundColor: "#fee2e2",
    padding: 10,
    borderRadius: 6,
    marginTop: 10,
  },
  mismatchText: {
    color: "#991b1b",
    fontSize: 12,
    fontWeight: "bold",
  },
  balancedBanner: {
    backgroundColor: "#dcfce7",
    padding: 10,
    borderRadius: 6,
    marginTop: 10,
  },
  balancedText: {
    color: "#166534",
    fontSize: 12,
    fontWeight: "bold",
    textAlign: "center",
  },
  tableSection: {
    flex: 1,
    backgroundColor: "#ffffff",
    borderRadius: 10,
    padding: 16,
    borderWidth: 1,
    borderColor: "#e2e8f0",
  },
  tableTitle: {
    fontSize: 12,
    fontWeight: "bold",
    color: "#64748b",
    marginBottom: 12,
  },
  captainRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: "#f1f5f9",
  },
  captainName: {
    fontSize: 14,
    fontWeight: "600",
    color: "#0f172a",
  },
  captainSub: {
    fontSize: 12,
    color: "#64748b",
    marginTop: 2,
  },
  captainCash: {
    fontSize: 16,
    fontWeight: "bold",
  },
  balancedCash: {
    color: "#16a34a",
  },
  mismatchCash: {
    color: "#dc2626",
  },
  refreshBtn: {
    backgroundColor: "#065f46",
    padding: 14,
    borderRadius: 8,
    alignItems: "center",
    marginTop: 12,
  },
  refreshBtnText: {
    color: "#ffffff",
    fontWeight: "bold",
    fontSize: 14,
  },
});
