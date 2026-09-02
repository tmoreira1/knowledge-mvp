#!/usr/bin/env bash
# Smoke test E2E do MVP: cria projeto + documentos no KR, indexa no search e
# valida que a busca semântica ranqueia o documento certo no topo.
# Pré-requisito: serviços no ar (./infra/run-services.sh).
# Uso: ./infra/smoke-test.sh
set -euo pipefail

KR=http://localhost:8080
KS=http://127.0.0.1:8001

command -v jq >/dev/null 2>&1 && JQ="jq" || JQ="python3 -m json.tool"

echo "==> 0. Checando serviços"
curl -sf "$KR/documents" >/dev/null || { echo "KR (8080) fora do ar — rode ./infra/run-services.sh"; exit 1; }
curl -sf "$KS/health"    >/dev/null || { echo "search (8001) fora do ar — rode ./infra/run-services.sh"; exit 1; }
echo "    KR e search UP"

echo "==> 1. Criando projeto"
PID=$(curl -s -X POST "$KR/projects" -H 'Content-Type: application/json' -H 'X-Actor: tester' \
  -d '{"name":"Smoke Test","description":"E2E"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
echo "    project_id=$PID"

echo "==> 2. Criando 2 documentos"
curl -s -X POST "$KR/documents" -H 'Content-Type: application/json' -H 'X-Actor: tester' \
  -d "{\"title\":\"Autenticação\",\"content\":\"# Login\\nOAuth2 com tokens JWT para autenticar usuários. Refresh em 24h.\",\"slug\":\"auth-smoke\",\"projectId\":\"$PID\",\"domain\":\"security\",\"tags\":[\"auth\"],\"owner\":\"tester\"}" >/dev/null
curl -s -X POST "$KR/documents" -H 'Content-Type: application/json' -H 'X-Actor: tester' \
  -d "{\"title\":\"Backup\",\"content\":\"# Backup\\nBackup diário às 2h, retenção 30 dias.\",\"slug\":\"backup-smoke\",\"projectId\":\"$PID\",\"domain\":\"process\",\"tags\":[\"ops\"],\"owner\":\"tester\"}" >/dev/null
echo "    2 documentos criados"

echo "==> 3. Indexando no search (/reindex puxa do KR)"
curl -s -X POST "$KS/reindex" | $JQ

echo "==> 4. Busca semântica: 'como autenticar usuário com token?'"
RESULT=$(curl -s -X POST "$KS/search" -H 'Content-Type: application/json' \
  -d '{"query":"como autenticar usuário com token?","top_k":2}')
echo "$RESULT" | $JQ

TOP=$(echo "$RESULT" | python3 -c 'import sys,json;r=json.load(sys.stdin)["results"];print(r[0]["chunk_text"][:20] if r else "")')
echo
if echo "$TOP" | grep -qi "Autentica"; then
  echo "✅ PASSOU — o doc de Autenticação veio no topo."
else
  echo "❌ FALHOU — top result inesperado: $TOP"
  exit 1
fi
