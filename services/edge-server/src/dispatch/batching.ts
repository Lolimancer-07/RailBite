export interface BatchEntry {
  orderId: string;
  coach: string;
  readyAt: number;
}

export class CookBatcher {
  private batches = new Map<string, { entries: BatchEntry[]; timer: NodeJS.Timeout | null }>();

  constructor(
    private maxBatchSize = 4,
    private maxWaitMs = 90_000,
  ) {}

  addOrder(
    coach: string,
    orderId: string,
    onBatchReady: (coach: string, orderIds: string[]) => void,
  ): void {
    const coachKey = coach.toUpperCase();
    let batch = this.batches.get(coachKey);

    if (!batch) {
      batch = { entries: [], timer: null };
      this.batches.set(coachKey, batch);
    }

    batch.entries.push({
      orderId,
      coach: coachKey,
      readyAt: Date.now(),
    });

    // If batch size reached, flush immediately
    if (batch.entries.length >= this.maxBatchSize) {
      if (batch.timer) clearTimeout(batch.timer);
      const toFlush = batch.entries.map((e) => e.orderId);
      this.batches.delete(coachKey);
      onBatchReady(coachKey, toFlush);
      return;
    }

    // Otherwise, start or maintain 90s timer
    if (!batch.timer) {
      batch.timer = setTimeout(() => {
        const currentBatch = this.batches.get(coachKey);
        if (currentBatch && currentBatch.entries.length > 0) {
          const toFlush = currentBatch.entries.map((e) => e.orderId);
          this.batches.delete(coachKey);
          onBatchReady(coachKey, toFlush);
        }
      }, this.maxWaitMs);
    }
  }

  flushAll(onBatchReady: (coach: string, orderIds: string[]) => void): void {
    for (const [coachKey, batch] of this.batches.entries()) {
      if (batch.timer) clearTimeout(batch.timer);
      const toFlush = batch.entries.map((e) => e.orderId);
      this.batches.delete(coachKey);
      if (toFlush.length > 0) {
        onBatchReady(coachKey, toFlush);
      }
    }
  }
}
