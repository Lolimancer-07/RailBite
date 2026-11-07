import { create } from "zustand";

export interface StaffSession {
  staffId: string;
  name: string;
  role: "CAPTAIN" | "COOK";
  zoneCoaches: string[];
  token: string;
}

interface AuthState {
  staff: StaffSession | null;
  setStaff: (staff: StaffSession) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  staff: null,
  setStaff: (staff) => set({ staff }),
  logout: () => set({ staff: null }),
}));

