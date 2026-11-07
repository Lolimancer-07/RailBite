import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { useConnectionStore } from "../store/connection.js";
import { colors } from "../ui/theme.js";

export function ConnectionBanner() {
  const { status, queuedCount } = useConnectionStore();

  const connected = status === "Connected";
  const searching = status === "Searching";

  const bgColor = connected ? colors.primary : searching ? colors.amber : colors.danger;
  const text = connected
    ? "● Connected"
    : searching
      ? "● Connecting to server..."
      : "● Cannot reach server" + (queuedCount > 0 ? ` — ${queuedCount} queued` : "");

  return (
    <View style={[styles.banner, { backgroundColor: bgColor }]}>
      <Text style={styles.text}>{text}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: {
    paddingVertical: 5,
    paddingHorizontal: 12,
    alignItems: "center",
    justifyContent: "center",
  },
  text: {
    color: "#ffffff",
    fontSize: 11,
    fontWeight: "600",
  },
});
