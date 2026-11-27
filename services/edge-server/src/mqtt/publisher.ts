import type Aedes from "aedes";
import { EventEmitter } from "node:events";
import { topics } from "@railbite/shared";
import type {
  MqttOrderStatus,
  MqttOrderAlert,
  MqttKitchenQueueItem,
  OrderMessage,
} from "@railbite/shared";

export class MqttPublisher extends EventEmitter {
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

  async publishOrderStatus(
    trainNumber: string,
    orderId: string,
    status: MqttOrderStatus,
    retain: boolean = true,
  ): Promise<void> {
    await this.publish(topics(trainNumber).orderStatus(orderId), status, retain, 1);
  }

  async publishOrderChat(trainNumber: string, orderId: string, message: OrderMessage): Promise<void> {
    await this.publish(topics(trainNumber).orderChat(orderId), message, false, 1);
  }

  async publishCaptainAlert(
    trainNumber: string,
    captainId: string,
    alert: MqttOrderAlert,
  ): Promise<void> {
    await this.publish(topics(trainNumber).captainAlerts(captainId), alert, false, 1);
  }

  async publishKitchenQueue(
    trainNumber: string,
    queue: MqttKitchenQueueItem[],
    retain: boolean = true,
  ): Promise<void> {
    await this.publish(topics(trainNumber).kitchenQueue, queue, retain, 1);
  }

  async publishComplaintStatus(
    trainNumber: string,
    complaintId: string,
    status: { status: string },
  ): Promise<void> {
    await this.publish(topics(trainNumber).complaintStatus(complaintId), status, true, 1);
  }
}
