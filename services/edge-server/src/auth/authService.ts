import crypto from "node:crypto";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import { OAuth2Client } from "google-auth-library";
import { config } from "../config.js";
import type { Db } from "../db/db.js";

export type AuthRole = "customer" | "picker" | "kitchen" | "admin";
export type SubjectType = "customer" | "staff";

export interface AccessClaims extends jwt.JwtPayload {
  sub: string;
  role: AuthRole;
  type: SubjectType;
  sid: string;
  phone?: string;
  staffId?: string;
}

export interface AuthUser {
  id: string;
  name: string | null;
  phone?: string;
  role: AuthRole;
  staffId?: string;
  zoneCoaches?: string[];
  trainNumber?: string;
}

export interface AuthResult {
  accessToken: string;
  refreshToken: string;
  /** Access-token lifetime (e.g. "1h"). */
  expiresIn: string;
  /** Absolute access-token expiry, ISO-8601. */
  expiresAt: string;
  user: AuthUser;
}

interface UserRow {
  id: string;
  phone: string;
  name: string | null;
  created_at: string;
  last_login_at: string | null;
}

interface StaffAuthRow {
  id: string;
  name: string;
  role: "CAPTAIN" | "COOK";
  zone_coaches: string;
  train_number: string;
  pin_hash: string | null;
  password_hash: string | null;
  active: number | boolean;
  is_admin: number | boolean;
}

interface SessionRow {
  id: string;
  subject_id: string;
  subject_type: SubjectType;
  refresh_hash: string;
  created_at: string;
  expires_at: string;
  revoked_at: string | null;
}

export interface AuthServiceOptions {
  jwtSecret?: string;
  jwtExpiresIn?: string;
  refreshDays?: number;
  nodeEnv?: string;
  firebaseProjectId?: string;
}

const DEV_SECRET = "railbite-dev-jwt-secret-change-before-production";

function sha256(value: string): string {
  return crypto.createHash("sha256").update(value).digest("hex");
}

/** Accepts +919876543210 / 9876543210 / 91 98765 43210 and returns E.164-ish text. */
export function normalizePhone(raw: string): string {
  const cleaned = raw.replace(/[\s()-]/g, "");
  if (!cleaned) return "";
  if (cleaned.startsWith("+")) return cleaned;
  if (/^\d{10}$/.test(cleaned)) return `+91${cleaned}`;
  if (/^91\d{10}$/.test(cleaned)) return `+${cleaned}`;
  return `+${cleaned.replace(/\D/g, "")}`;
}

/**
 * Owns customer sessions (Firebase Phone Auth ID tokens → our own JWTs) and
 * staff password login. Refresh tokens are opaque, hashed at rest, rotated on
 * every use and revocable — access tokens are short-lived JWTs.
 */
export class AuthService {
  private readonly jwtSecret: string;
  private readonly expiresIn: string;
  private readonly refreshDays: number;
  private readonly projectId: string;
  private firebaseClient: OAuth2Client | null = null;

  constructor(
    private readonly db: Db,
    options: AuthServiceOptions = {},
  ) {
    const nodeEnv = options.nodeEnv ?? config.nodeEnv;
    const secret = options.jwtSecret ?? config.jwtSecret;
    if (!secret && nodeEnv === "production") {
      throw new Error(
        "JWT_SECRET is required in production. Generate one with `openssl rand -hex 32` and set it on the service.",
      );
    }
    this.jwtSecret = secret || DEV_SECRET;
    this.expiresIn = options.jwtExpiresIn ?? config.jwtExpiresIn;
    this.refreshDays = options.refreshDays ?? config.refreshDays;
    this.projectId = options.firebaseProjectId || this.resolveProjectId();
  }

  private resolveProjectId(): string {
    if (config.firebaseProjectId) return config.firebaseProjectId;
    const raw = process.env.GOOGLE_SERVICE_ACCOUNT_JSON;
    if (raw && raw.trim()) {
      try {
        return JSON.parse(raw).project_id || "";
      } catch {
        // fall through
      }
    }
    return process.env.GOOGLE_CLOUD_PROJECT || process.env.GCLOUD_PROJECT || "";
  }

  get firebaseConfigured(): boolean {
    return this.projectId.length > 0;
  }

  // ── Customer login ──────────────────────────────────────────────────────

  /** Verifies a Firebase Phone Auth ID token and starts a RailBite session. */
  async loginWithFirebase(idToken: string, name?: string): Promise<AuthResult> {
    if (!idToken) throw new AuthError("idToken is required");
    if (!this.firebaseConfigured) {
      throw new AuthError(
        "Firebase Phone Auth is not configured on the server (FIREBASE_PROJECT_ID / GOOGLE_SERVICE_ACCOUNT_JSON)",
      );
    }
    const client = this.getFirebaseClient();
    const ticket = await client.verifyIdToken({ idToken, audience: this.projectId });
    const payload = ticket.getPayload();
    const phone = (payload as any)?.phone_number as string | undefined;
    if (!phone) throw new AuthError("Firebase token does not contain a verified phone number");

    const user = await this.upsertCustomer(normalizePhone(phone), name || payload?.name);
    return this.issueTokens(user, "customer", "customer");
  }

  /** Development-only phone login (disabled automatically when NODE_ENV=production). */
  async devLogin(phoneInput: string, name?: string): Promise<AuthResult> {
    if (!config.allowDevAuth) throw new AuthError("Development login is disabled");
    const phone = normalizePhone(phoneInput);
    if (!/^\+\d{8,15}$/.test(phone)) throw new AuthError("Enter a valid phone number");
    const user = await this.upsertCustomer(phone, name);
    return this.issueTokens(user, "customer", "customer");
  }

  private getFirebaseClient(): OAuth2Client {
    if (!this.firebaseClient) this.firebaseClient = new OAuth2Client();
    return this.firebaseClient;
  }

  private async upsertCustomer(phone: string, name?: string): Promise<AuthUser> {
    const now = new Date().toISOString();
    const id = crypto.randomUUID();
    await this.db.run(
      `INSERT INTO users (id, phone, name, created_at, last_login_at)
       VALUES (?, ?, ?, ?, ?)
       ON CONFLICT (phone) DO UPDATE SET
         last_login_at = excluded.last_login_at,
         name = COALESCE(users.name, excluded.name)`,
      [id, phone, name || null, now, now],
    );
    const user = await this.db.get<UserRow>("SELECT * FROM users WHERE phone = ?", [phone]);
    if (!user) throw new AuthError("Failed to create user");
    return { id: user.id, phone: user.phone, name: user.name, role: "customer" };
  }

  // ── Staff login ─────────────────────────────────────────────────────────

  async staffLogin(
    staffId: string,
    credentials: { password?: string; pin?: string; trainNumber?: string },
  ): Promise<AuthResult> {
    const row = await this.db.get<StaffAuthRow>(
      "SELECT id, name, role, zone_coaches, train_number, pin_hash, password_hash, active, is_admin FROM staff WHERE id = ?",
      [staffId],
    );
    if (!row) throw new AuthError("Invalid staff ID or password");
    if (!row.active) throw new AuthError("This staff account is deactivated");

    let valid = false;
    if (credentials.password && row.password_hash) {
      valid = await bcrypt.compare(credentials.password, row.password_hash);
    }
    // Legacy PIN fallback: existing pantry phones still send `pin` until updated.
    if (!valid && credentials.pin && row.pin_hash) {
      valid = sha256(credentials.pin) === row.pin_hash;
    }
    if (!valid) throw new AuthError("Invalid staff ID or password");

    const activeTrain = credentials.trainNumber || row.train_number || "12345";
    // Seeded/existing accounts keep working; a password set later upgrades them.
    const now = new Date().toISOString();
    await this.db.run("UPDATE staff SET online = 1, train_number = ? WHERE id = ?", [
      activeTrain,
      row.id,
    ]);

    const role: AuthRole = row.is_admin ? "admin" : row.role === "CAPTAIN" ? "picker" : "kitchen";
    const user: AuthUser = {
      id: row.id,
      staffId: row.id,
      name: row.name,
      role,
      zoneCoaches: JSON.parse(row.zone_coaches || "[]"),
      trainNumber: activeTrain,
    };
    const result = await this.issueTokens(user, "staff", role);
    // Keep the legacy `loginAt` signal for the kitchen/dashboard surfaces.
    void now;
    return result;
  }

  // ── Token lifecycle ─────────────────────────────────────────────────────

  private async issueTokens(
    user: AuthUser,
    type: SubjectType,
    role: AuthRole,
  ): Promise<AuthResult> {
    const sid = crypto.randomUUID();
    const refreshToken = crypto.randomBytes(32).toString("hex");
    const now = new Date();
    const expiresAt = new Date(now.getTime() + this.refreshDays * 24 * 60 * 60_000).toISOString();

    await this.db.run(
      `INSERT INTO sessions (id, subject_id, subject_type, refresh_hash, created_at, expires_at)
       VALUES (?, ?, ?, ?, ?, ?)`,
      [sid, user.id, type, sha256(refreshToken), now.toISOString(), expiresAt],
    );

    const claims: Record<string, unknown> = {
      sub: user.id,
      role,
      type,
      sid,
      ...(user.phone ? { phone: user.phone } : {}),
      ...(user.staffId ? { staffId: user.staffId } : {}),
    };
    const accessToken = jwt.sign(claims, this.jwtSecret, {
      algorithm: "HS256",
      expiresIn: this.expiresIn as jwt.SignOptions["expiresIn"],
      issuer: "railbite-edge",
      audience: "railbite-apps",
    });

    const decoded = jwt.decode(accessToken) as { exp?: number } | null;
    const expiresAtIso = decoded?.exp
      ? new Date(decoded.exp * 1000).toISOString()
      : new Date(now.getTime() + 60 * 60_000).toISOString();

    return {
      accessToken,
      refreshToken,
      expiresIn: this.expiresIn,
      expiresAt: expiresAtIso,
      user,
    };
  }

  verifyAccessToken(token: string): AccessClaims {
    const decoded = jwt.verify(token, this.jwtSecret, {
      algorithms: ["HS256"],
      issuer: "railbite-edge",
      audience: "railbite-apps",
    });
    if (typeof decoded === "string" || !decoded.sub || !decoded.sid) {
      throw new AuthError("Malformed access token");
    }
    return decoded as AccessClaims;
  }

  /** Rotates a refresh token. The old token is revoked before the new pair is issued. */
  async refresh(refreshToken: string): Promise<AuthResult> {
    if (!refreshToken) throw new AuthError("refreshToken is required");
    const hash = sha256(refreshToken);
    const session = await this.db.get<SessionRow>(
      "SELECT * FROM sessions WHERE refresh_hash = ? AND revoked_at IS NULL LIMIT 1",
      [hash],
    );
    if (!session) throw new AuthError("Invalid or expired refresh token");
    if (new Date(session.expires_at).getTime() <= Date.now()) {
      throw new AuthError("Refresh token expired — sign in again");
    }

    const now = new Date().toISOString();
    const revoked = await this.db.run(
      "UPDATE sessions SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL",
      [now, session.id],
    );
    if (revoked.changes === 0) throw new AuthError("Refresh token already used");

    const user =
      session.subject_type === "customer"
        ? await this.getCustomerById(session.subject_id)
        : await this.getStaffUser(session.subject_id);
    if (!user) throw new AuthError("Account no longer exists");
    return this.issueTokens(user, session.subject_type, user.role);
  }

  async logout(refreshToken: string): Promise<boolean> {
    if (!refreshToken) return false;
    const res = await this.db.run(
      "UPDATE sessions SET revoked_at = ? WHERE refresh_hash = ? AND revoked_at IS NULL",
      [new Date().toISOString(), sha256(refreshToken)],
    );
    return res.changes > 0;
  }

  async getMe(claims: AccessClaims): Promise<AuthUser | null> {
    if (claims.type === "customer") return this.getCustomerById(claims.sub);
    return this.getStaffUser(claims.sub);
  }

  private async getCustomerById(id: string): Promise<AuthUser | null> {
    const row = await this.db.get<UserRow>("SELECT * FROM users WHERE id = ?", [id]);
    if (!row) return null;
    return { id: row.id, name: row.name, phone: row.phone, role: "customer" };
  }

  private async getStaffUser(id: string): Promise<AuthUser | null> {
    const row = await this.db.get<StaffAuthRow>(
      "SELECT id, name, role, zone_coaches, train_number, active, is_admin FROM staff WHERE id = ?",
      [id],
    );
    if (!row || !row.active) return null;
    return {
      id: row.id,
      staffId: row.id,
      name: row.name,
      role: row.is_admin ? "admin" : row.role === "CAPTAIN" ? "picker" : "kitchen",
      zoneCoaches: JSON.parse(row.zone_coaches || "[]"),
      trainNumber: row.train_number || "12345",
    };
  }
}

export class AuthError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "AuthError";
  }
}
