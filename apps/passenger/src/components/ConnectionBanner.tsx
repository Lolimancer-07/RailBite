import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { useConnectionStore } from "../store/connection.js";

export function ConnectionBanner() {
  const { status, queuedCount } = useConnectionStore();

  let bgColor = "#2e7d32"; // green
  let text = "Pantry Wi-Fi Connected";

  if (status === "Searching") {
    bgColor = "#ef6c00"; // orange
    text = "Searching for Pantry Wi-Fi...";
  } else if (status === "Offline") {
    bgColor = "#c62828"; // red
    text = queuedCount > 0 ? `Offline — ${queuedCount} order/action queued` : "Offline — No connection";
  }

  return (
    <View style={[styles.banner, { backgroundColor: bgColor }]}>
      <Text style={styles.text}>{text}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: {
    paddingVertical: 6,
    paddingHorizontal: 12,
    alignItems: "center",
    justifyContent: "center",
  },
  text: {
    color: "#ffffff",
    fontSize: 12,
    fontWeight: "bold",
  },
});

