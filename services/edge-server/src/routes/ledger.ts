import type { FastifyPluginAsync } from "fastify";
import type { LedgerRepo } from "../db/repos/ledgerRepo.js";

export function createLedgerRoutes(ledgerRepo: LedgerRepo): FastifyPluginAsync {
  return async (fastify) => {
    fastify.get("/ledger/summary", async () => {
      return ledgerRepo.getSummary();
    });
  };
}
