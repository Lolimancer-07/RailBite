import React, { useEffect, useMemo, useState, useCallback } from "react";
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  TextInput,
  RefreshControl,
  Modal,
} from "react-native";
import { useRouter } from "expo-router";
import type { MenuItem } from "@railbite/shared";
import { fetchMenu } from "../../src/net/restClient.js";
import { getCachedMenuItems, saveMenuItems, getSetting, setSetting } from "../../src/db/queries.js";
import { useCartStore } from "../../src/store/cart.js";
import { useSessionStore } from "../../src/store/session.js";
import { colors } from "../../src/ui/theme.js";

// Emoji "images" — no copyrighted assets
function emojiForItem(item: { name: string; category: string }): string {
  const n = item.name.toLowerCase();
  if (n.includes("chai")) return "🍵";
  if (n.includes("coffee")) return "☕";
  if (n.includes("water")) return "💧";
  if (n.includes("cola") || n.includes("cold drink") || n.includes("buttermilk") || n.includes("lassi")) return "🥤";
  if (n.includes("biscuit") || n.includes("cookie")) return "🍪";
  if (n.includes("chip")) return "🥔";
  if (n.includes("samosa") || n.includes("bonda") || n.includes("puff") || n.includes("cake")) return "🥟";
  if (n.includes("biryani")) return "🍛";
  if (n.includes("sandwich")) return "🥪";
  if (n.includes("noodle")) return "🍜";
  if (n.includes("paratha") || n.includes("roti")) return "🫓";
  if (n.includes("soup")) return "🥣";
  switch (item.category) {
    case "BEVERAGES": return "🥤";
    case "WATER": return "💧";
    case "MEALS": return "🍱";
    case "BREAKFAST": return "🍳";
    default: return "🍿";
  }
}

const CATEGORY_META: Record<string, { label: string; emoji: string }> = {
  ALL: { label: "All", emoji: "🛒" },
  BEVERAGES: { label: "Beverages", emoji: "🥤" },
  SNACKS: { label: "Snacks", emoji: "🍿" },
  MEALS: { label: "Meals", emoji: "🍱" },
  BREAKFAST: { label: "Breakfast", emoji: "🍳" },
  WATER: { label: "Water", emoji: "💧" },
};

function rupees(paise: number): string {
  const v = paise / 100;
  return Number.isInteger(v) ? `₹${v}` : `₹${v.toFixed(2)}`;
}

export default function HomeScreen() {
  const router = useRouter();
  const session = useSessionStore((s) => s.session);
  const { items: cartMap, addItem, removeItem, totalItemsCount, totalPaise } = useCartStore();

  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState<string>("ALL");
  const [search, setSearch] = useState("");
  const [howItWorksOpen, setHowItWorksOpen] = useState(false);

  useEffect(() => {
    async function loadMenu() {
      const cached = getCachedMenuItems();
      if (cached.length > 0) {
        setMenuItems(cached);
        setLoading(false);
      }
      try {
        const fresh = await fetchMenu();
        setMenuItems(fresh);
        saveMenuItems(fresh);
      } catch (err) {
        // If fetch fails, we stay on cached items; stale banner shows connection state
      } finally {
        setLoading(false);
      }
    }

    // FOT-style "How it works" — auto-open the modal once per install (persisted dismissal)
    if (!getSetting("how_it_works_dismissed")) {
      setHowItWorksOpen(true);
    }

    loadMenu();
  }, []);

  const refresh = useCallback(async () => {
    setRefreshing(true);
    try {
      const fresh = await fetchMenu();
      setMenuItems(fresh);
      saveMenuItems(fresh);
    } catch {
      // keep cache
    } finally {
      setRefreshing(false);
    }
  }, []);

  const categories = useMemo(() => {
    const cats = new Set(menuItems.map((m) => m.category));
    return ["ALL", ...Array.from(cats)];
  }, [menuItems]);

  const filteredItems = useMemo(() => {
    let items = menuItems;
    if (selectedCategory !== "ALL") items = items.filter((m) => m.category === selectedCategory);
    const q = search.trim().toLowerCase();
    if (q) items = items.filter((m) => m.name.toLowerCase().includes(q));
    return items;
  }, [menuItems, selectedCategory, search]);

  const cartCount = totalItemsCount();
  const cartTotal = totalPaise();

  const seatChip = session ? `Coach ${session.coach} • Seat ${session.berth}` : "Set your seat";

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <View style={styles.headerTop}>
          <View>
            <Text style={styles.logoText}>🚆 railbite</Text>
            <Text style={styles.deliveryLine}>Delivery in ~10 mins</Text>
          </View>
          <View style={{ flexDirection: "row", gap: 8, alignItems: "center" }}>
            <TouchableOpacity style={styles.seatChip} onPress={() => router.push("/onboarding")}>
              <Text style={styles.seatChipText}>📍 {seatChip}</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.gearBtn} onPress={() => router.push("/settings")}>
              <Text style={styles.gearText}>⚙</Text>
            </TouchableOpacity>
          </View>
        </View>
      </View>

      <ScrollView
        style={{ flex: 1 }}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={refresh} tintColor={colors.primary} />
        }
        contentContainerStyle={{ paddingBottom: 130 }}
      >
        {/* Search */}
        <View style={styles.searchWrap}>
          <TextInput
            style={styles.searchInput}
            placeholder="Search chai, chips, biryani…"
            placeholderTextColor={colors.inkSoft}
            value={search}
            onChangeText={setSearch}
          />
          <Text style={styles.searchIcon}>🔍</Text>
        </View>

        {/* Category strip */}
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.catStripContent}
        >
          {categories.map((item: string) => {
            const meta = CATEGORY_META[item] || { label: item.toLowerCase(), emoji: "🍽️" };
            const selected = selectedCategory === item;
            return (
              <TouchableOpacity
                key={item}
                style={[styles.catChip, selected && styles.catChipSelected]}
                onPress={() => setSelectedCategory(item)}
              >
                <Text style={styles.catEmoji}>{meta.emoji}</Text>
                <Text style={[styles.catLabel, selected && styles.catLabelSelected]}>{meta.label}</Text>
              </TouchableOpacity>
            );
          })}
        </ScrollView>

        {/* FOT-style "How it works" card (recurring home section; tap to reopen) */}
        <TouchableOpacity style={styles.howItWorksCard} onPress={() => setHowItWorksOpen(true)}>
          <Text style={styles.hiwTitle}>🚆 How RailBite works</Text>
          <Text style={styles.hiwSub}>
            Order from the train pantry and it reaches your berth in ~10 minutes — paid in cash at handover.
          </Text>
        </TouchableOpacity>

        {/* Product grid */}
        {loading && menuItems.length === 0 ? (
          <View style={styles.skeletonGrid}>
            {[0, 1, 2, 3].map((i) => (
              <View key={i} style={styles.skeletonCard}>
                <View style={styles.skeletonTile} />
                <View style={styles.skeletonLine} />
                <View style={[styles.skeletonLine, { width: "40%" }]} />
              </View>
            ))}
          </View>
        ) : menuItems.length === 0 ? (
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyIcon}>📡</Text>
            <Text style={styles.emptyTitle}>Cannot reach the pantry server</Text>
            <Text style={styles.emptySub}>
              Check your Wi-Fi or the server address in Settings ⚙, then retry.
            </Text>
            <TouchableOpacity style={styles.retryBtn} onPress={refresh}>
              <Text style={styles.retryText}>Retry</Text>
            </TouchableOpacity>
          </View>
        ) : filteredItems.length === 0 ? (
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyIcon}>🔍</Text>
            <Text style={styles.emptyTitle}>Nothing found</Text>
            <Text style={styles.emptySub}>Try a different search or category.</Text>
          </View>
        ) : (
          <View style={styles.grid}>
            {filteredItems.map((item: MenuItem) => {
              const inCart = cartMap.get(item.id)?.qty || 0;
              const isSoldOut = item.stock <= 0;

              return (
                <View key={item.id} style={styles.productCard}>
                  <View style={styles.tile}>
                    <Text style={styles.tileEmoji}>{emojiForItem(item)}</Text>
                    {/* FOT-style veg / non-veg marker */}
                    <View
                      style={[
                        styles.vegDot,
                        item.isVeg === false && styles.vegDotNon,
                      ]}
                    >
                      <View
                        style={[
                          styles.vegDotInner,
                          item.isVeg === false && styles.vegDotInnerNon,
                        ]}
                      />
                    </View>
                    {isSoldOut ? (
                      <View style={styles.soldOutOverlay}>
                        <Text style={styles.soldOutText}>Sold out</Text>
                      </View>
                    ) : null}
                  </View>
                  <Text style={styles.itemName} numberOfLines={2}>
                    {item.name}
                  </Text>
                  <Text style={styles.itemPrice}>{rupees(item.mrpPaise)}</Text>

                  <View style={styles.cardAction}>
                    {isSoldOut ? null : inCart > 0 ? (
                      <View style={styles.stepper}>
                        <TouchableOpacity style={styles.stepperBtn} onPress={() => removeItem(item.id)}>
                          <Text style={styles.stepperBtnText}>−</Text>
                        </TouchableOpacity>
                        <Text style={styles.stepperQty}>{inCart}</Text>
                        <TouchableOpacity style={styles.stepperBtn} onPress={() => addItem(item)}>
                          <Text style={styles.stepperBtnText}>+</Text>
                        </TouchableOpacity>
                      </View>
                    ) : (
                      <TouchableOpacity style={styles.addBtn} onPress={() => addItem(item)}>
                        <Text style={styles.addBtnText}>ADD</Text>
                      </TouchableOpacity>
                    )}
                  </View>
                </View>
              );
            })}
          </View>
        )}
      </ScrollView>

      {/* Floating cart bar */}
      {cartCount > 0 ? (
        <TouchableOpacity style={styles.cartBar} onPress={() => router.push("/cart")}>
          <Text style={styles.cartBarCount}>
            {cartCount} item{cartCount > 1 ? "s" : ""} • {rupees(cartTotal)}
          </Text>
          <Text style={styles.cartBarCta}>View Cart →</Text>
        </TouchableOpacity>
      ) : null}

      {/* How it works modal */}
      <Modal visible={howItWorksOpen} transparent animationType="fade">
        <View style={styles.hiwOverlay}>
          <View style={styles.hiwModal}>
            <Text style={styles.hiwModalTitle}>How it works</Text>
            <Text style={styles.hiwStep}>📍 Enter your PNR, coach & seat once.</Text>
            <Text style={styles.hiwStep}>🛒 Fill your cart — pay in cash at your berth.</Text>
            <Text style={styles.hiwStep}>🔔 The pantry picker rings instantly (works on their lock screen).</Text>
            <Text style={styles.hiwStep}>📦 Pack, walk & hand over in ~10 minutes.</Text>
            <Text style={styles.hiwStep}>🔢 Share your delivery code when the food arrives — every handover is accountable.</Text>
            <TouchableOpacity
              style={styles.hiwGotItBtn}
              onPress={() => {
                setHowItWorksOpen(false);
                setSetting("how_it_works_dismissed", "1");
              }}
            >
              <Text style={styles.hiwGotItText}>Got it</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  header: {
    backgroundColor: colors.primary,
    paddingHorizontal: 16,
    paddingTop: 10,
    paddingBottom: 14,
    borderBottomLeftRadius: 18,
    borderBottomRightRadius: 18,
  },
  headerTop: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  logoText: { color: "#ffffff", fontSize: 20, fontWeight: "bold" },
  deliveryLine: { color: "#d3f4e2", fontSize: 13, fontWeight: "600", marginTop: 2 },
  seatChip: {
    backgroundColor: "#ffffff",
    borderRadius: 16,
    paddingVertical: 6,
    paddingHorizontal: 10,
    maxWidth: 180,
  },
  seatChipText: { color: colors.ink, fontSize: 12, fontWeight: "bold" },
  gearBtn: {
    backgroundColor: "#ffffff",
    borderRadius: 16,
    width: 32,
    height: 32,
    alignItems: "center",
    justifyContent: "center",
  },
  gearText: { fontSize: 15 },
  searchWrap: {
    marginTop: 12,
    marginHorizontal: 16,
    backgroundColor: "#ffffff",
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    flexDirection: "row",
    alignItems: "center",
  },
  searchInput: { flex: 1, paddingHorizontal: 14, paddingVertical: 10, fontSize: 14, color: colors.ink },
  searchIcon: { paddingHorizontal: 12, fontSize: 14 },
  catStripContent: { paddingHorizontal: 12, paddingVertical: 12, gap: 8 },
  catChip: {
    backgroundColor: "#ffffff",
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colors.border,
    paddingVertical: 8,
    paddingHorizontal: 12,
    alignItems: "center",
    minWidth: 84,
  },
  catChipSelected: { backgroundColor: colors.primarySoft, borderColor: colors.primary },
  catEmoji: { fontSize: 20 },
  catLabel: { fontSize: 11, fontWeight: "600", color: colors.inkSoft, marginTop: 2 },
  catLabelSelected: { color: colors.primary },
  skeletonGrid: { flexDirection: "row", flexWrap: "wrap", padding: 12, gap: 10 },
  skeletonCard: { width: "48%", backgroundColor: "#ffffff", borderRadius: 14, padding: 10 },
  skeletonTile: { height: 74, borderRadius: 10, backgroundColor: "#e6ebf1" },
  skeletonLine: { height: 12, borderRadius: 6, backgroundColor: "#e6ebf1", marginTop: 8 },
  emptyContainer: { justifyContent: "center", alignItems: "center", padding: 30, paddingTop: 60 },
  emptyIcon: { fontSize: 44, marginBottom: 12 },
  emptyTitle: { fontSize: 18, fontWeight: "bold", color: colors.ink },
  emptySub: { fontSize: 13, color: colors.inkSoft, textAlign: "center", marginTop: 6 },
  retryBtn: {
    backgroundColor: colors.primary,
    paddingVertical: 10,
    paddingHorizontal: 24,
    borderRadius: 10,
    marginTop: 14,
  },
  retryText: { color: "#ffffff", fontWeight: "bold" },
  grid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 10,
    paddingHorizontal: 12,
  },
  productCard: {
    width: "48.5%",
    backgroundColor: "#ffffff",
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 10,
  },
  tile: {
    height: 74,
    borderRadius: 10,
    backgroundColor: colors.primarySoft,
    alignItems: "center",
    justifyContent: "center",
  },
  tileEmoji: { fontSize: 34 },
  soldOutOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: "rgba(255,255,255,0.85)",
    alignItems: "center",
    justifyContent: "center",
  },
  soldOutText: { color: colors.danger, fontWeight: "bold", fontSize: 12 },
  itemName: { fontSize: 13, fontWeight: "600", color: colors.ink, marginTop: 8, minHeight: 32 },
  itemPrice: { fontSize: 14, fontWeight: "bold", color: colors.ink, marginTop: 2 },
  cardAction: { marginTop: 8, alignItems: "flex-end" },
  addBtn: {
    backgroundColor: colors.primary,
    borderRadius: 8,
    paddingVertical: 6,
    paddingHorizontal: 20,
  },
  addBtnText: { color: "#ffffff", fontWeight: "bold", fontSize: 13 },
  stepper: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: colors.primary,
    borderRadius: 8,
  },
  stepperBtn: { paddingHorizontal: 12, paddingVertical: 5 },
  stepperBtnText: { color: "#ffffff", fontSize: 16, fontWeight: "bold" },
  stepperQty: { color: "#ffffff", fontWeight: "bold", minWidth: 18, textAlign: "center" },
  cartBar: {
    position: "absolute",
    bottom: 10,
    left: 14,
    right: 14,
    backgroundColor: colors.primaryDark,
    borderRadius: 14,
    paddingVertical: 14,
    paddingHorizontal: 18,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    elevation: 6,
    shadowColor: "#000",
    shadowOpacity: 0.2,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 2 },
  },
  cartBarCount: { color: "#ffffff", fontSize: 14, fontWeight: "bold" },
  cartBarCta: { color: "#ffffff", fontSize: 14, fontWeight: "bold" },
  // FOT-style veg / non-veg marker on product tiles
  vegDot: {
    position: "absolute",
    top: 4,
    left: 4,
    width: 14,
    height: 14,
    borderRadius: 3,
    borderWidth: 1.5,
    borderColor: "#1a7a3c",
    backgroundColor: "#ffffff",
    alignItems: "center",
    justifyContent: "center",
  },
  vegDotNon: { borderColor: "#a02020" },
  vegDotInner: { width: 7, height: 7, borderRadius: 1.5, backgroundColor: "#1a7a3c" },
  vegDotInnerNon: {
    width: 0,
    height: 0,
    borderRadius: 0,
    backgroundColor: "transparent",
    borderLeftWidth: 4,
    borderRightWidth: 4,
    borderBottomWidth: 7,
    borderLeftColor: "transparent",
    borderRightColor: "transparent",
    borderBottomColor: "#a02020",
  },
  // "How it works" home card + modal
  howItWorksCard: {
    marginHorizontal: 16,
    marginTop: 2,
    marginBottom: 12,
    backgroundColor: colors.primarySoft,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colors.primary,
    padding: 12,
  },
  hiwTitle: { fontSize: 14, fontWeight: "bold", color: colors.primaryDark },
  hiwSub: { fontSize: 12, color: colors.inkSoft, marginTop: 3, lineHeight: 17 },
  hiwOverlay: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.55)",
    alignItems: "center",
    justifyContent: "center",
    padding: 24,
  },
  hiwModal: {
    backgroundColor: "#ffffff",
    borderRadius: 18,
    padding: 20,
    width: "100%",
    maxWidth: 380,
  },
  hiwModalTitle: { fontSize: 18, fontWeight: "bold", color: colors.ink, marginBottom: 10 },
  hiwStep: { fontSize: 13, color: colors.inkSoft, lineHeight: 22 },
  hiwGotItBtn: {
    backgroundColor: colors.primary,
    borderRadius: 10,
    paddingVertical: 12,
    alignItems: "center",
    marginTop: 14,
  },
  hiwGotItText: { color: "#ffffff", fontWeight: "bold" },
});
