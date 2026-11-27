-- Train Number scoping for orders, staff, and passenger manifest
ALTER TABLE orders ADD COLUMN train_number TEXT NOT NULL DEFAULT '12345';
ALTER TABLE staff ADD COLUMN train_number TEXT NOT NULL DEFAULT '12345';
ALTER TABLE manifest ADD COLUMN train_number TEXT NOT NULL DEFAULT '12345';
ALTER TABLE manifest ADD COLUMN train_name TEXT NOT NULL DEFAULT 'Saraighat Express';

-- Upgrade seeded cross-train rows once for existing local dev DBs
UPDATE staff SET train_number = '12423' WHERE id IN ('c3', 'c4') AND train_number = '12345';
UPDATE manifest SET train_number = '12423', train_name = 'Rajdhani Express'
 WHERE coach IN ('S1', 'S2', 'S3', 'S4') AND train_number = '12345';

CREATE INDEX IF NOT EXISTS idx_orders_train_status ON orders(train_number, status);
CREATE INDEX IF NOT EXISTS idx_staff_train_online ON staff(train_number, online);
CREATE INDEX IF NOT EXISTS idx_manifest_train_pnr ON manifest(train_number, pnr);
