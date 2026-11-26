# RailBite Design System Specification (Flat Design #2)

Derived from Stitch Project **`2149824161029451542`** (*RailBite Train Food Delivery*).

## Design Principles — Flat Utilitarianism
- **Zero gradients**, **zero glassmorphism/blur**, **zero heavy elevation drop shadows**.
- Hierarchy relies purely on planar value shifts (`#F4F6F8` canvas -> `#FFFFFF` cards -> `#F1F5F9` recessed image shells -> `#0F172A` dark contrast anchors) and crisp **`1.dp` `#E2E8F0` structural borders**.
- Minimum **`44.dp` × `44.dp`** touch targets across all buttons, quantity steppers, seat chips, category pills, and navigation actions for steady single-handed use in moving train coaches.
- **Tabular numerals (`fontFeatureSettings = "tnum"`)** across every currency readout (`₹`), cart item counter, timer, coach/berth number, and 4-digit handover PIN.

## Color Tokens
| Token | Hex | Role |
|---|---|---|
| `Canvas` (`background`) | `#F4F6F8` | Primary app canvas |
| `SurfaceCard` (`surface`) | `#FFFFFF` | Cards, checkout boxes, bottom sheets |
| `ImageTile` (`surfaceVariant`) | `#F1F5F9` | Food image container backing |
| `DarkSlate` (`secondary`) | `#0F172A` | Price pills, floating cart bar, handover PIN card, primary ink |
| `CyanPrimary` (`primary`) | `#0891B2` | Primary CTAs, active steppers, active timeline step |
| `CyanPressed` | `#0E7490` | Pressed primary action tint |
| `CyanTint` (`primaryContainer`) | `#ECFEFF` | Selected category chips, seat header pill, selected payment box |
| `BorderSubtle` (`outline`) | `#E2E8F0` | Universal `1.dp` hairline border |
| `TextPrimary` (`onBackground`) | `#0F172A` | Titles, headings, prices |
| `TextSecondary` (`onSurfaceVariant`) | `#64748B` | Subtitles, pantry metadata |
| `TextMuted` | `#94A3B8` | Disabled controls, inactive labels |
| `SemanticGreen` | `#16A34A` | Verified IRCTC MRP, delivered, online status, veg badge |
| `SemanticAmber` | `#D97706` | Active packing/countdown warnings |
| `SemanticRed` | `#DC2626` | Sold out, disconnected status, errors, issue links |

## Radii Scale
- `8.dp` — PIN digit cells (`HandoverPinCard`), dense micro-badges
- `12.dp` — Buttons and text fields
- `16.dp` — Feature cards (`FoodProductCard`, `CartItemCard`, `HandoverPinCard`, summaries)
- `18.dp` — Food photography frames & hero image containers
- `24.dp` — Top corners on `ModalBottomSheet` (`ItemQuickViewSheet`, `CashConfirmSheet`)
- `999.dp` — Pills (`PricePill`, `CategoryChip`, `FloatingCartBar`, seat chips)
