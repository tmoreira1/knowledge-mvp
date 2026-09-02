-- =====================================================================
-- Knowledge MVP — schema inicial (PostgreSQL + pgvector)
-- Um único banco: relacional + jsonb + vetores.
-- Este arquivo é a fonte canônica do schema. O knowledge-repository (Java)
-- o consome como migration Flyway (V1__init.sql); o knowledge-search (Python)
-- assume que estas tabelas já existem.
-- =====================================================================

-- Extensão de vetores (no ambiente corporativo: CREATE EXTENSION em RDS/Aurora).
CREATE EXTENSION IF NOT EXISTS vector;

-- =====================================================================
-- Hierarquia: Project → Product → Microservice
-- =====================================================================
CREATE TABLE project (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL,
    description TEXT,
    status      TEXT NOT NULL DEFAULT 'ACTIVE',
    metadata    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE product (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    description TEXT,
    status      TEXT NOT NULL DEFAULT 'ACTIVE',
    metadata    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_product_project ON product(project_id);

CREATE TABLE microservice (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    product_id  UUID REFERENCES product(id) ON DELETE SET NULL,
    name        TEXT NOT NULL,
    description TEXT,
    status      TEXT NOT NULL DEFAULT 'ACTIVE',
    metadata    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, name)
);
CREATE INDEX idx_microservice_project ON microservice(project_id);

-- =====================================================================
-- Documento + versionamento append-only
-- =====================================================================
CREATE TABLE document (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID REFERENCES project(id) ON DELETE SET NULL,
    microservice_id UUID REFERENCES microservice(id) ON DELETE SET NULL,
    title           TEXT NOT NULL,
    slug            TEXT,
    domain          TEXT,
    category        TEXT,                       -- taxonomia canônica (livre no MVP)
    tags            TEXT[] NOT NULL DEFAULT '{}',
    content         TEXT NOT NULL DEFAULT '',   -- conteúdo vivo (markdown)
    metadata        JSONB NOT NULL DEFAULT '{}'::jsonb,  -- flexível por fonte
    current_version INT  NOT NULL DEFAULT 1,
    status          TEXT NOT NULL DEFAULT 'ACTIVE',
    owner           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_document_project ON document(project_id);
CREATE INDEX idx_document_slug    ON document(slug);
CREATE INDEX idx_document_domain  ON document(domain);
CREATE INDEX idx_document_tags    ON document USING GIN (tags);

-- Histórico imutável: uma linha por versão. Nunca se atualiza; só insere.
CREATE TABLE document_version (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    version     INT  NOT NULL,
    title       TEXT NOT NULL,
    content     TEXT NOT NULL,
    actor       TEXT,                           -- de quem veio (header X-Actor)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, version)
);
CREATE INDEX idx_version_document ON document_version(document_id);

-- =====================================================================
-- Fila de ingestão (staging) — idempotente por (source_type, external_id)
-- =====================================================================
CREATE TABLE staged_document (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    raw_content          TEXT NOT NULL,
    source_type          TEXT NOT NULL,         -- confluence | github | upload | ...
    external_id          TEXT,
    external_url         TEXT,
    suggested_title      TEXT,
    suggested_slug       TEXT,
    suggested_category   TEXT,
    suggested_project_id UUID REFERENCES project(id) ON DELETE SET NULL,
    suggested_domain     TEXT,
    suggested_tags       TEXT[] NOT NULL DEFAULT '{}',
    summary              TEXT,
    status               TEXT NOT NULL DEFAULT 'PENDING',  -- PENDING|APPROVED|REJECTED
    duplicate_of         UUID REFERENCES document(id) ON DELETE SET NULL,
    promoted_document_id UUID REFERENCES document(id) ON DELETE SET NULL,
    review_note          TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source_type, external_id)
);
CREATE INDEX idx_staged_status ON staged_document(status);

-- =====================================================================
-- Auditoria formal
-- =====================================================================
CREATE TABLE audit_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type TEXT NOT NULL,                  -- Document | Project | ...
    entity_id   UUID,
    action      TEXT NOT NULL,                  -- CREATE | UPDATE | DELETE | APPROVE ...
    actor       TEXT,
    detail      JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);

-- =====================================================================
-- Chunks vetoriais (escrito pelo knowledge-search) — pgvector
-- Substitui a coleção 'documents' do Qdrant.
-- =====================================================================
CREATE TABLE document_chunk (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    chunk_index  INT  NOT NULL,
    heading      TEXT,
    chunk_text   TEXT NOT NULL,
    embedding    vector(1024) NOT NULL,         -- Qwen3-Embedding-0.6B
    -- payload denormalizado para filtrar sem join (espelha o payload do Qdrant)
    project_id   UUID,
    domain       TEXT,
    tags         TEXT[] NOT NULL DEFAULT '{}',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, chunk_index)
);

-- Índice ANN por cosseno (HNSW). vector_cosine_ops casa com embeddings normalizados.
CREATE INDEX idx_chunk_embedding_hnsw
    ON document_chunk USING hnsw (embedding vector_cosine_ops);

CREATE INDEX idx_chunk_document ON document_chunk(document_id);
CREATE INDEX idx_chunk_project  ON document_chunk(project_id);
