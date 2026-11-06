import type { MenuItem } from "./schemas/menu.js";

export interface PricingItem {
  itemId: string;
  qty: number;
}

export interface PricingResult {
  totalPaise: number;
  lineItems: Array<{
    itemId: string;
    qty: number;
    unitPricePaise: number;
    linePaise: number;
  }>;
}

/**
 * Compute total from items list using the server-authoritative menu.
 * Always uses integer paise. Never uses floats.
 */
export function computeTotal(
  items: PricingItem[],
  menu: Map<string, Pick<MenuItem, "mrpPaise">>,
): PricingResult {
  let totalPaise = 0;
  const lineItems: PricingResult["lineItems"] = [];

  for (const item of items) {
    const menuItem = menu.get(item.itemId);
    if (!menuItem) {
      throw new Error(`Menu item not found: ${item.itemId}`);
    }
    const linePaise = menuItem.mrpPaise * item.qty;
    totalPaise += linePaise;
    lineItems.push({
      itemId: item.itemId,
      qty: item.qty,
      unitPricePaise: menuItem.mrpPaise,
      linePaise,
    });
  }

  return { totalPaise, lineItems };
}

