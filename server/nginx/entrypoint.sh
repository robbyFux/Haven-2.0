#!/bin/sh
set -e

CERT_DIR=/etc/nginx/certs

if [ ! -f "$CERT_DIR/haven.crt" ] || [ ! -f "$CERT_DIR/haven.key" ]; then
    mkdir -p "$CERT_DIR"
    CERT_HOST="${CERT_HOSTNAME:-localhost}"
    openssl req -x509 -newkey rsa:4096 \
        -keyout "$CERT_DIR/haven.key" \
        -out  "$CERT_DIR/haven.crt" \
        -days 3650 -nodes \
        -subj "/C=DE/O=Haven/CN=${CERT_HOST}" \
        -addext "subjectAltName=DNS:${CERT_HOST},DNS:localhost,IP:127.0.0.1" \
        2>/dev/null
    echo "[Haven TLS] Self-signed certificate generated (valid 10 years)"
    echo "[Haven TLS] CN=${CERT_HOST} | SAN: ${CERT_HOST}, localhost, 127.0.0.1"
    echo "[Haven TLS] Export:  docker compose cp nginx:/etc/nginx/certs/haven.crt ./haven.crt"
fi

exec nginx -g 'daemon off;'
