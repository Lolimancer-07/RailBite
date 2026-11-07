import React from "react";
import { Stack } from "expo-router";
import { SafeAreaView, StatusBar, StyleSheet } from "react-native";
import { colors } from "../src/ui/theme.js";

export default function PickerLayout() {
  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: colors.primaryDark },
          headerTintColor: "#ffffff",
          headerTitleStyle: { fontWeight: "bold" },
        }}
      >
        <Stack.Screen name="login" options={{ title: "Picker Login", headerShown: false }} />
        <Stack.Screen name="active" options={{ title: "Picker" }} />
        <Stack.Screen name="alerts" options={{ title: "New Order Alert", headerShown: false }} />
        <Stack.Screen name="order/[id]" options={{ title: "Order in Progress" }} />
        <Stack.Screen name="cash" options={{ title: "Cash Ledger" }} />
        <Stack.Screen name="complaints" options={{ title: "Zone Issues" }} />
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
