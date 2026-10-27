ALTER TABLE products
  ADD COLUMN owner_store_id VARCHAR(100) NULL;

CREATE INDEX idx_products_owner_store
  ON products(owner_store_id);
