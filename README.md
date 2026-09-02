# Knowledge MVP

MVP enxuto de uma plataforma de conhecimento corporativa: **centralize documentos e encontre qualquer coisa por busca semântica**.

Recorte mínimo de valor: dois microservices — `knowledge-repository` (núcleo canônico) e
`knowledge-search` (busca semântica) — sobre um **único PostgreSQL com pgvector**
(relacional + `jsonb` + vetores no mesmo banco). Sem MongoDB, sem Qdrant, sem LLM.

## Monorepo (cada MS isolado, igual ao projeto real)

```
knowledge-mvp/
├── knowledge-repository/   # MS Java · Spring Boot · JPA  (:8080)  — fonte de verdade
├── knowledge-search/       # MS Python · FastAPI · pgvector (:8001) — busca semântica
├── infra/                  # scripts podman: Postgres+pgvector, RabbitMQ + schema SQL
├── docs/                   # modelo de dados, plano de implementação
└── README.md
```

Cada serviço tem seu próprio build/deps/runtime — nada compartilhado no código.

## Arquitetura

```
cliente ─REST─► knowledge-repository (:8080, Java/Spring, JPA)
                     │  escreve (JPA)
                     ├──────────────► PostgreSQL + pgvector (:5432)  ── ÚNICO store
                     │  publica evento                (relacional · jsonb · vector(1024))
                     ▼
                RabbitMQ (:5672)  knowledge.events
                     │  consome
                     ▼
                knowledge-search (:8001, Python/FastAPI)
                  embeddings Qwen3-Embedding-0.6B (local) → grava/consulta pgvector
```

- **knowledge-repository** — fonte de verdade. CRUD de Project/Product/Microservice/Document,
  versionamento append-only, auditoria (header `X-Actor`), eventos de domínio no RabbitMQ.
- **knowledge-search** — indexa por evento, gera embeddings locais (sentence-transformers,
  Qwen3-Embedding-0.6B, 1024-dim) e responde busca semântica via pgvector.

## Infra local

- **PostgreSQL 16 + pgvector** (`:5432`) — único store de dados.
- **RabbitMQ** (`:5672`) — barramento de eventos.

Teste local: `infra/up.sh` sobe os componentes via podman. No ambiente corporativo
o Postgres vira RDS/Aurora (`CREATE EXTENSION vector`) — sem container próprio.

## Status

Em construção (fundação: infra + schema). Ver `docs/` para o modelo de dados e o plano.
