import { create } from "zustand";
import { getSetting, setSetting } from "../db/queries.js";

export interface SessionData {
  pnr: string;
  coach: string;
  berth: number;
  diners: number;
}

const SESSION_KEY = "passenger_session";

interface SessionState {
  session: SessionData | null;
  hydrated: boolean;
  setSession: (session: SessionData) => void;
  hydrate: () => void;
  clearSession: () => void;
}

export const useSessionStore = create<SessionState>((set) => ({
  session: null,
  hydrated: false,

  setSession: (session) => {
    setSetting(SESSION_KEY, JSON.stringify(session));
    set({ session });
  },

  hydrate: () => {
    if (useSessionStore.getState().hydrated) return;
    try {
      const raw = getSetting(SESSION_KEY);
      const session = raw ? JSON.parse(raw) : null;
      set({ session, hydrated: true });
    } catch {
      set({ session: null, hydrated: true });
    }
  },

  clearSession: () => {
    set({ session: null, hydrated: true });
  },
}));
