# RailBite edge server — one container serving REST + MQTT-over-WebSocket on a single
# HTTP port (Render exposes only HTTP(S), so MQTT rides ws(s)://…/mqtt over the same port).
#
# BUILD CONTEXT IS THE REPO ROOT (see render.yaml / docker commands): the pnpm workspace
# layout is required because the server depends on the @railbite/shared workspace package.

# Full Debian node image: ships python3/make/g++ for better-sqlite3 if a prebuilt
# binary is missing (slim + apt would add ~10 min of mirror-dependent installs).
FROM node:22 AS deps
RUN corepack enable
WORKDIR /app

# Manifests first for a cacheable dependency layer.
# The filter installs @railbite/edge-server plus its workspace deps (@railbite/shared),
# including the devDependencies needed to build both.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml tsconfig.base.json ./
COPY packages/shared/package.json packages/shared/
COPY services/edge-server/package.json services/edge-server/
RUN pnpm install --frozen-lockfile --filter @railbite/edge-server...

FROM deps AS build
COPY tsconfig.base.json ./
COPY packages/shared packages/shared
COPY services/edge-server services/edge-server
RUN pnpm --filter @railbite/shared build \
    && pnpm --filter @railbite/edge-server build

FROM node:22 AS runtime
ENV NODE_ENV=production
WORKDIR /app/services/edge-server

# pnpm's symlinked node_modules layout is copied verbatim so imports resolve exactly
# as they do in the repo (root store + per-package links).
COPY --from=build /app/node_modules /app/node_modules
COPY --from=build /app/packages/shared /app/packages/shared
COPY --from=build /app/services/edge-server/node_modules ./node_modules
COPY --from=build /app/services/edge-server/dist ./dist
# tsc does not copy .sql files: ship migrations both where the loader looks first
# (dist/db/migrations) and where its fallback points (../../src/db/migrations).
COPY --from=build /app/services/edge-server/src/db/migrations ./dist/db/migrations
COPY --from=build /app/services/edge-server/src/db/migrations ./src/db/migrations

# DB lands in the working dir by default (ephemeral on Render's free plan — the server
# re-seeds menu/staff/manifest on boot). On a paid plan mount a disk and set
# DB_PATH=/var/data/railbite.db instead.
# ENV DB_PATH=/app/services/edge-server/railbite.db
EXPOSE 8080
CMD ["node", "dist/index.js"]
