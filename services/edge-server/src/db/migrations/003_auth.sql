-- Production auth spine: customer accounts, revocable refresh sessions, staff passwords.
-- Kept in portable SQL (the runner translates SQLite-only syntax for Postgres).

CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  phone TEXT NOT NULL UNIQUE,
  name TEXT,
  created_at TEXT NOT NULL,
  last_login_at TEXT
);

CREATE TABLE IF NOT EXISTS sessions (
  id TEXT PRIMARY KEY,
  subject_id TEXT NOT NULL,
  subject_type TEXT NOT NULL CHECK (subject_type IN ('customer', 'staff')),
  refresh_hash TEXT NOT NULL,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  revoked_at TEXT,
  user_agent TEXT
);

CREATE INDEX IF NOT EXISTS idx_sessions_subject ON sessions(subject_id);
CREATE INDEX IF NOT EXISTS idx_sessions_refresh ON sessions(refresh_hash);
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);

-- Staff credentials: bcrypt hash (pin_hash stays for legacy rows / PIN fallback).
ALTER TABLE staff ADD COLUMN password_hash TEXT;
ALTER TABLE staff ADD COLUMN active INTEGER NOT NULL DEFAULT 1;
ALTER TABLE staff ADD COLUMN is_admin INTEGER NOT NULL DEFAULT 0;

-- Orders belong to the signed-in customer (nullable for pre-auth/legacy orders).
ALTER TABLE orders ADD COLUMN user_id TEXT;
CREATE INDEX IF NOT EXISTS idx_orders_user ON orders(user_id);
