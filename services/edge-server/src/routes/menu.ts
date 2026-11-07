import type { FastifyPluginAsync } from "fastify";
import type { MenuRepo } from "../db/repos/menuRepo.js";

export function createMenuRoutes(menuRepo: MenuRepo): FastifyPluginAsync {
  return async (fastify) => {
    fastify.get("/menu", async () => {
      return menuRepo.listActive();
    });
  };
}

