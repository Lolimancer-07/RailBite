import React, { useEffect, useState, useMemo } from "react";
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
} from "react-native";
import { useRouter } from "expo-router";
import type { MenuItem } from "@railbite/shared";
import { fetchMenu } from "../src/net/restClient.js";
import { getCachedMenuItems, saveMenuItems } from "../src/db/queries.js";
import { useCartStore } from "../src/store/cart.js";
import { useSessionStore } from "../src/store/session.js";

export default function MenuScreen() {
  const router = useRouter();
  const session = useSessionStore((s) => s.session);
  const { items: cartMap, addItem, removeItem, totalItemsCount, totalPaise } = useCartStore();

  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedCategory, setSelectedCategory] = useState<string>("ALL");

  useEffect(() => {
    async function loadMenu() {
      // 1. First show locally cached items
      const cached = getCachedMenuItems();
      if (cached.length > 0) {
        setMenuItems(cached);
        setLoading(false);
      }

      // 2. Fetch fresh items from edge server
      try {
        const fresh = await fetchMenu();
        setMenuItems(fresh);
        saveMenuItems(fresh);
      } catch (err) {
        // If fetch fails, we stay on cached items
      } finally {
        setLoading(false);
      }
    }

    loadMenu();
  }, []);

  const categories = useMemo(() => {
    const cats = new Set(menuItems.map((m) => m.category));
    return ["ALL", ...Array.from(cats)];
  }, [menuItems]);

  const filteredItems = useMemo(() => {
    if (selectedCategory === "ALL") return menuItems;
    return menuItems.filter((m) => m.category === selectedCategory);
  }, [menuItems, selectedCategory]);

  const cartCount = totalItemsCount();
  const cartTotal = totalPaise();

  return (
    <View style={styles.container}>
      {/* Passenger Seat Header */}
      <View style={styles.seatHeader}>
        <Text style={styles.seatText}>
          Coach {session?.coach || "—"} • Berth {session?.berth || "—"} • {session?.diners || 1} Diner(s)
        </Text>
        <TouchableOpacity onPress={() => router.push("/orders")}>
          <Text style={styles.myOrdersLink}>My Orders →</Text>
        </TouchableOpacity>
      </View>

      {/* Categories Filter */}
      <View style={styles.categoryContainer}>
        <FlatList
          horizontal
          showsHorizontalScrollIndicator={false}
          data={categories}
          keyExtractor={(item) => item}
          renderItem={({ item }) => (
            <TouchableOpacity
              style={[
                styles.categoryChip,
                selectedCategory === item && styles.categoryChipSelected,
              ]}
              onPress={() => setSelectedCategory(item)}
            >
              <Text
                style={[
                  styles.categoryText,
                  selectedCategory === item && styles.categoryTextSelected,
                ]}
              >
                {item}
              </Text>
            </TouchableOpacity>
          )}
        />
      </View>

      {/* Menu Item List */}
      {loading && menuItems.length === 0 ? (
        <ActivityIndicator size="large" color="#1e3a8a" style={{ marginTop: 40 }} />
      ) : (
        <FlatList
          data={filteredItems}
          keyExtractor={(item) => item.id}
          contentContainerStyle={{ paddingBottom: 100 }}
          renderItem={({ item }) => {
            const inCart = cartMap.get(item.id)?.qty || 0;
            const isSoldOut = item.stock <= 0;

            return (
              <View style={styles.menuCard}>
                <View style={styles.cardLeft}>
                  <View style={styles.badgeRow}>
                    <Text
                      style={[
                        styles.trackBadge,
                        item.track === "READY" ? styles.readyBadge : styles.cookBadge,
                      ]}
                    >
                      {item.track === "READY" ? "⚡ INSTANT" : "🍳 COOK TO ORDER"}
                    </Text>
                    {isSoldOut ? <Text style={styles.soldOutBadge}>SOLD OUT</Text> : null}
                  </View>
                  <Text style={styles.itemName}>{item.name}</Text>
                  <Text style={styles.itemPrice}>₹{item.mrpPaise / 100}</Text>
                  {item.prepMinutes > 0 ? (
                    <Text style={styles.prepTime}>~{item.prepMinutes} mins prep</Text>
                  ) : null}
                </View>

                <View style={styles.cardRight}>
                  {isSoldOut ? (
                    <Text style={styles.soldOutText}>Out of Stock</Text>
                  ) : inCart > 0 ? (
                    <View style={styles.qtyControls}>
                      <TouchableOpacity
                        style={styles.qtyBtn}
                        onPress={() => removeItem(item.id)}
                      >
                        <Text style={styles.qtyBtnText}>-</Text>
                      </TouchableOpacity>
                      <Text style={styles.qtyText}>{inCart}</Text>
                      <TouchableOpacity
                        style={styles.qtyBtn}
                        onPress={() => addItem(item)}
                      >
                        <Text style={styles.qtyBtnText}>+</Text>
                      </TouchableOpacity>
                    </View>
                  ) : (
                    <TouchableOpacity
                      style={styles.addBtn}
                      onPress={() => addItem(item)}
                    >
                      <Text style={styles.addBtnText}>ADD</Text>
                    </TouchableOpacity>
                  )}
                </View>
              </View>
            );
          }}
        />
      )}

      {/* Floating Bottom Cart Bar */}
      {cartCount > 0 ? (
        <View style={styles.cartBar}>
          <View>
            <Text style={styles.cartBarCount}>
              {cartCount} item{cartCount > 1 ? "s" : ""} in tray
            </Text>
            <Text style={styles.cartBarTotal}>Total: ₹{cartTotal / 100}</Text>
          </View>
          <TouchableOpacity
            style={styles.viewTrayBtn}
            onPress={() => router.push("/cart")}
          >
            <Text style={styles.viewTrayText}>View Tray →</Text>
          </TouchableOpacity>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#f8fafc",
  },
  seatHeader: {
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 16,
    paddingVertical: 10,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  seatText: {
    fontSize: 13,
    fontWeight: "600",
    color: "#334155",
  },
  myOrdersLink: {
    fontSize: 13,
    fontWeight: "bold",
    color: "#1e3a8a",
  },
  categoryContainer: {
    paddingVertical: 10,
    paddingHorizontal: 12,
    backgroundColor: "#ffffff",
    borderBottomWidth: 1,
    borderBottomColor: "#e2e8f0",
  },
  categoryChip: {
    paddingVertical: 6,
    paddingHorizontal: 14,
    borderRadius: 20,
    backgroundColor: "#f1f5f9",
    marginRight: 8,
  },
  categoryChipSelected: {
    backgroundColor: "#1e3a8a",
  },
  categoryText: {
    fontSize: 13,
    fontWeight: "600",
    color: "#64748b",
  },
  categoryTextSelected: {
    color: "#ffffff",
  },
  menuCard: {
    flexDirection: "row",
    backgroundColor: "#ffffff",
    padding: 16,
    marginHorizontal: 12,
    marginTop: 10,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: "#e2e8f0",
    justifyContent: "space-between",
    alignItems: "center",
  },
  cardLeft: {
    flex: 1,
  },
  badgeRow: {
    flexDirection: "row",
    gap: 6,
    marginBottom: 4,
  },
  trackBadge: {
    fontSize: 10,
    fontWeight: "bold",
    paddingVertical: 2,
    paddingHorizontal: 6,
    borderRadius: 4,
    overflow: "hidden",
  },
  readyBadge: {
    backgroundColor: "#dcfce7",
    color: "#166534",
  },
  cookBadge: {
    backgroundColor: "#fed7aa",
    color: "#9a3412",
  },
  soldOutBadge: {
    backgroundColor: "#fee2e2",
    color: "#991b1b",
    fontSize: 10,
    fontWeight: "bold",
    paddingVertical: 2,
    paddingHorizontal: 6,
    borderRadius: 4,
  },
  itemName: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#0f172a",
    marginTop: 2,
  },
  itemPrice: {
    fontSize: 15,
    fontWeight: "bold",
    color: "#1e3a8a",
    marginTop: 4,
  },
  prepTime: {
    fontSize: 12,
    color: "#94a3b8",
    marginTop: 2,
  },
  cardRight: {
    marginLeft: 12,
  },
  addBtn: {
    backgroundColor: "#1e3a8a",
    paddingVertical: 8,
    paddingHorizontal: 18,
    borderRadius: 6,
  },
  addBtnText: {
    color: "#ffffff",
    fontWeight: "bold",
    fontSize: 14,
  },
  qtyControls: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#f1f5f9",
    borderRadius: 6,
    borderWidth: 1,
    borderColor: "#cbd5e1",
  },
  qtyBtn: {
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  qtyBtnText: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#1e3a8a",
  },
  qtyText: {
    fontSize: 14,
    fontWeight: "bold",
    paddingHorizontal: 4,
    color: "#0f172a",
  },
  soldOutText: {
    color: "#94a3b8",
    fontSize: 12,
    fontStyle: "italic",
  },
  cartBar: {
    position: "absolute",
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: "#0f172a",
    paddingHorizontal: 20,
    paddingVertical: 14,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  cartBarCount: {
    color: "#94a3b8",
    fontSize: 12,
  },
  cartBarTotal: {
    color: "#ffffff",
    fontSize: 18,
    fontWeight: "bold",
  },
  viewTrayBtn: {
    backgroundColor: "#2563eb",
    paddingVertical: 10,
    paddingHorizontal: 18,
    borderRadius: 6,
  },
  viewTrayText: {
    color: "#ffffff",
    fontSize: 14,
    fontWeight: "bold",
  },
});
