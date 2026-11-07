export const INIT_SQL = `
CREATE TABLE IF NOT EXISTS menu_items (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  category TEXT NOT NULL,
  track TEXT NOT NULL,
  mrp_paise INTEGER NOT NULL,
  stock INTEGER NOT NULL,
  prep_minutes INTEGER DEFAULT 0,
  active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS orders (
  id TEXT PRIMARY KEY,
  pnr TEXT NOT NULL,
  coach TEXT NOT NULL,
  berth INTEGER NOT NULL,
  diners INTEGER NOT NULL,
  track TEXT NOT NULL,
  status TEXT NOT NULL,
  payment_mode TEXT NOT NULL,
  total_paise INTEGER NOT NULL,
  source TEXT NOT NULL,
  verified INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  items_json TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS pending_actions (
  id TEXT PRIMARY KEY,
  type TEXT NOT NULL,
  payload TEXT NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  next_try_at TEXT NOT NULL,
  created_at TEXT NOT NULL
);
`;

