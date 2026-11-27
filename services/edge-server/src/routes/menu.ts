import type { FastifyPluginAsync } from "fastify";
import type { MenuRepo } from "../db/repos/menuRepo.js";

export function createMenuRoutes(menuRepo: MenuRepo): FastifyPluginAsync {
  return async (fastify) => {
    fastify.get("/menu", async () => {
      return menuRepo.listActive();
    });

    fastify.get("/menu/trending", async (request) => {
      const q = request.query as { limit?: string } | undefined;
      const parsed = Number(q?.limit);
      const limit = Number.isFinite(rawInt(parsed)) && parsed > 0 ? Math.min(Math.floor(parsed), 20) : 6;
      return menuRepo.listTrending(limit);
    });
  };
}

function rawInt(n: number): number {
  return Number.isFinite(n) ? n : 6;
}
