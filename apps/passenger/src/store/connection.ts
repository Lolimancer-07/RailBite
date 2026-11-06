import { create } from "zustand";

export type ConnectionStatus = "Connected" | "Searching" | "Offline";

interface ConnectionState {
  status: ConnectionStatus;
  queuedCount: number;
  setStatus: (status: ConnectionStatus) => void;
  setQueuedCount: (count: number) => void;
}

export const useConnectionStore = create<ConnectionState>((set) => ({
  status: "Searching",
  queuedCount: 0,
  setStatus: (status) => set({ status }),
  setQueuedCount: (queuedCount) => set({ queuedCount }),
}));
