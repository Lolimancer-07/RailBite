import { create } from "zustand";
import type { Order } from "@railbite/shared";

interface ActiveOrdersState {
  activeOrders: Order[];
  setActiveOrders: (orders: Order[]) => void;
  addOrder: (order: Order) => void;
  updateOrderStatus: (orderId: string, status: string) => void;
  removeOrder: (orderId: string) => void;
}

export const useActiveOrdersStore = create<ActiveOrdersState>((set) => ({
  activeOrders: [],
  setActiveOrders: (activeOrders) => set({ activeOrders }),
  addOrder: (order) =>
    set((state) => {
      const exists = state.activeOrders.some((o) => o.id === order.id);
      if (exists) return state;
      return { activeOrders: [order, ...state.activeOrders] };
    }),
  updateOrderStatus: (orderId, status) =>
    set((state) => ({
      activeOrders: state.activeOrders.map((o) =>
        o.id === orderId ? { ...o, status: status as any } : o,
      ),
    })),
  removeOrder: (orderId) =>
    set((state) => ({
      activeOrders: state.activeOrders.filter((o) => o.id !== orderId),
    })),
}));

