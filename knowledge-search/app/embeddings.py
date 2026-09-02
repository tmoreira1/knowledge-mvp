"""Wrapper do modelo de embeddings (Qwen3-Embedding-0.6B) via sentence-transformers.

Inalterado em relação ao serviço original: o embedding continua sendo gerado
localmente, normalizado, 1024 dimensões. Só o destino do vetor mudou (pgvector).
"""

import logging
from sentence_transformers import SentenceTransformer

from .config import settings

logger = logging.getLogger(__name__)

_model: SentenceTransformer | None = None


def get_model() -> SentenceTransformer:
    """Carrega o modelo sob demanda (primeira chamada baixa ~610MB)."""
    global _model
    if _model is None:
        logger.info(f"Carregando modelo de embedding: {settings.embedding_model}")
        _model = SentenceTransformer(settings.embedding_model)
        logger.info(f"Modelo carregado. Dimensões: {settings.embedding_dimensions}")
    return _model


def generate_embedding(text: str) -> list[float]:
    """Gera o vetor de embedding de um texto."""
    model = get_model()
    embedding = model.encode(text, normalize_embeddings=True)
    return embedding.tolist()


def generate_embeddings_batch(texts: list[str]) -> list[list[float]]:
    """Gera embeddings para vários textos (batch, mais eficiente)."""
    model = get_model()
    embeddings = model.encode(texts, normalize_embeddings=True, batch_size=32)
    return [e.tolist() for e in embeddings]
