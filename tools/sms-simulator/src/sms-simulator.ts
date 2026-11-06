import { encodeSmsToken, parseSmsAck } from "@railbite/shared";

interface SmsSimOptions {
  serverUrl: string;
  secret: string;
}

const DEFAULT_OPTIONS: SmsSimOptions = {
  serverUrl: "http://127.0.0.1:8080",
  secret: "railbite_secret_pantry_trip_2026",
};

export async function runSmsSimulator(opts: Partial<SmsSimOptions> = {}) {
  const options = { ...DEFAULT_OPTIONS, ...opts };

  console.log("=================================================");
  console.log("      RailBite SMS Token Gateway Simulator       ");
  console.log("=================================================");
  console.log(`Endpoint:    ${options.serverUrl}/sms/ingest`);
  console.log(`HMAC Secret: ${options.secret.slice(0, 8)}...`);

  // 1. Health check
  try {
    const res = await fetch(`${options.serverUrl}/health`);
    if (!res.ok) throw new Error("Health check not 200");
  } catch (err) {
    console.error(`Cannot connect to server at ${options.serverUrl}`);
    process.exit(1);
  }

  let passed = 0;
  let failed = 0;

  // Test 1: Valid SMS Token -> 201 Created + ACK token
  console.log("\n[Test 1] Posting valid SMS token (Chai + Samosa, Coach B2 Berth 14)...");
  const nonce1 = "7f3a";
  const validToken = encodeSmsToken(
    {
      pnrLast4: "4829",
      coach: "B2",
      berth: 14,
      diners: 2,
      itemCodes: "CH1,SA2",
      nonce: nonce1,
    },
    options.secret
  );

  console.log(`  Payload: ${validToken}`);
  const res1 = await fetch(`${options.serverUrl}/sms/ingest`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ from: "+919876543210", text: validToken }),
  });

  if (res1.status === 201) {
    const data = (await res1.json()) as any;
    const ack = parseSmsAck(data.ackToken);
    console.log(`  ✅ Order created: ${data.orderId}`);
    console.log(`  ✅ ACK Token received: ${data.ackToken} (Total: ₹${ack.totalPaise / 100}, ETA: ${ack.etaMin}m)`);
    passed++;
  } else {
    console.error(`  ❌ Failed: Status ${res1.status}`);
    failed++;
  }

  // Test 2: Tampered HMAC -> 400 Bad Request
  console.log("\n[Test 2] Posting tampered token (Modified Coach from B2 to B3)...");
  const tamperedToken = validToken.replace("B2-14", "B3-14");
  const res2 = await fetch(`${options.serverUrl}/sms/ingest`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ from: "+919876543210", text: tamperedToken }),
  });

  if (res2.status === 400) {
    const err = (await res2.json()) as any;
    console.log(`  ✅ Tampering correctly rejected with HTTP 400:`, err.error);
    passed++;
  } else {
    console.error(`  ❌ Failed: Expected 400, got ${res2.status}`);
    failed++;
  }

  // Test 3: Replay Nonce -> 409 Conflict
  console.log("\n[Test 3] Posting replay with same nonce (Replay Attack)...");
  const res3 = await fetch(`${options.serverUrl}/sms/ingest`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ from: "+919876543210", text: validToken }),
  });

  if (res3.status === 409) {
    const err = (await res3.json()) as any;
    console.log(`  ✅ Replay correctly rejected with HTTP 409:`, err.error);
    passed++;
  } else {
    console.error(`  ❌ Failed: Expected 409, got ${res3.status}`);
    failed++;
  }

  console.log("\n=================================================");
  console.log(`Results: ${passed} Passed, ${failed} Failed`);
  console.log("=================================================");
  if (failed > 0) process.exit(1);
}

if (process.argv[1]?.includes("sms-simulator.ts")) {
  runSmsSimulator().catch((err) => {
    console.error(err);
    process.exit(1);
  });
}
