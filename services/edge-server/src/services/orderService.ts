import type Database from "better-sqlite3";
import crypto from "node:crypto";
import {
  canTransition,
  computeTotal,
  determineTrack,
  OrderStatus,
  Track,
  type Order,
  type OrderCreate,
} from "@railbite/shared";
import { OrderRepo } from "../db/repos/orderRepo.js";
import { MenuRepo } from "../db/repos/menuRepo.js";
import { ManifestRepo } from "../db/repos/manifestRepo.js";
import { StaffRepo } from "../db/repos/staffRepo.js";
import { LedgerRepo } from "../db/repos/ledgerRepo.js";
import { OutboxRepo } from "../db/repos/outboxRepo.js";
import type { FeedbackCreate } from "@railbite/shared";
import type { MqttPublisher } from "../mqtt/publisher.js";
import type { Dispatcher } from "../dispatch/dispatcher.js";

export class StateTransitionError extends Error {
  constructor(public from: string, public to: string, public track: string) {
    super(`Illegal transition: ${from} -> ${to} on track ${track}`);
    this.name = "StateTransitionError";
  }
}

export class DeliveryCodeError extends Error {
  constructor() {
    super("Invalid delivery code — ask the passenger to reveal it");
    this.name = "DeliveryCodeError";
  }
}

export class OrderService {
  private orderRepo: OrderRepo;
  private menuRepo: MenuRepo;
  private manifestRepo: ManifestRepo;
  private staffRepo: StaffRepo;
  private ledgerRepo: LedgerRepo;
  private outboxRepo: OutboxRepo;
  private dispatcher: Dispatcher | null = null;

  constructor(
    private db: Database.Database,
    private publisher: MqttPublisher,
  ) {
    this.orderRepo = new OrderRepo(db);
    this.menuRepo = new MenuRepo(db);
    this.manifestRepo = new ManifestRepo(db);
    this.staffRepo = new StaffRepo(db);
    this.ledgerRepo = new LedgerRepo(db);
    this.outboxRepo = new OutboxRepo(db);
  }

  setDispatcher(dispatcher: Dispatcher): void {
    this.dispatcher = dispatcher;
  }

  flushBatches(): void {
    if (this.dispatcher) {
      this.dispatcher.flushBatches();
    }
  }

  getOrder(id: string) {
    return this.orderRepo.findById(id);
  }

  getOrdersByPnr(pnr: string) {
    return this.orderRepo.findByPnr(pnr);
  }

  create(input: OrderCreate): Order {
    // 1. Check idempotency: if order id already exists, return existing order
    const existing = this.orderRepo.findById(input.id);
    if (existing) {
      return existing;
    }

    // 2. Fetch menu items for authoritative server-side pricing & stock
    const menuMap = this.menuRepo.getMenuMap();

    // Check all items exist
    const itemTracks: string[] = [];
    for (const item of input.items) {
      const menuItem = menuMap.get(item.itemId);
      if (!menuItem) {
        throw new Error(`Menu item not found: ${item.itemId}`);
      }
      itemTracks.push(menuItem.track);
    }

    // 3. Compute authoritative total (client prices/totals are ignored completely)
    const pricing = computeTotal(input.items, menuMap);

    // 4. Determine track: any COOK item makes the whole order COOK track
    const track = determineTrack(itemTracks);

    // 5. Check manifest (set verified=0 if passenger not in manifest)
    const isManifested = this.manifestRepo.checkPassenger(input.pnr, input.coach, input.berth);
    const verified = isManifested ? 1 : 0;

    const now = new Date().toISOString();

    // 6. Transaction: check & decrement stock, insert order and items with INSERT OR IGNORE
    const createTx = this.db.transaction(() => {
      // Check & decrement stock for every item
      for (const item of input.items) {
        const decremented = this.menuRepo.decrementStock(item.itemId, item.qty);
        if (!decremented) {
          throw new Error(`Insufficient stock for item: ${item.itemId}`);
        }
      }

      const inserted = this.orderRepo.insertOrder({
        id: input.id,
        pnr: input.pnr,
        coach: input.coach,
        berth: input.berth,
        diners: input.diners,
        track,
        status: OrderStatus.PLACED,
        paymentMode: input.paymentMode,
        totalPaise: pricing.totalPaise,
        source: input.source,
        verified,
        createdAt: input.createdAt || now,
        updatedAt: now,
      });

      if (!inserted) {
        // Race condition: another request inserted this id concurrently
        return;
      }

      this.orderRepo.insertOrderItems(
        input.id,
        pricing.lineItems.map((li) => ({
          itemId: li.itemId,
          qty: li.qty,
          unitPricePaise: li.unitPricePaise,
        })),
      );

      this.outboxRepo.append("ORDER_CREATED", {
        orderId: input.id,
        pnr: input.pnr,
        totalPaise: pricing.totalPaise,
        track,
        createdAt: now,
      });
    });

    createTx();

    const order = this.orderRepo.findById(input.id);
    if (!order) {
      throw new Error(`Failed to retrieve order after creation: ${input.id}`);
    }

    // Publish initial status
    this.publisher.publishOrderStatus(order.id, {
      status: order.status,
      updatedAt: order.updatedAt,
    });

    // 7. Dispatch based on track
    if (track === Track.READY) {
      // READY track: assign immediately
      if (this.dispatcher) {
        this.dispatcher.dispatchOrder(order);
      }
    } else {
      // COOK track: notify kitchen display
      this.publishKitchenQueue();
    }

    return order;
  }

  async transition(
    orderId: string,
    toStatus: (typeof OrderStatus)[keyof typeof OrderStatus],
    metadata?: { captainId?: string; captainName?: string; etaMin?: number },
  ): Promise<Order> {
    const order = this.orderRepo.findById(orderId);
    if (!order) {
      throw new Error(`Order not found: ${orderId}`);
    }

    if (!canTransition(order.status as any, toStatus, order.track)) {
      throw new StateTransitionError(order.status, toStatus, order.track);
    }

    const now = new Date().toISOString();
    this.orderRepo.updateStatus(orderId, toStatus, now);

    // Record in outbox
    this.outboxRepo.append("ORDER_STATUS_CHANGED", {
      orderId,
      from: order.status,
      to: toStatus,
      updatedAt: now,
      ...metadata,
    });

    // Publish retained MQTT status
    await this.publisher.publishOrderStatus(orderId, {
      status: toStatus,
      updatedAt: now,
      captainName: metadata?.captainName,
      etaMin: metadata?.etaMin,
    });

    const updated = this.orderRepo.findById(orderId)!;
    return updated;
  }

  async accept(orderId: string, captainId: string): Promise<Order> {
    const order = this.orderRepo.findById(orderId);
    if (!order) throw new Error(`Order not found: ${orderId}`);

    const captain = this.staffRepo.findById(captainId);
    const now = new Date().toISOString();

    if (this.dispatcher) {
      this.dispatcher.cancelTimer(orderId);
    }

    this.orderRepo.updateAssignmentAccepted(orderId, captainId, now);

    const updated = await this.transition(orderId, OrderStatus.ACCEPTED, {
      captainId,
      captainName: captain?.name,
    });

    return updated;
  }

  async pickup(orderId: string, captainId: string): Promise<Order> {
    const captain = this.staffRepo.findById(captainId);
    const order = this.orderRepo.findById(orderId);
    if (!order) throw new Error(`Order not found: ${orderId}`);

    // FOT-style delivery code: generated when the picker leaves for delivery;
    // passenger reveals it, picker must repeat it on /deliver.
    const deliveryCode = !order.deliveryCode
      ? String(crypto.randomInt(1000, 10000))
      : order.deliveryCode;

    const updated = await this.transition(orderId, OrderStatus.OUT_FOR_DELIVERY, {
      captainId,
      captainName: captain?.name,
    });
    this.orderRepo.setDeliveryCode(orderId, deliveryCode);
    return updated;
  }

  async startPacking(orderId: string, captainId?: string): Promise<Order> {
    const order = await this.transition(orderId, OrderStatus.PACKING, captainId ? { captainId } : undefined);
    return order;
  }

  async reject(orderId: string, captainId: string): Promise<void> {
    const now = new Date().toISOString();
    this.orderRepo.updateAssignmentRejected(orderId, captainId, now);

    if (this.dispatcher) {
      this.dispatcher.handleRejection(orderId, captainId);
    }
  }

  async deliver(
    orderId: string,
    captainId: string,
    cashPaise?: number,
    deliveryCode?: string,
  ): Promise<Order> {
    const order = this.orderRepo.findById(orderId);
    if (!order) throw new Error(`Order not found: ${orderId}`);

    // FOT-style handover: the picker must repeat the passenger's delivery code
    // (unless the order never got one, e.g. hand-migrated flows)
    const storedCode = this.orderRepo.getDeliveryCode(orderId);
    if (storedCode && deliveryCode?.trim() !== storedCode) {
      throw new DeliveryCodeError();
    }

    const now = new Date().toISOString();

    // 1. Transition to DELIVERED
    const updated = await this.transition(orderId, OrderStatus.DELIVERED, { captainId });

    // 2. Mark assignment delivered
    this.orderRepo.updateAssignmentDelivered(orderId, captainId, now);

    // 3. Append-only ledger record. UPI_QUEUED orders settle digitally — no cash hits the
    // ledger unless the picker explicitly reports some (the passenger confirms in-app).
    const amount = cashPaise !== undefined
      ? cashPaise
      : order.paymentMode === "UPI_QUEUED"
        ? 0
        : order.totalPaise;
    this.ledgerRepo.append(orderId, captainId, amount, now);

    return updated;
  }

  /** FOT-style structured feedback, allowed only after DELIVERED */
  submitFeedback(orderId: string, input: FeedbackCreate): {
    received: boolean;
    rating: number | null;
    reasons: string[];
  } {
    const order = this.orderRepo.findById(orderId);
    if (!order) throw new Error(`Order not found: ${orderId}`);

    if (order.status !== OrderStatus.DELIVERED) {
      throw new StateTransitionError(order.status, "FEEDBACK", order.track);
    }

    const saved = {
      received: input.received,
      rating: input.rating ?? null,
      reasons: input.reasons ?? [],
    };
    this.orderRepo.upsertFeedback(orderId, { ...input, now: new Date().toISOString() });
    this.outboxRepo.append("ORDER_FEEDBACK", {
      orderId,
      received: saved.received,
      rating: saved.rating,
      reasons: saved.reasons,
    });
    return saved;
  }

  async confirmCash(orderId: string): Promise<boolean> {
    const confirmed = this.ledgerRepo.confirmByPassenger(orderId);
    if (confirmed) {
      this.outboxRepo.append("CASH_CONFIRMED", { orderId, confirmedAt: new Date().toISOString() });
    }
    return confirmed;
  }

  async cancel(orderId: string): Promise<Order> {
    const order = this.orderRepo.findById(orderId);
    if (!order) throw new Error(`Order not found: ${orderId}`);

    if (order.status !== OrderStatus.PLACED && order.status !== OrderStatus.ASSIGNED) {
      throw new StateTransitionError(order.status, OrderStatus.CANCELLED, order.track);
    }

    if (this.dispatcher) {
      this.dispatcher.cancelTimer(orderId);
    }

    return this.transition(orderId, OrderStatus.CANCELLED);
  }

  async startCooking(orderId: string): Promise<Order> {
    const order = await this.transition(orderId, OrderStatus.COOKING);
    this.publishKitchenQueue();
    return order;
  }

  async readyCooking(orderId: string): Promise<Order> {
    const order = await this.transition(orderId, OrderStatus.READY);
    this.publishKitchenQueue();

    // Pass to dispatcher batcher or immediate dispatch
    if (this.dispatcher) {
      this.dispatcher.handleCookReady(order);
    }

    return order;
  }

  publishKitchenQueue(): void {
    const kitchenOrders = this.orderRepo.listKitchenOrders();
    const formatted = kitchenOrders.map((ko) => {
      const fullOrder = this.orderRepo.findById(ko.orderId);
      return {
        orderId: ko.orderId,
        coach: ko.coach,
        berth: ko.berth,
        status: ko.status,
        pnr: ko.pnr,
        diners: ko.diners,
        createdAt: ko.createdAt,
        items: (fullOrder?.items || []).map((i) => ({
          name: i.name || "Item",
          qty: i.qty,
          prepMinutes: 10,
        })),
      };
    });

    this.publisher.publishKitchenQueue(formatted);
  }
}
