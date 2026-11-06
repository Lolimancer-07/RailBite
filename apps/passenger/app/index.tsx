import React, { useEffect } from "react";
import { View, Text, StyleSheet, ActivityIndicator } from "react-native";
import { useRouter } from "expo-router";
import { useSessionStore } from "../src/store/session.js";

export default function IndexScreen() {
  const router = useRouter();
  const session = useSessionStore((s) => s.session);

  useEffect(() => {
    const timer = setTimeout(() => {
      if (session && session.pnr) {
        router.replace("/menu");
      } else {
        router.replace("/onboarding");
      }
    }, 500);

    return () => clearTimeout(timer);
  }, [session, router]);

  return (
    <View style={styles.container}>
      <Text style={styles.logo}>🚆 RailBite</Text>
      <Text style={styles.tagline}>Official Pantry Car Ordering</Text>
      <ActivityIndicator size="large" color="#1e3a8a" style={styles.spinner} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "#f8fafc",
  },
  logo: {
    fontSize: 32,
    fontWeight: "bold",
    color: "#1e3a8a",
  },
  tagline: {
    fontSize: 16,
    color: "#64748b",
    marginTop: 8,
  },
  spinner: {
    marginTop: 24,
  },
});
