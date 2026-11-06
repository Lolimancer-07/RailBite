// STRETCH: SMS token encode/decode with HMAC. Stubbed for MVP.
export const SMS_TOKEN_PREFIX = "RB";

export interface SmsTokenData {
  pnrLast4: string;
  coach: string;
  berth: number;
  diners: number;
  itemCodes: string;
  nonce: string;
}

/** Item short codes for SMS compression */
export const ITEM_SHORT_CODES: Record<string, string> = {
  CH: "chai",
  CO: "coffee",
  BI: "biscuits",
  CH2: "chips",
  WA: "water",
  SA: "samosa",
  PB: "packed_biryani",
  TH: "thali",
  PA: "paratha",
  OM: "omelette",
};

/** Stub: encode order as SMS token */
export function encodeSmsToken(_data: SmsTokenData, _secret: string): string {
  throw new Error("SMS token encoding is STRETCH - not yet implemented");
}

/** Stub: decode and verify SMS token */
export function decodeSmsToken(_token: string, _secret: string): SmsTokenData {
  throw new Error("SMS token decoding is STRETCH - not yet implemented");
}

