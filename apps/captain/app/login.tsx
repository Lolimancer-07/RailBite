import React, { useState } from "react";
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ScrollView,
} from "react-native";
import { useRouter } from "expo-router";
import { loginStaff } from "../src/net/captainRestClient.js";
import { useAuthStore } from "../src/store/auth.js";
import { captainMqtt } from "../src/net/captainMqttClient.js";

export default function LoginScreen() {
  const router = useRouter();
  const setStaff = useAuthStore((s) => s.setStaff);

  const [staffId, setStaffId] = useState("c1");
  const [pin, setPin] = useState("1234");
  const [loading, setLoading] = useState(false);

  const handleLogin = async (idToUse?: string, pinToUse?: string) => {
    const id = (idToUse || staffId).trim();
    const p = (pinToUse || pin).trim();

    if (!id || !p) {
      Alert.alert("Missing Input", "Please enter Staff ID and PIN");
      return;
    }

    setLoading(true);
    try {
      const session = await loginStaff({ staffId: id, pin: p });
      setStaff({
        staffId: session.staffId,
        name: session.name,
        role: session.role,
        zoneCoaches: session.zoneCoaches,
        token: session.token,
      });

      // Connect MQTT for incoming orders in assigned zone
      captainMqtt.connect(session.staffId);

      router.replace("/active");
    } catch (err: any) {
      Alert.alert("Login Failed", err.message || "Invalid credentials");
    } finally {
      setLoading(false);
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.badge}>IRCTC PANTRY CREW</Text>
      <Text style={styles.title}>Captain App</Text>
      <Text style={styles.subtitle}>Log in to receive loud instant alerts for your coach zone.</Text>

      <View style={styles.card}>
        <Text style={styles.label}>Staff ID</Text>
        <TextInput
          style={styles.input}
          placeholder="e.g. c1, c2"
          autoCapitalize="none"
          value={staffId}
          onChangeText={setStaffId}
        />

        <Text style={styles.label}>Security PIN</Text>
        <TextInput
          style={styles.input}
          placeholder="••••"
          secureTextEntry
          keyboardType="numeric"
          maxLength={8}
          value={pin}
          onChangeText={setPin}
        />

        <TouchableOpacity
          style={styles.loginBtn}
          onPress={() => handleLogin()}
          disabled={loading}
        >
          <Text style={styles.loginBtnText}>{loading ? "Verifying..." : "Sign In →"}</Text>
        </TouchableOpacity>
      </View>

      {/* Quick Switch for Demo Testing */}
      <Text style={styles.quickLabel}>Quick Demo Profiles:</Text>
      <View style={styles.quickGrid}>
        <TouchableOpacity
          style={styles.quickBtn}
          onPress={() => {
            setStaffId("c1");
            setPin("1234");
            handleLogin("c1", "1234");
          }}
        >
          <Text style={styles.quickBtnText}>Captain C1 (Coaches B1-B2)</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.quickBtn}
          onPress={() => {
            setStaffId("c2");
            setPin("1234");
            handleLogin("c2", "1234");
          }}
        >
          <Text style={styles.quickBtnText}>Captain C2 (Coaches B3-B4)</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.quickBtn}
          onPress={() => {
            setStaffId("c3");
            setPin("1234");
            handleLogin("c3", "1234");
          }}
        >
          <Text style={styles.quickBtnText}>Captain C3 (Coaches S1-S2)</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.quickBtn}
          onPress={() => {
            setStaffId("c4");
            setPin("1234");
            handleLogin("c4", "1234");
          }}
        >
          <Text style={styles.quickBtnText}>Captain C4 (Coaches S3-S4)</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 24,
    backgroundColor: "#064e3b",
    flexGrow: 1,
    justifyContent: "center",
  },
  badge: {
    color: "#6ee7b7",
    fontWeight: "bold",
    fontSize: 12,
    letterSpacing: 1.5,
    marginBottom: 4,
  },
  title: {
    fontSize: 32,
    fontWeight: "bold",
    color: "#ffffff",
    marginBottom: 6,
  },
  subtitle: {
    fontSize: 14,
    color: "#a7f3d0",
    marginBottom: 24,
    lineHeight: 20,
  },
  card: {
    backgroundColor: "#ffffff",
    borderRadius: 12,
    padding: 20,
    marginBottom: 24,
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
    marginBottom: 16,
  },
  loginBtn: {
    backgroundColor: "#059669",
    padding: 16,
    borderRadius: 8,
    alignItems: "center",
    marginTop: 8,
  },
  loginBtnText: {
    color: "#ffffff",
    fontSize: 16,
    fontWeight: "bold",
  },
  quickLabel: {
    color: "#a7f3d0",
    fontSize: 12,
    fontWeight: "bold",
    marginBottom: 10,
    textTransform: "uppercase",
  },
  quickGrid: {
    gap: 8,
  },
  quickBtn: {
    backgroundColor: "#065f46",
    padding: 12,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: "#047857",
  },
  quickBtnText: {
    color: "#ffffff",
    fontSize: 13,
    fontWeight: "600",
  },
});
