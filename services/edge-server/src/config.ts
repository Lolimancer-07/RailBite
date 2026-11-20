import path from "node:path";
import process from "node:process";

try {
  if (typeof process.loadEnvFile === "function") {
    process.loadEnvFile();
  }
} catch {
  // Ignore missing .env
}

const nodeEnv = process.env.NODE_ENV || "development";

function envBool(name: string, fallback: boolean): boolean {
  const value = process.env[name];
  if (value === undefined || value === "") return fallback;
  return value.toLowerCase() === "true" || value === "1";
}

export const config = {
  port: Number(process.env.PORT || 8080),
  host: process.env.HOST || "0.0.0.0",
  mqttPort: Number(process.env.MQTT_PORT || 1883),
  mqttWsPort: Number(process.env.MQTT_WS_PORT || 8883),
  tripId: process.env.TRIP_ID || "TRIP_001",
  dbPath: process.env.DB_PATH || path.resolve(process.cwd(), "railbite.db"),
  /** Render Postgres connection string — when set, the server uses Postgres instead of SQLite. */
  databaseUrl: process.env.DATABASE_URL || "",
  hmacSecret: process.env.HMAC_SECRET || "railbite-trip-secret-key-xyz",
  nodeEnv,
  logLevel: process.env.LOG_LEVEL || "info",

  // ── Auth ────────────────────────────────────────────────────────────────
  /** Every route except /health, /auth/*, /staff/login and signed webhooks needs a JWT. */
  authRequired: envBool("AUTH_REQUIRED", nodeEnv !== "test"),
  /** Firebase project used to verify Phone Auth ID tokens (falls back to the FCM service account project). */
  firebaseProjectId: process.env.FIREBASE_PROJECT_ID || "",
  /** JWT signing key. Production refuses to start without an explicit key (see AuthService). */
  jwtSecret: process.env.JWT_SECRET || "",
  jwtExpiresIn: process.env.JWT_EXPIRES_IN || "1h",
  refreshDays: Number(process.env.REFRESH_TOKEN_DAYS || 30),
  /** Development-only phone login that skips Firebase (never active when NODE_ENV=production). */
  allowDevAuth: envBool("AUTH_DEV_MODE", nodeEnv !== "production"),
};
