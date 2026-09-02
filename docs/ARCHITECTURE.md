# Arquitetura — Knowledge MVP

MVP de uma plataforma de conhecimento corporativa: **centralize documentos e
encontre qualquer coisa por busca semântica.** Dois microservices sobre um
único PostgreSQL com `pgvector`.

## 1. Visão geral

```
   cliente / curl / Swagger / test bench
              │  REST
              ▼
┌───────────────────────────────────────────────┐
│  knowledge-repository  :8080                     │   Java 25 · Spring Boot 3.3.5
│  Spring Data JPA · Flyway · Lombok · Swagger     │   (compilado release 21)
│                                                  │
│  controllers: Project·Product·Microservice·      │        escreve (JPA)
│               Document·StagedDocument            │─────────────────┐
│  versionamento append-only · auditoria X-Actor   │                 │
└──────────────┬───────────────────────────────────┘                 │
               │ publica evento                                       ▼
               │ {"event":"DocumentCreated","documentId":..}  ┌──────────────────────┐
               ▼                                              │  PostgreSQL 16         │
        ┌──────────────────┐                                 │  + pgvector 0.8.6      │
        │   RabbitMQ :5672  │                                 │  :5432  (ÚNICO store)  │
        │  knowledge.events │                                 │                        │
        └────────┬──────────┘                                 │  project·product·      │
                 │ consome (fila indexer)                      │  microservice·         │
                 ▼                          escreve/consulta   │  document·             │
┌───────────────────────────────────────────┐   (SQL pgvector)│  document_version·     │
│  knowledge-search  :8001                    │────────────────►│  staged_document·      │
│  Python · FastAPI                           │                 │  audit_log·            │
│  embeddings Qwen3-Embedding-0.6B (local)    │◄────────────────│  document_chunk        │
│  chunker · consumer · /search /reindex      │   busca <=>      │   embedding vector(1024)│
│  vector_store → pgvector (CTE, cosine)      │                 │   índice HNSW cosine   │
└───────────────────────────────────────────┘                 └──────────────────────┘

 Infra local (podman): km-postgres + km-rabbit.   SEM Mongo · SEM Qdrant · SEM LLM.
```

## 2. Decisão de fundação: um único PostgreSQL
A plataforma original usava **MongoDB** (documentos) + **Qdrant** (vetores) +
**Neo4j** (grafo). O MVP consolida o núcleo num **único PostgreSQL com
`pgvector`**, que guarda no mesmo banco:

- **Relacional** — FKs reais, hierarquia `Project → Product → Microservice →
  Document`, transações ACID (ex.: aprovar um staged document cria documento +
  versão + audit log atomicamente).
- **`jsonb`** — metadata flexível por fonte, preservando a flexibilidade de
  schema que atraía no Mongo.
- **Vetores** — `document_chunk.embedding vector(1024)` com índice **HNSW**
  (cosine), aposentando o Qdrant.

**Por que importa para o cenário corporativo:** `pgvector` é uma *extensão*
(`CREATE EXTENSION vector`), não um serviço novo. Uma empresa que não permite
subir containers arbitrários pode usar um PostgreSQL gerenciado (RDS/Aurora) sem
introduzir componente de infraestrutura adicional. Dois stores a menos para
operar, backupear e para a TI aprovar.

## 2.1 Organização por `Space` (não só por projeto)

Nem todo documento pertence a um "projeto". Conhecimento corporativo inclui
processos, políticas, informação de RH e de times — que se organizam por
**time/departamento**, não por projeto de software. Por isso o modelo tem um
agrupador genérico, `space`:

- `space.type` ∈ `{TEAM, DEPARTMENT, PROJECT, PRODUCT, OTHER}` (vocabulário
  controlado por CHECK, mas em TEXT para ser extensível sem enum). Valor inválido
  é coagido para `OTHER`.
- `space.parent_id` permite aninhamento (ex.: `DEPARTMENT > TEAM`), espelhando os
  *spaces* do Confluence (fonte de ingestão corporativa).
- `document.space_id` é **opcional** — um documento pode não pertencer a nada, ou
  pertencer a um space de qualquer tipo. O `project_id` técnico permanece como
  faceta opcional (a estrutura `Project → Product → Microservice` continua).
- Migração V2 faz *backfill*: cada `Project` existente vira um `Space` do tipo
  `PROJECT` (mesmo UUID), e os documentos daquele projeto passam a apontar para o
  space correspondente. Nada órfão.

Endpoints REST em `/spaces` (CRUD). A busca (`knowledge-search`) aceita um filtro
opcional `space_id` (ex.: "buscar só no conhecimento de RH"), resolvido via join
com `document`.


## 3. Microservices

### 3.1 knowledge-repository (`:8080`) — fonte de verdade

| Item | Detalhe |
|------|---------|
| Stack | Java 25, Spring Boot 3.3.5, Spring Data JPA, Flyway, Lombok, springdoc-openapi |
| Persistência | PostgreSQL via JPA; schema versionado por Flyway (`V1__init.sql`) |
| Responsabilidade | CRUD de Project/Product/Microservice/Document; versionamento append-only; auditoria; staging; publicação de eventos |

Pacotes (`com.knowledge.repository`):

- `domain/` — 7 entidades `@Entity` (Project, Product, Microservice, Document,
  DocumentVersion, StagedDocument, AuditLog). `document_chunk` **não** é mapeada
  aqui (é escrita pelo search).
- `repo/` — `JpaRepository` por agregado.
- `service/` — regras de negócio; versionamento append-only; aprovação de staged
  numa transação.
- `web/` — controllers REST + `GlobalExceptionHandler` (404/400).
- `audit/` — `ActorFilter` lê o header `X-Actor` → `ActorContext`; `AuditService`
  grava `audit_log`.
- `event/` — `DocumentEventPublisher` publica no RabbitMQ (best-effort).
- `config/` — `RabbitConfig` (exchange topic durável + conversor JSON).

Flyway usa `baseline-on-migrate=true` (as tabelas podem já existir) e o Hibernate
roda em `ddl-auto=validate` — o Flyway é a fonte do schema, não o Hibernate.

### 3.2 knowledge-search (`:8001`) — busca semântica

| Item | Detalhe |
|------|---------|
| Stack | Python, FastAPI, sentence-transformers, `psycopg` + `pgvector`, `pika` |
| Modelo | `Qwen/Qwen3-Embedding-0.6B` (local, ~610MB, 1024-dim, normalizado) |
| Responsabilidade | Indexa documentos por evento; responde busca por similaridade |

Módulos (`app/`):

- `embeddings.py` — wrapper do modelo (inalterado em relação ao projeto original).
- `chunker.py` — split por heading markdown; sub-split por parágrafo acima de
  ~2000 chars; prefixa cada chunk com o título.
- `vector_store.py` — **camada pgvector** (substitui o cliente Qdrant), mesma
  interface `upsert_chunks` / `search` / `delete_document`.
- `consumer.py` — consumer RabbitMQ; indexa em `DocumentCreated/Updated`, remove
  em `DocumentDeleted`.
- `main.py` — FastAPI: `POST /search`, `POST /index/{id}`, `POST /reindex`,
  `GET /health`.

A busca usa um **CTE** que calcula a distância de cosseno (`<=>`) uma única vez e
reaproveita no `SELECT` (score) e no `ORDER BY`:

```sql
WITH scored AS (
  SELECT document_id, heading, chunk_text, domain, project_id,
         (embedding <=> %s) AS distance
  FROM document_chunk
  [WHERE filtros opcionais]
)
SELECT ..., 1 - distance AS score FROM scored ORDER BY distance LIMIT %s;
```

O vetor de query é passado como `pgvector.psycopg.Vector` (necessário para o
operador `<=>` — uma lista Python crua causa erro de tipo).

## 4. Contrato entre os serviços

Acoplamento **assíncrono na escrita, síncrono na leitura de conteúdo**:

1. **Evento (assíncrono):** o KR escreve → publica no RabbitMQ
   (`exchange knowledge.events`, corpo `{"event":"DocumentCreated","documentId":...}`,
   routing key `document.created`) → o search consome (fila
   `knowledge-search.indexer`).
2. **Fetch (síncrono):** ao processar o evento, o search chama
   `GET /documents/{id}` no KR e recebe `id, title, content, slug, projectId,
   domain, tags`.
3. **Backfill (síncrono):** `POST /reindex` no search puxa `GET /documents` do KR
   e reindexa tudo (indexação determinística, usada nos testes).

O KR **não conhece** o search (só publica eventos); o search **conhece** o KR.
O núcleo não depende do satélite.

## 5. Modelo de dados

`infra/sql/V1__init.sql` (canônico; copiado para
`knowledge-repository/src/main/resources/db/migration/V1__init.sql`):

```
project (id, name, description, status, metadata jsonb, timestamps)
product (id, project_id FK, ...)
microservice (id, project_id FK, product_id FK, name, UNIQUE(project_id,name))
document (id, project_id FK, microservice_id FK, title, slug, domain,
          category, tags text[], content, metadata jsonb, current_version, ...)
document_version (id, document_id FK, version, title, content, actor,
                  UNIQUE(document_id, version))          -- append-only
staged_document (id, raw_content, source_type, external_id, suggested_*,
                 status, duplicate_of, UNIQUE(source_type, external_id))
audit_log (id, entity_type, entity_id, action, actor, detail jsonb, ts)
document_chunk (id, document_id FK ON DELETE CASCADE, chunk_index, heading,
                chunk_text, embedding vector(1024),
                project_id, domain, tags text[])
                -- índice HNSW (embedding vector_cosine_ops)
```

`document_chunk` tem FK `ON DELETE CASCADE` → apagar um documento remove seus
chunks automaticamente (no Qdrant isso era feito manualmente).

## 6. Infraestrutura local

Dois containers via podman, gerenciados por scripts em `infra/`:

| Componente | Container | Porta |
|-----------|-----------|-------|
| PostgreSQL 16 + pgvector | `km-postgres` (volume `km-pgdata`) | 5432 |
| RabbitMQ | `km-rabbit` | 5672 / 15672 |

Scripts:

- `infra/up.sh` — sobe Postgres+RabbitMQ e aplica o schema (idempotente).
- `infra/down.sh` — para os containers (`--purge` remove o volume de dados).
- `infra/run-services.sh` — sobe os dois microservices.
- `infra/smoke-test.sh` — E2E: cria projeto+docs, indexa, valida o ranking.

No ambiente corporativo o Postgres vira RDS/Aurora (`CREATE EXTENSION vector`) —
sem container próprio.

## 7. Qualidade de busca (baseline validado)

O test bench de acurácia do projeto original foi portado
(`tests/accuracy/`, golden set com 29 queries em linguagem de usuário sobre 10
documentos). Resultado do MVP em `pgvector` (baseline, sem reranker):

| Métrica | Valor |
|---------|-------|
| Precision@1 | 65,5% (19/29) |
| Recall@1 | 65,5% |
| Recall@3 | 82,8% |
| Recall@5 | **100%** |
| MRR@10 | 0,761 |
| nDCG@10 | 0,820 |

Reproduz o baseline do sistema original sem reranker — confirma que a troca
Qdrant → pgvector **não degradou** a busca. As 10 falhas são todas rank 2–4
(o doc certo apareceu, só não em 1º), em temas vizinhos (cache↔busca,
observabilidade↔rate_limit, deploy↔notificações) — padrão típico de
desempate fino, endereçável por reranker ou busca híbrida.

## 8. Melhorias mapeadas (não implementadas)

Em ordem de custo/benefício para este corpus:

1. **Busca híbrida** (vetor + full-text `tsvector` no mesmo Postgres) — melhor
   custo/benefício para docs curtos com termos exatos; sem custo de RAM.
2. **Reranker cross-encoder** (BGE-reranker-v2-m3, ~560MB) — maior ganho de
   precisão@1, mas pesa na memória.
3. **Chunking mais fino** — só relevante para documentos longos e multi-seção
   (o corpus do test bench é curto demais para se beneficiar).

## 9. Gaps conhecidos para deploy corporativo

- **Sem autenticação/autorização** — o header `X-Actor` é um stand-in de
  auditoria, não segurança. Requer OIDC/SAML + RBAC via IdP corporativo.
- **Sem CORS** configurado.
- **Eventos best-effort** — avaliar Transactional Outbox para garantir entrega.
- **Sem observabilidade** (actuator/métricas/tracing) e sem Dockerfile/manifests.

## 10. Divergências conhecidas do projeto original

- O `SearchResponse` do MVP **não retorna `slug`** (o original retornava). O test
  bench foi ajustado para casar por `doc_id`, que o MVP retorna.
- A indexação automática por evento (consumer RabbitMQ) existe mas os testes usam
  `POST /reindex` para indexação determinística.
