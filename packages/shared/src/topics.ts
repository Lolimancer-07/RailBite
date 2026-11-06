export function topics(tripId: string) {
  const prefix = `rb/${tripId}`;
  return {
    ordersNew: `${prefix}/orders/new`,
    orderStatus: (orderId: string) => `${prefix}/orders/${orderId}/status`,
    captainAlerts: (captainId: string) => `${prefix}/captain/${captainId}/alerts`,
    captainActions: (captainId: string) => `${prefix}/captain/${captainId}/actions`,
    kitchenQueue: `${prefix}/kitchen/queue`,
    kitchenActions: `${prefix}/kitchen/actions`,
    complaintsNew: `${prefix}/complaints/new`,
    complaintStatus: (complaintId: string) => `${prefix}/complaints/${complaintId}/status`,
  };
}
export type Topics = ReturnType<typeof topics>;

