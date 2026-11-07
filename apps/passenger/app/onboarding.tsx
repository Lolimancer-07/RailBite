import React, { useState } from "react";
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
} from "react-native";
import { useRouter } from "expo-router";
import { useSessionStore } from "../src/store/session.js";
import { colors } from "../src/ui/theme.js";

export default function OnboardingScreen() {
  const router = useRouter();
  const session = useSessionStore((s) => s.session);
  const setSession = useSessionStore((s) => s.setSession);

  const [pnr, setPnr] = useState(session?.pnr || "8492000001");
  const [coach, setCoach] = useState(session?.coach || "B1");
  const [berth, setBerth] = useState(session ? String(session.berth) : "1");
  const [diners, setDiners] = useState(session ? String(session.diners) : "1");
  const [error, setError] = useState("");

  const handleContinue = () => {
    setError("");

    const cleanPnr = pnr.trim();
    if (!/^\d{10}$/.test(cleanPnr)) {
      setError("PNR must be exactly 10 digits");
      return;
    }

    const cleanCoach = coach.trim().toUpperCase();
    if (!/^[A-Z]{1,2}\d{1,2}$/.test(cleanCoach)) {
      setError("Coach format is invalid (e.g. B1, B3, S2, SL4)");
      return;
    }

    const berthNum = parseInt(berth, 10);
    if (isNaN(berthNum) || berthNum < 1 || berthNum > 100) {
      setError("Berth number must be between 1 and 100");
      return;
    }

    const dinersNum = parseInt(diners, 10);
    if (isNaN(dinersNum) || dinersNum < 1 || dinersNum > 8) {
      setError("Number of diners must be between 1 and 8");
      return;
    }

    setSession({
      pnr: cleanPnr,
      coach: cleanCoach,
      berth: berthNum,
      diners: dinersNum,
    });

    router.replace("/(tabs)/home");
  };

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.title}>Where should we deliver?</Text>
      <Text style={styles.subtitle}>
        Enter your seat details so our picker brings food straight to your berth.
      </Text>

      {error ? <Text style={styles.errorText}>{error}</Text> : null}

      <View style={styles.fieldGroup}>
        <Text style={styles.label}>10-digit PNR number</Text>
        <TextInput
          style={styles.input}
          placeholder="e.g. 8492000001"
          placeholderTextColor={colors.inkSoft}
          keyboardType="numeric"
          maxLength={10}
          value={pnr}
          onChangeText={setPnr}
        />
      </View>

      <View style={styles.row}>
        <View style={[styles.fieldGroup, { flex: 1, marginRight: 8 }]}>
          <Text style={styles.label}>Coach</Text>
          <TextInput
            style={styles.input}
            placeholder="B1"
            placeholderTextColor={colors.inkSoft}
            autoCapitalize="characters"
            maxLength={4}
            value={coach}
            onChangeText={setCoach}
          />
        </View>

        <View style={[styles.fieldGroup, { flex: 1, marginLeft: 8 }]}>
          <Text style={styles.label}>Seat / berth</Text>
          <TextInput
            style={styles.input}
            placeholder="1"
            placeholderTextColor={colors.inkSoft}
            keyboardType="numeric"
            maxLength={3}
            value={berth}
            onChangeText={setBerth}
          />
        </View>
      </View>

      {/* Diners kept out of the way, default 1 */}
      <View style={styles.optionalRow}>
        <Text style={styles.optionalLabel}>Diners (default 1)</Text>
        <TextInput
          style={styles.optionalInput}
          keyboardType="numeric"
          maxLength={1}
          value={diners}
          onChangeText={setDiners}
        />
      </View>

      <TouchableOpacity style={styles.button} onPress={handleContinue}>
        <Text style={styles.buttonText}>Save & start ordering →</Text>
      </TouchableOpacity>

      <Text style={styles.demoHint}>
        Demo tip: use PNR 8492000001, Coach B1, Seat 1 (seeded on the server).
      </Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 24,
    backgroundColor: "#ffffff",
    flexGrow: 1,
    justifyContent: "center",
  },
  title: {
    fontSize: 26,
    fontWeight: "bold",
    color: colors.ink,
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 14,
    color: colors.inkSoft,
    marginBottom: 24,
    lineHeight: 20,
  },
  errorText: {
    color: colors.danger,
    backgroundColor: colors.dangerSoft,
    padding: 10,
    borderRadius: 8,
    marginBottom: 16,
    fontWeight: "600",
  },
  fieldGroup: {
    marginBottom: 16,
  },
  row: {
    flexDirection: "row",
  },
  label: {
    fontSize: 13,
    fontWeight: "600",
    color: colors.ink,
    marginBottom: 6,
  },
  input: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 12,
    padding: 14,
    fontSize: 16,
    color: colors.ink,
  },
  optionalRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginTop: 4,
    marginBottom: 20,
    opacity: 0.8,
  },
  optionalLabel: { fontSize: 12, color: colors.inkSoft },
  optionalInput: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
    paddingVertical: 6,
    paddingHorizontal: 10,
    fontSize: 14,
    color: colors.ink,
    width: 46,
    textAlign: "center",
  },
  button: {
    backgroundColor: colors.primary,
    padding: 16,
    borderRadius: 12,
    alignItems: "center",
  },
  buttonText: {
    color: "#ffffff",
    fontWeight: "bold",
    fontSize: 16,
  },
  demoHint: {
    fontSize: 12,
    color: colors.inkSoft,
    textAlign: "center",
    marginTop: 16,
  },
});
