import { initCloudDb } from "./db.js";
import { createCloudApp } from "./app.js";

const PORT = parseInt(process.env.PORT || "8090", 10);
const HOST = process.env.HOST || "0.0.0.0";
const DB_PATH = process.env.DB_PATH || "./cloud.db";

async function main() {
  const db = initCloudDb(DB_PATH);
  const app = createCloudApp(db);

  await app.listen({ port: PORT, host: HOST });
  console.log(`[Cloud API] Running on http://${HOST}:${PORT}`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
