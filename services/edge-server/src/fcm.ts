import { GoogleAuth } from "google-auth-library";
import fs from "node:fs";
import pino from "pino";
import { config } from "./config.js";

const logger = pino({ name: "fcm", level: config.logLevel });

export interface FcmPayload {
  token: string;
  data: Record<string, string>;
}

let authClient: GoogleAuth | null = null;
let cachedProjectId: string | null = null;

function getCredentials(): { credentials?: any; keyFilename?: string; projectId?: string } | null {
  const jsonEnv = process.env.GOOGLE_SERVICE_ACCOUNT_JSON;
  if (jsonEnv && jsonEnv.trim().length > 0) {
    try {
      const parsed = JSON.parse(jsonEnv.trim());
      return { credentials: parsed, projectId: parsed.project_id };
    } catch (err) {
      logger.error({ err }, "Failed to parse GOOGLE_SERVICE_ACCOUNT_JSON environment variable");
      return null;
    }
  }

  const pathEnv = process.env.GOOGLE_SERVICE_ACCOUNT_PATH || process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (pathEnv && fs.existsSync(pathEnv)) {
    try {
      const content = fs.readFileSync(pathEnv, "utf-8");
      const parsed = JSON.parse(content);
      return { keyFilename: pathEnv, projectId: parsed.project_id };
    } catch (err) {
      logger.error({ err }, "Failed to read service account key from file");
      return null;
    }
  }

  return null;
}

function initAuth(): boolean {
  if (authClient) return true;

  const creds = getCredentials();
  if (!creds) {
    logger.debug("No Firebase service account configured (GOOGLE_SERVICE_ACCOUNT_JSON or PATH) — FCM disabled");
    return false;
  }

  try {
    cachedProjectId = creds.projectId || null;
    authClient = new GoogleAuth({
      credentials: creds.credentials,
      keyFilename: creds.keyFilename,
      scopes: ["https://www.googleapis.com/auth/firebase.messaging"],
    });
    return true;
  } catch (err) {
    logger.error({ err }, "Failed to initialize GoogleAuth for FCM");
    return false;
  }
}

/**
 * Sends a high-priority data message via FCM HTTP v1.
 * Returns true if sent successfully, false if skipped or failed.
 */
export async function sendFcmAlert(token: string, data: Record<string, string>): Promise<boolean> {
  if (!token) return false;
  if (!initAuth() || !authClient) return false;

  try {
    const projectId = cachedProjectId || (await authClient.getProjectId());
    if (!projectId) {
      logger.error("FCM Project ID could not be determined");
      return false;
    }

    const client = await authClient.getClient();
    const url = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;

    const body = {
      message: {
        token,
        android: {
          priority: "high",
        },
        data,
      },
    };

    const res = await client.request({
      url,
      method: "POST",
      data: body,
      headers: { "Content-Type": "application/json" },
    });

    logger.info({ status: (res as any).status, token: token.slice(0, 10) + "..." }, "FCM alert dispatched successfully");
    return true;
  } catch (err: any) {
    logger.warn({ error: err?.message, token: token.slice(0, 10) + "..." }, "Failed to dispatch FCM alert");
    return false;
  }
}

