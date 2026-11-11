# Run the backend with Docker Compose

Install Docker Desktop (or Docker Engine with Compose). Java, Maven, MySQL and Kafka do not need to be installed on the host. Allow roughly 8 GB or more of Docker memory for the full stack; the initial build downloads Maven dependencies and container images.

From the repository root:

```bash
docker compose up -d --build --wait --wait-timeout 600
```

Subsequent starts can use `docker compose up -d`. The stack contains all 13 backend applications, MySQL, Kafka, an internal HTTPS synchronization proxy, a certificate initialization job, and Mailpit for local email delivery. `http-sync` is built as a shared library, not run as an application. The sibling frontend is not included.

## Access

| Component | Local address |
|---|---|
| API Gateway | http://localhost:18080 |
| Eureka | http://localhost:18081 |
| Config Server | http://localhost:18082 |
| Local email inbox (including OTP messages) | http://localhost:18093 |
| Org Access | http://localhost:18083 |
| Customer Profile | http://localhost:18084 |
| Catalog | http://localhost:18085 |
| Inventory | http://localhost:18086 |
| Cart | http://localhost:18087 |
| Order | http://localhost:18088 |
| Payment | http://localhost:18089 |
| Fulfillment | http://localhost:18090 |
| Notification | http://localhost:18091 |
| Alerts | http://localhost:18092 |

Published ports bind to localhost. MySQL, Kafka and the sync proxy are reachable only on the Compose network. All database services use `mysql:3306`, with separate schemas and the `ecom` database user. Inspect MySQL with `docker compose exec mysql mysql -uecom -p` (default local password: `ecom-local-password`).

## Configuration

The defaults work without creating an environment file. They are local development credentials. To override them, copy `docker/.env.example` to `docker/.env`, edit it, and run:

```bash
docker compose --env-file docker/.env up -d --build
```

Use the same `--env-file` on subsequent commands. Existing root `.env` contents are not passed wholesale to containers. Docker-specific settings use the `DOCKER_` prefix. MySQL initialization and database grants happen only on the first start of an empty MySQL volume; changing a password variable later does not change an existing database password.

If ports are already occupied, override the corresponding host port. For example:

```bash
DOCKER_EUREKA_PORT=28081 DOCKER_CONFIG_SERVER_PORT=28082 docker compose up -d
```

The same `DOCKER_<SERVICE_NAME>_PORT` pattern applies to the other services (uppercase, with hyphens replaced by underscores). The default host ports are 18080–18093. Internal container ports and addresses remain unchanged. Existing DOCKER_*_PORT values in your shell or environment file override these defaults. Update frontend API URLs, reverse proxy upstreams and SSH tunnels to use the new host ports (gateway: 18080).

## Database initialization and persistence

`docker/mysql/01-schema.sql` provisions a fresh, isolated database volume. It contains schema definitions extracted from the checked-in `Ecom_DB` snapshot, newer schema changes required by current services, the `app_users` view, and required synchronization/checkout lock rows. It intentionally excludes customer data, accounts, password hashes, catalog/demo records, old events, and Flyway history. A fresh stack therefore has empty business data and no pre-created login accounts. Provision application roles/accounts/catalog records through the application's supported workflows or reviewed SQL as needed.

Flyway stays disabled, Hibernate only validates, and Spring SQL initialization stays disabled, matching `DATABASE_SETUP.md`. Schema changes belong in reviewed Docker initialization SQL as well as the project's existing manual database process. Editing initialization files does not update an already initialized volume. This setup does not connect to or import your host MySQL database.

Named volumes retain MySQL data, Kafka data and the internal TLS certificate across container restarts and `docker compose down`.

```bash
docker compose ps
docker compose logs -f order-service
docker compose restart order-service
docker compose down
```

To deliberately delete this stack's databases, Kafka data and certificates and initialize again:

```bash
docker compose down -v
```

## Internal HTTPS and external integrations

The existing event dispatcher requires HTTPS for non-loopback destinations. `tls-init` creates a development certificate and a Java truststore containing both the standard public CAs and the internal certificate. The Nginx proxy exposes only `/internal/events` internally, verifies no application credentials itself, and forwards the signed requests unchanged. Java verifies its certificate normally. No application source changes or TLS verification bypasses are used. The certificate is persisted for ten years; renew it before expiry by replacing the TLS volume while services are stopped.

Mail is captured by Mailpit, so the stack does not send real emails. Payments, SMS, maps/routing and other external providers still need their real credentials/settings to exercise those features. Supply overrides explicitly in Compose/environment configuration; the existing host `.env` is not automatically forwarded. This Compose configuration is intended for local development.

Run `./docker/verify.sh` from the repository root for read-only application health, MySQL bootstrap, TLS certificate and synchronization authentication checks. With a custom environment file, export its settings first or use the equivalent `docker compose --env-file ...` commands. These checks do not create customers or place orders.
