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

export async function getOrderById(orderId: string): Promise<Order & { items?: any[] }> {
  const url = `${getApiBaseUrl()}/orders/${orderId}`;
  const res = await fetchWithTimeout(url);
  if (!res.ok) {
    throw new Error(`Failed to fetch order: ${res.statusText}`);
  }
  return res.json();
}

export async function startPacking(orderId: string, captainId: string): Promise<Order> {
  const url = `${getApiBaseUrl()}/orders/${orderId}/packing`;
  const res = await fetchWithTimeout(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ captainId }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || `Start packing failed: ${res.statusText}`);
  }
  return res.json();
}

export async function setPickerOnline(staffId: string, online: boolean): Promise<void> {
  const url = `${getApiBaseUrl()}/staff/${staffId}/online`;
  const res = await fetchWithTimeout(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ online }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || `Failed to set online: ${res.statusText}`);
  }
}

export interface PickerStats {
  staffId: string;
  staffName: string;
  date: string;
  ordersDelivered: number;
  expectedPaise: number;
  collectedPaise: number;
  passengerConfirmedPaise: number;
}

export async function getPickerStats(staffId: string): Promise<PickerStats> {
  const url = `${getApiBaseUrl()}/staff/${staffId}/stats`;
  const res = await fetchWithTimeout(url);
  if (!res.ok) {
    throw new Error(`Failed to get picker stats: ${res.statusText}`);
  }
  return res.json();
}

