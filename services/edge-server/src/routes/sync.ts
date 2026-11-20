import type { FastifyPluginAsync } from "fastify";
import type { OutboxRepo } from "../db/repos/outboxRepo.js";
import type { OutboxWorker } from "../sync/outboxWorker.js";
import { requireRoles } from "../auth/plugin.js";

export function createSyncRoutes(
  outboxRepo: OutboxRepo,
  outboxWorker?: OutboxWorker,
): FastifyPluginAsync {
  return async (fastify) => {
    fastify.post("/sync/flush", async (request, reply) => {
      if (!requireRoles(request, reply, ["admin"])) return;

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

      const unsynced = await outboxRepo.getUnsynced(100);
      return {
        status: "flushed",
        count: unsynced.length,
        items: unsynced,
      };
    });
  };
}
