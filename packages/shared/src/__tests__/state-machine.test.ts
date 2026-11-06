import { describe, it, expect } from "vitest";
import { canTransition, determineTrack } from "../state-machine.js";
import { OrderStatus, Track } from "../enums.js";

describe("READY track transitions", () => {
  it("allows PLACED -> ASSIGNED", () => {
    expect(canTransition(OrderStatus.PLACED, OrderStatus.ASSIGNED, Track.READY)).toBe(true);
  });
  it("allows ASSIGNED -> ACCEPTED", () => {
    expect(canTransition(OrderStatus.ASSIGNED, OrderStatus.ACCEPTED, Track.READY)).toBe(true);
  });
  it("allows ACCEPTED -> OUT_FOR_DELIVERY", () => {
    expect(canTransition(OrderStatus.ACCEPTED, OrderStatus.OUT_FOR_DELIVERY, Track.READY)).toBe(true);
  });
  it("allows OUT_FOR_DELIVERY -> DELIVERED", () => {
    expect(canTransition(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, Track.READY)).toBe(true);
  });
  it("allows PLACED -> CANCELLED", () => {
    expect(canTransition(OrderStatus.PLACED, OrderStatus.CANCELLED, Track.READY)).toBe(true);
  });
  it("allows ASSIGNED -> CANCELLED", () => {
    expect(canTransition(OrderStatus.ASSIGNED, OrderStatus.CANCELLED, Track.READY)).toBe(true);
  });
  it("allows ASSIGNED -> EXPIRED", () => {
    expect(canTransition(OrderStatus.ASSIGNED, OrderStatus.EXPIRED, Track.READY)).toBe(true);
  });
  it("rejects PLACED -> DELIVERED", () => {
    expect(canTransition(OrderStatus.PLACED, OrderStatus.DELIVERED, Track.READY)).toBe(false);
  });
  it("rejects ACCEPTED -> CANCELLED (after ACCEPTED)", () => {
    expect(canTransition(OrderStatus.ACCEPTED, OrderStatus.CANCELLED, Track.READY)).toBe(false);
  });
  it("rejects DELIVERED -> PLACED", () => {
    expect(canTransition(OrderStatus.DELIVERED, OrderStatus.PLACED, Track.READY)).toBe(false);
  });
  it("rejects PLACED -> COOKING on READY track", () => {
    expect(canTransition(OrderStatus.PLACED, OrderStatus.COOKING, Track.READY)).toBe(false);
  });
});

describe("COOK track transitions", () => {
  it("allows PLACED -> COOKING", () => {
    expect(canTransition(OrderStatus.PLACED, OrderStatus.COOKING, Track.COOK)).toBe(true);
  });
  it("allows COOKING -> READY", () => {
    expect(canTransition(OrderStatus.COOKING, OrderStatus.READY, Track.COOK)).toBe(true);
  });
  it("allows READY -> ASSIGNED", () => {
    expect(canTransition(OrderStatus.READY, OrderStatus.ASSIGNED, Track.COOK)).toBe(true);
  });
  it("allows ASSIGNED -> ACCEPTED", () => {
    expect(canTransition(OrderStatus.ASSIGNED, OrderStatus.ACCEPTED, Track.COOK)).toBe(true);
  });
  it("allows ACCEPTED -> OUT_FOR_DELIVERY", () => {
    expect(canTransition(OrderStatus.ACCEPTED, OrderStatus.OUT_FOR_DELIVERY, Track.COOK)).toBe(true);
  });
  it("allows OUT_FOR_DELIVERY -> DELIVERED", () => {
    expect(canTransition(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, Track.COOK)).toBe(true);
  });
  it("allows PLACED -> CANCELLED", () => {
    expect(canTransition(OrderStatus.PLACED, OrderStatus.CANCELLED, Track.COOK)).toBe(true);
  });
  it("allows ASSIGNED -> EXPIRED", () => {
    expect(canTransition(OrderStatus.ASSIGNED, OrderStatus.EXPIRED, Track.COOK)).toBe(true);
  });
  it("rejects PLACED -> ASSIGNED on COOK track (must go COOKING first)", () => {
    expect(canTransition(OrderStatus.PLACED, OrderStatus.ASSIGNED, Track.COOK)).toBe(false);
  });
  it("rejects COOKING -> DELIVERED (skip steps)", () => {
    expect(canTransition(OrderStatus.COOKING, OrderStatus.DELIVERED, Track.COOK)).toBe(false);
  });
  it("rejects DELIVERED -> any on COOK track", () => {
    expect(canTransition(OrderStatus.DELIVERED, OrderStatus.PLACED, Track.COOK)).toBe(false);
    expect(canTransition(OrderStatus.DELIVERED, OrderStatus.COOKING, Track.COOK)).toBe(false);
  });
});

describe("determineTrack", () => {
  it("returns READY when all items are READY", () => {
    expect(determineTrack(["READY", "READY"])).toBe(Track.READY);
  });
  it("returns COOK when any item is COOK", () => {
    expect(determineTrack(["READY", "COOK"])).toBe(Track.COOK);
  });
  it("returns COOK when all items are COOK", () => {
    expect(determineTrack(["COOK", "COOK"])).toBe(Track.COOK);
  });
  it("returns READY for single READY item", () => {
    expect(determineTrack(["READY"])).toBe(Track.READY);
  });
});

