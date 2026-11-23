#!/bin/bash
# Crea los tópicos de Pedidos360 con su configuración y la vuelve a aplicar si ya existen
# (idempotente: se puede ejecutar cuantas veces se quiera). Lo usa el servicio kafka-init de docker compose.
#
#   pedidos360.orders.events      3 particiones, RF 2, delete, retención 7 días   (orders -> catalog, notifications)
#   pedidos360.billing.receipts   3 particiones, RF 2, compact (última boleta por pedido) (notifications -> orders)
#   *.DLT                         1 partición,   RF 2, delete, retención 30 días  (mensajes que fallaron)
set -euo pipefail

BOOTSTRAP="${BOOTSTRAP:-kafka:29092,kafka2:29092}"

ensure_topic() {
  local topic="$1" partitions="$2" replicas="$3" configs="$4"
  local args=()
  IFS=',' read -ra pairs <<< "$configs"
  for pair in "${pairs[@]}"; do args+=(--config "$pair"); done

  kafka-topics --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
    --topic "$topic" --partitions "$partitions" --replication-factor "$replicas" "${args[@]}"
  # Si el tópico ya existía, alinea su configuración con la declarada aquí.
  kafka-configs --bootstrap-server "$BOOTSTRAP" --alter --entity-type topics --entity-name "$topic" \
    --add-config "$configs"
}

DAY_MS=86400000

ensure_topic pedidos360.orders.events 3 2 \
  "cleanup.policy=delete,retention.ms=$((7 * DAY_MS)),min.insync.replicas=1,max.message.bytes=1048576"

ensure_topic pedidos360.billing.receipts 3 2 \
  "cleanup.policy=compact,min.compaction.lag.ms=60000,delete.retention.ms=${DAY_MS},segment.ms=3600000,min.insync.replicas=1"

ensure_topic pedidos360.orders.events.DLT 1 2 \
  "cleanup.policy=delete,retention.ms=$((30 * DAY_MS))"

ensure_topic pedidos360.billing.receipts.DLT 1 2 \
  "cleanup.policy=delete,retention.ms=$((30 * DAY_MS))"

echo "Tópicos de Pedidos360:"
kafka-topics --bootstrap-server "$BOOTSTRAP" --describe --topic 'pedidos360\..*' | grep -v "^\s*Topic: .*Partition:" || true
