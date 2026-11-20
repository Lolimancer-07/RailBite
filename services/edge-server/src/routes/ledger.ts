import type { FastifyPluginAsync } from "fastify";
import type { LedgerRepo } from "../db/repos/ledgerRepo.js";
import { requireRoles } from "../auth/plugin.js";

export function createLedgerRoutes(ledgerRepo: LedgerRepo): FastifyPluginAsync {
  return async (fastify) => {
    fastify.get("/ledger/summary", async (request, reply) => {
      if (!requireRoles(request, reply, ["picker", "kitchen", "admin"])) return;
      return ledgerRepo.getSummary();
    });
  };
}
