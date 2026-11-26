import { create } from "zustand";
import { deleteSetting, getSetting, setSetting } from "../db/queries.js";

export interface SessionData {
  pnr: string;
  coach: string;
  berth: number;
  diners: number;
}

const SESSION_KEY = "passenger_session";
const PHONE_KEY = "passenger_phone";

interface SessionState {
  session: SessionData | null;
  /** Verified phone number in E.164 form, e.g. +919876543210 */
  phone: string | null;
  hydrated: boolean;
  setSession: (session: SessionData) => void;
  setPhone: (phone: string) => void;
  hydrate: () => void;
  clearSession: () => void;
  clearPhone: () => void;
}

export const useSessionStore = create<SessionState>((set) => ({
  session: null,
  phone: null,
  hydrated: false,

  setSession: (session) => {
    setSetting(SESSION_KEY, JSON.stringify(session));
    set({ session });
  },

  setPhone: (phone) => {
    setSetting(PHONE_KEY, phone);
    set({ phone });
  },

  hydrate: () => {
    if (useSessionStore.getState().hydrated) return;
    try {
      const raw = getSetting(SESSION_KEY);
      const session = raw ? JSON.parse(raw) : null;
      const phone = getSetting(PHONE_KEY) || null;
      set({ session, phone, hydrated: true });
    } catch {
      set({ session: null, phone: null, hydrated: true });
    }
  },

  clearSession: () => {
    set({ session: null, hydrated: true });
  },

  clearPhone: () => {
    deleteSetting(PHONE_KEY);
    set({ phone: null });
  },
}));
