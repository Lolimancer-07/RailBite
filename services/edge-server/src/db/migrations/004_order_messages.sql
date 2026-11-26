-- Order-scoped quick chat messages exchanged between passenger and picker.
CREATE TABLE IF NOT EXISTS order_messages (
  id TEXT PRIMARY KEY,
  order_id TEXT NOT NULL REFERENCES orders(id),
  sender_role TEXT NOT NULL CHECK (sender_role IN ('CUSTOMER', 'PICKER')),
  sender_id TEXT,
  preset_code TEXT,
  body TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_order_messages_order_created ON order_messages(order_id, created_at);
