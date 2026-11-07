import { getApiBaseUrl, captainNetConfig } from "./config.js";
import type { Order, StaffLogin } from "@railbite/shared";

async function fetchWithTimeout(url: string, options: RequestInit = {}): Promise<Response> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), captainNetConfig.timeoutMs);
  try {
    const res = await fetch(url, { ...options, signal: controller.signal });
    return res;
  } finally {
    clearTimeout(timeoutId);
  }
}

export async function loginStaff(creds: StaffLogin) {
  const url = `${getApiBaseUrl()}/staff/login`;
  const res = await fetchWithTimeout(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(creds),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || `Login failed: ${res.statusText}`);
  }
  return res.json();
}

export async function acceptOrder(orderId: string, captainId: string): Promise<Order> {
  const url = `${getApiBaseUrl()}/orders/${orderId}/accept`;
  const res = await fetchWithTimeout(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ captainId }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || `Accept failed: ${res.statusText}`);
  }
  return res.json();
}

export async function rejectOrder(orderId: string, captainId: string): Promise<void> {
  const url = `${getApiBaseUrl()}/orders/${orderId}/reject`;
  const res = await fetchWithTimeout(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ captainId }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || `Reject failed: ${res.statusText}`);
  }
}

export async function pickupOrder(orderId: string, captainId: string): Promise<Order> {
  const url = `${getApiBaseUrl()}/orders/${orderId}/pickup`;
  const res = await fetchWithTimeout(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ captainId }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || `Pickup failed: ${res.statusText}`);
  }
  return res.json();
}

export async function deliverOrder(
  orderId: string,
  captainId: string,
  cashPaise?: number,
): Promise<Order> {
  const url = `${getApiBaseUrl()}/orders/${orderId}/deliver`;
  const res = await fetchWithTimeout(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ captainId, cashPaise }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || `Deliver failed: ${res.statusText}`);
  }
  return res.json();
}

export async function getLedgerSummary(): Promise<any[]> {
  const url = `${getApiBaseUrl()}/ledger/summary`;
  const res = await fetchWithTimeout(url);
  if (!res.ok) {
    throw new Error(`Failed to get ledger summary: ${res.statusText}`);
  }
  return res.json();
}

export async function getComplaints(): Promise<any[]> {
  const url = `${getApiBaseUrl()}/complaints`;
  const res = await fetchWithTimeout(url);
  if (!res.ok) {
    throw new Error(`Failed to get complaints: ${res.statusText}`);
  }
  return res.json();
}

