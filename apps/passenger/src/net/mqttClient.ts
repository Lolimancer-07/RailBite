import mqtt from "mqtt";
import { topics } from "@railbite/shared";
import { getMqttWsUrl, netConfig } from "./config.js";
import { updateLocalOrderStatus } from "../db/queries.js";
import { useConnectionStore } from "../store/connection.js";

type StatusListener = (orderId: string, status: string, data: any) => void;

class PassengerMqttClient {
  private client: mqtt.MqttClient | null = null;
  private subscribedOrders = new Set<string>();
  private statusListeners = new Set<StatusListener>();
  private clientId = `p-${Math.random().toString(36).substring(2, 10)}`;

  connect(): void {
    if (this.client?.connected) return;

    try {
      const url = getMqttWsUrl();
      this.client = mqtt.connect(url, {
        clientId: this.clientId,
        reconnectPeriod: 3000,
        connectTimeout: 5000,
      });

      this.client.on("connect", () => {
        useConnectionStore.getState().setStatus("Connected");
        // Re-subscribe to all active orders
        for (const orderId of this.subscribedOrders) {
          this.subscribeToOrder(orderId);
        }
      });

      this.client.on("message", (topic: string, payload: any) => {
        try {
          const data = JSON.parse(payload.toString());
          const match = topic.match(/\/orders\/([^/]+)\/status$/);
          if (match && match[1]) {
            const orderId = match[1];
            updateLocalOrderStatus(orderId, data.status as any, data.updatedAt);
            for (const listener of this.statusListeners) {
              listener(orderId, data.status, data);
            }
          }
        } catch {
          // ignore parse error
        }
      });

      this.client.on("offline", () => {
        useConnectionStore.getState().setStatus("Offline");
      });

      this.client.on("error", () => {
        useConnectionStore.getState().setStatus("Offline");
      });
    } catch {
      useConnectionStore.getState().setStatus("Offline");
    }
  }

  subscribeToOrder(orderId: string): void {
    this.subscribedOrders.add(orderId);
    if (this.client?.connected) {
      const t = topics(netConfig.tripId);
      this.client.subscribe(t.orderStatus(orderId), { qos: 1 });
    }
  }

  onStatusChange(listener: StatusListener): () => void {
    this.statusListeners.add(listener);
    return () => {
      this.statusListeners.delete(listener);
    };
  }

  disconnect(): void {
    if (this.client) {
      this.client.end();
      this.client = null;
    }
  }
}

export const passengerMqtt = new PassengerMqttClient();
