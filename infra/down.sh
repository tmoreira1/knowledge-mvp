#!/usr/bin/env bash
# Para a infra local do Knowledge MVP (mantém os volumes por padrão).
# Uso: ./infra/down.sh          (para os containers)
#      ./infra/down.sh --purge  (para e REMOVE containers + volume de dados)
set -euo pipefail

PG_CONTAINER="km-postgres"
RABBIT_CONTAINER="km-rabbit"
PG_VOLUME="km-pgdata"

podman stop "$PG_CONTAINER" "$RABBIT_CONTAINER" 2>/dev/null || true
echo "==> Containers parados."

if [[ "${1:-}" == "--purge" ]]; then
  podman rm "$PG_CONTAINER" "$RABBIT_CONTAINER" 2>/dev/null || true
  podman volume rm "$PG_VOLUME" 2>/dev/null || true
  echo "==> Containers e volume de dados removidos (--purge)."
fi
