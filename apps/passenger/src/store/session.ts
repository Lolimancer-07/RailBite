import { create } from "zustand";

export interface SessionData {
  pnr: string;
  coach: string;
  berth: number;
  diners: number;
}

interface SessionState {
  session: SessionData | null;
  setSession: (session: SessionData) => void;
  clearSession: () => void;
}

export const useSessionStore = create<SessionState>((set) => ({
  session: null,
  setSession: (session) => set({ session }),
  clearSession: () => set({ session: null }),
}));

