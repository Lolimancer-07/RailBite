import { getApiBaseUrl, netConfig } from "./config.js";
import type { MenuItem, Order, OrderCreate, ComplaintCreate, Complaint } from "@railbite/shared";

async function fetchWithTimeout(url: string, options: RequestInit = {}): Promise<Response> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), netConfig.timeoutMs);
  try {
    const res = await fetch(url, { ...options, signal: controller.signal });
    return res;
  } finally {
    clearTimeout(timeoutId);
  }
}

export async function fetchMenu(): Promise<MenuItem[]> {
  const url = `${getApiBaseUrl()}/menu`;
  const res = await fetchWithTimeout(url);
  if (!res.ok) {
    throw new Error(`Failed to fetch menu: ${res.statusText}`);
  }
  return res.json();
}

export async function postOrder(order: OrderCreate): Promise<Order> {
  const url = `${getApiBaseUrl()}/orders`;
  const res = await fetchWithTimeout(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(order),
  });
  if (!res.ok) {
    const errorBody = await res.json().catch(() => ({}));
    throw new Error(errorBody.error || `Order failed: ${res.statusText}`);
  }
  return res.json();
}

export async function getOrder(id: string): Promise<Order & { items: any[] }> {
  const url = `${getApiBaseUrl()}/orders/${id}`;
  const res = await fetchWithTimeout(url);
  if (!res.ok) {
    throw new Error(`Failed to get order: ${res.statusText}`);
  }
  return res.json();
}

export async function getOrdersByPnr(pnr: string): Promise<Order[]> {
  const url = `${getApiBaseUrl()}/orders?pnr=${encodeURIComponent(pnr)}`;
  const res = await fetchWithTimeout(url);
  if (!res.ok) {
    throw new Error(`Failed to get orders: ${res.statusText}`);
  }
  return res.json();
}

export async function cancelOrder(id: string): Promise<Order> {
  const url = `${getApiBaseUrl()}/orders/${id}/cancel`;
  const res = await fetchWithTimeout(url, { method: "POST" });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || `Cancel failed: ${res.statusText}`);
  }
  return res.json();
}

export async function confirmCashPayment(id: string): Promise<{ ok: boolean; orderId: string; passengerConfirmed: boolean }> {
  const url = `${getApiBaseUrl()}/orders/${id}/confirm-cash`;
  const res = await fetchWithTimeout(url, { method: "POST" });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || `Confirm cash failed: ${res.statusText}`);
  }
  return res.json();
}

export async function postComplaint(complaint: ComplaintCreate): Promise<Complaint> {
  const url = `${getApiBaseUrl()}/complaints`;
  const res = await fetchWithTimeout(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(complaint),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || `Complaint failed: ${res.statusText}`);
  }
  return res.json();
}
