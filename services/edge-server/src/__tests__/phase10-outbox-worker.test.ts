import { describe, it, expect, beforeEach, afterEach } from "vitest";
import Database from "better-sqlite3";
import { runMigrations } from "../db/connection.js";
import { OutboxRepo } from "../db/repos/outboxRepo.js";
import { OutboxWorker } from "../sync/outboxWorker.js";
import fastify, { type FastifyInstance } from "fastify";

describe("Phase 10: Edge Server Outbox Worker", () => {
  let edgeDb: Database.Database;
  let outboxRepo: OutboxRepo;
  let mockCloud: FastifyInstance;
  let mockCloudUrl: string;
  const receivedBatches: any[] = [];

  beforeEach(async () => {
    edgeDb = new Database(":memory:");
    edgeDb.pragma("journal_mode = WAL");
    edgeDb.pragma("foreign_keys = ON");
    runMigrations(edgeDb);
    outboxRepo = new OutboxRepo(edgeDb);

    receivedBatches.length = 0;
    mockCloud = fastify({ logger: false });
    mockCloud.post("/sync/batch", async (req, reply) => {
      const body = req.body as any;
      receivedBatches.push(body);
      const maxSeq = Math.max(...body.items.map((i: any) => i.seq));
      return { ok: true, ackedUpTo: maxSeq };
    });

    await mockCloud.listen({ port: 0, host: "127.0.0.1" });
    const addr = mockCloud.server.address() as any;
    mockCloudUrl = `http://127.0.0.1:${addr.port}`;
  });

  afterEach(async () => {
    await mockCloud.close();
    edgeDb.close();
  });

  it("Done when 2: Outbox worker polls unsynced outbox rows, POSTs batch to cloud API, and marks rows synced_at", async () => {
    // 1. Queue 3 items in edge server outbox
    const now = new Date().toISOString();
    outboxRepo.append("ORDER_CREATED", { orderId: "ord-1", totalPaise: 4500 }, now);
    outboxRepo.append("CASH_DELIVERED", { orderId: "ord-1", captainId: "c1", cashPaise: 4500 }, now);
    outboxRepo.append(
      "COMPLAINT_ESCALATION",
      { id: "comp-1", orderId: "ord-1", type: "HYGIENE", text: "Stale food" },
      now
    );

    const unsyncedBefore = outboxRepo.getUnsynced(10);
    expect(unsyncedBefore.length).toBe(3);

    // 2. Run worker
    const worker = new OutboxWorker({
      db: edgeDb,
      tripId: "TRIP_001",
      cloudApiUrl: mockCloudUrl,
    });

    const result = await worker.flushOnce();
    expect(result.syncedCount).toBe(3);
    expect(result.ackedUpTo).toBe(3);

    // 3. Verify mock cloud received batch
    expect(receivedBatches.length).toBe(1);
    expect(receivedBatches[0].tripId).toBe("TRIP_001");
    expect(receivedBatches[0].items.length).toBe(3);

    // 4. Verify in edge DB that synced_at is now set
    const unsyncedAfter = outboxRepo.getUnsynced(10);
    expect(unsyncedAfter.length).toBe(0);

    const rows = edgeDb.prepare("SELECT * FROM outbox").all() as any[];
    for (const row of rows) {
      expect(row.synced_at).not.toBeNull();
    }
  });
});
