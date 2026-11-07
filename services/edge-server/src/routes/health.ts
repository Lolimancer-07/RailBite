import type { FastifyPluginAsync } from "fastify";
import { config } from "../config.js";

export const healthRoutes: FastifyPluginAsync = async (fastify) => {
  fastify.get("/health", async () => {
    return {
      ok: true,
      tripId: config.tripId,
      time: new Date().toISOString(),
      version: "1.0.0",
    };
  });
};

