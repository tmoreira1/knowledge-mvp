"""Configuração via variáveis de ambiente (prefixo KS_)."""

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # Knowledge Repository API
    kr_base_url: str = "http://localhost:8080"

    # RabbitMQ
    rabbitmq_host: str = "localhost"
    rabbitmq_port: int = 5672
    rabbitmq_user: str = "guest"
    rabbitmq_pass: str = "guest"
    rabbitmq_exchange: str = "knowledge.events"
    rabbitmq_queue: str = "knowledge-search.indexer"

    # PostgreSQL + pgvector (substitui o Qdrant)
    pg_dsn: str = "postgresql://knowledge:knowledge@localhost:5432/knowledge_mvp"

    # Modelo de embedding
    embedding_model: str = "Qwen/Qwen3-Embedding-0.6B"
    embedding_dimensions: int = 1024

    # Servidor
    host: str = "0.0.0.0"
    port: int = 8001

    class Config:
        env_prefix = "KS_"


settings = Settings()
