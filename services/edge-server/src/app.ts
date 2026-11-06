import fastify, { type FastifyInstance } from "fastify";
import cors from "@fastify/cors";
import fastifyStatic from "@fastify/static";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import type Database from "better-sqlite3";
import { config } from "./config.js";
import { MenuRepo } from "./db/repos/menuRepo.js";
import { StaffRepo } from "./db/repos/staffRepo.js";
import { LedgerRepo } from "./db/repos/ledgerRepo.js";
import { OutboxRepo } from "./db/repos/outboxRepo.js";
import { OrderRepo } from "./db/repos/orderRepo.js";
import { OrderService } from "./services/orderService.js";
import { ComplaintService } from "./services/complaintService.js";
import { Dispatcher } from "./dispatch/dispatcher.js";
import type { MqttPublisher } from "./mqtt/publisher.js";

import { healthRoutes } from "./routes/health.js";
import { createMenuRoutes } from "./routes/menu.js";
import { createStaffRoutes } from "./routes/staff.js";
import { createOrderRoutes } from "./routes/orders.js";
import { createKitchenRoutes } from "./routes/kitchen.js";
import { createComplaintRoutes } from "./routes/complaints.js";
import { createLedgerRoutes } from "./routes/ledger.js";
import { createSyncRoutes } from "./routes/sync.js";
import { smsRoutes } from "./routes/sms.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export interface AppDependencies {
  db: Database.Database;
  publisher: MqttPublisher;
}

export async function createApp(deps: AppDependencies): Promise<{
  app: FastifyInstance;
  orderService: OrderService;
  complaintService: ComplaintService;
  dispatcher: Dispatcher;
}> {
  const app = fastify({
    logger: {
      level: config.logLevel,
    },
    genReqId: () => crypto.randomUUID(),
    disableRequestLogging: false,
  });

  // Enable CORS
  await app.register(cors, {
    origin: "*",
    methods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
  });

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
  const menuRepo = new MenuRepo(deps.db);
  const staffRepo = new StaffRepo(deps.db);
  const ledgerRepo = new LedgerRepo(deps.db);
  const outboxRepo = new OutboxRepo(deps.db);
  const orderRepo = new OrderRepo(deps.db);

  const orderService = new OrderService(deps.db, deps.publisher);
  const complaintService = new ComplaintService(deps.db, deps.publisher);
  const dispatcher = new Dispatcher(orderRepo, staffRepo, deps.publisher, orderService);

  orderService.setDispatcher(dispatcher);

  // Register Routes
  await app.register(healthRoutes);
  await app.register(createMenuRoutes(menuRepo));
  await app.register(createStaffRoutes(staffRepo));
  await app.register(createOrderRoutes(orderService));
  await app.register(createKitchenRoutes(orderService));
  await app.register(createComplaintRoutes(complaintService));
  await app.register(createLedgerRoutes(ledgerRepo));
  await app.register(createSyncRoutes(outboxRepo));
  await app.register(smsRoutes);

  return { app, orderService, complaintService, dispatcher };
}
