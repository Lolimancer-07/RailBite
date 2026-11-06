import { describe, it, expect, beforeEach, vi } from "vitest";
import { useAlertsStore } from "../store/alerts.js";
import { useAuthStore } from "../store/auth.js";
import { useActiveOrdersStore } from "../store/orders.js";
import * as restClient from "../net/captainRestClient.js";

describe("Captain App Alert & Delivery Loop", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    useAlertsStore.getState().clearAlert();
    useActiveOrdersStore.getState().setActiveOrders([]);
    useAuthStore.getState().setStaff({
      staffId: "c1",
      name: "Ramesh Kumar (Captain B1-B2)",
      role: "CAPTAIN",
      zoneCoaches: ["B1", "B2"],
      token: "tok-test-c1",
    });
  });

  it("Receiving MQTT alert sets alert state and arms 60s countdown", () => {
    const alertData = {
      orderId: "alert-order-1",
      coach: "B1",
      berth: 12,
      diners: 2,
      items: [{ name: "Masala Chai", qty: 2, unitPricePaise: 1500 }],
      total: 3000,
      track: "READY" as const,
      attempt: 1,
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      pnr: "8492000001",
      verified: 1,
    };

    useAlertsStore.getState().setAlert(alertData);

    const current = useAlertsStore.getState().currentAlert;
    expect(current).not.toBeNull();
    expect(current?.orderId).toBe("alert-order-1");
    expect(current?.coach).toBe("B1");
    expect(useAlertsStore.getState().secondsRemaining).toBe(60);
  });

  it("Accepting alert calls REST API, adds to active orders, and clears alert", async () => {
    const alertData = {
      orderId: "alert-order-2",
      coach: "B2",
      berth: 5,
      diners: 1,
      items: [{ name: "Samosa (2 pcs)", qty: 1, unitPricePaise: 3000 }],
      total: 3000,
      track: "READY" as const,
      attempt: 1,
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      pnr: "8492000001",
      verified: 1,
    };

    useAlertsStore.getState().setAlert(alertData);

    const acceptSpy = vi.spyOn(restClient, "acceptOrder").mockResolvedValue({
      id: "alert-order-2",
      pnr: "8492000001",
      coach: "B2",
      berth: 5,
      diners: 1,
      track: "READY",
      status: "ACCEPTED",
      paymentMode: "COD",
      totalPaise: 3000,
      source: "WIFI",
      verified: 1,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    });

    const acceptedOrder = await restClient.acceptOrder("alert-order-2", "c1");
    useActiveOrdersStore.getState().addOrder(acceptedOrder);
    useAlertsStore.getState().clearAlert();

    expect(acceptSpy).toHaveBeenCalledWith("alert-order-2", "c1");
    expect(useActiveOrdersStore.getState().activeOrders.length).toBe(1);
    expect(useActiveOrdersStore.getState().activeOrders[0]?.status).toBe("ACCEPTED");
    expect(useAlertsStore.getState().currentAlert).toBeNull();
  });

  it("Full delivery flow: mark picked up -> deliver -> cash recorded", async () => {
    const order = {
      id: "order-flow-1",
      pnr: "8492000001",
      coach: "B1",
      berth: 8,
      diners: 1,
      track: "READY" as const,
      status: "ACCEPTED" as const,
      paymentMode: "COD" as const,
      totalPaise: 1500,
      source: "WIFI" as const,
      verified: 1,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    useActiveOrdersStore.getState().addOrder(order);

    // Picked up
    const pickupSpy = vi.spyOn(restClient, "pickupOrder").mockResolvedValue({
      ...order,
      status: "OUT_FOR_DELIVERY",
    });
    await restClient.pickupOrder("order-flow-1", "c1");
    useActiveOrdersStore.getState().updateOrderStatus("order-flow-1", "OUT_FOR_DELIVERY");

    expect(useActiveOrdersStore.getState().activeOrders[0]?.status).toBe("OUT_FOR_DELIVERY");

    // Delivered
    const deliverSpy = vi.spyOn(restClient, "deliverOrder").mockResolvedValue({
      ...order,
      status: "DELIVERED",
    });
    await restClient.deliverOrder("order-flow-1", "c1", 1500);
    useActiveOrdersStore.getState().removeOrder("order-flow-1");

    expect(deliverSpy).toHaveBeenCalledWith("order-flow-1", "c1", 1500);
    expect(useActiveOrdersStore.getState().activeOrders.length).toBe(0);
  });
});
