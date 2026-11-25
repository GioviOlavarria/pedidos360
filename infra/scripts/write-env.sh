#!/usr/bin/env bash
# Escribe infra/.env a partir de variables de entorno. Lo usan los workflows de GitHub Actions, que
# reciben los valores desde GitHub Secrets (ver README, sección "GitHub Secrets").
#
#   write-env.sh <archivo> local   -> .env para levantar el compose en un PC (BFF en localhost)
#   write-env.sh <archivo> cloud   -> .env del servidor; CLOUD_URL es la URL pública de API Gateway
#
# Solo se escriben las variables definidas: el resto toma el valor por defecto de docker-compose.yml.
set -euo pipefail

out="${1:?archivo de salida}"
mode="${2:-local}"

keys=(
  DB_URL DB_USER DB_PASSWORD DB_ROOT_PASSWORD
  AZURE_AD_TENANT_ID AZURE_AD_CLIENT_ID API_CLIENT_ID
  ADMIN_EMAILS ADMIN_TENANT_ID
  INTERNAL_API_KEY
  RABBITMQ_USER RABBITMQ_PASSWORD RABBITMQ_ERLANG_COOKIE
  MAIL_HOST MAIL_PORT MAIL_USERNAME MAIL_PASSWORD MAIL_SMTP_AUTH MAIL_SMTP_STARTTLS MAIL_FROM
  SII_MODE SII_RUT_EMISOR
  PAYMENT_PROVIDER FLOW_API_URL FLOW_API_KEY FLOW_SECRET_KEY PAYMENT_EXPIRATION
)

if [ "$mode" = cloud ]; then
  : "${CLOUD_URL:?CLOUD_URL (URL de API Gateway) es obligatoria en modo cloud}"
  CLOUD_URL="${CLOUD_URL%/}"
  # Frontend y API comparten el dominio de API Gateway (/api/* -> BFF, el resto -> frontend).
  BFF_URL="$CLOUD_URL"
  FRONTEND_URL="$CLOUD_URL"
  PAYMENTS_PUBLIC_URL="$CLOUD_URL"
  CORS_ALLOWED_ORIGINS="$CLOUD_URL"
  keys+=(BFF_URL FRONTEND_URL PAYMENTS_PUBLIC_URL CORS_ALLOWED_ORIGINS IMAGE_REGISTRY IMAGE_TAG)
else
  BFF_URL="${BFF_URL:-http://localhost:8080}"
  keys+=(BFF_URL)
fi

umask 077
: > "$out"
for key in "${keys[@]}"; do
  value="${!key:-}"
  # Comillas simples: docker compose no interpola '$' dentro de ellas (contraseñas con símbolos).
  [ -n "$value" ] && printf "%s='%s'\n" "$key" "$value" >> "$out"
done
echo "$(wc -l < "$out") variables escritas en $out (modo $mode)"
