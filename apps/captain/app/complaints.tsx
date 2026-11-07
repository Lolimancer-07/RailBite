import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
} from "react-native";
import { getComplaints } from "../src/net/captainRestClient.js";

export default function CaptainComplaintsScreen() {
  const [complaints, setComplaints] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  const loadComplaints = () => {
    setLoading(true);
    getComplaints()
      .then((data) => setComplaints(data.filter((c) => c.tier === 1)))
      .catch(() => {})
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadComplaints();
    const interval = setInterval(loadComplaints, 10000);
    return () => clearInterval(interval);
  }, []);

  const getMinutesLeft = (dueAt?: string) => {
    if (!dueAt) return 15;
    const diff = Math.round((new Date(dueAt).getTime() - Date.now()) / 60000);
    return diff;
  };

  return (
    <View style={styles.container}>
      <Text style={styles.header}>OPERATIONAL COMPLAINTS (TIER 1)</Text>
      <Text style={styles.subHeader}>
        Target: Seat-side replacement within 15 minutes of passenger report.
      </Text>

      {loading && complaints.length === 0 ? (
        <ActivityIndicator color="#065f46" style={{ marginTop: 40 }} />
      ) : complaints.length === 0 ? (
        <View style={styles.emptyBox}>
          <Text style={styles.emptyIcon}>✓</Text>
          <Text style={styles.emptyTitle}>No Open Complaints</Text>
          <Text style={styles.emptyText}>All pantry food issues resolved.</Text>
        </View>
      ) : (
        <FlatList
          data={complaints}
          keyExtractor={(item) => item.id}
          renderItem={({ item }) => {
            const minsLeft = getMinutesLeft(item.dueAt);
            const isOverdue = minsLeft <= 0;

            return (
              <View style={[styles.complaintCard, isOverdue && styles.overdueCard]}>
                <View style={styles.cardTop}>
                  <Text style={styles.issueType}>{item.type.replace(/_/g, " ")}</Text>
                  <View style={[styles.timerBadge, isOverdue && styles.overdueBadge]}>
                    <Text style={[styles.timerText, isOverdue && styles.overdueTimerText]}>
                      {isOverdue ? `OVERDUE (${Math.abs(minsLeft)}m ago)` : `${minsLeft}m left`}
                    </Text>
                  </View>
                </View>

                {item.text ? <Text style={styles.commentText}>"{item.text}"</Text> : null}

                <View style={styles.cardBottom}>
                  <Text style={styles.statusLabel}>Status: {item.status}</Text>
                  <Text style={styles.actionPrompt}>Action: Replacement Order</Text>
                </View>
              </View>
            );
          }}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#f0fdf4",
    padding: 16,
  },
  header: {
    fontSize: 12,
    fontWeight: "bold",
    color: "#065f46",
    letterSpacing: 1,
  },
  subHeader: {
    fontSize: 13,
    color: "#475569",
    marginTop: 4,
    marginBottom: 16,
  },
  emptyBox: {
    backgroundColor: "#ffffff",
    padding: 30,
    borderRadius: 10,
    alignItems: "center",
    marginTop: 20,
    borderWidth: 1,
    borderColor: "#bbf7d0",
  },
  emptyIcon: {
    fontSize: 32,
    color: "#16a34a",
    marginBottom: 8,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: "bold",
    color: "#064e3b",
  },
  emptyText: {
    fontSize: 13,
    color: "#64748b",
    marginTop: 4,
  },
  complaintCard: {
    backgroundColor: "#ffffff",
    padding: 16,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: "#e2e8f0",
    marginBottom: 12,
  },
  overdueCard: {
    borderColor: "#f87171",
    backgroundColor: "#fff5f5",
  },
  cardTop: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 8,
  },
  issueType: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#0f172a",
  },
  timerBadge: {
    backgroundColor: "#fef3c7",
    paddingVertical: 4,
    paddingHorizontal: 8,
    borderRadius: 6,
  },
  overdueBadge: {
    backgroundColor: "#fee2e2",
  },
  timerText: {
    color: "#92400e",
    fontSize: 12,
    fontWeight: "bold",
  },
  overdueTimerText: {
    color: "#991b1b",
  },
  commentText: {
    fontSize: 14,
    color: "#475569",
    fontStyle: "italic",
    marginBottom: 10,
  },
  cardBottom: {
    flexDirection: "row",
    justifyContent: "space-between",
    borderTopWidth: 1,
    borderTopColor: "#f1f5f9",
    paddingTop: 8,
  },
  statusLabel: {
    fontSize: 12,
    color: "#64748b",
    fontWeight: "600",
  },
  actionPrompt: {
    fontSize: 12,
    color: "#2563eb",
    fontWeight: "bold",
  },
});

