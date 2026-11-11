#!/bin/sh
set -eu
if [ ! -s /tls/truststore.p12 ]; then
  umask 077
  openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
    -keyout /tls/server.key -out /tls/server.crt \
    -subj '/CN=sync-proxy' -addext 'subjectAltName=DNS:sync-proxy'
  keytool -importkeystore -noprompt \
    -srckeystore "$JAVA_HOME/lib/security/cacerts" -srcstoretype JKS \
    -srcstorepass changeit -destkeystore /tls/truststore.p12 \
    -deststoretype PKCS12 -deststorepass changeit
  keytool -importcert -noprompt -alias ecom-internal-sync \
    -file /tls/server.crt -keystore /tls/truststore.p12 \
    -storetype PKCS12 -storepass changeit
  chmod 644 /tls/server.crt /tls/truststore.p12
fi
