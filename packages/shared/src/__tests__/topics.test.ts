import { describe, it, expect } from "vitest";
import { topics } from "../topics.js";

describe("topic builders", () => {
  const t = topics("12345");

  it("builds ordersNew topic", () => {
    expect(t.ordersNew).toBe("rb/train/12345/orders/new");
  });

  it("builds orderStatus topic", () => {
    expect(t.orderStatus("order-abc")).toBe("rb/train/12345/orders/order-abc/status");
  });

  it("builds orderChat topic", () => {
    expect(t.orderChat("order-abc")).toBe("rb/train/12345/orders/order-abc/chat");
  });

  it("builds captainAlerts topic", () => {
    expect(t.captainAlerts("cap-1")).toBe("rb/train/12345/captain/cap-1/alerts");
  });

  it("builds captainActions topic", () => {
    expect(t.captainActions("cap-1")).toBe("rb/train/12345/captain/cap-1/actions");
  });

  it("builds kitchenQueue topic", () => {
    expect(t.kitchenQueue).toBe("rb/train/12345/kitchen/queue");
  });

  it("builds kitchenActions topic", () => {
    expect(t.kitchenActions).toBe("rb/train/12345/kitchen/actions");
  });

  it("builds complaintsNew topic", () => {
    expect(t.complaintsNew).toBe("rb/train/12345/complaints/new");
  });

  it("builds complaintStatus topic", () => {
    expect(t.complaintStatus("comp-1")).toBe("rb/train/12345/complaints/comp-1/status");
  });
});

