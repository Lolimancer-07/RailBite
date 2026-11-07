import React, { useEffect } from "react";
import { SafeAreaView, StatusBar, StyleSheet } from "react-native";
import { Stack } from "expo-router";
import { ConnectionBanner } from "../src/components/ConnectionBanner.js";
import { startQueueWorker, stopQueueWorker } from "../src/sync/queueWorker.js";
import { passengerMqtt } from "../src/net/mqttClient.js";
import { loadSavedServerUrl } from "../src/net/server-url.js";
import { useSessionStore } from "../src/store/session.js";
import { colors } from "../src/ui/theme.js";

export default function RootLayout() {
  useEffect(() => {
    // 1. Hydrate saved seat/session and saved server URL before connecting
    useSessionStore.getState().hydrate();
    loadSavedServerUrl();

    // 2. Start background queue processing & MQTT
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
          headerShown: false,
          contentStyle: { backgroundColor: colors.bg },
        }}
      >
        <Stack.Screen name="index" />
        <Stack.Screen name="(tabs)" />
        <Stack.Screen name="onboarding" />
        <Stack.Screen name="settings" />
        <Stack.Screen name="cart" options={{ presentation: "modal" }} />
        <Stack.Screen name="orders/[id]" />
        <Stack.Screen name="complaint/[orderId]" />
      </Stack>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.bg,
  },
});
