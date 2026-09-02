#!/usr/bin/env bash
# Sobe a infra local do Knowledge MVP via podman: PostgreSQL+pgvector e RabbitMQ.
# Idempotente: se o container já existe, apenas (re)inicia.
# Uso: ./infra/up.sh
set -euo pipefail

PG_CONTAINER="km-postgres"
PG_IMAGE="docker.io/pgvector/pgvector:pg16"   # versão fixada (reprodutível)
PG_PORT="5432"
PG_USER="knowledge"
PG_PASS="knowledge"
PG_DB="knowledge_mvp"
PG_VOLUME="km-pgdata"                          # volume persistente

RABBIT_CONTAINER="km-rabbit"
RABBIT_IMAGE="docker.io/library/rabbitmq:3-management"

echo "==> PostgreSQL + pgvector ($PG_IMAGE)"
if podman container exists "$PG_CONTAINER"; then
  podman start "$PG_CONTAINER" >/dev/null
  echo "    (reiniciado)"
else
  podman volume exists "$PG_VOLUME" || podman volume create "$PG_VOLUME" >/dev/null
  podman run -d --name "$PG_CONTAINER" \
    -e POSTGRES_USER="$PG_USER" \
    -e POSTGRES_PASSWORD="$PG_PASS" \
    -e POSTGRES_DB="$PG_DB" \
    -p "${PG_PORT}:5432" \
    -v "${PG_VOLUME}:/var/lib/postgresql/data" \
    "$PG_IMAGE" >/dev/null
  echo "    (criado)"
fi

echo "==> RabbitMQ ($RABBIT_IMAGE)"
if podman container exists "$RABBIT_CONTAINER"; then
  podman start "$RABBIT_CONTAINER" >/dev/null
  echo "    (reiniciado)"
else
  podman run -d --name "$RABBIT_CONTAINER" \
    -p 5672:5672 -p 15672:15672 \
    "$RABBIT_IMAGE" >/dev/null
  echo "    (criado)"
fi

echo "==> Aguardando o Postgres aceitar conexões..."
for i in $(seq 1 30); do
  if podman exec "$PG_CONTAINER" pg_isready -U "$PG_USER" -d "$PG_DB" >/dev/null 2>&1; then
    echo "    pronto."
    break
  fi
  sleep 1
done

echo "==> Aplicando schema (infra/sql/V1__init.sql)"
# Nota: em produção o Flyway do knowledge-repository aplica isto na subida.
# Aqui aplicamos direto para o knowledge-search poder rodar de forma independente.
podman exec -i "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" \
  < "$(dirname "$0")/sql/V1__init.sql" >/dev/null && echo "    schema aplicado." \
  || echo "    (schema já aplicado ou erro — verifique se as tabelas já existem)"

cat <<EOF

==> Infra pronta.
    PostgreSQL  →  postgresql://${PG_USER}:${PG_PASS}@localhost:${PG_PORT}/${PG_DB}
    RabbitMQ    →  amqp://guest:guest@localhost:5672  (UI: http://localhost:15672)

    Derrubar:   ./infra/down.sh
EOF
