import { describe, it, expect } from "vitest";
import { normalizeTrainNumber, TrainNumberSchema } from "../schemas/order.js";
import { topics } from "../topics.js";

describe("topic builders and train normalization", () => {
  const t = topics("12345");

  it("normalizes arbitrary alphanumeric train numbers consistently", () => {
    expect(normalizeTrainNumber(" 88412 ")).toBe("88412");
    expect(normalizeTrainNumber("ab 123")).toBe("AB123");
    expect(TrainNumberSchema.parse("777")).toBe("777");
    expect(TrainNumberSchema.parse(" 98a7b ")).toBe("98A7B");
    expect(() => TrainNumberSchema.parse("")).toThrow();
    expect(() => TrainNumberSchema.parse("12345678901")).toThrow();
    expect(() => TrainNumberSchema.parse("12-34")).toThrow();
  });

  it("normalizes trainNumber in topic builders", () => {
    expect(topics(" 88412 ").ordersNew).toBe("rb/train/88412/orders/new");
    expect(topics("ab 9").captainAlerts("c1")).toBe("rb/train/AB9/captain/c1/alerts");
  });

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

