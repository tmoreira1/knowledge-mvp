"""Modelos de request/response da API de busca."""

from pydantic import BaseModel


class SearchRequest(BaseModel):
    query: str
    top_k: int = 5
    project_id: str | None = None
    domain: str | None = None
    tags: list[str] | None = None


class SearchResult(BaseModel):
    doc_id: str
    heading: str | None = None
    chunk_text: str | None = None
    domain: str | None = None
    project_id: str | None = None
    score: float


class SearchResponse(BaseModel):
    query: str
    results: list[SearchResult]
    total: int
