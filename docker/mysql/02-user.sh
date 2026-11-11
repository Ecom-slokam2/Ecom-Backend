#!/bin/bash
set -euo pipefail
# Hex encoding safely handles quotes and shell metacharacters in the password.
password_hex=$(printf %s "$DOCKER_MYSQL_PASSWORD" | od -An -v -tx1 | tr -d ' \n')
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot <<SQL
SET @password = CONVERT(0x${password_hex} USING utf8mb4);
SET @statement = CONCAT('CREATE USER ''ecom''@''%'' IDENTIFIED BY ', QUOTE(@password));
PREPARE user_statement FROM @statement;
EXECUTE user_statement;
DEALLOCATE PREPARE user_statement;
SQL
for database in users_roles customer_profiles catalog_service inventory-service cart_service order-service payment_service fulfillment-service notification_service; do
    MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "GRANT ALL PRIVILEGES ON \`$database\`.* TO 'ecom'@'%';"
done
# Health checks require this marker, not merely an open MySQL socket.
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot <<'SQL'
CREATE DATABASE docker_bootstrap;
CREATE TABLE docker_bootstrap.status (ready INT NOT NULL PRIMARY KEY);
INSERT INTO docker_bootstrap.status VALUES (1);
SQL
