#!/usr/bin/env bash
# RailBite Temporary Cloud Dev Tunnel
# Starts the edge server locally and exposes REST + MQTT-over-WebSocket via cloudflared.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${PORT:-8080}"

echo "========================================="
echo "   RailBite Cloud Dev Tunnel"
echo "========================================="

# 1. Check for cloudflared
if ! command -v cloudflared &>/dev/null; then
  echo "❌ cloudflared is not installed."
  echo "Install it with:"
  echo "  - Linux: sudo pacman -S cloudflared  OR  curl -L --output cloudflared.deb https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb && sudo dpkg -i cloudflared.deb"
  echo "  - macOS: brew install cloudflared"
  echo "  - Windows: winget install Cloudflare.cloudflared"
  exit 1
fi

# 2. Start edge-server in the background
echo "🚀 Starting edge server on port $PORT..."
cd "$ROOT_DIR"
PORT="$PORT" pnpm --filter @railbite/edge-server dev &
SERVER_PID=$!

cleanup() {
  echo ""
  echo "🛑 Shutting down server (PID $SERVER_PID) and tunnel..."
  kill "$SERVER_PID" 2>/dev/null || true
  kill 0 2>/dev/null || true
  wait 2>/dev/null || true
  echo "Done."
}
trap cleanup EXIT INT TERM

# 3. Wait for /health to respond
echo "⏳ Waiting for edge server to boot..."
for i in {1..30}; do
  if curl -sf "http://localhost:$PORT/health" >/dev/null 2>&1; then
    echo "✅ Edge server is up and healthy."
    break
  fi
  sleep 1
done

# 4. Start cloudflared tunnel
echo "🌐 Starting Cloudflare quick tunnel to http://localhost:$PORT..."
echo "--------------------------------------------------------"
echo "Look for the public URL below (https://*.trycloudflare.com)"
echo "Set this URL in your app Settings or build with:"
echo "  ./gradlew assembleRelease -PSERVER_URL=https://<your-subdomain>.trycloudflare.com"
echo "--------------------------------------------------------"

cloudflared tunnel --url "http://localhost:$PORT"
