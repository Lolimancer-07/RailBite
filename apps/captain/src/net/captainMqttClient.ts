import mqtt from "mqtt";
import { topics, type MqttOrderAlert } from "@railbite/shared";
import { captainNetConfig } from "./config.js";
import { useAlertsStore } from "../store/alerts.js";
import { triggerForegroundAlert } from "../../modules/alert-foreground-service/index.js";

class CaptainMqttClient {
  private client: mqtt.MqttClient | null = null;
  private currentStaffId: string | null = null;

  connect(staffId: string): void {
    if (this.client?.connected && this.currentStaffId === staffId) return;
    this.currentStaffId = staffId;

    try {
      this.client = mqtt.connect(captainNetConfig.mqttWsUrl, {
        clientId: `c-${staffId}`,
        reconnectPeriod: 2000,
        connectTimeout: 5000,
      });

      this.client.on("connect", () => {
        const t = topics(captainNetConfig.tripId);
        this.client?.subscribe(t.captainAlerts(staffId), { qos: 1 });
      });

      this.client.on("message", (topic, payload) => {
        try {
          const t = topics(captainNetConfig.tripId);
          if (topic === t.captainAlerts(staffId)) {
            const alert = JSON.parse(payload.toString()) as MqttOrderAlert;
            // 1. Update alert state in store
            useAlertsStore.getState().setAlert(alert);

            // 2. Trigger native Android Foreground Service alert with full-screen intent
            triggerForegroundAlert(
              alert.orderId,
              alert.coach,
              String(alert.berth),
              `₹${alert.total / 100}`,
            );
          }
        } catch {
          // ignore parse error
        }
      });
    } catch {
      // ignore
    }
  }

  sendAction(staffId: string, actionPayload: { orderId: string; action: "ACCEPT" | "REJECT" | "PICKED_UP" | "DELIVERED"; cashPaise?: number }): void {
    if (this.client?.connected) {
      const t = topics(captainNetConfig.tripId);
      this.client.publish(
        t.captainActions(staffId),
        JSON.stringify({ ...actionPayload, captainId: staffId }),
        { qos: 1 },
      );
    }
  }

  disconnect(): void {
    if (this.client) {
      this.client.end();
      this.client = null;
    }
  }
}

export const captainMqtt = new CaptainMqttClient();
