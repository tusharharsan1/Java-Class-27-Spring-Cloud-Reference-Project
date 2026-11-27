-- Product fixtures for the separate shopnest_c27 schema. Run after Flyway.
-- The local embedding fixture puts beauty, lipstick and lamps in slots 0, 10 and 30.
INSERT IGNORE INTO product (id, name, description, price, state, embedding) VALUES
  (UUID_TO_BIN('27000000-0000-4000-8000-000000000001'), 'Red Lipstick', 'A red lipstick', 8.50, 'ACTIVE',
   CONCAT(REPEAT('0,', 10), '1,', REPEAT('0,', 756), '0')),
  (UUID_TO_BIN('27000000-0000-4000-8000-000000000002'), 'Essence Mascara', 'Black mascara', 9.99, 'ACTIVE',
   CONCAT('1,', REPEAT('0,', 766), '0')),
  (UUID_TO_BIN('27000000-0000-4000-8000-000000000003'), 'Desk Lamp', 'A lamp for the desk', 18.00, 'ACTIVE',
   CONCAT(REPEAT('0,', 30), '1,', REPEAT('0,', 736), '0'));
