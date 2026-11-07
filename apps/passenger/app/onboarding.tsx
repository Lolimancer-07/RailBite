import React, { useState } from "react";
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
} from "react-native";
import { useRouter } from "expo-router";
import { useSessionStore } from "../src/store/session.js";

export default function OnboardingScreen() {
  const router = useRouter();
  const setSession = useSessionStore((s) => s.setSession);

  const [pnr, setPnr] = useState("8492000001");
  const [coach, setCoach] = useState("B1");
  const [berth, setBerth] = useState("1");
  const [diners, setDiners] = useState("1");
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

    router.replace("/menu");
  };

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.title}>Welcome to RailBite</Text>
      <Text style={styles.subtitle}>
        Please enter your seat details so pantry car staff can deliver directly to your berth.
      </Text>

      {error ? <Text style={styles.errorText}>{error}</Text> : null}

      <View style={styles.fieldGroup}>
        <Text style={styles.label}>10-Digit PNR Number</Text>
        <TextInput
          style={styles.input}
          placeholder="e.g. 8492000001"
          keyboardType="numeric"
          maxLength={10}
          value={pnr}
          onChangeText={setPnr}
        />
      </View>

      <View style={styles.row}>
        <View style={[styles.fieldGroup, { flex: 1, marginRight: 8 }]}>
          <Text style={styles.label}>Coach (e.g. B1, S2)</Text>
          <TextInput
            style={styles.input}
            placeholder="B1"
            autoCapitalize="characters"
            maxLength={4}
            value={coach}
            onChangeText={setCoach}
          />
        </View>

        <View style={[styles.fieldGroup, { flex: 1, marginLeft: 8 }]}>
          <Text style={styles.label}>Berth Number</Text>
          <TextInput
            style={styles.input}
            placeholder="1"
            keyboardType="numeric"
            maxLength={3}
            value={berth}
            onChangeText={setBerth}
          />
        </View>
      </View>

      <View style={styles.fieldGroup}>
        <Text style={styles.label}>Number of Diners</Text>
        <TextInput
          style={styles.input}
          placeholder="1"
          keyboardType="numeric"
          maxLength={1}
          value={diners}
          onChangeText={setDiners}
        />
      </View>

      <TouchableOpacity style={styles.button} onPress={handleContinue}>
        <Text style={styles.buttonText}>View Pantry Menu →</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 20,
    backgroundColor: "#ffffff",
    flexGrow: 1,
  },
  title: {
    fontSize: 24,
    fontWeight: "bold",
    color: "#0f172a",
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 14,
    color: "#64748b",
    marginBottom: 20,
    lineHeight: 20,
  },
  errorText: {
    color: "#dc2626",
    backgroundColor: "#fee2e2",
    padding: 10,
    borderRadius: 6,
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
    fontSize: 14,
    fontWeight: "600",
    color: "#334155",
    marginBottom: 6,
  },
  input: {
    borderWidth: 1,
    borderColor: "#cbd5e1",
    borderRadius: 8,
    padding: 12,
    fontSize: 16,
    backgroundColor: "#f8fafc",
  },
  button: {
    backgroundColor: "#1e3a8a",
    padding: 16,
    borderRadius: 8,
    alignItems: "center",
    marginTop: 20,
  },
  buttonText: {
    color: "#ffffff",
    fontSize: 16,
    fontWeight: "bold",
  },
});

