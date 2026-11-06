module.exports = {
  apps: [
    {
      name: "railbite-edge-server",
      script: "dist/index.js",
      instances: 1,
      autorestart: true,
      watch: false,
      max_memory_restart: "300M",
      env: {
        NODE_ENV: "production",
        PORT: 8080,
        MQTT_PORT: 1883,
        MQTT_WS_PORT: 8883,
        TRIP_ID: "TRIP_001",
        DB_PATH: "./railbite.db",
      },
    },
  ],
};
