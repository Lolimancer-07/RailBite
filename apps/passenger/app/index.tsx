import React, { useEffect } from "react";
import { View, Text, StyleSheet, ActivityIndicator } from "react-native";
import { useRouter } from "expo-router";
import { useSessionStore } from "../src/store/session.js";
import { colors } from "../src/ui/theme.js";

export default function IndexScreen() {
  const router = useRouter();
  const session = useSessionStore((s) => s.session);
  const phone = useSessionStore((s) => s.phone);
  const hydrate = useSessionStore((s) => s.hydrate);
  const hydrated = useSessionStore((s) => s.hydrated);

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  useEffect(() => {
    if (!hydrated) return;
    const timer = setTimeout(() => {
      if (!phone) {
        router.replace("/login");
      } else if (session && session.pnr) {
        router.replace("/(tabs)/home");
      } else {
        router.replace("/onboarding");
      }
    }, 300);

    return () => clearTimeout(timer);
  }, [hydrated, session, phone, router]);

  return (
    <View style={styles.container}>
      <Text style={styles.logo}>🚆 RailBite</Text>
      <Text style={styles.tagline}>Fresh food to your train seat in ~10 mins</Text>
      <ActivityIndicator size="large" color={colors.primary} style={styles.spinner} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "#ffffff",
  },
  logo: {
    fontSize: 34,
    fontWeight: "bold",
    color: colors.primary,
  },
  tagline: {
    fontSize: 14,
    color: colors.inkSoft,
    marginTop: 8,
  },
  spinner: {
    marginTop: 24,
  },
});
