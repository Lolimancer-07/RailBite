import { describe, it, expect } from "vitest";
import { topics } from "../topics.js";

describe("topic builders", () => {
  const t = topics("TRIP123");

  it("builds ordersNew topic", () => {
    expect(t.ordersNew).toBe("rb/TRIP123/orders/new");
  });

  it("builds orderStatus topic", () => {
    expect(t.orderStatus("order-abc")).toBe("rb/TRIP123/orders/order-abc/status");
  });

  it("builds captainAlerts topic", () => {
    expect(t.captainAlerts("cap-1")).toBe("rb/TRIP123/captain/cap-1/alerts");
  });

  it("builds captainActions topic", () => {
    expect(t.captainActions("cap-1")).toBe("rb/TRIP123/captain/cap-1/actions");
  });

  it("builds kitchenQueue topic", () => {
    expect(t.kitchenQueue).toBe("rb/TRIP123/kitchen/queue");
  });

  it("builds kitchenActions topic", () => {
    expect(t.kitchenActions).toBe("rb/TRIP123/kitchen/actions");
  });

  it("builds complaintsNew topic", () => {
    expect(t.complaintsNew).toBe("rb/TRIP123/complaints/new");
  });

  it("builds complaintStatus topic", () => {
    expect(t.complaintStatus("comp-1")).toBe("rb/TRIP123/complaints/comp-1/status");
  });
});

