import type { FastifyPluginAsync } from "fastify";

export const smsRoutes: FastifyPluginAsync = async (fastify) => {
  fastify.post("/sms/ingest", async (request) => {
    const { from, text } = (request.body || {}) as { from?: string; text?: string };
    return {
      status: "received",
      from,
      text,
      note: "SMS ingest stubbed for MVP",
    };
  });
};
