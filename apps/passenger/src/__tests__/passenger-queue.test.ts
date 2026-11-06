import { describe, it, expect, beforeEach, vi } from "vitest";

vi.mock("expo-sqlite", () => ({
  openDatabaseSync: () => null,
}));

import {
  saveLocalOrder,
  getLocalOrder,
  queueAction,
  getPendingActions,
  countPendingActions,
  updateLocalOrderStatus,
  updateActionAttempt,
} from "../db/queries.js";
import { processQueue } from "../sync/queueWorker.js";
import * as restClient from "../net/restClient.js";
import { useConnectionStore } from "../store/connection.js";

describe("Passenger App Offline-First Queue (Write-Then-Send)", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    useConnectionStore.getState().setStatus("Connected");
    useConnectionStore.getState().setQueuedCount(0);
  });

  it("Writing order to local SQLite works immediately when offline", () => {
    const orderId = "test-order-offline-1";
    const now = new Date().toISOString();

    const orderPayload = {
      id: orderId,
      pnr: "8492000001",
      coach: "B1",
      berth: 1,
      diners: 1,
      paymentMode: "COD" as const,
      items: [{ itemId: "item-1", qty: 2 }],
      createdAt: now,
      source: "WIFI" as const,
    };

    // 1. Write order locally first
    saveLocalOrder(
      {
        id: orderId,
        pnr: "8492000001",
        coach: "B1",
        berth: 1,
        diners: 1,
        track: "READY",
        status: "PLACED",
        paymentMode: "COD",
        totalPaise: 3000,
        source: "WIFI",
        verified: 1,
        createdAt: now,
        updatedAt: now,
      },
      [{ itemId: "item-1", qty: 2, unitPricePaise: 1500, name: "Masala Chai" }],
    );

    // 2. Queue in outbox
    queueAction(orderId, "PLACE_ORDER", orderPayload);

    // 3. Verify order exists locally
    const local = getLocalOrder(orderId);
    expect(local).not.toBeNull();
    expect(local?.id).toBe(orderId);
    expect(local?.status).toBe("PLACED");
    expect(local?.items.length).toBe(1);

    // 4. Verify action is queued
    const pending = getPendingActions();
    expect(pending.some((p) => p.id === orderId)).toBe(true);
  });

  it("Killing Wi-Fi then restoring it delivers exactly one order", async () => {
    const orderId = "test-order-wifi-drop-1";
    const now = new Date().toISOString();

    const orderPayload = {
      id: orderId,
      pnr: "8492000001",
      coach: "B1",
      berth: 1,
      diners: 1,
      paymentMode: "COD" as const,
      items: [{ itemId: "item-1", qty: 1 }],
      createdAt: now,
      source: "WIFI" as const,
    };

    saveLocalOrder(
      {
        id: orderId,
        pnr: "8492000001",
        coach: "B1",
        berth: 1,
        diners: 1,
        track: "READY",
        status: "PLACED",
        paymentMode: "COD",
        totalPaise: 1500,
        source: "WIFI",
        verified: 1,
        createdAt: now,
        updatedAt: now,
      },
      [],
    );
    queueAction(orderId, "PLACE_ORDER", orderPayload);

    // SIMULATE WI-FI OFF: network throws TypeError: Failed to fetch
    const postSpy = vi.spyOn(restClient, "postOrder").mockRejectedValue(new Error("Network offline"));

    // Attempt to process queue while offline
    await processQueue();

    // Still in queue with attempts recorded
    expect(countPendingActions()).toBeGreaterThanOrEqual(1);
    expect(useConnectionStore.getState().status).toBe("Offline");

    // SIMULATE WI-FI RESTORED: network succeeds
    postSpy.mockResolvedValue({
      id: orderId,
      pnr: "8492000001",
      coach: "B1",
      berth: 1,
      diners: 1,
      track: "READY",
      status: "ASSIGNED",
      paymentMode: "COD",
      totalPaise: 1500,
      source: "WIFI",
      verified: 1,
      createdAt: now,
      updatedAt: new Date().toISOString(),
    });

    // Reset next_try_at for immediate processing
    updateActionAttempt(orderId, 0, new Date(Date.now() - 1000).toISOString());

    await processQueue();

    // Now queue should be empty and connection restored
    const local = getLocalOrder(orderId);
    expect(local?.status).toBe("ASSIGNED");
    expect(useConnectionStore.getState().status).toBe("Connected");
  });

  it("Updating status via MQTT push updates local SQLite state", () => {
    const orderId = "test-order-mqtt-status-1";
    const now = new Date().toISOString();

    saveLocalOrder(
      {
        id: orderId,
        pnr: "8492000001",
        coach: "B1",
        berth: 1,
        diners: 1,
        track: "READY",
        status: "ASSIGNED",
        paymentMode: "COD",
        totalPaise: 1500,
        source: "WIFI",
        verified: 1,
        createdAt: now,
        updatedAt: now,
      },
      [],
    );

    // Simulate incoming MQTT status message: ACCEPTED
    const acceptTime = new Date().toISOString();
    updateLocalOrderStatus(orderId, "ACCEPTED" as any, acceptTime);

    const updated = getLocalOrder(orderId);
    expect(updated?.status).toBe("ACCEPTED");
    expect(updated?.updatedAt).toBe(acceptTime);

    // Simulate incoming MQTT status message: DELIVERED
    const deliverTime = new Date().toISOString();
    updateLocalOrderStatus(orderId, "DELIVERED" as any, deliverTime);

    const delivered = getLocalOrder(orderId);
    expect(delivered?.status).toBe("DELIVERED");
  });
});
