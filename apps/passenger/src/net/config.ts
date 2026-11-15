/**
 * Passenger app network config.
 *
 * Default server URL can come from the environment (.env / app config), e.g.
 *   EXPO_PUBLIC_SERVER_URL=http://192.168.1.7:8080
 * The passenger can override it in the Settings screen; the override is
 * persisted locally (apps/passenger/src/db/queries.ts settings table).
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

export const netConfig = {
  apiBaseUrl: DEFAULT_API_BASE_URL,
  mqttWsUrl: deriveMqttWsUrl(DEFAULT_API_BASE_URL),
  tripId: process.env.EXPO_PUBLIC_TRIP_ID || "TRIP_001",
  timeoutMs: 5000,
};

let customApiUrl: string | null = null;

export function getApiBaseUrl(): string {
  return customApiUrl || netConfig.apiBaseUrl;
}

export function setCustomApiUrl(url: string | null): void {
  customApiUrl = url;
}

export function getMqttWsUrl(): string {
  return deriveMqttWsUrl(getApiBaseUrl());
}
