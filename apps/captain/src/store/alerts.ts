import { create } from "zustand";
import type { MqttOrderAlert } from "@railbite/shared";

interface AlertsState {
  currentAlert: MqttOrderAlert | null;
  secondsRemaining: number;
  setAlert: (alert: MqttOrderAlert | null) => void;
  setSecondsRemaining: (seconds: number) => void;
  clearAlert: () => void;
}

export const useAlertsStore = create<AlertsState>((set) => ({
  currentAlert: null,
  secondsRemaining: 60,
  setAlert: (currentAlert) => set({ currentAlert, secondsRemaining: 60 }),
  setSecondsRemaining: (secondsRemaining) => set({ secondsRemaining }),
  clearAlert: () => set({ currentAlert: null, secondsRemaining: 0 }),
}));

