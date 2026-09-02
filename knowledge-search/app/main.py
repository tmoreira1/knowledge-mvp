"""Knowledge Search Service — busca semântica sobre pgvector.

Consome eventos do KR (RabbitMQ) e indexa; responde busca por similaridade.
Embeddings locais (Qwen3-Embedding-0.6B), vetores no PostgreSQL/pgvector.
"""

import logging
import os
from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI

from .config import settings
from .embeddings import generate_embedding, generate_embeddings_batch, get_model
from .models import SearchRequest, SearchResponse, SearchResult
from .vector_store import search, get_conn, upsert_chunks
from .chunker import chunk_document

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Iniciando knowledge-search...")
    logger.info("KR: %s | Postgres: pgvector | RabbitMQ: %s:%s",
                settings.kr_base_url, settings.rabbitmq_host, settings.rabbitmq_port)
    get_model()   # pré-carrega o modelo de embedding (~610MB na 1ª vez)
    get_conn()    # abre conexão com o Postgres

    # Consumer é opcional para testes isolados (KS_DISABLE_CONSUMER=1).
    if os.getenv("KS_DISABLE_CONSUMER") != "1":
        try:
            from .consumer import start_consumer
            start_consumer()
        except Exception as e:  # noqa: BLE001
            logger.warning("Consumer não iniciado (%s). Use /index e /reindex manualmente.", e)

    logger.info("✓ knowledge-search pronto")
    yield
    logger.info("Encerrando knowledge-search...")


app = FastAPI(
    title="Knowledge Search Service (MVP)",
    description="Busca semântica com Qwen3 embeddings + PostgreSQL/pgvector",
    version="0.1.0",
    lifespan=lifespan,
)


@app.post("/search", response_model=SearchResponse)
async def search_documents(request: SearchRequest) -> SearchResponse:
    query_vector = generate_embedding(request.query)
    results = search(
        query_vector=query_vector,
        top_k=request.top_k,
        project_id=request.project_id,
        domain=request.domain,
        tags=request.tags,
        space_id=request.space_id,
    )
    return SearchResponse(
        query=request.query,
        results=[SearchResult(**r) for r in results],
        total=len(results),
    )


@app.get("/health")
async def health() -> dict:
    return {"status": "healthy", "model": settings.embedding_model, "store": "pgvector"}


@app.post("/index/{doc_id}")
async def index_document(doc_id: str) -> dict:
    """Indexa manualmente um documento (útil para backfill/teste)."""
    resp = httpx.get(f"{settings.kr_base_url}/documents/{doc_id}", timeout=15)
    if resp.status_code != 200:
        return {"error": f"Documento não encontrado: HTTP {resp.status_code}"}
    doc = resp.json()
    chunks = chunk_document(doc.get("title", ""), doc.get("content", ""))
    vectors = generate_embeddings_batch([c["text"] for c in chunks])
    upsert_chunks(
        doc_id=doc_id, chunks=chunks, vectors=vectors,
        title=doc.get("title", ""), slug=doc.get("slug"),
        project_id=doc.get("projectId"), domain=doc.get("domain"), tags=doc.get("tags", []),
    )
    return {"status": "indexed", "doc_id": doc_id, "chunks": len(chunks)}


@app.post("/reindex")
async def reindex_all() -> dict:
    """Reindexa todos os documentos do KR (backfill)."""
    resp = httpx.get(f"{settings.kr_base_url}/documents", timeout=30)
    if resp.status_code != 200:
        return {"error": f"Falha ao listar documentos: HTTP {resp.status_code}"}
    docs = resp.json()
    if not docs:
        return {"status": "nenhum documento para indexar"}
    indexed = total_chunks = 0
    for doc in docs:
        chunks = chunk_document(doc.get("title", ""), doc.get("content", ""))
        vectors = generate_embeddings_batch([c["text"] for c in chunks])
        upsert_chunks(
            doc_id=doc["id"], chunks=chunks, vectors=vectors,
            title=doc.get("title", ""), slug=doc.get("slug"),
            project_id=doc.get("projectId"), domain=doc.get("domain"), tags=doc.get("tags", []),
        )
        indexed += 1
        total_chunks += len(chunks)
    return {"status": "reindex completo", "indexed": indexed, "total_chunks": total_chunks}
