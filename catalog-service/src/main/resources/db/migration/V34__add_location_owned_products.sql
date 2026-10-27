ALTER TABLE products
  ADD COLUMN owner_location VARCHAR(160) NULL;

CREATE INDEX idx_products_owner_location
  ON products(owner_location);
