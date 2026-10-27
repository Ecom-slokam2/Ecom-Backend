-- Keep persisted banner enum values while matching the VARCHAR columns defined by V20
-- and the Catalog entity mappings. Runtime Flyway is disabled; review and apply this
-- schema change to each Catalog database before starting a validating runtime.
ALTER TABLE banners
  MODIFY COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'GLOBAL',
  MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
