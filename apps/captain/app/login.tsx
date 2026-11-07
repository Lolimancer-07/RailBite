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
import { getApiBaseUrl, setCustomApiUrl } from "../src/net/config.js";
import { setSetting } from "../src/db/kv.js";

const SERVER_URL_KEY = "server_url";

export default function LoginScreen() {
  const router = useRouter();
  const setStaff = useAuthStore((s) => s.setStaff);

  const [staffId, setStaffId] = useState("c1");
  const [pin, setPin] = useState("1234");
  const [serverUrl, setServerUrl] = useState(getApiBaseUrl());
  const [loading, setLoading] = useState(false);

  const normalizeUrl = (raw: string): string => {
    const trimmed = raw.trim().replace(/\/+$/, "");
    if (!trimmed) return getApiBaseUrl();
    return /^https?:\/\//.test(trimmed) ? trimmed : `http://${trimmed}`;
  };

  const handleLogin = async (idToUse?: string, pinToUse?: string) => {
    const id = (idToUse || staffId).trim();
    const p = (pinToUse || pin).trim();

    if (!id || !p) {
      Alert.alert("Missing Input", "Please enter Staff ID and PIN");
      return;
    }

    setLoading(true);
    try {
      // Apply (and persist) the server URL before logging in
      const finalUrl = normalizeUrl(serverUrl);
      setCustomApiUrl(finalUrl);
      setSetting(SERVER_URL_KEY, finalUrl);

      const session = await loginStaff({ staffId: id, pin: p });
      setStaff({
        staffId: session.staffId,
        name: session.name,
        role: session.role,
        zoneCoaches: session.zoneCoaches,
        token: session.token,
      });

      // Connect MQTT for incoming order alerts in assigned zone
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
      <Text style={styles.badge}>RAILBITE PANTRY CREW</Text>
      <Text style={styles.title}>Picker App</Text>
      <Text style={styles.subtitle}>
        Log in to get loud instant alerts when a passenger order lands in your zone.
      </Text>

      <View style={styles.card}>
        <Text style={styles.label}>Edge server URL</Text>
        <TextInput
          style={styles.inputSmall}
          placeholder="http://192.168.1.7:8080"
          autoCapitalize="none"
          autoCorrect={false}
          value={serverUrl}
          onChangeText={setServerUrl}
        />

        <Text style={styles.label}>Staff ID</Text>
        <TextInput
          style={styles.input}
          placeholder="e.g. c1, c2"
          autoCapitalize="none"
          value={staffId}
          onChangeText={setStaffId}
        />

        <Text style={styles.label}>4-digit PIN</Text>
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
      <Text style={styles.quickLabel}>Quick demo profiles (PIN 1234):</Text>
      <View style={styles.quickGrid}>
        <TouchableOpacity
          style={styles.quickBtn}
          onPress={() => {
            setStaffId("c1");
            setPin("1234");
            handleLogin("c1", "1234");
          }}
        >
          <Text style={styles.quickBtnText}>Picker C1 (B1-B2)</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.quickBtn}
          onPress={() => {
            setStaffId("c2");
            setPin("1234");
            handleLogin("c2", "1234");
          }}
        >
          <Text style={styles.quickBtnText}>Picker C2 (B3-B4)</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.quickBtn}
          onPress={() => {
            setStaffId("c3");
            setPin("1234");
            handleLogin("c3", "1234");
          }}
        >
          <Text style={styles.quickBtnText}>Picker C3 (S1-S2)</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.quickBtn}
          onPress={() => {
            setStaffId("c4");
            setPin("1234");
            handleLogin("c4", "1234");
          }}
        >
          <Text style={styles.quickBtnText}>Picker C4 (S3-S4)</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 24,
    backgroundColor: "#0b6b3a",
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
  inputSmall: {
    borderWidth: 1,
    borderColor: "#cbd5e1",
    borderRadius: 8,
    padding: 10,
    fontSize: 13,
    marginBottom: 16,
  },
  loginBtn: {
    backgroundColor: "#0f8a4b",
    padding: 16,
    borderRadius: 10,
    alignItems: "center",
  },
  loginBtnText: {
    color: "#ffffff",
    fontWeight: "bold",
    fontSize: 16,
  },
  quickLabel: {
    color: "#a7f3d0",
    fontSize: 12,
    fontWeight: "bold",
    textTransform: "uppercase",
    marginBottom: 8,
  },
  quickGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  quickBtn: {
    backgroundColor: "#065f46",
    borderWidth: 1,
    borderColor: "#10b981",
    borderRadius: 8,
    paddingVertical: 8,
    paddingHorizontal: 12,
  },
  quickBtnText: {
    color: "#d1fae5",
    fontSize: 12,
    fontWeight: "600",
  },
});
