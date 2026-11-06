import type { FastifyPluginAsync } from "fastify";
import type { OutboxRepo } from "../db/repos/outboxRepo.js";

export function createSyncRoutes(outboxRepo: OutboxRepo): FastifyPluginAsync {
  return async (fastify) => {
    fastify.post("/sync/flush", async () => {
      const unsynced = outboxRepo.getUnsynced(100);
      return {
        status: "flushed",
        count: unsynced.length,
        items: unsynced,
      };
    });
  };
}
