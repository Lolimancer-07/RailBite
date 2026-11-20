import type Database from "better-sqlite3";
import { toDb, type Db } from "../db/db.js";

export interface OutboxWorkerOptions {
  db: Db | Database.Database;
  tripId: string;
  cloudApiUrl: string;
  pollIntervalMs?: number;
}

export class OutboxWorker {
  private db: Db;
  private tripId: string;
  private cloudApiUrl: string;
  private pollIntervalMs: number;
  private timer: NodeJS.Timeout | null = null;
  private isRunning: boolean = false;
  private consecutiveErrors: number = 0;

  constructor(options: OutboxWorkerOptions) {
    this.db = toDb(options.db);
    this.tripId = options.tripId;
    this.cloudApiUrl = options.cloudApiUrl.replace(/\/$/, "");
    this.pollIntervalMs = options.pollIntervalMs || 5000;
  }

  async flushOnce(): Promise<{ syncedCount: number; ackedUpTo?: number }> {
    const unsynced = await this.db.all<{
      seq: number;
      entity: string;
      payload: string;
      created_at: string;
      synced_at: string | null;
    }>("SELECT * FROM outbox WHERE synced_at IS NULL ORDER BY seq ASC LIMIT 50");

    if (unsynced.length === 0) {
      return { syncedCount: 0 };
    }

    const items = unsynced.map((r) => ({
      seq: Number(r.seq),
      entity: r.entity,
      payload: r.payload,
      createdAt: r.created_at,
    }));

    try {
      const res = await fetch(`${this.cloudApiUrl}/sync/batch`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          tripId: this.tripId,
          items,
        }),
      });

      if (!res.ok) {
        throw new Error(`Cloud API returned HTTP ${res.status}`);
      }

      const data = (await res.json()) as { ackedUpTo: number };
      const now = new Date().toISOString();

      await this.db.run(
        "UPDATE outbox SET synced_at = ? WHERE seq <= ? AND synced_at IS NULL",
        [now, data.ackedUpTo],
      );

      this.consecutiveErrors = 0;
      return { syncedCount: items.length, ackedUpTo: data.ackedUpTo };
    } catch (err) {
      this.consecutiveErrors++;
      throw err;
    }
  }

  start(): void {
    if (this.isRunning) return;
    this.isRunning = true;

    const scheduleNext = () => {
      if (!this.isRunning) return;

      // Exponential backoff with jitter on consecutive errors
      let delay = this.pollIntervalMs;
      if (this.consecutiveErrors > 0) {
        const factor = Math.min(2 ** this.consecutiveErrors, 32);
        const jitter = Math.random() * 500;
        delay = Math.min(this.pollIntervalMs * factor + jitter, 60000);
      }

      this.timer = setTimeout(() => {
        void (async () => {
          try {
            await this.flushOnce();
          } catch {
            // Handled and backed off
          }
          scheduleNext();
        })();
      }, delay);
    };

    scheduleNext();
  }

  stop(): void {
    this.isRunning = false;
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }
}
