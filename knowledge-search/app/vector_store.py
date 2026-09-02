"""Vector store sobre PostgreSQL + pgvector (substitui o cliente Qdrant).

Mantém a MESMA interface do serviço original — upsert_chunks / search /
delete_document — para que consumer.py e main.py não precisem saber que o
backend mudou de Qdrant para pgvector.

Tabela: document_chunk (ver infra/sql/V1__init.sql)
  embedding vector(1024) · índice HNSW (vector_cosine_ops)
"""

import logging

import psycopg
from pgvector.psycopg import register_vector, Vector

from .config import settings

logger = logging.getLogger(__name__)

_conn: psycopg.Connection | None = None


def get_conn() -> psycopg.Connection:
    """Conexão única, com autocommit e o tipo vector registrado."""
    global _conn
    if _conn is None or _conn.closed:
        _conn = psycopg.connect(settings.pg_dsn, autocommit=True)
        register_vector(_conn)
        logger.info("Conectado ao PostgreSQL (pgvector)")
    return _conn


def upsert_chunks(
    doc_id: str,
    chunks: list[dict],
    vectors: list[list[float]],
    title: str,
    slug: str | None = None,
    project_id: str | None = None,
    domain: str | None = None,
    tags: list[str] | None = None,
) -> None:
    """Indexa todos os chunks de um documento. Apaga os antigos primeiro.

    (title/slug ficam na assinatura por compatibilidade com o chamador; o
    título canônico vive na tabela `document` do KR, não no chunk.)
    """
    conn = get_conn()
    with conn.transaction():
        # Substitui os chunks anteriores deste documento (reindexação limpa).
        conn.execute("DELETE FROM document_chunk WHERE document_id = %s", (doc_id,))
        rows = []
        for i, (chunk, vector) in enumerate(zip(chunks, vectors)):
            rows.append((
                doc_id,
                chunk.get("chunk_index", i),
                chunk.get("heading", ""),
                chunk.get("text", ""),
                Vector(vector),
                project_id,
                domain,
                tags or [],
            ))
        if rows:
            conn.cursor().executemany(
                """
                INSERT INTO document_chunk
                    (document_id, chunk_index, heading, chunk_text,
                     embedding, project_id, domain, tags)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                """,
                rows,
            )
    logger.info(f"Indexados {len(chunks)} chunks para o doc {doc_id} ({title})")


def delete_document(doc_id: str) -> None:
    """Remove todos os chunks de um documento."""
    get_conn().execute("DELETE FROM document_chunk WHERE document_id = %s", (doc_id,))
    logger.debug(f"Chunks removidos do documento {doc_id}")


def search(
    query_vector: list[float],
    top_k: int = 5,
    project_id: str | None = None,
    domain: str | None = None,
    tags: list[str] | None = None,
) -> list[dict]:
    """Busca os chunks mais similares por distância de cosseno (operador <=>).

    score = 1 - distância_cosseno  (maior = mais similar), espelhando o Qdrant.
    Filtros opcionais por project_id / domain / tags viram cláusulas WHERE.
    """
    # Filtros opcionais → cláusulas WHERE, na ordem em que aparecem no SQL.
    conditions: list[str] = []
    filter_params: list = []
    if project_id:
        conditions.append("project_id = %s")
        filter_params.append(project_id)
    if domain:
        conditions.append("domain = %s")
        filter_params.append(domain)
    if tags:
        # o chunk deve conter TODAS as tags pedidas (operador @>).
        conditions.append("tags @> %s")
        filter_params.append(tags)

    where = ("WHERE " + " AND ".join(conditions)) if conditions else ""

    # O vetor de consulta aparece uma única vez: a distância é calculada num CTE
    # e reaproveitada no SELECT (score) e no ORDER BY. Passar o mesmo vetor em
    # dois placeholders separados leva o psycopg a resultados degenerados.
    sql = f"""
        WITH scored AS (
            SELECT document_id, heading, chunk_text, domain, project_id,
                   (embedding <=> %s) AS distance
            FROM document_chunk
            {where}
        )
        SELECT document_id, heading, chunk_text, domain, project_id,
               1 - distance AS score
        FROM scored
        ORDER BY distance
        LIMIT %s
    """
    # Ordem dos %s: vetor (no CTE), filtros (no CTE), limite.
    params = [Vector(query_vector), *filter_params, top_k]

    rows = get_conn().execute(sql, params).fetchall()
    return [
        {
            "doc_id": str(r[0]),
            "heading": r[1],
            "chunk_text": r[2],
            "domain": r[3],
            "project_id": str(r[4]) if r[4] else None,
            "score": float(r[5]),
        }
        for r in rows
    ]
