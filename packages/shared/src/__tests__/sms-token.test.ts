import { describe, it, expect } from "vitest";
import {
  encodeSmsToken,
  decodeSmsToken,
  formatSmsAck,
  parseSmsAck,
  parseItemCodes,
  encodeItemCodes,
  ITEM_SHORT_CODES,
  computeHmac8,
} from "../sms-token.js";

describe("SMS Token Encoding and Decoding", () => {
  const secret = "test_trip_hmac_secret_42";

  it("encodes and decodes a valid SMS token with matching HMAC", () => {
    const original = {
      pnrLast4: "4829",
      coach: "B2",
      berth: 14,
      diners: 2,
      itemCodes: "CH1,SA2",
      nonce: "7f3a",
    };

    const token = encodeSmsToken(original, secret);
    expect(token).toMatch(/^RB#4829#B2-14#2#CH1,SA2#7f3a#[0-9a-f]{8}$/);

    const decoded = decodeSmsToken(token, secret);
    expect(decoded.pnrLast4).toBe("4829");
    expect(decoded.coach).toBe("B2");
    expect(decoded.berth).toBe(14);
    expect(decoded.diners).toBe(2);
    expect(decoded.nonce).toBe("7f3a");
    expect(decoded.items).toEqual([
      { itemId: ITEM_SHORT_CODES.CH, qty: 1 },
      { itemId: ITEM_SHORT_CODES.SA, qty: 2 },
    ]);
  });

  it("rejects tampered token with invalid HMAC", () => {
    const token = encodeSmsToken(
      {
        pnrLast4: "4829",
        coach: "B2",
        berth: 14,
        diners: 2,
        itemCodes: "CH1,SA2",
        nonce: "7f3a",
      },
      secret
    );

    // Tamper coach from B2 to B3
    const tampered = token.replace("B2-14", "B3-14");
    expect(() => decodeSmsToken(tampered, secret)).toThrow(/Invalid SMS HMAC/);
  });

  it("rejects token with wrong secret", () => {
    const token = encodeSmsToken(
      {
        pnrLast4: "1234",
        coach: "S1",
        berth: 40,
        diners: 1,
        itemCodes: "WA1",
        nonce: "abcd",
      },
      secret
    );

    expect(() => decodeSmsToken(token, "wrong_secret")).toThrow(/Invalid SMS HMAC/);
  });

  it("correctly encodes and parses item codes", () => {
    const items = [
      { itemId: ITEM_SHORT_CODES.CH!, qty: 2 },
      { itemId: ITEM_SHORT_CODES.VT!, qty: 1 },
    ];
    const encoded = encodeItemCodes(items);
    expect(encoded).toBe("CH2,VT1");

    const parsed = parseItemCodes(encoded);
    expect(parsed).toEqual(items);
  });

  it("encodes and parses SMS ACK token", () => {
    const ack = formatSmsAck("84920000-0000-0000-0000-000000123456", 11000, 15);
    expect(ack).toBe("RB#ACK#123456#11000#15");

    const parsed = parseSmsAck(ack);
    expect(parsed.orderIdLast6).toBe("123456");
    expect(parsed.totalPaise).toBe(11000);
    expect(parsed.etaMin).toBe(15);
  });
});
