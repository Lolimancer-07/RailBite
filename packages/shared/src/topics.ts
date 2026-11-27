import { normalizeTrainNumber } from "./schemas/order.js";

export function topics(trainNumber: string) {
  const prefix = `rb/train/${normalizeTrainNumber(trainNumber)}`;
  return {
    ordersNew: `${prefix}/orders/new`,
    orderStatus: (orderId: string) => `${prefix}/orders/${orderId}/status`,
    orderChat: (orderId: string) => `${prefix}/orders/${orderId}/chat`,
    captainAlerts: (staffId: string) => `${prefix}/captain/${staffId}/alerts`,
    captainActions: (staffId: string) => `${prefix}/captain/${staffId}/actions`,
    kitchenQueue: `${prefix}/kitchen/queue`,
    kitchenActions: `${prefix}/kitchen/actions`,
    complaintsNew: `${prefix}/complaints/new`,
    complaintStatus: (complaintId: string) => `${prefix}/complaints/${complaintId}/status`,
  };
}
export type Topics = ReturnType<typeof topics>;

