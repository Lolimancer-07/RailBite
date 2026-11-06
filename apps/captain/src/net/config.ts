export const captainNetConfig = {
  apiBaseUrl: "http://192.168.50.1:8080",
  mqttWsUrl: "ws://192.168.50.1:8883",
  tripId: "TRIP_001",
  timeoutMs: 5000,
};

let customApiUrl: string | null = null;

export function getApiBaseUrl(): string {
  return customApiUrl || captainNetConfig.apiBaseUrl;
}

export function setCustomApiUrl(url: string | null): void {
  customApiUrl = url;
}
