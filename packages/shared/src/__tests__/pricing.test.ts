import { describe, it, expect } from "vitest";
import { computeTotal } from "../pricing.js";

function makeMenu(items: Array<{ id: string; mrpPaise: number }>) {
  return new Map(items.map((i) => [i.id, { mrpPaise: i.mrpPaise }]));
}

describe("computeTotal", () => {
  it("computes total in integer paise", () => {
    const menu = makeMenu([
      { id: "chai-id", mrpPaise: 1500 },
      { id: "thali-id", mrpPaise: 8000 },
    ]);
    const result = computeTotal(
      [
        { itemId: "chai-id", qty: 2 },
        { itemId: "thali-id", qty: 1 },
      ],
      menu,
    );
    expect(result.totalPaise).toBe(3000 + 8000);
    expect(result.lineItems[0]?.linePaise).toBe(3000);
    expect(result.lineItems[1]?.linePaise).toBe(8000);
  });

  it("handles single item", () => {
    const menu = makeMenu([{ id: "water-id", mrpPaise: 2000 }]);
    const result = computeTotal([{ itemId: "water-id", qty: 3 }], menu);
    expect(result.totalPaise).toBe(6000);
  });

  it("throws for unknown item", () => {
    const menu = makeMenu([]);
    expect(() => computeTotal([{ itemId: "unknown", qty: 1 }], menu)).toThrow();
  });

  it("never uses floats - all values are integers", () => {
    const menu = makeMenu([{ id: "item", mrpPaise: 1333 }]);
    const result = computeTotal([{ itemId: "item", qty: 3 }], menu);
    expect(Number.isInteger(result.totalPaise)).toBe(true);
    expect(result.totalPaise).toBe(3999);
  });
});

