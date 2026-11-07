import React, { useEffect } from "react";
import { Stack } from "expo-router";
import { SafeAreaView, StatusBar, StyleSheet } from "react-native";
import { ConnectionBanner } from "../src/components/ConnectionBanner.js";
import { startQueueWorker, stopQueueWorker } from "../src/sync/queueWorker.js";
import { passengerMqtt } from "../src/net/mqttClient.js";

export default function RootLayout() {
  useEffect(() => {
    // Start background queue processing & MQTT
    startQueueWorker();
    passengerMqtt.connect();

    return () => {
      stopQueueWorker();
      passengerMqtt.disconnect();
    };
  }, []);

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="dark-content" />
      <ConnectionBanner />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: "#1e3a8a" },
          headerTintColor: "#ffffff",
          headerTitleStyle: { fontWeight: "bold" },
        }}
      >
        <Stack.Screen name="index" options={{ title: "RailBite" }} />
        <Stack.Screen name="onboarding" options={{ title: "Your Seat Details" }} />
        <Stack.Screen name="menu" options={{ title: "Pantry Menu (MRP)" }} />
        <Stack.Screen name="cart" options={{ title: "Your Tray" }} />
        <Stack.Screen name="orders/index" options={{ title: "Your Orders" }} />
        <Stack.Screen name="orders/[id]" options={{ title: "Order Details" }} />
        <Stack.Screen name="complaint/[orderId]" options={{ title: "File Complaint" }} />
      </Stack>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#f8fafc",
  },
});

