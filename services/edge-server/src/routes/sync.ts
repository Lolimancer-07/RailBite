import type { FastifyPluginAsync } from "fastify";
import type { OutboxRepo } from "../db/repos/outboxRepo.js";
import type { OutboxWorker } from "../sync/outboxWorker.js";

export function createSyncRoutes(
  outboxRepo: OutboxRepo,
  outboxWorker?: OutboxWorker
): FastifyPluginAsync {
  return async (fastify) => {
    fastify.post("/sync/flush", async () => {
      if (outboxWorker) {
        try {
          const result = await outboxWorker.flushOnce();
          return {
            status: "flushed",
            syncedCount: result.syncedCount,
            ackedUpTo: result.ackedUpTo,
          };
        } catch (err: any) {
          return { status: "offline", error: err.message };
        }
      }

      const unsynced = outboxRepo.getUnsynced(100);
      return {
        status: "flushed",
        count: unsynced.length,
        items: unsynced,
      };
    });
  };
}
