import { NativeModules } from "react-native";

const AlertService = NativeModules?.AlertForegroundService || {
  startAlert(_orderId: string, _coach: string, _berth: string, _total: string) {
    // Web / dev fallback
  },
  stopAlert() {
    // Web / dev fallback
  },
};

export function triggerForegroundAlert(orderId: string, coach: string, berth: string, total: string): void {
  try {
    AlertService.startAlert(orderId, coach, berth, total);
  } catch {
    // Ignore in non-Android environments
  }
}

export function dismissForegroundAlert(): void {
  try {
    AlertService.stopAlert();
  } catch {
    // Ignore in non-Android environments
  }
}
