-- FOT-inspired prototype features (delivery code handover, passenger feedback, veg markers).
-- ALTER TABLE statements are made idempotent by the migration runner (duplicate-column tolerated).

ALTER TABLE menu_items ADD COLUMN is_veg INTEGER NOT NULL DEFAULT 1;

ALTER TABLE orders ADD COLUMN delivery_code TEXT;   -- 4-digit code generated on pickup; picker must repeat it on deliver

CREATE TABLE IF NOT EXISTS order_feedback (
  order_id TEXT PRIMARY KEY REFERENCES orders(id),
  received INTEGER NOT NULL,              -- "Did you receive this order?"
  rating INTEGER CHECK (rating IS NULL OR (rating >= 1 AND rating <= 5)),
  reasons TEXT,                           -- JSON array of chips, e.g. ["Food was great"]
  created_at TEXT NOT NULL
);
