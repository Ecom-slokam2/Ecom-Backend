-- Keep every active local-delivery store connected to an order location. Store
-- profile location may be a neighborhood; city is the stable admin scope.
UPDATE store_delivery_zones z
JOIN stores s ON s.id = z.store_id
SET z.location_id = CASE LOWER(TRIM(COALESCE(NULLIF(s.city, ''), s.location)))
  WHEN 'hyderabad' THEN 'hyderabad'
  WHEN 'secunderabad' THEN 'hyderabad'
  WHEN 'sangareddy' THEN 'hyderabad'
  WHEN 'chennai' THEN 'chennai'
  WHEN 'bengaluru' THEN 'bengaluru'
  WHEN 'bangalore' THEN 'bengaluru'
  WHEN 'vijayawada' THEN 'vijayawada'
  ELSE z.location_id
END
WHERE z.location_id IS NULL
  AND s.active = TRUE
  AND s.delivery_enabled = TRUE;

-- Stores added after the original catalog seed need persisted delivery zones,
-- otherwise the public quote endpoint always reports them outside their area.
INSERT INTO store_delivery_zones (
  store_id, location_id, min_radius_km, max_radius_km,
  estimated_delivery_min, estimated_delivery_max, delivery_charge,
  free_delivery_above, minimum_order, status, created_at, updated_at
)
SELECT
  s.id,
  CASE LOWER(TRIM(COALESCE(NULLIF(s.city, ''), s.location)))
    WHEN 'hyderabad' THEN 'hyderabad'
    WHEN 'secunderabad' THEN 'hyderabad'
    WHEN 'sangareddy' THEN 'hyderabad'
    WHEN 'chennai' THEN 'chennai'
    WHEN 'bengaluru' THEN 'bengaluru'
    WHEN 'bangalore' THEN 'bengaluru'
    WHEN 'vijayawada' THEN 'vijayawada'
  END,
  0.00,
  COALESCE(s.delivery_radius_km, 10.00),
  GREATEST(COALESCE(s.delivery_eta_minutes, 20) - 5, 5),
  COALESCE(s.delivery_eta_minutes, 20) + 10,
  COALESCE(s.delivery_charge, 35.00),
  s.free_delivery_above,
  0.00,
  COALESCE(s.status, 'Active'),
  NOW(6), NOW(6)
FROM stores s
WHERE s.active = TRUE
  AND s.delivery_enabled = TRUE
  AND s.status = 'Active'
  AND s.id <> 'global-store'
  AND s.id NOT LIKE 'location-%'
  AND COALESCE(s.delivery_radius_km, 0) > 0
  AND LOWER(TRIM(COALESCE(NULLIF(s.city, ''), s.location))) IN
    ('hyderabad', 'secunderabad', 'sangareddy', 'chennai', 'bengaluru', 'bangalore', 'vijayawada')
  AND NOT EXISTS (SELECT 1 FROM store_delivery_zones existing WHERE existing.store_id = s.id);

-- Standard delivery is the default checkout method for each persisted zone.
INSERT INTO store_delivery_methods (
  store_id, delivery_zone_id, delivery_type, enabled, delivery_charge,
  estimated_time, settings_configuration, created_at, updated_at
)
SELECT z.store_id, z.id, 'Standard Delivery', TRUE, z.delivery_charge,
  CONCAT(z.estimated_delivery_min, '-', z.estimated_delivery_max, ' min'), '{}', NOW(6), NOW(6)
FROM store_delivery_zones z
JOIN stores s ON s.id = z.store_id
WHERE s.active = TRUE
  AND s.delivery_enabled = TRUE
  AND z.status = 'Active'
  AND NOT EXISTS (
    SELECT 1 FROM store_delivery_methods m
    WHERE m.delivery_zone_id = z.id AND LOWER(m.delivery_type) = 'standard delivery'
  );

INSERT INTO store_delivery_methods (
  store_id, delivery_zone_id, delivery_type, enabled, delivery_charge,
  estimated_time, settings_configuration, created_at, updated_at
)
SELECT z.store_id, z.id, 'Store Pickup', TRUE, 0.00, 'Ready in 30 min',
  '{"pickupInstructions":"Collect from billing counter"}', NOW(6), NOW(6)
FROM store_delivery_zones z
JOIN stores s ON s.id = z.store_id
WHERE s.active = TRUE
  AND s.pickup_enabled = TRUE
  AND z.status = 'Active'
  AND NOT EXISTS (
    SELECT 1 FROM store_delivery_methods m
    WHERE m.delivery_zone_id = z.id AND LOWER(m.delivery_type) = 'store pickup'
  );
