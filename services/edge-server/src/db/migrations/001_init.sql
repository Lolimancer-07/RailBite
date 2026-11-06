PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS trips (
  id TEXT PRIMARY KEY,
  train_no TEXT NOT NULL,
  run_date TEXT NOT NULL,
  hmac_secret TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS manifest (
  pnr TEXT NOT NULL,
  coach TEXT NOT NULL,
  berth INTEGER NOT NULL,
  name_hash TEXT,
  PRIMARY KEY (pnr, coach, berth)
);

CREATE TABLE IF NOT EXISTS menu_items (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  category TEXT NOT NULL,
  track TEXT NOT NULL CHECK (track IN ('READY','COOK')),
  mrp_paise INTEGER NOT NULL,
  stock INTEGER NOT NULL,
  prep_minutes INTEGER DEFAULT 0,
  active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS staff (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('CAPTAIN','COOK')),
  zone_coaches TEXT NOT NULL,      -- JSON array e.g. ["B1","B2","B3"]
  pin_hash TEXT NOT NULL,
  device_token TEXT,
  online INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS orders (
  id TEXT PRIMARY KEY,             -- client-generated UUID (idempotency key)
  pnr TEXT NOT NULL,
  coach TEXT NOT NULL,
  berth INTEGER NOT NULL,
  diners INTEGER NOT NULL,
  track TEXT NOT NULL,
  status TEXT NOT NULL,
  payment_mode TEXT NOT NULL,
  total_paise INTEGER NOT NULL,
  source TEXT NOT NULL,
  verified INTEGER NOT NULL DEFAULT 1,   -- 0 if manifest missing; captain must verify
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS order_items (
  order_id TEXT NOT NULL REFERENCES orders(id),
  item_id TEXT NOT NULL REFERENCES menu_items(id),
  qty INTEGER NOT NULL,
  unit_price_paise INTEGER NOT NULL,
  PRIMARY KEY (order_id, item_id)
);

CREATE TABLE IF NOT EXISTS assignments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  order_id TEXT NOT NULL REFERENCES orders(id),
  captain_id TEXT NOT NULL REFERENCES staff(id),
  attempt INTEGER NOT NULL DEFAULT 1,
  assigned_at TEXT NOT NULL,
  accepted_at TEXT,
  delivered_at TEXT,
  rejected_at TEXT
);

CREATE TABLE IF NOT EXISTS cash_ledger (               -- APPEND ONLY. Never update or delete.
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  order_id TEXT NOT NULL,
  captain_id TEXT NOT NULL,
  amount_paise INTEGER NOT NULL,
  passenger_confirmed INTEGER DEFAULT 0,
  ts TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS complaints (
  id TEXT PRIMARY KEY,
  order_id TEXT NOT NULL REFERENCES orders(id),
  tier INTEGER NOT NULL CHECK (tier IN (1,2)),
  type TEXT NOT NULL,
  text TEXT,
  photo_path TEXT,
  photo_sha256 TEXT,
  photo_ts TEXT,
  status TEXT NOT NULL DEFAULT 'OPEN',   -- OPEN, REPLACEMENT_SENT, RESOLVED, ESCALATED
  due_at TEXT,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS outbox (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  entity TEXT NOT NULL,
  payload TEXT NOT NULL,
  created_at TEXT NOT NULL,
  synced_at TEXT
);

CREATE TABLE IF NOT EXISTS sms_nonces (
  nonce TEXT PRIMARY KEY,
  pnr_last4 TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_coach ON orders(coach);
CREATE INDEX IF NOT EXISTS idx_assignments_order ON assignments(order_id);
CREATE INDEX IF NOT EXISTS idx_complaints_order ON complaints(order_id);
