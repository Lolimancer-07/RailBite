import { config } from "./config.js";
import { getDb, closeDb } from "./db/connection.js";
import { seed } from "./seed/seed.js";
import { createBroker } from "./mqtt/broker.js";
import { MqttPublisher } from "./mqtt/publisher.js";
import { registerMqttHandlers } from "./mqtt/handlers.js";
import { createApp } from "./app.js";

async function main() {
  console.log("=== Starting RailBite Edge Server ===");
  console.log(`Trip ID: ${config.tripId}`);
  console.log(`Database: ${config.dbPath}`);

  // 1. Database & Migrations
  const db = getDb();

  // Auto-seed if menu_items is empty
  const itemCount = (db.prepare("SELECT COUNT(*) as cnt FROM menu_items").get() as any)?.cnt || 0;
  if (itemCount === 0) {
    console.log("Database empty. Seeding initial data...");
    seed();
  }

  // 2. MQTT Broker
  const broker = createBroker();
  const publisher = new MqttPublisher(broker.aedes);

  // 3. Fastify HTTP REST API
  const { app, orderService, complaintService, dispatcher } = await createApp({
    db,
    publisher,
  });

  // 4. Wire MQTT Handlers
  registerMqttHandlers(broker.aedes, orderService, complaintService);

  // 5. Start Servers
  broker.tcpServer.listen(config.mqttPort, config.host, () => {
    console.log(`[MQTT] TCP broker listening on ${config.host}:${config.mqttPort}`);
  });

  broker.wsServer.listen(config.mqttWsPort, config.host, () => {
    console.log(`[MQTT] WebSocket broker listening on ${config.host}:${config.mqttWsPort}`);
  });

  await app.listen({ port: config.port, host: config.host });
  console.log(`[HTTP] REST API listening on http://${config.host}:${config.port}`);

  // 6. Recover pending timers from DB
  dispatcher.recoverPendingTimers();

  // Graceful shutdown
  const shutdown = async () => {
    console.log("\nShutting down gracefully...");
    await app.close();
    await broker.close();
    closeDb();
    console.log("RailBite Edge Server stopped.");
    process.exit(0);
  };

  process.on("SIGINT", shutdown);
  process.on("SIGTERM", shutdown);
}

main().catch((err) => {
  console.error("Fatal startup error:", err);
  process.exit(1);
});
