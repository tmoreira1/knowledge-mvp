#!/usr/bin/env bash
# Sobe os dois microservices do MVP (knowledge-repository e knowledge-search).
# Pré-requisito: infra de pé (./infra/up.sh) e o KR já buildado (mvn package).
# Uso: ./infra/run-services.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-/home/thiagom/.jdks/jdk-25.0.4+7}"
KR_JAR="$ROOT/knowledge-repository/target/knowledge-repository-0.1.0.jar"
KS_DIR="$ROOT/knowledge-search"

# --- knowledge-repository (:8080) ---
if [[ ! -f "$KR_JAR" ]]; then
  echo "ERRO: jar do KR não encontrado. Rode primeiro:"
  echo "  (cd knowledge-repository && JAVA_HOME=$JAVA_HOME mvn -q -DskipTests package)"
  exit 1
fi
echo "==> Subindo knowledge-repository (:8080)"
setsid nohup "$JAVA_HOME/bin/java" -jar "$KR_JAR" > /tmp/km-kr.log 2>&1 &
for i in $(seq 1 40); do
  curl -sf http://localhost:8080/documents >/dev/null 2>&1 && { echo "    UP"; break; }
  sleep 2
done

# --- knowledge-search (:8001) ---
echo "==> Subindo knowledge-search (:8001) — carrega o modelo (~610MB) na 1ª vez"
if [[ ! -d "$KS_DIR/.venv" ]]; then
  echo "    criando venv e instalando deps..."
  python3 -m venv "$KS_DIR/.venv"
  "$KS_DIR/.venv/bin/pip" install -q -r "$KS_DIR/requirements.txt"
fi
( cd "$KS_DIR" && setsid nohup ./.venv/bin/python -m uvicorn app.main:app \
    --host 127.0.0.1 --port 8001 > /tmp/km-ks.log 2>&1 & )
for i in $(seq 1 60); do
  curl -sf http://127.0.0.1:8001/health >/dev/null 2>&1 && { echo "    UP"; break; }
  sleep 2
done

cat <<EOF

==> Serviços no ar.
    Repository  →  http://localhost:8080        (Swagger: http://localhost:8080/swagger-ui.html)
    Search      →  http://127.0.0.1:8001         (health: /health)
    Logs        →  /tmp/km-kr.log  ·  /tmp/km-ks.log

    Teste rápido:  ./infra/smoke-test.sh
    Parar:         pkill -f knowledge-repository-0.1.0.jar ; pkill -f 'uvicorn app.main:app'
EOF
