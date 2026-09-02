# Teste de Acertividade — Busca Semântica

Mede se a busca retorna o **documento certo** para queries que usam vocabulário de usuário (sinônimos, linguagem coloquial) diferente do texto do documento — o "vocabulary gap" que a busca semântica deve resolver.

## Como funciona

- `fixtures/docs.json` — 10 documentos com vocabulário técnico controlado
- `golden_set.json` — 29 queries em linguagem de usuário → documento esperado
- Exemplo: doc fala "autenticação/JWT/credenciais", query pergunta "como faço login" → deve achar o doc de autenticação

## Métricas (padrão IR)

| Métrica | O que mede | Referência |
|---|---|---|
| **Precision@1** | % de queries com o doc certo em 1º lugar | — |
| **Recall@1/3/5** | % com o doc certo no top-k | — |
| **MRR@10** | Posição média inversa do acerto (1/rank) | MS MARCO |
| **nDCG@10** | Ganho descontado normalizado (premia acerto no topo) | BEIR, padrão-ouro |
| **Score médio** | Confiança do modelo nos acertos | Calibração de threshold |

Referência de qualidade: MRR/nDCG **> 0.8** é bom para um domínio bem definido; **> 0.9** é excelente.

## Executar

```bash
# 1. Instalar deps (usa httpx)
python3 -m venv .venv && source .venv/bin/activate
pip install httpx

# 2. Popular o Test Bench (cria projeto isolado + 10 docs no KR)
python seed.py
#    → aguarda indexação automática via evento RabbitMQ

# 3. Rodar a avaliação
python run_accuracy.py

# Opções:
python run_accuracy.py --top-k 10          # profundidade da busca
python run_accuracy.py --no-filter         # busca em TODOS os projetos (não só Test Bench)

# 4. Limpar os docs de teste (opcional)
python seed.py --cleanup
```

## Saída

- Terminal: métricas + falhas + matriz de confusão
- `report.md` — relatório Markdown
- `report.json` — dados para automação/CI

## Variáveis de ambiente

| Var | Default |
|---|---|
| `KR_BASE_URL` | http://localhost:8080 |
| `SEARCH_URL` | http://localhost:8001 |

## Interpretando falhas

- **rank > 1:** o doc certo apareceu, mas não em 1º → busca funciona, ranking imperfeito
- **AUSENTE no top-k:** o doc não veio no top-k → gap semântico grande ou chunk ruim
- **Matriz de confusão:** com qual doc o sistema confundiu → revela sobreposição semântica (ex: "cache" vs "performance")
