import pino from "pino";
import { OrderStatus, type Order } from "@railbite/shared";
import type { OrderRepo } from "../db/repos/orderRepo.js";
import type { StaffRepo } from "../db/repos/staffRepo.js";
import type { MqttPublisher } from "../mqtt/publisher.js";
import type { OrderService } from "../services/orderService.js";
import { findBestCaptain } from "./zones.js";
import { CookBatcher } from "./batching.js";

const logger = pino({ name: "dispatcher" });

export class Dispatcher {
  private activeTimers = new Map<string, NodeJS.Timeout>();
  private expiryTimers = new Map<string, NodeJS.Timeout>();
  private batcher = new CookBatcher(4, 90_000);

  constructor(
    private orderRepo: OrderRepo,
    private staffRepo: StaffRepo,
    private publisher: MqttPublisher,
    private orderService: OrderService,
  ) {}

  cancelTimer(orderId: string): void {
    const timer = this.activeTimers.get(orderId);
    if (timer) {
      clearTimeout(timer);
      this.activeTimers.delete(orderId);
    }
    const expiry = this.expiryTimers.get(orderId);
    if (expiry) {
      clearTimeout(expiry);
      this.expiryTimers.delete(orderId);
    }
  }

  async dispatchOrder(
    order: Order & { items?: Array<{ itemId: string; qty: number; unitPricePaise: number; name?: string }> },
    attempt: number = 1,
    excludedCaptainIds: string[] = [],
  ): Promise<void> {
    this.cancelTimer(order.id);

    const captains = this.staffRepo.listCaptains();
    const assignedAt = new Date().toISOString();
    const expiresAt = new Date(Date.now() + 60_000).toISOString();

    // Find best captain
    let captain = findBestCaptain(order.coach, captains, excludedCaptainIds);

    // If attempt >= 3 or no captain found in zone, alert all online captains
    const isBroadcast = attempt >= 3 || !captain;

    const alertPayload = {
      orderId: order.id,
      coach: order.coach,
      berth: order.berth,
      items: (order.items || []).map((i) => ({
        name: i.name || "Item",
        qty: i.qty,
        unitPricePaise: i.unitPricePaise,
      })),
      total: order.totalPaise,
      track: order.track,
      attempt,
      expiresAt,
      pnr: order.pnr,
      diners: order.diners,
      verified: order.verified,
    };

    if (order.status !== OrderStatus.ASSIGNED) {
      try {
        await this.orderService.transition(order.id, OrderStatus.ASSIGNED);
      } catch (err) {
        logger.error({ err, orderId: order.id }, "Failed to transition order to ASSIGNED");
        return;
      }
    }

    if (isBroadcast) {
      logger.info({ orderId: order.id, attempt }, "Re-broadcasting order to ALL captains");
      for (const c of captains) {
        if (c.online) {
          this.orderRepo.createAssignment(order.id, c.id, attempt, assignedAt);
          await this.publisher.publishCaptainAlert(c.id, alertPayload);
        }
      }

      // Start 10-minute maximum expiry timer if not already set
      if (!this.expiryTimers.has(order.id)) {
        const expiryTimer = setTimeout(async () => {
          this.cancelTimer(order.id);
          const current = this.orderRepo.findById(order.id);
          if (current && current.status === OrderStatus.ASSIGNED) {
            logger.warn({ orderId: order.id }, "Order expired after 10 minutes without acceptance");
            try {
              await this.orderService.transition(order.id, OrderStatus.EXPIRED);
            } catch (e) {
              logger.error({ e }, "Error setting EXPIRED");
            }
          }
        }, 10 * 60_000);
        this.expiryTimers.set(order.id, expiryTimer);
      }
    } else if (captain) {
      this.orderRepo.createAssignment(order.id, captain.id, attempt, assignedAt);
      await this.publisher.publishCaptainAlert(captain.id, alertPayload);
      logger.info(
        { orderId: order.id, captainId: captain.id, coach: order.coach, attempt },
        "Order assigned to captain",
      );
    }

    // 60-second countdown timer for captain acceptance
    const timer = setTimeout(async () => {
      this.activeTimers.delete(order.id);
      const current = this.orderRepo.findById(order.id);
      if (current && current.status === OrderStatus.ASSIGNED) {
        logger.info({ orderId: order.id, attempt }, "60s accept timer expired, rebroadcasting");
        const nextExcluded = captain ? [...excludedCaptainIds, captain.id] : excludedCaptainIds;
        await this.dispatchOrder(current, attempt + 1, nextExcluded);
      }
    }, 60_000);

    this.activeTimers.set(order.id, timer);
  }

  async handleRejection(orderId: string, captainId: string): Promise<void> {
    this.cancelTimer(orderId);
    const order = this.orderRepo.findById(orderId);
    if (!order || order.status !== OrderStatus.ASSIGNED) return;

    logger.info({ orderId, captainId }, "Captain rejected order, finding next captain");
    const latestAssignment = this.orderRepo.getLatestAssignment(orderId);
    const nextAttempt = (latestAssignment?.attempt || 1) + 1;
    await this.dispatchOrder(order, nextAttempt, [captainId]);
  }

  handleCookReady(order: Order): void {
    // Cook track order is ready: add to batcher
    this.batcher.addOrder(order.coach, order.id, async (_coach, orderIds) => {
      for (const oId of orderIds) {
        const fullOrder = this.orderRepo.findById(oId);
        if (fullOrder && (fullOrder.status === OrderStatus.READY || fullOrder.status === OrderStatus.PLACED)) {
          await this.dispatchOrder(fullOrder);
        }
      }
    });
  }

  recoverPendingTimers(): void {
    const pending = this.orderRepo.getAssignedOrdersForRecovery();
    const now = Date.now();
    logger.info({ count: pending.length }, "Recovering pending assigned orders from database");

    for (const item of pending) {
      const assignedTime = new Date(item.assignedAt).getTime();
      const elapsed = now - assignedTime;
      const fullOrder = this.orderRepo.findById(item.id);
      if (!fullOrder) continue;

      if (elapsed >= 60_000) {
        logger.info({ orderId: item.id, elapsed }, "Re-arming expired pending assignment immediately");
        this.dispatchOrder(fullOrder, item.attempt + 1, [item.captainId]);
      } else {
        const remaining = 60_000 - elapsed;
        logger.info({ orderId: item.id, remaining }, "Re-arming active timer for pending assignment");
        const timer = setTimeout(() => {
          this.activeTimers.delete(item.id);
          const current = this.orderRepo.findById(item.id);
          if (current && current.status === OrderStatus.ASSIGNED) {
            this.dispatchOrder(current, item.attempt + 1, [item.captainId]);
          }
        }, remaining);
        this.activeTimers.set(item.id, timer);
      }
    }
  }
}
