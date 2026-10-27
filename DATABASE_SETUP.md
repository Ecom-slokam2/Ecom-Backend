# Using existing databases

Runtime profiles and the bundled Config Server configuration disable Flyway and Spring SQL initialization. Hibernate validates existing schemas instead of updating them. JDBC URLs no longer request automatic database creation. Test profiles retain isolated test database initialization.

Provision databases, tables, indexes, constraints, and required operational records manually before startup. In particular, HTTP sync requires `sync_receiver_lock` with `id=1`, and cart operations require `cart_write_lock` with `id=1`. These are operational lock records, not demo data. Existing roles, permissions, and application accounts must also be provisioned as needed. The order review table must already exist; startup no longer creates it. Inventory startup no longer inserts the default raisins stock record.

Migration files and existing `flyway_schema_history` records are retained but are not executed by the configured runtime. No existing demo records are deleted. Review any cleanup separately.

Before starting, check external Config Server responses, environment variables, and command-line arguments: they can override repository configuration. Effective settings must be:

```properties
spring.flyway.enabled=false
spring.jpa.hibernate.ddl-auto=validate
spring.sql.init.mode=never
```

Schema changes are now managed manually. A missing or incompatible mapped table/column causes validation to fail rather than creating it.

Catalog migration V36__normalize_banner_scope_columns.sql converts the banners.scope and banners.status columns to the VARCHAR(20) types already defined by the original banner schema and current entity mappings. It preserves existing enum text values. Review and apply this migration to the intended Catalog schema using the database change process before starting Catalog; runtime Flyway remains disabled.

## Verification on a database copy

1. Restore backups to separate verification databases and point the services at those copies.
2. Capture schema definitions and exact table row counts before startup.
3. For a startup-only comparison, set `SYNC_DISPATCH_ENABLED=false` so pending outbox business events are not processed. Do not send API requests or connect external event producers.
4. Start the services and confirm schema validation/readiness checks succeed, with no Flyway migration execution.
5. Compare schema definitions and exact row counts with the baseline. Inspect any differences before using the configuration with your main databases.
6. Restore normal event dispatch for business operation. Processing existing queued business events can legitimately update data; it does not seed demo data.

No live database startup or before/after database comparison is performed by the repository checks alone.
