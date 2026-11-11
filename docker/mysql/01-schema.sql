-- Schema-only snapshot extracted from Ecom_DB; no business/account data.

SET FOREIGN_KEY_CHECKS=0;

CREATE DATABASE `cart_service` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `cart_service`;

CREATE TABLE `cart_checkout_claims` (
  `cart_id` varchar(36) NOT NULL,
  `request_hash` varchar(64) NOT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`cart_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cart_items` (
  `id` binary(16) NOT NULL,
  `product_id` binary(16) NOT NULL,
  `product_name` varchar(255) NOT NULL,
  `quantity` int NOT NULL,
  `total_price` decimal(19,2) NOT NULL,
  `status` varchar(30) NOT NULL DEFAULT 'ACTIVE',
  `unit_price` decimal(19,2) NOT NULL,
  `variant_id` binary(16) DEFAULT NULL,
  `cart_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cart_items_cart_status` (`cart_id`,`status`),
  CONSTRAINT `FKpcttvuq4mxppo8sxggjtn5i2c` FOREIGN KEY (`cart_id`) REFERENCES `carts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cart_mutation_requests` (
  `cart_id` varchar(36) NOT NULL,
  `request_key` varchar(128) NOT NULL,
  `request_hash` varchar(64) NOT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`cart_id`,`request_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cart_write_lock` (
  `id` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `carts` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `customer_id` binary(16) NOT NULL,
  `status` varchar(20) NOT NULL,
  `store_id` varchar(100) NOT NULL,
  `total_amount` decimal(19,2) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `carts_chk_1` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'CHECKED_OUT',_utf8mb4'ABANDONED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO cart_write_lock (id) VALUES (1);

CREATE DATABASE `catalog_service` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `catalog_service`;

CREATE TABLE `banners` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `ends_on` date DEFAULT NULL,
  `image_url` mediumtext NOT NULL,
  `location_id` varchar(100) DEFAULT NULL,
  `mobile_image_url` mediumtext,
  `scope` enum('GLOBAL','LOCATION','STORE') NOT NULL,
  `sort_order` int NOT NULL,
  `starts_on` date DEFAULT NULL,
  `status` enum('ACTIVE','SCHEDULED','INACTIVE') NOT NULL,
  `store_id` varchar(100) DEFAULT NULL,
  `target_url` varchar(2048) DEFAULT NULL,
  `title` varchar(180) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `categories` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `active` bit(1) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `name` varchar(120) NOT NULL,
  `slug` varchar(140) NOT NULL,
  `thumbnail_url` varchar(2048) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_categories_slug` (`slug`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `product_images` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `alt_text` varchar(255) DEFAULT NULL,
  `display_order` int NOT NULL,
  `primary_image` bit(1) NOT NULL,
  `url` varchar(2048) NOT NULL,
  `product_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqnq71xsohugpqwf3c9gxmsuy` (`product_id`),
  CONSTRAINT `FKqnq71xsohugpqwf3c9gxmsuy` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `product_variants` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `active` bit(1) NOT NULL,
  `attributes` json DEFAULT NULL,
  `compare_at_price` decimal(19,2) DEFAULT NULL,
  `name` varchar(180) NOT NULL,
  `price` decimal(19,2) NOT NULL,
  `sku` varchar(100) NOT NULL,
  `product_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_variants_sku` (`sku`),
  KEY `FKosqitn4s405cynmhb87lkvuau` (`product_id`),
  CONSTRAINT `FKosqitn4s405cynmhb87lkvuau` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `products` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `about_source` text,
  `best_seller` tinyint(1) NOT NULL DEFAULT '0',
  `global_product` tinyint(1) NOT NULL DEFAULT '0',
  `fulfillment_type` varchar(40) DEFAULT NULL,
  `delivery_method` varchar(80) DEFAULT NULL,
  `brand` varchar(120) DEFAULT NULL,
  `description` text,
  `featured` tinyint(1) NOT NULL DEFAULT '0',
  `highlights` text,
  `ingredients` text,
  `name` varchar(180) NOT NULL,
  `new_arrival` tinyint(1) NOT NULL DEFAULT '0',
  `pack` varchar(80) DEFAULT NULL,
  `shelf_life` varchar(80) DEFAULT NULL,
  `sku` varchar(100) NOT NULL,
  `slug` varchar(200) NOT NULL,
  `special` tinyint(1) NOT NULL DEFAULT '0',
  `specifications` text,
  `status` enum('DRAFT','ACTIVE','INACTIVE') NOT NULL,
  `tags` text,
  `type` varchar(80) DEFAULT NULL,
  `category_id` binary(16) NOT NULL,
  `subcategory` varchar(80) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_products_slug` (`slug`),
  UNIQUE KEY `uk_products_sku` (`sku`),
  KEY `FKog2rp4qthbtt2lfyhfo32lsw9` (`category_id`),
  CONSTRAINT `FKog2rp4qthbtt2lfyhfo32lsw9` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `store_delivery_methods` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `cutoff_time` varchar(20) DEFAULT NULL,
  `delivery_charge` decimal(10,2) DEFAULT NULL,
  `delivery_type` varchar(60) NOT NULL,
  `enabled` bit(1) NOT NULL,
  `estimated_time` varchar(80) DEFAULT NULL,
  `settings_configuration` text,
  `store_id` varchar(100) NOT NULL,
  `delivery_zone_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKr64g3y9decbtvfegmjlc82ywe` (`delivery_zone_id`),
  CONSTRAINT `FKr64g3y9decbtvfegmjlc82ywe` FOREIGN KEY (`delivery_zone_id`) REFERENCES `store_delivery_zones` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=104 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `store_delivery_zones` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `delivery_charge` decimal(10,2) NOT NULL,
  `estimated_delivery_max` int DEFAULT NULL,
  `estimated_delivery_min` int DEFAULT NULL,
  `free_delivery_above` decimal(10,2) DEFAULT NULL,
  `max_radius_km` decimal(8,2) NOT NULL,
  `min_radius_km` decimal(8,2) NOT NULL,
  `minimum_order` decimal(10,2) DEFAULT NULL,
  `status` varchar(40) NOT NULL,
  `store_id` varchar(100) NOT NULL,
  `location_id` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_store_delivery_zones_location` (`location_id`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `store_managers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `store_id` varchar(100) NOT NULL,
  `name` varchar(180) NOT NULL,
  `email` varchar(180) NOT NULL,
  `mobile` varchar(30) DEFAULT NULL,
  `password_hash` varchar(255) DEFAULT NULL,
  `status` varchar(40) NOT NULL DEFAULT 'Active',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_store_managers_store` (`store_id`),
  CONSTRAINT `fk_store_managers_store` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `store_products` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `available` bit(1) NOT NULL,
  `price_override` decimal(19,2) DEFAULT NULL,
  `store_id` varchar(100) NOT NULL,
  `store_name` varchar(180) DEFAULT NULL,
  `product_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_store_products_store_product` (`store_id`,`product_id`),
  KEY `FK4h269080dgg4gx53cxukn7i3v` (`product_id`),
  CONSTRAINT `FK4h269080dgg4gx53cxukn7i3v` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `stores` (
  `id` varchar(100) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `address` varchar(700) NOT NULL,
  `admin_name` varchar(180) DEFAULT NULL,
  `alternate_phone` varchar(30) DEFAULT NULL,
  `area` varchar(160) DEFAULT NULL,
  `city` varchar(120) DEFAULT NULL,
  `delivery_charge` decimal(10,2) DEFAULT NULL,
  `delivery_enabled` tinyint(1) NOT NULL DEFAULT '1',
  `delivery_eta_minutes` int DEFAULT NULL,
  `delivery_modes` varchar(300) DEFAULT NULL,
  `delivery_notes` varchar(500) DEFAULT NULL,
  `is_new` tinyint(1) NOT NULL DEFAULT '0',
  `delivery_radius_km` decimal(8,2) DEFAULT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `district` varchar(120) DEFAULT NULL,
  `door_number` varchar(80) DEFAULT NULL,
  `email` varchar(180) DEFAULT NULL,
  `free_delivery_above` decimal(10,2) DEFAULT NULL,
  `gst_number` varchar(30) DEFAULT NULL,
  `landmark` varchar(180) DEFAULT NULL,
  `latitude` decimal(10,7) NOT NULL,
  `location` varchar(160) NOT NULL,
  `location_accuracy_meters` decimal(10,2) DEFAULT NULL,
  `logo_url` varchar(2048) DEFAULT NULL,
  `longitude` decimal(10,7) NOT NULL,
  `name` varchar(180) NOT NULL,
  `phone` varchar(30) DEFAULT NULL,
  `pickup_enabled` tinyint(1) NOT NULL DEFAULT '1',
  `pincode` varchar(20) DEFAULT NULL,
  `provider` varchar(40) NOT NULL,
  `provider_osm_id` varchar(40) DEFAULT NULL,
  `provider_osm_type` varchar(20) DEFAULT NULL,
  `provider_place_id` varchar(120) DEFAULT NULL,
  `state` varchar(120) DEFAULT NULL,
  `status` varchar(40) DEFAULT NULL,
  `store_code` varchar(60) DEFAULT NULL,
  `store_rating` decimal(3,2) DEFAULT NULL,
  `street_name` varchar(180) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stores_name` (`name`),
  KEY `idx_stores_status` (`status`),
  KEY `idx_stores_code` (`store_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE coupons (
  id BINARY(16) NOT NULL PRIMARY KEY,
  code VARCHAR(64) NOT NULL,
  coupon_name VARCHAR(180) NOT NULL,
  description TEXT,
  discount_type VARCHAR(20) NOT NULL,
  discount DECIMAL(12,2) NOT NULL,
  minimum_order DECIMAL(12,2) NOT NULL DEFAULT 0,
  maximum_discount DECIMAL(12,2),
  start_date DATE NOT NULL,
  expiry DATE NOT NULL,
  usage_limit INT NOT NULL,
  per_user_limit INT,
  scope VARCHAR(20) NOT NULL DEFAULT 'GLOBAL',
  location VARCHAR(150),
  store VARCHAR(150),
  product VARCHAR(180),
  category VARCHAR(180),
  eligibility VARCHAR(60) NOT NULL DEFAULT 'All customers',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_coupons_code UNIQUE (code),
  CONSTRAINT chk_coupons_discount_positive CHECK (discount > 0),
  CONSTRAINT chk_coupons_minimum_nonnegative CHECK (minimum_order >= 0),
  CONSTRAINT chk_coupons_usage_positive CHECK (usage_limit > 0),
  INDEX idx_coupons_status_dates (status, start_date, expiry),
  INDEX idx_coupons_scope (scope, location, store)
);

ALTER TABLE products
  ADD COLUMN owner_location VARCHAR(160) NULL;

CREATE INDEX idx_products_owner_location
  ON products(owner_location);

ALTER TABLE products
  ADD COLUMN owner_store_id VARCHAR(100) NULL;

CREATE INDEX idx_products_owner_store
  ON products(owner_store_id);

-- Docker snapshot update: V36__normalize_banner_scope_columns.sql

-- Keep persisted banner enum values while matching the VARCHAR columns defined by V20
-- and the Catalog entity mappings. Runtime Flyway is disabled; review and apply this
-- schema change to each Catalog database before starting a validating runtime.
ALTER TABLE banners
  MODIFY COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'GLOBAL',
  MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';


CREATE DATABASE `customer_profiles` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `customer_profiles`;

CREATE TABLE `customer_addresses` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `address` varchar(1000) NOT NULL,
  `city` varchar(120) DEFAULT NULL,
  `default_address` bit(1) NOT NULL,
  `floor` varchar(50) NOT NULL,
  `house_number` varchar(120) NOT NULL,
  `landmark` varchar(160) DEFAULT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `phone` varchar(20) NOT NULL,
  `pincode` varchar(12) DEFAULT NULL,
  `state` varchar(120) DEFAULT NULL,
  `street` varchar(255) DEFAULT NULL,
  `type` varchar(30) NOT NULL,
  `username` varchar(191) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_customer_addresses_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `customer_profiles` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `date_of_birth` date DEFAULT NULL,
  `email` varchar(254) DEFAULT NULL,
  `gender` enum('FEMALE','MALE','OTHER','PREFER_NOT_TO_SAY') DEFAULT NULL,
  `name` varchar(120) NOT NULL,
  `phone` varchar(20) NOT NULL,
  `photo` mediumtext,
  `username` varchar(191) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_profiles_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE DATABASE `fulfillment-service` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `fulfillment-service`;

CREATE TABLE `fulfillment_order_snapshots` (
  `order_id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `cancelled` bit(1) NOT NULL,
  `customer_id` binary(16) NOT NULL,
  `fulfillment_type` varchar(30) NOT NULL,
  `shipping_address_id` binary(16) DEFAULT NULL,
  `store_id` varchar(100) NOT NULL,
  PRIMARY KEY (`order_id`),
  CONSTRAINT `fulfillment_order_snapshots_chk_1` CHECK ((`fulfillment_type` in (_utf8mb4'SHIP_TO_HOME',_utf8mb4'STORE_PICKUP',_utf8mb4'GLOBAL',_utf8mb4'DIGITAL')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `fulfillments` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `customer_id` binary(16) NOT NULL,
  `fulfillment_type` varchar(30) NOT NULL,
  `order_id` binary(16) NOT NULL,
  `shipping_address_id` binary(16) NOT NULL,
  `status` varchar(30) NOT NULL,
  `store_id` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_rksk0lb23na52f941uubcuea2` (`order_id`),
  CONSTRAINT `fulfillments_chk_1` CHECK ((`fulfillment_type` in (_utf8mb4'SHIP_TO_HOME',_utf8mb4'STORE_PICKUP',_utf8mb4'GLOBAL',_utf8mb4'DIGITAL'))),
  CONSTRAINT `fulfillments_chk_2` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'ALLOCATED',_utf8mb4'PACKING',_utf8mb4'READY_TO_SHIP',_utf8mb4'SHIPPED',_utf8mb4'DELIVERED',_utf8mb4'CANCELLED',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `shipments` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `carrier` varchar(80) NOT NULL,
  `delivered_at` datetime(6) DEFAULT NULL,
  `delivery_assigned_at` datetime(6) DEFAULT NULL,
  `delivery_partner_id` varchar(100) DEFAULT NULL,
  `delivery_partner_name` varchar(180) DEFAULT NULL,
  `delivery_partner_phone` varchar(40) DEFAULT NULL,
  `delivery_partner_vehicle_number` varchar(40) DEFAULT NULL,
  `estimated_delivery_date` date NOT NULL,
  `order_id` binary(16) NOT NULL,
  `shipped_at` datetime(6) DEFAULT NULL,
  `status` varchar(30) NOT NULL,
  `tracking_number` varchar(120) NOT NULL,
  `fulfillment_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_2980t5kjkkrwnjhwvit59x61k` (`tracking_number`),
  KEY `FKl8y5773trj4ii0kfpgvgcomx8` (`fulfillment_id`),
  KEY `idx_shipments_delivery_partner` (`delivery_partner_id`,`status`),
  CONSTRAINT `FKl8y5773trj4ii0kfpgvgcomx8` FOREIGN KEY (`fulfillment_id`) REFERENCES `fulfillments` (`id`),
  CONSTRAINT `shipments_chk_1` CHECK ((`status` in (_utf8mb4'CREATED',_utf8mb4'IN_TRANSIT',_utf8mb4'OUT_FOR_DELIVERY',_utf8mb4'DELIVERED',_utf8mb4'FAILED',_utf8mb4'RETURNED',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sync_inbox` (
  `source` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `event_id` varchar(36) COLLATE utf8mb4_bin NOT NULL,
  `body_hash` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `received_at` bigint NOT NULL,
  PRIMARY KEY (`source`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `sync_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_id` varchar(36) COLLATE utf8mb4_bin NOT NULL,
  `target` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `aggregate_id` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `body` longtext COLLATE utf8mb4_bin NOT NULL,
  `state` varchar(16) COLLATE utf8mb4_bin NOT NULL,
  `attempts` int NOT NULL,
  `next_attempt` bigint NOT NULL,
  `lease_token` varchar(36) COLLATE utf8mb4_bin DEFAULT NULL,
  `lease_until` bigint DEFAULT NULL,
  `last_error` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `event_id` (`event_id`,`target`),
  KEY `sync_outbox_due` (`state`,`next_attempt`),
  KEY `sync_outbox_ordering` (`target`,`aggregate_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `sync_receiver_lock` (
  `id` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `tracking_events` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(500) NOT NULL,
  `event_time` datetime(6) NOT NULL,
  `location` varchar(160) NOT NULL,
  `status` varchar(30) NOT NULL,
  `shipment_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKejyddin0ap6h36fq9fuhoq8lp` (`shipment_id`),
  CONSTRAINT `FKejyddin0ap6h36fq9fuhoq8lp` FOREIGN KEY (`shipment_id`) REFERENCES `shipments` (`id`),
  CONSTRAINT `tracking_events_chk_1` CHECK ((`status` in (_utf8mb4'CREATED',_utf8mb4'IN_TRANSIT',_utf8mb4'OUT_FOR_DELIVERY',_utf8mb4'DELIVERED',_utf8mb4'FAILED',_utf8mb4'RETURNED',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sync_receiver_lock (id) VALUES (1);

-- Docker snapshot update: V6__allow_global_fulfillment_type.sql

SET @legacy_check = (
  SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'fulfillment_order_snapshots'
    AND CONSTRAINT_TYPE = 'CHECK' AND CONSTRAINT_NAME = 'fulfillment_order_snapshots_chk_1'
  LIMIT 1
);
SET @ddl = IF(@legacy_check IS NULL, 'SELECT 1',
  CONCAT('ALTER TABLE fulfillment_order_snapshots DROP CHECK `', @legacy_check, '`'));
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @has_check = (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'fulfillment_order_snapshots'
    AND CONSTRAINT_TYPE = 'CHECK' AND CONSTRAINT_NAME = 'chk_fulfillment_order_snapshot_type'
);
SET @ddl = IF(@has_check > 0, 'SELECT 1',
  'ALTER TABLE fulfillment_order_snapshots ADD CONSTRAINT chk_fulfillment_order_snapshot_type CHECK (fulfillment_type IN (''SHIP_TO_HOME'', ''STORE_PICKUP'', ''GLOBAL'', ''DIGITAL''))');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @legacy_check = (
  SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'fulfillments'
    AND CONSTRAINT_TYPE = 'CHECK' AND CONSTRAINT_NAME = 'fulfillments_chk_1'
  LIMIT 1
);
SET @ddl = IF(@legacy_check IS NULL, 'SELECT 1',
  CONCAT('ALTER TABLE fulfillments DROP CHECK `', @legacy_check, '`'));
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @has_check = (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'fulfillments'
    AND CONSTRAINT_TYPE = 'CHECK' AND CONSTRAINT_NAME = 'chk_fulfillment_type'
);
SET @ddl = IF(@has_check > 0, 'SELECT 1',
  'ALTER TABLE fulfillments ADD CONSTRAINT chk_fulfillment_type CHECK (fulfillment_type IN (''SHIP_TO_HOME'', ''STORE_PICKUP'', ''GLOBAL'', ''DIGITAL''))');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;


-- Docker snapshot update: V7__add_carrier_retry_attempts.sql

CREATE TABLE IF NOT EXISTS carrier_retry_attempts (
  id BINARY(16) NOT NULL,
  shipment_id BINARY(16) NOT NULL,
  idempotency_key VARCHAR(100) NOT NULL,
  attempt_number INT NOT NULL,
  outcome VARCHAR(30) NOT NULL,
  carrier_status INT NULL,
  error_code VARCHAR(80) NULL,
  retryable BOOLEAN NOT NULL DEFAULT FALSE,
  started_at TIMESTAMP(6) NOT NULL,
  completed_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_carrier_retry_attempt_shipment FOREIGN KEY (shipment_id) REFERENCES shipments(id),
  CONSTRAINT uk_carrier_retry_shipment_attempt UNIQUE (shipment_id, attempt_number),
  CONSTRAINT uk_carrier_retry_request_attempt UNIQUE (shipment_id, idempotency_key, attempt_number),
  INDEX idx_carrier_retry_shipment (shipment_id, attempt_number)
) ENGINE=InnoDB;


CREATE DATABASE `inventory-service` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `inventory-service`;

CREATE TABLE `checkout_reservation_audit` (
  `id` varchar(36) NOT NULL,
  `child_id` varchar(36) NOT NULL,
  `previous_state` varchar(24) DEFAULT NULL,
  `next_state` varchar(24) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_reservation_audit_child` (`child_id`),
  CONSTRAINT `fk_reservation_audit_child` FOREIGN KEY (`child_id`) REFERENCES `checkout_reservations` (`child_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `checkout_reservation_lines` (
  `child_id` varchar(36) NOT NULL,
  `inventory_id` binary(16) NOT NULL,
  `quantity` int NOT NULL,
  PRIMARY KEY (`child_id`,`inventory_id`),
  KEY `fk_reservation_line_inventory` (`inventory_id`),
  CONSTRAINT `fk_reservation_line_child` FOREIGN KEY (`child_id`) REFERENCES `checkout_reservations` (`child_id`),
  CONSTRAINT `fk_reservation_line_inventory` FOREIGN KEY (`inventory_id`) REFERENCES `inventories` (`id`),
  CONSTRAINT `chk_reservation_quantity` CHECK ((`quantity` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `checkout_reservation_lock` (
  `id` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `checkout_reservations` (
  `child_id` varchar(36) NOT NULL,
  `checkout_id` varchar(36) NOT NULL,
  `customer_id` varchar(36) NOT NULL,
  `store_id` varchar(100) NOT NULL,
  `request_hash` varchar(64) NOT NULL,
  `state` varchar(24) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`child_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `inventories` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `available_quantity` int NOT NULL,
  `product_id` binary(16) NOT NULL,
  `reorder_level` int NOT NULL,
  `reserved_quantity` int NOT NULL,
  `status` varchar(20) NOT NULL,
  `store_id` varchar(100) NOT NULL,
  `variant_id` binary(16) DEFAULT NULL,
  `variant_key` binary(16) NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventories_lookup` (`product_id`,`variant_key`,`store_id`),
  CONSTRAINT `inventories_chk_1` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'INACTIVE',_utf8mb4'OUT_OF_STOCK')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `stock_movements` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `movement_type` varchar(20) NOT NULL,
  `quantity` int NOT NULL,
  `reason` varchar(500) NOT NULL,
  `inventory_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKpjmqc52qx806y3aafjnwteqo0` (`inventory_id`),
  CONSTRAINT `FKpjmqc52qx806y3aafjnwteqo0` FOREIGN KEY (`inventory_id`) REFERENCES `inventories` (`id`),
  CONSTRAINT `stock_movements_chk_1` CHECK ((`movement_type` in (_utf8mb4'INCREASE',_utf8mb4'DECREASE',_utf8mb4'RESERVE',_utf8mb4'RELEASE',_utf8mb4'ADJUST')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sync_inbox` (
  `source` varchar(64) NOT NULL,
  `event_id` varchar(36) NOT NULL,
  `body_hash` varchar(64) NOT NULL,
  `received_at` bigint NOT NULL,
  PRIMARY KEY (`source`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sync_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_id` varchar(36) NOT NULL,
  `target` varchar(64) NOT NULL,
  `aggregate_id` varchar(128) NOT NULL,
  `body` longtext NOT NULL,
  `state` varchar(16) NOT NULL,
  `attempts` int NOT NULL,
  `next_attempt` bigint NOT NULL,
  `lease_token` varchar(36) DEFAULT NULL,
  `lease_until` bigint DEFAULT NULL,
  `last_error` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `event_id` (`event_id`,`target`),
  KEY `sync_outbox_due` (`state`,`next_attempt`),
  KEY `sync_outbox_ordering` (`target`,`aggregate_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sync_receiver_lock` (
  `id` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sync_receiver_lock (id) VALUES (1);

INSERT INTO checkout_reservation_lock (id) VALUES (1);

CREATE DATABASE `notification_service` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `notification_service`;

CREATE TABLE `notification_templates` (
  `id` binary(16) NOT NULL,
  `name` varchar(120) NOT NULL,
  `notification_type` varchar(40) NOT NULL,
  `channel` varchar(20) NOT NULL,
  `subject_template` varchar(255) NOT NULL,
  `message_template` text NOT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` timestamp(6) NOT NULL,
  `updated_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_templates_name` (`name`),
  KEY `idx_notification_templates_type_channel_status` (`notification_type`,`channel`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `notifications` (
  `id` binary(16) NOT NULL,
  `customer_id` binary(16) NOT NULL,
  `order_id` binary(16) DEFAULT NULL,
  `notification_type` varchar(40) NOT NULL,
  `channel` varchar(20) NOT NULL,
  `recipient` varchar(255) DEFAULT NULL,
  `subject` varchar(255) NOT NULL,
  `message` text NOT NULL,
  `status` varchar(20) NOT NULL,
  `source_event_id` binary(16) DEFAULT NULL,
  `failure_reason` varchar(500) DEFAULT NULL,
  `sent_at` timestamp(6) NULL DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL,
  `updated_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notifications_source_event` (`source_event_id`),
  KEY `idx_notifications_customer_created` (`customer_id`,`created_at`),
  KEY `idx_notifications_order_created` (`order_id`,`created_at`),
  KEY `idx_notifications_type` (`notification_type`),
  KEY `idx_notifications_channel` (`channel`),
  KEY `idx_notifications_status_created` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sync_inbox` (
  `source` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `event_id` varchar(36) COLLATE utf8mb4_bin NOT NULL,
  `body_hash` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `received_at` bigint NOT NULL,
  PRIMARY KEY (`source`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `sync_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_id` varchar(36) COLLATE utf8mb4_bin NOT NULL,
  `target` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `aggregate_id` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `body` longtext COLLATE utf8mb4_bin NOT NULL,
  `state` varchar(16) COLLATE utf8mb4_bin NOT NULL,
  `attempts` int NOT NULL,
  `next_attempt` bigint NOT NULL,
  `lease_token` varchar(36) COLLATE utf8mb4_bin DEFAULT NULL,
  `lease_until` bigint DEFAULT NULL,
  `last_error` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `event_id` (`event_id`,`target`),
  KEY `sync_outbox_due` (`state`,`next_attempt`),
  KEY `sync_outbox_ordering` (`target`,`aggregate_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `sync_receiver_lock` (
  `id` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sync_receiver_lock (id) VALUES (1);

-- Docker snapshot update: V4__notification_read_and_clear.sql

ALTER TABLE notifications
  ADD COLUMN read_at TIMESTAMP(6) NULL,
  ADD COLUMN cleared_at TIMESTAMP(6) NULL,
  ADD INDEX idx_notifications_customer_cleared (customer_id, cleared_at, created_at);


CREATE DATABASE `order-service` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `order-service`;

CREATE TABLE `order_addresses` (
  `id` binary(16) NOT NULL,
  `order_id` binary(16) NOT NULL,
  `address_type` varchar(30) NOT NULL,
  `address_line1` varchar(255) NOT NULL,
  `address_line2` varchar(255) DEFAULT NULL,
  `city` varchar(120) NOT NULL,
  `state` varchar(120) NOT NULL,
  `country` varchar(120) NOT NULL,
  `postal_code` varchar(30) NOT NULL,
  `recipient_name` varchar(180) DEFAULT NULL,
  `recipient_phone` varchar(30) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order_addresses_order` (`order_id`),
  KEY `idx_order_addresses_type` (`order_id`,`address_type`),
  CONSTRAINT `fk_order_addresses_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_creation_requests` (
  `customer_id` varchar(36) COLLATE utf8mb4_bin NOT NULL,
  `request_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_hash` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `order_id` varchar(36) COLLATE utf8mb4_bin NOT NULL,
  PRIMARY KEY (`customer_id`,`request_key`),
  UNIQUE KEY `uk_order_creation_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `order_items` (
  `id` binary(16) NOT NULL,
  `order_id` binary(16) NOT NULL,
  `product_id` binary(16) NOT NULL,
  `variant_id` binary(16) DEFAULT NULL,
  `product_name` varchar(255) NOT NULL,
  `quantity` int NOT NULL,
  `unit_price` decimal(19,2) NOT NULL,
  `total_price` decimal(19,2) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order_items_order` (`order_id`),
  KEY `idx_order_items_product_variant` (`product_id`,`variant_id`),
  CONSTRAINT `fk_order_items_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_reviews` (
  `id` binary(16) NOT NULL,
  `order_id` binary(16) NOT NULL,
  `customer_id` binary(16) NOT NULL,
  `rating` tinyint NOT NULL,
  `comment` varchar(1000) NOT NULL,
  `photo_urls` json NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `order_id` (`order_id`),
  CONSTRAINT `fk_order_reviews_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `chk_order_reviews_rating` CHECK ((`rating` between 1 and 5))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `orders` (
  `id` binary(16) NOT NULL,
  `customer_id` binary(16) NOT NULL,
  `cart_id` binary(16) NOT NULL,
  `store_id` varchar(100) NOT NULL,
  `delivery_zone_id` bigint DEFAULT NULL,
  `calculated_distance_km` decimal(8,2) DEFAULT NULL,
  `route_distance_meters` bigint DEFAULT NULL,
  `route_duration_seconds` int DEFAULT NULL,
  `route_duration_minutes` int DEFAULT NULL,
  `matched_zone_label` varchar(80) DEFAULT NULL,
  `customer_latitude` decimal(10,7) DEFAULT NULL,
  `customer_longitude` decimal(10,7) DEFAULT NULL,
  `store_latitude` decimal(10,7) DEFAULT NULL,
  `store_longitude` decimal(10,7) DEFAULT NULL,
  `delivery_method` varchar(80) DEFAULT NULL,
  `fulfillment_type` varchar(40) DEFAULT NULL,
  `global_approval` varchar(40) DEFAULT NULL,
  `delivery_charge` decimal(10,2) NOT NULL DEFAULT '0.00',
  `estimated_delivery_min` int DEFAULT NULL,
  `estimated_delivery_max` int DEFAULT NULL,
  `free_delivery_above` decimal(10,2) DEFAULT NULL,
  `free_delivery_applied` tinyint(1) NOT NULL DEFAULT '0',
  `scheduled_delivery_date` date DEFAULT NULL,
  `scheduled_delivery_slot` varchar(80) DEFAULT NULL,
  `pickup_details` varchar(1000) DEFAULT NULL,
  `order_number` varchar(40) NOT NULL,
  `status` varchar(30) NOT NULL,
  `total_amount` decimal(19,2) NOT NULL,
  `payment_status` varchar(30) NOT NULL,
  `created_at` timestamp(6) NOT NULL,
  `updated_at` timestamp(6) NOT NULL,
  `handling_fee` decimal(10,2) NOT NULL DEFAULT '0.00',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_orders_order_number` (`order_number`),
  KEY `idx_orders_customer_created` (`customer_id`,`created_at`),
  KEY `idx_orders_customer_status_created` (`customer_id`,`status`,`created_at`),
  KEY `idx_orders_cart` (`cart_id`),
  KEY `idx_orders_store` (`store_id`),
  KEY `idx_orders_store_status_created` (`store_id`,`status`,`created_at`),
  KEY `idx_orders_delivery_method` (`delivery_method`),
  KEY `idx_orders_fulfillment_type` (`fulfillment_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sync_inbox` (
  `source` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `event_id` varchar(36) COLLATE utf8mb4_bin NOT NULL,
  `body_hash` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `received_at` bigint NOT NULL,
  PRIMARY KEY (`source`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `sync_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_id` varchar(36) COLLATE utf8mb4_bin NOT NULL,
  `target` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `aggregate_id` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `body` longtext COLLATE utf8mb4_bin NOT NULL,
  `state` varchar(16) COLLATE utf8mb4_bin NOT NULL,
  `attempts` int NOT NULL,
  `next_attempt` bigint NOT NULL,
  `lease_token` varchar(36) COLLATE utf8mb4_bin DEFAULT NULL,
  `lease_until` bigint DEFAULT NULL,
  `last_error` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `event_id` (`event_id`,`target`),
  KEY `sync_outbox_due` (`state`,`next_attempt`),
  KEY `sync_outbox_ordering` (`target`,`aggregate_id`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `sync_receiver_lock` (
  `id` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sync_receiver_lock (id) VALUES (1);

-- Docker snapshot update: V13__checkout_groups.sql

CREATE TABLE checkout_groups (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  customer_id VARCHAR(36) NOT NULL,
  request_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_hash VARCHAR(64) NOT NULL,
  request_payload LONGTEXT NOT NULL,
  state VARCHAR(32) NOT NULL,
  handling_policy VARCHAR(32) NOT NULL,
  aggregate_total DECIMAL(19,2) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_checkout_customer_key (customer_id, request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE checkout_children (
  checkout_id VARCHAR(36) NOT NULL,
  store_id VARCHAR(100) NOT NULL,
  cart_id VARCHAR(36) NOT NULL,
  order_id VARCHAR(36) NULL,
  state VARCHAR(32) NOT NULL,
  handling_fee DECIMAL(19,2) NOT NULL,
  PRIMARY KEY (checkout_id, store_id),
  UNIQUE KEY uk_checkout_cart (cart_id),
  UNIQUE KEY uk_checkout_child_order (order_id),
  CONSTRAINT fk_checkout_child_group FOREIGN KEY (checkout_id) REFERENCES checkout_groups(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE checkout_audit (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  checkout_id VARCHAR(36) NOT NULL,
  previous_state VARCHAR(32) NULL,
  next_state VARCHAR(32) NOT NULL,
  actor VARCHAR(36) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_checkout_audit_group FOREIGN KEY (checkout_id) REFERENCES checkout_groups(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;


-- Docker snapshot update: V14__checkout_reservation_identity.sql

ALTER TABLE checkout_children ADD COLUMN reservation_id VARCHAR(36) NULL;
UPDATE checkout_children SET reservation_id=UUID() WHERE reservation_id IS NULL;
ALTER TABLE checkout_children MODIFY reservation_id VARCHAR(36) NOT NULL;
CREATE UNIQUE INDEX uk_checkout_reservation ON checkout_children(reservation_id);


-- Docker snapshot update: V15__add_coupon_fields_to_orders.sql

ALTER TABLE orders
  ADD COLUMN coupon_code VARCHAR(64) NULL,
  ADD COLUMN discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00;


-- Docker snapshot update: V17__add_global_store_order_assignment.sql

ALTER TABLE orders
  ADD COLUMN global_assigned_store_id VARCHAR(100) NULL,
  ADD COLUMN global_assigned_at TIMESTAMP(6) NULL;

CREATE INDEX idx_orders_global_assigned_store ON orders(global_assigned_store_id);


-- Docker snapshot update: V18__create_moderated_order_reviews.sql

-- V15/V16 already create the legacy order-level review table. Extend that table
-- so the moderation and product-review endpoints use the same persisted model.
-- Product fields remain nullable for pre-existing order-level review rows.
ALTER TABLE order_reviews
  ADD COLUMN product_id BINARY(16) NULL AFTER customer_id,
  ADD COLUMN product_name VARCHAR(255) NULL AFTER product_id,
  ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING' AFTER photo_urls,
  ADD COLUMN admin_reply VARCHAR(1000) NULL AFTER status,
  DROP INDEX order_id,
  ADD CONSTRAINT uk_order_reviews_order_product UNIQUE (order_id, product_id),
  ADD CONSTRAINT chk_order_reviews_status CHECK (status IN ('PENDING','APPROVED','REJECTED')),
  ADD INDEX idx_order_reviews_product_status_created (product_id, status, created_at),
  ADD INDEX idx_order_reviews_customer_created (customer_id, created_at),
  ADD INDEX idx_order_reviews_status_created (status, created_at);


CREATE DATABASE `payment_service` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `payment_service`;

CREATE TABLE `payment_transactions` (
  `id` binary(16) NOT NULL,
  `payment_id` binary(16) NOT NULL,
  `transaction_reference` varchar(60) NOT NULL,
  `transaction_type` varchar(30) NOT NULL,
  `amount` decimal(19,2) NOT NULL,
  `status` varchar(30) NOT NULL,
  `created_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_transactions_reference` (`transaction_reference`),
  KEY `idx_payment_transactions_payment` (`payment_id`),
  KEY `idx_payment_transactions_type_status` (`transaction_type`,`status`),
  CONSTRAINT `fk_payment_transactions_payment` FOREIGN KEY (`payment_id`) REFERENCES `payments` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payments` (
  `id` binary(16) NOT NULL,
  `order_id` binary(16) NOT NULL,
  `customer_id` binary(16) NOT NULL,
  `payment_reference` varchar(50) NOT NULL,
  `amount` decimal(19,2) NOT NULL,
  `currency` varchar(3) NOT NULL,
  `payment_method` varchar(30) NOT NULL,
  `status` varchar(30) NOT NULL,
  `created_at` timestamp(6) NOT NULL,
  `updated_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payments_payment_reference` (`payment_reference`),
  UNIQUE KEY `uk_payments_order` (`order_id`),
  KEY `idx_payments_order` (`order_id`),
  KEY `idx_payments_customer_created` (`customer_id`,`created_at`),
  KEY `idx_payments_status_created` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `provider_payment_bindings` (
  `provider_payment_id` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `payment_id` varchar(36) COLLATE utf8mb4_bin NOT NULL,
  PRIMARY KEY (`provider_payment_id`),
  UNIQUE KEY `payment_id` (`payment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `provider_payment_receipts` (
  `event_id` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `provider_payment_id` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `evidence_id` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `body_hash` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  PRIMARY KEY (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `sync_inbox` (
  `source` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `event_id` varchar(36) COLLATE utf8mb4_bin NOT NULL,
  `body_hash` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `received_at` bigint NOT NULL,
  PRIMARY KEY (`source`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `sync_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_id` varchar(36) COLLATE utf8mb4_bin NOT NULL,
  `target` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `aggregate_id` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `body` longtext COLLATE utf8mb4_bin NOT NULL,
  `state` varchar(16) COLLATE utf8mb4_bin NOT NULL,
  `attempts` int NOT NULL,
  `next_attempt` bigint NOT NULL,
  `lease_token` varchar(36) COLLATE utf8mb4_bin DEFAULT NULL,
  `lease_until` bigint DEFAULT NULL,
  `last_error` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `event_id` (`event_id`,`target`),
  KEY `sync_outbox_due` (`state`,`next_attempt`),
  KEY `sync_outbox_ordering` (`target`,`aggregate_id`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `sync_receiver_lock` (
  `id` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sync_receiver_lock (id) VALUES (1);

CREATE DATABASE `users_roles` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `users_roles`;

CREATE TABLE `app_customer_contacts` (
  `contact` varchar(320) NOT NULL,
  `customer_user_id` bigint NOT NULL,
  PRIMARY KEY (`contact`),
  KEY `customer_user_id` (`customer_user_id`),
  CONSTRAINT `app_customer_contacts_ibfk_1` FOREIGN KEY (`customer_user_id`) REFERENCES `app_customers` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `app_customer_otp` (
  `id` char(36) NOT NULL,
  `user_id` bigint NOT NULL,
  `purpose` varchar(20) NOT NULL,
  `code_hash` varchar(255) NOT NULL,
  `expires_at` timestamp(6) NOT NULL,
  `sent_at` timestamp(6) NOT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `consumed` tinyint(1) NOT NULL DEFAULT '0',
  `reset_hash` varchar(255) DEFAULT NULL,
  `reset_expires_at` timestamp(6) NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_otp_user_purpose` (`user_id`,`purpose`,`sent_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `app_customers` (
  `user_id` bigint NOT NULL AUTO_INCREMENT,
  `id` binary(16) NOT NULL,
  `username` varchar(190) NOT NULL,
  `email` varchar(255) NOT NULL,
  `mobile` varchar(30) DEFAULT NULL,
  `full_name` varchar(120) NOT NULL,
  `password_hash` varchar(255) DEFAULT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `locked` tinyint(1) NOT NULL DEFAULT '0',
  `email_verified` tinyint(1) NOT NULL DEFAULT '1',
  `token_version` bigint NOT NULL DEFAULT '0',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `id` (`id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=218 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `audit_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `actor` varchar(190) NOT NULL,
  `action` varchar(80) NOT NULL,
  `target` varchar(255) NOT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `auth_rate_limits` (
  `rate_key` varchar(255) NOT NULL,
  `window_start` timestamp(6) NOT NULL,
  `attempts` int NOT NULL,
  PRIMARY KEY (`rate_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `customer_contact_claims` (
  `contact` varchar(320) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`contact`),
  KEY `user_id` (`user_id`),
  CONSTRAINT `customer_contact_claims_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `customer_otp` (
  `id` char(36) NOT NULL,
  `user_id` bigint NOT NULL,
  `purpose` varchar(20) NOT NULL,
  `code_hash` varchar(255) NOT NULL,
  `expires_at` timestamp(6) NOT NULL,
  `sent_at` timestamp(6) NOT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `consumed` tinyint(1) NOT NULL DEFAULT '0',
  `reset_hash` varchar(255) DEFAULT NULL,
  `reset_expires_at` timestamp(6) NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_otp_user_purpose` (`user_id`,`purpose`,`sent_at`),
  CONSTRAINT `customer_otp_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `customers` (
  `id` binary(16) NOT NULL,
  `user_id` bigint NOT NULL,
  `full_name` varchar(120) NOT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`),
  CONSTRAINT `fk_customer_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `delivery_verifications` (
  `order_id` varchar(36) NOT NULL,
  `partner_id` bigint NOT NULL,
  `code_hash` varchar(255) NOT NULL,
  `issued_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` timestamp NOT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `consumed` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_id`),
  KEY `partner_id` (`partner_id`),
  CONSTRAINT `delivery_verifications_ibfk_1` FOREIGN KEY (`partner_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `memberships` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `organization_id` varchar(100) DEFAULT NULL,
  `location_id` varchar(100) DEFAULT NULL,
  `store_id` varchar(100) DEFAULT NULL,
  `store_name` varchar(180) DEFAULT NULL,
  `store_location` varchar(160) DEFAULT NULL,
  `store_address` varchar(700) DEFAULT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `provider` varchar(40) DEFAULT NULL,
  `provider_place_id` varchar(120) DEFAULT NULL,
  `provider_osm_type` varchar(20) DEFAULT NULL,
  `provider_osm_id` varchar(40) DEFAULT NULL,
  `location_accuracy_meters` double DEFAULT NULL,
  `assigned_by` varchar(190) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `role_id` (`role_id`),
  CONSTRAINT `memberships_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `memberships_ibfk_2` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=180 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `pending_signups` (
  `id` char(36) NOT NULL,
  `email` varchar(255) NOT NULL,
  `mobile` varchar(30) NOT NULL,
  `full_name` varchar(120) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `code_hash` varchar(255) NOT NULL,
  `expires_at` timestamp(6) NOT NULL,
  `sent_at` timestamp(6) NOT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pending_signups_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(120) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `revoked_tokens` (
  `token_hash` char(64) NOT NULL,
  `expires_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`token_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `role_permissions` (
  `role_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  PRIMARY KEY (`role_id`,`permission_id`),
  KEY `permission_id` (`permission_id`),
  CONSTRAINT `role_permissions_ibfk_1` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`),
  CONSTRAINT `role_permissions_ibfk_2` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `roles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(80) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_roles` (
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`user_id`,`role_id`),
  KEY `role_id` (`role_id`),
  CONSTRAINT `user_roles_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `user_roles_ibfk_2` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(190) NOT NULL,
  `email` varchar(255) NOT NULL,
  `password_hash` varchar(255) DEFAULT NULL,
  `oauth_subject` varchar(255) DEFAULT NULL,
  `display_name` varchar(180) DEFAULT NULL,
  `mobile` varchar(30) DEFAULT NULL,
  `status` varchar(30) NOT NULL DEFAULT 'Active',
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `locked` tinyint(1) NOT NULL DEFAULT '0',
  `vehicle_type` varchar(40) DEFAULT NULL,
  `vehicle_number` varchar(40) DEFAULT NULL,
  `licence_number` varchar(60) DEFAULT NULL,
  `address` varchar(700) DEFAULT NULL,
  `emergency_contact` varchar(30) DEFAULT NULL,
  `delivery_latitude` double DEFAULT NULL,
  `delivery_longitude` double DEFAULT NULL,
  `delivery_location_updated_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `email_verified` tinyint(1) NOT NULL DEFAULT '1',
  `token_version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `email` (`email`),
  UNIQUE KEY `oauth_subject` (`oauth_subject`)
) ENGINE=InnoDB AUTO_INCREMENT=206 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

USE `users_roles`;
CREATE SQL SECURITY INVOKER VIEW `app_users` AS select `u`.`id` AS `id`,`u`.`username` AS `username`,`u`.`email` AS `email`,`u`.`password_hash` AS `password_hash`,`u`.`oauth_subject` AS `oauth_subject`,`u`.`display_name` AS `display_name`,`u`.`mobile` AS `mobile`,`u`.`status` AS `status`,`u`.`enabled` AS `enabled`,`u`.`locked` AS `locked`,`u`.`vehicle_type` AS `vehicle_type`,`u`.`vehicle_number` AS `vehicle_number`,`u`.`licence_number` AS `licence_number`,`u`.`address` AS `address`,`u`.`emergency_contact` AS `emergency_contact`,`u`.`delivery_latitude` AS `delivery_latitude`,`u`.`delivery_longitude` AS `delivery_longitude`,`u`.`delivery_location_updated_at` AS `delivery_location_updated_at`,`u`.`created_at` AS `created_at`,`u`.`updated_at` AS `updated_at`,`u`.`email_verified` AS `email_verified`,`u`.`token_version` AS `token_version` from `users` `u` where exists(select 1 from `app_customers` `c` where (`c`.`username` = `u`.`username`)) is false;

SET FOREIGN_KEY_CHECKS=1;
