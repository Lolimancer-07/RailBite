import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import type { AccessClaims, AuthRole, AuthService } from "./authService.js";

declare module "fastify" {
  interface FastifyRequest {
    auth: AccessClaims | null;
  }
}

/** Routes reachable without a JWT. Everything else is protected when auth is on. */
const PUBLIC_PREFIXES = ["/health", "/health/", "/auth/", "/menu", "/sms/ingest"];
const PUBLIC_EXACT = new Set(["/health", "/menu", "/staff/login", "/sms/ingest"]);
/** Within the public /auth/ namespace, these still require a bearer token. */
const PROTECTED_EXACT = new Set(["/auth/me"]);

function isPublic(pathname: string): boolean {
  if (PROTECTED_EXACT.has(pathname)) return false;
  if (PUBLIC_EXACT.has(pathname)) return true;
  return PUBLIC_PREFIXES.some((prefix) => pathname.startsWith(prefix));
}

function bearerToken(request: FastifyRequest): string | null {
  const header = request.headers.authorization;
  if (!header) return null;
  const [scheme, token] = header.split(" ");
  if (!token || scheme?.toLowerCase() !== "bearer") return null;
  return token.trim();
}

export function registerAuthHook(
  app: FastifyInstance,
  authService: AuthService,
  authRequired: boolean,
): void {
  app.decorateRequest("auth", null);

  app.addHook("onRequest", async (request: FastifyRequest, reply: FastifyReply) => {
    const pathname = (request.url || "").split("?")[0] || "";
    if (request.method === "OPTIONS" || isPublic(pathname)) return;

    const token = bearerToken(request);
    if (!token) {
      if (authRequired) {
        return reply.code(401).send({ error: "Authentication required", code: "AUTH_REQUIRED" });
      }
      return;
    }

    try {
      request.auth = authService.verifyAccessToken(token);
    } catch {
      return reply.code(401).send({ error: "Invalid or expired session", code: "AUTH_INVALID" });
    }
  });
}

/**
 * Route-level role gate. When auth is disabled (tests/local `AUTH_REQUIRED=false`)
 * anonymous requests are allowed through — the onRequest hook already enforces
 * authentication whenever it is enabled.
 */
export function requireRoles(
  request: FastifyRequest,
  reply: FastifyReply,
  roles: AuthRole[],
): boolean {
  if (!request.auth) return true;
  if (roles.includes(request.auth.role)) return true;
  reply.code(403).send({ error: "You do not have access to this action", code: "FORBIDDEN" });
  return false;
}

/** Pickers may only act as themselves; admins may act on behalf of any staff id. */
export function effectiveStaffId(
  request: FastifyRequest,
  bodyCaptainId?: string,
): string | undefined {
  const auth = request.auth;
  if (auth?.role === "picker" || auth?.role === "kitchen") return auth.staffId;
  if (auth?.role === "admin") return bodyCaptainId || auth.staffId;
  return bodyCaptainId;
}
