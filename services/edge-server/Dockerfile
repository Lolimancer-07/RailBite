FROM node:20-alpine AS builder

WORKDIR /app

RUN npm install -g pnpm@9

# Copy root configuration
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml tsconfig.base.json ./
COPY packages/shared/package.json ./packages/shared/
COPY services/edge-server/package.json ./services/edge-server/

# Install build dependencies
RUN pnpm install --frozen-lockfile --filter @railbite/edge-server...

# Copy source trees
COPY packages/shared/ ./packages/shared/
COPY services/edge-server/ ./services/edge-server/

# Build shared and edge-server
RUN pnpm --filter @railbite/shared build && pnpm --filter @railbite/edge-server build

# Production runtime stage
FROM node:20-alpine AS runner

WORKDIR /app

RUN npm install -g pnpm@9

ENV NODE_ENV=production
ENV PORT=8080
ENV DB_PATH=/data/railbite.db

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml tsconfig.base.json ./
COPY packages/shared/package.json ./packages/shared/
COPY services/edge-server/package.json ./services/edge-server/

# Install production dependencies only
RUN pnpm install --frozen-lockfile --prod --filter @railbite/edge-server...

# Copy compiled artifacts from builder
COPY --from=builder /app/packages/shared/dist ./packages/shared/dist
COPY --from=builder /app/services/edge-server/dist ./services/edge-server/dist
COPY --from=builder /app/services/edge-server/src/db/migrations ./services/edge-server/dist/db/migrations
COPY --from=builder /app/services/edge-server/src/db/migrations ./services/edge-server/src/db/migrations

# Create volume dir for SQLite database
RUN mkdir -p /data

WORKDIR /app/services/edge-server

EXPOSE 8080

CMD ["node", "dist/index.js"]

