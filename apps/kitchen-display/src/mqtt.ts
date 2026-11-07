import mqtt from "mqtt";
import { topics, type MqttKitchenQueueItem, type MqttKitchenAction } from "@railbite/shared";

export const tripId = "TRIP_001";
const t = topics(tripId);

export type KitchenQueueListener = (orders: MqttKitchenQueueItem[]) => void;
export type ComplaintListener = (complaint: any) => void;

class KitchenMqttClient {
  private client: mqtt.MqttClient | null = null;
  private queueListeners = new Set<KitchenQueueListener>();
  private complaintListeners = new Set<ComplaintListener>();

  connect(): void {
    if (this.client?.connected) return;

    // Detect host from current browser location
    const host = typeof window !== "undefined" ? window.location.hostname || "localhost" : "localhost";
    const wsUrl = `ws://${host}:8883`;

    this.client = mqtt.connect(wsUrl, {
      clientId: `k-tablet-${Math.random().toString(36).substring(2, 8)}`,
      reconnectPeriod: 2000,
    });

    this.client.on("connect", () => {
      console.log("[Kitchen] Connected to MQTT broker at", wsUrl);
      this.client?.subscribe(t.kitchenQueue, { qos: 1 });
      this.client?.subscribe(t.complaintsNew, { qos: 1 });
    });

    this.client.on("message", (topic, payload) => {
      try {
        const data = JSON.parse(payload.toString());
        if (topic === t.kitchenQueue) {
          const list = Array.isArray(data) ? (data as MqttKitchenQueueItem[]) : [];
          for (const l of this.queueListeners) l(list);
        } else if (topic === t.complaintsNew) {
          for (const cl of this.complaintListeners) cl(data);
        }
      } catch (err) {
        console.error("Failed to parse kitchen MQTT message", err);
      }
    });
  }

  sendAction(orderId: string, action: "START" | "READY", cookId: string = "k1"): void {
    if (!this.client?.connected) {
      console.warn("MQTT not connected, sending REST action fallback");
      const host = typeof window !== "undefined" ? window.location.hostname || "localhost" : "localhost";
      const actionPath = action === "START" ? "start" : "ready";
      fetch(`http://${host}:8080/kitchen/${orderId}/${actionPath}`, { method: "POST" });
      return;
    }

    const payload: MqttKitchenAction = { orderId, action, cookId };
    this.client.publish(t.kitchenActions, JSON.stringify(payload), { qos: 1 });
  }

  onQueueUpdate(listener: KitchenQueueListener): () => void {
    this.queueListeners.add(listener);
    return () => this.queueListeners.delete(listener);
  }

  onComplaint(listener: ComplaintListener): () => void {
    this.complaintListeners.add(listener);
    return () => this.complaintListeners.delete(listener);
  }
}

export const kitchenMqtt = new KitchenMqttClient();

