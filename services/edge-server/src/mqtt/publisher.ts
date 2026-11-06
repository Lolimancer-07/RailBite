import type Aedes from "aedes";
import { EventEmitter } from "node:events";
import { topics } from "@railbite/shared";
import type {
  MqttOrderStatus,
  MqttOrderAlert,
  MqttKitchenQueueItem,
} from "@railbite/shared";
import { config } from "../config.js";

export class MqttPublisher extends EventEmitter {
  private t = topics(config.tripId);

  constructor(private aedes: Aedes) {
    super();
  }

  publish(topic: string, payload: unknown, retain: boolean = false, qos: 0 | 1 = 1): Promise<void> {
    this.emit("published", { topic, payload, retain, qos });
    return new Promise((resolve, reject) => {
      const message = typeof payload === "string" ? payload : JSON.stringify(payload);
      this.aedes.publish(
        {
          topic,
          payload: Buffer.from(message),
          retain,
          qos,
          cmd: "publish",
          dup: false,
        },
        (error) => {
          if (error) reject(error);
          else resolve();
        },
      );
    });
  }

  async publishOrderStatus(orderId: string, status: MqttOrderStatus, retain: boolean = true): Promise<void> {
    await this.publish(this.t.orderStatus(orderId), status, retain, 1);
  }

  async publishCaptainAlert(captainId: string, alert: MqttOrderAlert): Promise<void> {
    await this.publish(this.t.captainAlerts(captainId), alert, false, 1);
  }

  async publishKitchenQueue(queue: MqttKitchenQueueItem[], retain: boolean = true): Promise<void> {
    await this.publish(this.t.kitchenQueue, queue, retain, 1);
  }

  async publishComplaintStatus(complaintId: string, status: { status: string }): Promise<void> {
    await this.publish(this.t.complaintStatus(complaintId), status, true, 1);
  }
}
