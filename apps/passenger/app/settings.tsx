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
import { saveServerUrlConfig } from "../src/net/server-url.js";
import { getApiBaseUrl } from "../src/net/config.js";
import { colors } from "../src/ui/theme.js";

export default function SettingsScreen() {
  const router = useRouter();
  const [url, setUrl] = useState(getApiBaseUrl());
  const [status, setStatus] = useState<"idle" | "ok" | "fail">("idle");

  const normalize = (raw: string): string => {
    const trimmed = raw.trim().replace(/\/+$/, "");
    if (!trimmed) return "";
    return /^https?:\/\//.test(trimmed) ? trimmed : `http://${trimmed}`;
  };

  const check = async () => {
    const normalized = normalize(url);
    if (!normalized) {
      setStatus("fail");
      return;
    }
    setUrl(normalized);
    setStatus("idle");
    try {
      const controller = new AbortController();
      const t = setTimeout(() => controller.abort(), 4000);
      const res = await fetch(`${normalized}/health`, { signal: controller.signal });
      clearTimeout(t);
      if (res.ok) {
        setStatus("ok");
      } else {
        setStatus("fail");
      }
    } catch {
      setStatus("fail");
    }
  };

  const handleSave = () => {
    const normalized = normalize(url);
    if (!normalized) {
      Alert.alert("Invalid URL", "Enter the server address, e.g. http://192.168.1.7:8080");
      return;
    }
    saveServerUrlConfig(normalized);
    Alert.alert("Saved", `Server set to ${normalized}. Reconnecting...`, [
      { text: "OK" },
      { text: "Go Home", onPress: () => router.replace("/(tabs)/home") },
    ]);
  };

  let statusLabel = "";
  let statusColor = colors.inkSoft;
  if (status === "ok") {
    statusLabel = "● Server reachable";
    statusColor = colors.primary;
  } else if (status === "fail") {
    statusLabel = "● Cannot reach server";
    statusColor = colors.danger;
  }

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={styles.backText}>‹ Back</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Settings</Text>
        <View style={{ width: 40 }} />
      </View>

      <View style={styles.card}>
        <Text style={styles.label}>Edge server URL</Text>
        <TextInput
          style={styles.input}
          placeholder="http://192.168.1.7:8080"
          autoCapitalize="none"
          autoCorrect={false}
          keyboardType="url"
          value={url}
          onChangeText={setUrl}
        />
        {statusLabel ? <Text style={[styles.statusText, { color: statusColor }]}>{statusLabel}</Text> : null}

        <View style={styles.row}>
          <TouchableOpacity style={styles.secondaryBtn} onPress={check}>
            <Text style={styles.secondaryText}>Test connection</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.primaryBtn} onPress={handleSave}>
            <Text style={styles.primaryText}>Save</Text>
          </TouchableOpacity>
        </View>

        <Text style={styles.hint}>
          Default comes from EXPO_PUBLIC_SERVER_URL at build time. MQTT (WebSocket, port 8883) and
          REST (port 8080) both use this host.
        </Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flexGrow: 1, backgroundColor: colors.bg },
  header: {
    backgroundColor: colors.primary,
    paddingHorizontal: 12,
    paddingTop: 12,
    paddingBottom: 14,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  backText: { color: "#ffffff", fontSize: 16, fontWeight: "bold" },
  headerTitle: { color: "#ffffff", fontSize: 17, fontWeight: "bold" },
  card: {
    backgroundColor: "#ffffff",
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 16,
    margin: 16,
  },
  label: { fontSize: 13, fontWeight: "bold", color: colors.ink, marginBottom: 8 },
  input: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 10,
    padding: 12,
    fontSize: 15,
    color: colors.ink,
  },
  statusText: { fontSize: 12, fontWeight: "600", marginTop: 8 },
  row: { flexDirection: "row", gap: 10, marginTop: 14 },
  secondaryBtn: {
    flex: 1,
    borderWidth: 1.5,
    borderColor: colors.primary,
    borderRadius: 10,
    paddingVertical: 12,
    alignItems: "center",
  },
  secondaryText: { color: colors.primary, fontWeight: "bold" },
  primaryBtn: {
    flex: 1,
    backgroundColor: colors.primary,
    borderRadius: 10,
    paddingVertical: 12,
    alignItems: "center",
  },
  primaryText: { color: "#ffffff", fontWeight: "bold" },
  hint: { fontSize: 12, color: colors.inkSoft, marginTop: 14, lineHeight: 18 },
});
