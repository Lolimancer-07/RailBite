/**
 * Picker app network config.
 * Default: EXPO_PUBLIC_SERVER_URL env, override saved locally (src/db/kv.ts) & editable at login.
 */

function defaultServerUrl(): string {
  const envUrl = process.env.EXPO_PUBLIC_SERVER_URL;
  if (envUrl && /^(http|ws):\/\//.test(envUrl)) return envUrl;
  return "https://railbite-api.onrender.com";
}

const DEFAULT_API_BASE_URL = defaultServerUrl();

/** Derive the MQTT WebSocket URL from an API base URL (http://host:8080 -> ws://host:8080/mqtt, https://host -> wss://host/mqtt) */
export function deriveMqttWsUrl(apiBaseUrl: string): string {
  try {
    const url = new URL(apiBaseUrl);
    const proto = url.protocol === "https:" ? "wss:" : "ws:";
    return `${proto}//${url.host}/mqtt`;
  } catch {
    return "ws://localhost:8080/mqtt";
  }
}

export const captainNetConfig = {
  apiBaseUrl: DEFAULT_API_BASE_URL,
  mqttWsUrl: deriveMqttWsUrl(DEFAULT_API_BASE_URL),
  tripId: process.env.EXPO_PUBLIC_TRIP_ID || "TRIP_001",
  trainNumber: process.env.EXPO_PUBLIC_TRAIN_NUMBER || "12345",
  timeoutMs: 5000,
};

let customApiUrl: string | null = null;

export function getApiBaseUrl(): string {
  return customApiUrl || captainNetConfig.apiBaseUrl;
}

export function setCustomApiUrl(url: string | null): void {
  customApiUrl = url;
}

export function getMqttWsUrl(): string {
  return deriveMqttWsUrl(getApiBaseUrl());
}
