# knowledge-repository

Repository microservice do **Knowledge MVP** — CRUD de conhecimento corporativo
sobre **PostgreSQL/pgvector** via Spring Data JPA. Fonte de verdade relacional
(projetos, produtos, microsserviços, documentos, versões, staging, auditoria) e
publicador de eventos de ciclo de vida de documentos para o `knowledge-search`.

- **Java 25** (compilado com `release=21`) · **Spring Boot 3.3.5** · **Spring Data JPA**
- **PostgreSQL** (schema gerido por **Flyway**, `ddl-auto=validate`)
- **RabbitMQ** (eventos best-effort) · **springdoc-openapi** (Swagger UI) · **Lombok**

## Pré-requisitos

- JDK 25 (ex.: `~/.jdks/jdk-25.0.4+7`)
- Maven 3.6+
- PostgreSQL em `localhost:5432` (db `knowledge_mvp`, user/pass `knowledge`)
- RabbitMQ em `localhost:5672` (guest/guest)

O schema canônico vive em `../infra/sql/V1__init.sql` e é embarcado como
migration Flyway (`src/main/resources/db/migration/V1__init.sql`). Como as 8
tabelas normalmente já existem no banco, o Flyway roda com
`baseline-on-migrate=true` / `baseline-version=1`: em banco já populado ele faz
baseline em v1 e não reaplica; em banco vazio ele executa V1 normalmente.

> A tabela `document_chunk` (embeddings) é escrita pelo `knowledge-search` e
> **não** é mapeada por JPA aqui.

## Build

```bash
export JAVA_HOME=/home/thiagom/.jdks/jdk-25.0.4+7
mvn -q -DskipTests package
```

Gera `target/knowledge-repository-0.1.0.jar`.

## Rodar

```bash
export JAVA_HOME=/home/thiagom/.jdks/jdk-25.0.4+7
$JAVA_HOME/bin/java -jar target/knowledge-repository-0.1.0.jar
```

Sobe em `http://localhost:8080`. Swagger UI em `http://localhost:8080/swagger-ui.html`.

### Configuração (env vars com defaults)

| Variável | Default |
|---|---|
| `SERVER_PORT` | `8080` |
| `DB_URL` | `jdbc:postgresql://localhost:5432/knowledge_mvp` |
| `DB_USER` / `DB_PASSWORD` | `knowledge` / `knowledge` |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `guest` / `guest` |

## API

CRUD REST em `/projects`, `/products`, `/microservices`, `/documents`.

**Documentos**
- `GET /documents` · `GET /documents/{id}` → `{ id, title, content, slug, projectId, microserviceId, domain, category, tags[], currentVersion, status, owner }` (contrato consumido pelo `knowledge-search`)
- `POST /documents` · `PUT /documents/{id}` · `DELETE /documents/{id}`
- `GET /documents/{id}/versions` → histórico append-only

**Staging** (`/staged-documents`)
- `POST` (idempotente por `source_type` + `external_id`)
- `GET` / `GET /{id}`
- `POST /{id}/approve` → promove a `Document` real (v1 + version row + auditoria + evento `DocumentCreated`), tudo numa transação
- `POST /{id}/reject`

### Versionamento

Toda criação/edição de `Document` grava uma linha imutável em `document_version`
(`version` incremental, `title`, `content`, `actor`); `document.current_version`
acompanha.

### Auditoria

Um `OncePerRequestFilter` lê o header **`X-Actor`** (default `system`) para um
`ActorContext` (ThreadLocal). Operações de create/update/delete/approve gravam em
`audit_log` com o ator corrente.

### Eventos RabbitMQ

Exchange **`knowledge.events`** (topic, durable). Ao criar/atualizar/deletar um
documento é publicado:

```json
{ "event": "DocumentCreated", "documentId": "<uuid>" }
```

com routing key `document.created` / `document.updated` / `document.deleted`.
Publicação é **best-effort**: se o broker estiver indisponível, loga e segue —
não derruba o request.

## Verificação rápida

```bash
# criar projeto
curl -s -X POST localhost:8080/projects -H 'Content-Type: application/json' \
  -H 'X-Actor: thiago' -d '{"name":"Knowledge MVP"}'

# criar documento (use o id do projeto acima)
curl -s -X POST localhost:8080/documents -H 'Content-Type: application/json' \
  -H 'X-Actor: thiago' \
  -d '{"title":"Autenticação","content":"# Login","slug":"autenticacao",
       "projectId":"<PID>","domain":"security","tags":["auth"]}'

# ler de volta
curl -s localhost:8080/documents/<DID>
```
