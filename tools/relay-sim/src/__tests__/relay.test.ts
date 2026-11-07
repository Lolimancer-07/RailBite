import { describe, it, expect } from "vitest";
import { TrainRakeNetwork, CoachNode, type RelayPacket } from "../relay.js";

describe("Phase 11: Device-to-Device Relay Simulation (STRETCH)", () => {
  it("Scenario A: All nodes online -> message travels from Coach 24 to Pantry Coach 12 (12 hops)", () => {
    const network = new TrainRakeNetwork(24, 12);
    const result = network.simulateOrderTransmission(24, "ord-test-001", 16);

    expect(result.delivered).toBe(true);
    expect(result.reachedPantry).toBe(true);
    expect(result.hopCount).toBe(12); // Distance between coach 24 and coach 12
    expect(result.path.length).toBe(12);
  });

  it("Scenario B: 2 broken adjacent nodes partition a standard 1-hop BLE network", () => {
    const network = new TrainRakeNetwork(24, 12);
    network.maxBleRangeHops = 1;

    // Disconnect Coach 18 and 19
    network.getNode(18)!.isOnline = false;
    network.getNode(19)!.isOnline = false;

    // Message originating at Coach 24 cannot cross the gap to Coach 12
    const result = network.simulateOrderTransmission(24, "ord-test-partition", 16);
    expect(result.delivered).toBe(false);
    expect(result.reachedPantry).toBe(false);
  });

  it("Scenario B: ESP32 extended range (2-hop) successfully bridges a single dead coach node", () => {
    const network = new TrainRakeNetwork(24, 12);
    network.maxBleRangeHops = 2; // Coded PHY / High power antenna

    // Disconnect single Coach 18
    network.getNode(18)!.isOnline = false;

    // Coach 19 can bridge directly to Coach 17
    const result = network.simulateOrderTransmission(24, "ord-test-bridged", 16);
    expect(result.delivered).toBe(true);
    expect(result.reachedPantry).toBe(true);
  });

  it("Scenario C: LRU deduplication cache prevents broadcast storms and loops", () => {
    const network = new TrainRakeNetwork(24, 12);
    const node = new CoachNode("TEST_NODE", 5, false, true);

    const packet: RelayPacket = {
      v: 1,
      orderId: "dedup-order-123",
      ttl: 5,
      origin: "OTHER_NODE",
      payloadB64: "dGVzdA==",
      hmac8: "abcd1234",
      hops: ["NODE_1", "NODE_2"],
    };

    // First arrival accepted
    const firstReceive = node.receive(packet, network);
    expect(firstReceive).toBe(true);
    expect(node.lruCache.has("dedup-order-123")).toBe(true);

    // Second arrival of identical orderId dropped immediately
    const duplicateReceive = node.receive(packet, network);
    expect(duplicateReceive).toBe(false);
  });

  it("TTL decrement: Packet drops when TTL expires before reaching destination", () => {
    const network = new TrainRakeNetwork(24, 12);
    // Distance from coach 24 to 12 is 12 hops. TTL of only 5 will expire before reaching pantry
    const result = network.simulateOrderTransmission(24, "ord-expired", 5);

    expect(result.delivered).toBe(false);
    expect(result.reachedPantry).toBe(false);
  });
});

