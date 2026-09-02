-- =====================================================================
-- Knowledge MVP — V2: agrupador genérico `space`
--
-- Motivação: nem todo documento pertence a um "projeto". Conhecimento
-- corporativo inclui processos, RH, informação de times, políticas — que
-- se organizam por time/departamento, não por projeto de software.
--
-- `space` é um agrupador genérico com um `type` (vocabulário controlado,
-- extensível) e aninhamento opcional (parent_id), espelhando os "spaces"
-- do Confluence. Project/Product/Microservice continuam existindo como
-- estrutura técnica; um Project passa a ter um Space correspondente do
-- tipo PROJECT para que o Document possa referenciar tudo de forma uniforme.
-- =====================================================================

-- 1) Tabela de spaces (agrupador genérico, aninhável)
-- IF NOT EXISTS: a migration é idempotente — pode ter sido aplicada via
-- infra/up.sh (psql direto) antes do Flyway do KR rodar. Roda sem erro tanto
-- num banco novo quanto num já populado.
CREATE TABLE IF NOT EXISTS space (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL,
    slug        TEXT,
    -- vocabulário controlado; TEXT (não enum) para ser extensível sem migration.
    type        TEXT NOT NULL DEFAULT 'OTHER'
                CHECK (type IN ('TEAM','DEPARTMENT','PROJECT','PRODUCT','OTHER')),
    description TEXT,
    parent_id   UUID REFERENCES space(id) ON DELETE SET NULL,  -- ex.: DEPARTMENT > TEAM
    status      TEXT NOT NULL DEFAULT 'ACTIVE',
    metadata    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_space_parent ON space(parent_id);
CREATE INDEX IF NOT EXISTS idx_space_type   ON space(type);

-- 2) Document ganha space_id (opcional — documento pode não pertencer a nada)
ALTER TABLE document
    ADD COLUMN IF NOT EXISTS space_id UUID REFERENCES space(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_document_space ON document(space_id);

-- 3) Backfill: cada Project existente vira um Space do tipo PROJECT, e os
--    documentos daquele projeto passam a apontar para o space correspondente.
--    Preserva o vínculo histórico via metadata.source_project_id.
INSERT INTO space (id, name, slug, type, description, status, metadata, created_at, updated_at)
SELECT p.id,                       -- reusa o mesmo UUID do project (1:1)
       p.name,
       NULL,
       'PROJECT',
       p.description,
       p.status,
       jsonb_build_object('source_project_id', p.id::text),
       p.created_at,
       p.updated_at
FROM project p
ON CONFLICT (id) DO NOTHING;

-- Documentos que tinham project_id passam a ter space_id equivalente.
UPDATE document d
SET    space_id = d.project_id
WHERE  d.project_id IS NOT NULL
  AND  d.space_id IS NULL;

-- Nota: project_id permanece na tabela document por compatibilidade e para a
-- estrutura técnica (Project→Product→Microservice). space_id é a organização
-- primária e genérica; project_id vira uma faceta técnica opcional.
