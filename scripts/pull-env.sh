#!/usr/bin/env bash
# Crea infra/.env y frontend-pedidos360/.env.local en un PC nuevo a partir de GitHub Secrets.
#
# Requisitos: GitHub CLI autenticado (gh auth login) y openssl (incluido en Git Bash).
# Uso (desde la raíz del repo, en Git Bash o Linux/macOS):  bash scripts/pull-env.sh
#
# Ejecuta el workflow "Env file", espera a que termine, descarga el .env cifrado y lo descifra con la
# clave que guardaste en el secret ENV_PASSPHRASE.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

command -v gh >/dev/null || { echo "Instala GitHub CLI: https://cli.github.com (winget install GitHub.cli)"; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "Inicia sesión primero: gh auth login"; exit 1; }

if [ -f "$root/infra/.env" ]; then
  read -r -p "infra/.env ya existe. ¿Reemplazarlo? [s/N] " answer
  [[ "$answer" =~ ^[sS]$ ]] || exit 0
fi

read -r -s -p "Clave ENV_PASSPHRASE: " passphrase; echo

echo "Ejecutando workflow env-file.yml..."
before="$(gh run list --workflow env-file.yml --limit 1 --json databaseId --jq '.[0].databaseId // 0')"
gh workflow run env-file.yml >/dev/null
run_id="$before"
for _ in $(seq 1 30); do
  sleep 2
  run_id="$(gh run list --workflow env-file.yml --limit 1 --json databaseId --jq '.[0].databaseId // 0')"
  [ "$run_id" != "$before" ] && break
done
[ "$run_id" != "$before" ] || { echo "No se pudo iniciar el workflow"; exit 1; }

gh run watch "$run_id" --exit-status >/dev/null
gh run download "$run_id" --name env-encrypted --dir "$tmp"

PASSPHRASE="$passphrase" openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass env:PASSPHRASE \
  -in "$tmp/env.enc" -out "$tmp/.env" || { echo "Clave incorrecta"; exit 1; }
install -m 600 "$tmp/.env" "$root/infra/.env"

# El frontend en modo desarrollo (npm start) usa los mismos identificadores públicos de Entra ID.
grep -E "^(AZURE_AD_CLIENT_ID|AZURE_AD_TENANT_ID|API_CLIENT_ID|ADMIN_EMAILS|BFF_URL)=" "$root/infra/.env" \
  > "$root/frontend-pedidos360/.env.local" || true

echo "Listo: infra/.env ($(wc -l < "$root/infra/.env") variables) y frontend-pedidos360/.env.local"
echo "Siguiente paso: cd infra && docker compose up --build -d"
