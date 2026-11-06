import {
  getPendingActions,
  removeAction,
  updateActionAttempt,
  saveLocalOrder,
  getLocalOrder,
  countPendingActions,
} from "../db/queries.js";
import { postOrder, confirmCashPayment, postComplaint } from "../net/restClient.js";
import { useConnectionStore } from "../store/connection.js";

let workerInterval: any = null;
let isProcessing = false;

export async function processQueue(): Promise<void> {
  if (isProcessing) return;
  isProcessing = true;

  try {
    const actions = getPendingActions();
    if (actions.length === 0) {
      useConnectionStore.getState().setQueuedCount(0);
      return;
    }

    useConnectionStore.getState().setQueuedCount(countPendingActions());

    for (const action of actions) {
      try {
        if (action.type === "PLACE_ORDER") {
          const serverOrder = await postOrder(action.payload);
          // Preserve local item details, update status/total
          const localOrder = getLocalOrder(action.payload.id);
          saveLocalOrder(serverOrder, localOrder?.items || []);
          removeAction(action.id);
          useConnectionStore.getState().setStatus("Connected");
        } else if (action.type === "CONFIRM_CASH") {
          await confirmCashPayment(action.payload.orderId);
          removeAction(action.id);
          useConnectionStore.getState().setStatus("Connected");
        } else if (action.type === "COMPLAINT") {
          await postComplaint(action.payload);
          removeAction(action.id);
          useConnectionStore.getState().setStatus("Connected");
        }
      } catch (err: any) {
        // If network error, back off
        const attempts = action.attempts + 1;
        const delayMs = Math.min(1000 * Math.pow(2, attempts), 30_000);
        const nextTryAt = new Date(Date.now() + delayMs).toISOString();
        updateActionAttempt(action.id, attempts, nextTryAt);
        useConnectionStore.getState().setStatus("Offline");
      }
    }

    useConnectionStore.getState().setQueuedCount(countPendingActions());
  } finally {
    isProcessing = false;
  }
}

export function startQueueWorker(intervalMs: number = 3000): void {
  if (workerInterval) return;
  processQueue();
  workerInterval = setInterval(() => {
    processQueue();
  }, intervalMs);
}

export function stopQueueWorker(): void {
  if (workerInterval) {
    clearInterval(workerInterval);
    workerInterval = null;
  }
}
