import crypto from "node:crypto";
import type Database from "better-sqlite3";
import { getTier, type ComplaintCreate, type Complaint } from "@railbite/shared";
import { toDb, type Db } from "../db/db.js";
import { ComplaintRepo } from "../db/repos/complaintRepo.js";
import { OrderRepo } from "../db/repos/orderRepo.js";
import { OutboxRepo } from "../db/repos/outboxRepo.js";
import type { MqttPublisher } from "../mqtt/publisher.js";

export class ComplaintService {
  private db: Db;
  private complaintRepo: ComplaintRepo;
  private orderRepo: OrderRepo;
  private outboxRepo: OutboxRepo;

  constructor(
    db: Db | Database.Database,
    private publisher: MqttPublisher,
  ) {
    this.db = toDb(db);
    this.complaintRepo = new ComplaintRepo(this.db);
    this.orderRepo = new OrderRepo(this.db);
    this.outboxRepo = new OutboxRepo(this.db);
  }

  async create(input: ComplaintCreate): Promise<Complaint> {
    const order = await this.orderRepo.findById(input.orderId);
    if (!order) {
      throw new Error(`Order not found for complaint: ${input.orderId}`);
    }

    const tier = getTier(input.type);
    const now = new Date().toISOString();

    if (tier === 2) {
      // Tier 2: Check rate limit (max 3 Tier 2 per PNR per trip)
      const count = await this.complaintRepo.countTier2ForPnr(order.pnr);
      if (count >= 3) {
        throw new Error("Rate limit exceeded: maximum 3 regulatory complaints per PNR per trip");
      }

      // Must have photo sha256
      const photoSha256 =
        input.photoSha256 ||
        (input.photoBase64
          ? crypto.createHash("sha256").update(input.photoBase64).digest("hex")
          : undefined);

      await this.complaintRepo.create({
        id: input.id,
        orderId: input.orderId,
        tier: 2,
        type: input.type,
        text: input.text,
        photoSha256,
        photoTs: input.photoTs || now,
        status: "OPEN",
        createdAt: input.createdAt || now,
      });

      // Write to outbox as COMPLAINT_ESCALATION
      await this.outboxRepo.append("COMPLAINT_ESCALATION", {
        complaintId: input.id,
        orderId: input.orderId,
        pnr: order.pnr,
        coach: order.coach,
        berth: order.berth,
        type: input.type,
        photoSha256,
        createdAt: now,
      });
    } else {
      // Tier 1: Target seat-side replacement within 15 minutes
      const dueAt = new Date(Date.now() + 15 * 60_000).toISOString();

      await this.complaintRepo.create({
        id: input.id,
        orderId: input.orderId,
        tier: 1,
        type: input.type,
        text: input.text,
        status: "OPEN",
        dueAt,
        createdAt: input.createdAt || now,
      });

      // Write to outbox
      await this.outboxRepo.append("COMPLAINT_TIER1", {
        complaintId: input.id,
        orderId: input.orderId,
        pnr: order.pnr,
        type: input.type,
        dueAt,
      });
    }

    const complaint = await this.complaintRepo.findById(input.id);
    if (!complaint) throw new Error(`Failed to load complaint after create: ${input.id}`);
    await this.publisher.publishComplaintStatus(complaint.id, { status: complaint.status });
    return complaint;
  }

  async getComplaint(id: string): Promise<Complaint | null> {
    return this.complaintRepo.findById(id);
  }

  async listActive(): Promise<Complaint[]> {
    return this.complaintRepo.listActive();
  }
}
