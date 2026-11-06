import path from "node:path";
import process from "node:process";

try {
  if (typeof process.loadEnvFile === "function") {
    process.loadEnvFile();
  }
} catch {
  // Ignore missing .env
}

export const config = {
  port: Number(process.env.PORT || 8080),
  host: process.env.HOST || "0.0.0.0",
  mqttPort: Number(process.env.MQTT_PORT || 1883),
  mqttWsPort: Number(process.env.MQTT_WS_PORT || 8883),
  tripId: process.env.TRIP_ID || "TRIP_001",
  dbPath: process.env.DB_PATH || path.resolve(process.cwd(), "railbite.db"),
  hmacSecret: process.env.HMAC_SECRET || "railbite-trip-secret-key-xyz",
  nodeEnv: process.env.NODE_ENV || "development",
  logLevel: process.env.LOG_LEVEL || "info",
};
