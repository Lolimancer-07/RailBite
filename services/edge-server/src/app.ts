import fastify, { type FastifyInstance } from "fastify";
import cors from "@fastify/cors";
import fastifyStatic from "@fastify/static";
import rateLimit from "@fastify/rate-limit";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import type Database from "better-sqlite3";
import { config } from "./config.js";
import { toDb, type Db } from "./db/db.js";
import { MenuRepo } from "./db/repos/menuRepo.js";
import { StaffRepo } from "./db/repos/staffRepo.js";
import { LedgerRepo } from "./db/repos/ledgerRepo.js";
import { OutboxRepo } from "./db/repos/outboxRepo.js";
import { OrderRepo } from "./db/repos/orderRepo.js";
import { OrderService } from "./services/orderService.js";
import { ComplaintService } from "./services/complaintService.js";
import { Dispatcher } from "./dispatch/dispatcher.js";
import type { MqttPublisher } from "./mqtt/publisher.js";
import { AuthService, type AuthServiceOptions } from "./auth/authService.js";
import { registerAuthHook } from "./auth/plugin.js";

import { healthRoutes } from "./routes/health.js";
import { createMenuRoutes } from "./routes/menu.js";
import { createStaffRoutes } from "./routes/staff.js";
import { createOrderRoutes } from "./routes/orders.js";
import { createKitchenRoutes } from "./routes/kitchen.js";
import { createComplaintRoutes } from "./routes/complaints.js";
import { createLedgerRoutes } from "./routes/ledger.js";
import { createSyncRoutes } from "./routes/sync.js";
import { createSmsRoutes } from "./routes/sms.js";
import { createAuthRoutes } from "./routes/auth.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export interface AppDependencies {
  db: Db | Database.Database;
  publisher: MqttPublisher;
}

export interface CreateAppOptions {
  /** Overrides config (tests use this; production follows AUTH_REQUIRED). */
  authRequired?: boolean;
  auth?: AuthServiceOptions;
}

export async function createApp(
  deps: AppDependencies,
  options: CreateAppOptions = {},
): Promise<{
  app: FastifyInstance;
  db: Db;
  orderService: OrderService;
  complaintService: ComplaintService;
  dispatcher: Dispatcher;
  authService: AuthService;
}> {
  const app = fastify({
    logger: {
      level: config.logLevel,
    },
    trustProxy: true,
    genReqId: () => crypto.randomUUID(),
    disableRequestLogging: false,
  });

  const db = toDb(deps.db);

  // Enable CORS
  await app.register(cors, {
    origin: "*",
    methods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
  });

  // Auth rate limits are opt-in per route (OTP verify / staff login).
  await app.register(rateLimit, { global: false });

  // JWT on every route except the public allowlist; parses optional tokens too.
  const authService = new AuthService(db, options.auth);
  const authRequired = options.authRequired ?? config.authRequired;
  registerAuthHook(app, authService, authRequired);

  // Serve Kitchen Display web app if dist exists
  const kitchenDist = path.resolve(__dirname, "../../../apps/kitchen-display/dist");
  if (fs.existsSync(kitchenDist)) {
    await app.register(fastifyStatic, {
      root: kitchenDist,
      prefix: "/kitchen/",
      decorateReply: false,
    });
  }

  // Repositories & Services
  const menuRepo = new MenuRepo(db);
  const staffRepo = new StaffRepo(db);
  const ledgerRepo = new LedgerRepo(db);
  const outboxRepo = new OutboxRepo(db);
  const orderRepo = new OrderRepo(db);

  const orderService = new OrderService(db, deps.publisher);
  const complaintService = new ComplaintService(db, deps.publisher);
  const dispatcher = new Dispatcher(orderRepo, staffRepo, deps.publisher, orderService);

  orderService.setDispatcher(dispatcher);

  // Register Routes
  await app.register(healthRoutes);
  await app.register(createAuthRoutes(authService));
  await app.register(createMenuRoutes(menuRepo));
  await app.register(createStaffRoutes(staffRepo, ledgerRepo, authService, orderRepo));
  await app.register(createOrderRoutes(orderService));
  await app.register(createKitchenRoutes(orderService));
  await app.register(createComplaintRoutes(complaintService));
  await app.register(createLedgerRoutes(ledgerRepo));
  await app.register(createSyncRoutes(outboxRepo));
  await app.register(createSmsRoutes(db, orderService));

  return { app, db, orderService, complaintService, dispatcher, authService };
}
