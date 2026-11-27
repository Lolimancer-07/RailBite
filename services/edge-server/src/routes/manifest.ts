import type { FastifyPluginAsync } from "fastify";
import { ManifestRepo } from "../db/repos/manifestRepo.js";

export function createManifestRoutes(manifestRepo: ManifestRepo): FastifyPluginAsync {
  return async (fastify) => {
    // GET /manifest/:pnr — auto-identify passenger train number, name, coach, and berth
    fastify.get("/manifest/:pnr", async (request, reply) => {
      const { pnr } = request.params as { pnr: string };
      if (!/^\d{10}$/.test(pnr)) {
        return reply.status(400).send({ error: "PNR must be 10 digits" });
      }

      const entry = await manifestRepo.findByPnr(pnr);
      if (!entry) {
        return reply.status(404).send({ error: "PNR not found in manifest" });
      }
      return entry;
    });
  };
}
