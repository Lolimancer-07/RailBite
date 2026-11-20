import type { FastifyPluginAsync } from "fastify";
import { z } from "zod";
import { AuthError, type AuthService } from "../auth/authService.js";

const FirebaseVerifySchema = z.object({
  idToken: z.string().min(20),
  name: z.string().max(120).optional(),
});

const DevLoginSchema = z.object({
  phone: z.string().min(8).max(20),
  name: z.string().max(120).optional(),
});

const RefreshSchema = z.object({ refreshToken: z.string().min(20) });

const authRateLimit = { rateLimit: { max: 20, timeWindow: "1 minute" } };

export function createAuthRoutes(authService: AuthService): FastifyPluginAsync {
  return async (fastify) => {
    const verifyFirebase = async (request: any, reply: any) => {
      const parsed = FirebaseVerifySchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.code(400).send({ error: "Invalid login payload", details: parsed.error.issues });
      }
      try {
        const result = await authService.loginWithFirebase(parsed.data.idToken, parsed.data.name);
        return reply.send({ ok: true, ...result });
      } catch (err) {
        const message = err instanceof AuthError ? err.message : "Could not verify phone login";
        return reply.code(401).send({ error: message, code: "PHONE_LOGIN_FAILED" });
      }
    };

    // Firebase Phone Auth: the app completes the SMS challenge, then posts the ID token here.
    fastify.post("/auth/otp/verify", { config: authRateLimit }, verifyFirebase);
    fastify.post("/auth/firebase", { config: authRateLimit }, verifyFirebase);

    // Development-only shortcut (disabled in production): useful for local devices/tests.
    fastify.post("/auth/dev/login", { config: authRateLimit }, async (request: any, reply: any) => {
      const parsed = DevLoginSchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.code(400).send({ error: "Invalid login payload", details: parsed.error.issues });
      }
      try {
        const result = await authService.devLogin(parsed.data.phone, parsed.data.name);
        return reply.send({ ok: true, ...result });
      } catch (err) {
        const message = err instanceof AuthError ? err.message : "Development login unavailable";
        return reply.code(403).send({ error: message, code: "DEV_LOGIN_DISABLED" });
      }
    });

    fastify.post("/auth/refresh", { config: authRateLimit }, async (request: any, reply: any) => {
      const parsed = RefreshSchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.code(400).send({ error: "refreshToken is required" });
      }
      try {
        const result = await authService.refresh(parsed.data.refreshToken);
        return reply.send({ ok: true, ...result });
      } catch (err) {
        const message = err instanceof AuthError ? err.message : "Could not refresh session";
        return reply.code(401).send({ error: message, code: "REFRESH_FAILED" });
      }
    });

    fastify.post("/auth/logout", async (request: any, reply: any) => {
      const refreshToken = (request.body || {}).refreshToken as string | undefined;
      if (refreshToken) await authService.logout(refreshToken);
      return reply.send({ ok: true });
    });

    fastify.get("/auth/me", async (request: any, reply: any) => {
      if (!request.auth) {
        return reply.code(401).send({ error: "Authentication required", code: "AUTH_REQUIRED" });
      }
      const user = await authService.getMe(request.auth);
      if (!user) {
        return reply.code(401).send({ error: "Account no longer exists", code: "AUTH_INVALID" });
      }
      return reply.send({ ok: true, user });
    });
  };
}
