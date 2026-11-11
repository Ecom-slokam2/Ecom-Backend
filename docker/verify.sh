#!/bin/sh
# Read-only checks against the running Compose stack; run from the repository root.
set -eu
docker compose ps
for service in eureka config-server org-access customer-profile-service catalog-service inventory-service cart-service order-service payment-service fulfillment-service notification-service api-gateway; do
  docker compose exec -T "$service" sh -c 'curl -fsS "http://localhost:$SERVER_PORT/actuator/health" >/dev/null'
  printf 'Healthy: %s\n' "$service"
done
docker compose exec -T alerts-service curl -fsS http://localhost:8087/api/alerts/health
printf 'Healthy: alerts-service\n'

# Eureka must see every discovery-enabled application. The registry response is
# XML by default, so check each application name directly in the registry body.
registry=$(docker compose exec -T eureka sh -c 'curl -fsS http://localhost:8761/eureka/apps')
for application in CONFIG-SERVER ORG-ACCESS CUSTOMER-PROFILE-SERVICE CATALOG-SERVICE INVENTORY-SERVICE CART-SERVICE ORDER-SERVICE PAYMENT-SERVICE FULFILLMENT-SERVICE NOTIFICATION-SERVICE API-GATEWAY ALERTS-SERVICE; do
  printf '%s\n' "$registry" | grep -q "<name>$application</name>" || {
    printf 'Missing Eureka registration: %s\n' "$application" >&2
    exit 1
  }
  printf 'Registered in Eureka: %s\n' "$application"
done

# Exercise certificate validation and proxy forwarding without creating an event.
# Missing authentication must be rejected by each application with HTTP 401.
for port in 3311 3314 3315 3317 8095; do
  status=$(docker compose exec -T order-service curl --cacert /tls/server.crt -sS -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' -d '{}' "https://sync-proxy:$port/internal/events")
  test "$status" = 401 || { printf 'Unexpected sync response on %s: %s\n' "$port" "$status"; exit 1; }
  printf 'Verified TLS and authenticated sync endpoint: %s\n' "$port"
done
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -Nse "SELECT ready FROM docker_bootstrap.status"' | grep -qx 1
printf 'Database bootstrap verified.\n'
