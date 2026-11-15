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

  // Auto-seed if menu_items or trips is empty
  const itemCount = (db.prepare("SELECT COUNT(*) as cnt FROM menu_items").get() as any)?.cnt || 0;
  const tripCount = (db.prepare("SELECT COUNT(*) as cnt FROM trips").get() as any)?.cnt || 0;
  if (itemCount === 0 || tripCount === 0) {
    console.log("Database empty or missing seed data. Seeding initial data...");
    seed(db);
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

  // 5. Start Server (single port for REST and MQTT-over-WebSocket)
  await app.listen({ port: config.port, host: config.host });
  broker.attachToHttpServer(app.server);

  console.log(`[HTTP+MQTT] Server listening on http://${config.host}:${config.port}`);
  console.log(`[MQTT/WS]   WebSocket endpoint: ws://${config.host}:${config.port}/mqtt`);

  // Optional: keep standalone TCP port 1883 if explicitly requested via ENABLE_TCP_MQTT
  if (process.env.ENABLE_TCP_MQTT === "true") {
    const net = await import("node:net");
    const tcp = net.createServer(broker.aedes.handle);
    tcp.listen(config.mqttPort, config.host, () => {
      console.log(`[MQTT/TCP] Optional TCP broker listening on ${config.host}:${config.mqttPort}`);
    });
  }

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
