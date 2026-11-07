import { getSetting, setSetting } from "../db/queries.js";
import { setCustomApiUrl, getApiBaseUrl } from "./config.js";

export const SERVER_URL_KEY = "server_url";

export function loadSavedServerUrl(): string | null {
  const saved = getSetting(SERVER_URL_KEY);
  if (saved && /^(http|ws):\/\//.test(saved)) {
    setCustomApiUrl(saved);
    return saved;
  }
  setCustomApiUrl(getApiBaseUrl());
  return null;
}

export function saveServerUrlConfig(url: string, _key?: string): void {
  setSetting(SERVER_URL_KEY, url);
  setCustomApiUrl(url);
}
