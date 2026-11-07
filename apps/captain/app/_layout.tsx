import React from "react";
import { Stack } from "expo-router";
import { SafeAreaView, StatusBar, StyleSheet } from "react-native";

export default function CaptainLayout() {
  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: "#065f46" }, // Emerald green for Captain
          headerTintColor: "#ffffff",
          headerTitleStyle: { fontWeight: "bold" },
        }}
      >
        <Stack.Screen name="login" options={{ title: "Captain Login", headerShown: false }} />
        <Stack.Screen name="active" options={{ title: "Active Deliveries" }} />
        <Stack.Screen name="alerts" options={{ title: "Incoming Order Alert", headerShown: false }} />
        <Stack.Screen name="cash" options={{ title: "Cash Ledger" }} />
        <Stack.Screen name="complaints" options={{ title: "Zone Complaints" }} />
      </Stack>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#f0fdf4",
  },
});

