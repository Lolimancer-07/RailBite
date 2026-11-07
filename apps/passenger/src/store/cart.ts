import { create } from "zustand";
import type { MenuItem } from "@railbite/shared";

export interface CartItem {
  item: MenuItem;
  qty: number;
}

interface CartState {
  items: Map<string, CartItem>;
  paymentMode: "COD" | "UPI_QUEUED";
  addItem: (item: MenuItem) => void;
  removeItem: (itemId: string) => void;
  setQty: (itemId: string, qty: number) => void;
  setPaymentMode: (mode: "COD" | "UPI_QUEUED") => void;
  clearCart: () => void;
  totalPaise: () => number;
  totalItemsCount: () => number;
}

export const useCartStore = create<CartState>((set, get) => ({
  items: new Map(),
  paymentMode: "COD",

  addItem: (item) => {
    set((state) => {
      const next = new Map(state.items);
      const existing = next.get(item.id);
      if (existing) {
        if (existing.qty < 10) {
          next.set(item.id, { item, qty: existing.qty + 1 });
        }
      } else {
        next.set(item.id, { item, qty: 1 });
      }
      return { items: next };
    });
  },

  removeItem: (itemId) => {
    set((state) => {
      const next = new Map(state.items);
      const existing = next.get(itemId);
      if (existing && existing.qty > 1) {
        next.set(itemId, { item: existing.item, qty: existing.qty - 1 });
      } else {
        next.delete(itemId);
      }
      return { items: next };
    });
  },

  setQty: (itemId, qty) => {
    set((state) => {
      const next = new Map(state.items);
      if (qty <= 0) {
        next.delete(itemId);
      } else {
        const existing = next.get(itemId);
        if (existing) {
          next.set(itemId, { item: existing.item, qty: Math.min(qty, 10) });
        }
      }
      return { items: next };
    });
  },

  setPaymentMode: (paymentMode) => set({ paymentMode }),

  clearCart: () => set({ items: new Map() }),

  totalPaise: () => {
    let total = 0;
    for (const { item, qty } of get().items.values()) {
      total += item.mrpPaise * qty;
    }
    return total;
  },

  totalItemsCount: () => {
    let count = 0;
    for (const { qty } of get().items.values()) {
      count += qty;
    }
    return count;
  },
}));

