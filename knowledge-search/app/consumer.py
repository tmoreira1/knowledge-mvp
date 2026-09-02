"""Consumer RabbitMQ: indexa documentos quando o KR publica eventos.

Eventos: DocumentCreated / DocumentUpdated → (re)indexa; DocumentDeleted → remove.
Ao receber um evento de criação/atualização, busca o conteúdo do documento no
KR (GET /documents/{id}), faz chunk + embedding e grava no pgvector.
"""

import json
import logging
import threading

import httpx
import pika

from .config import settings

logger = logging.getLogger(__name__)


def _index_document(doc_id: str) -> None:
    from .chunker import chunk_document
    from .embeddings import generate_embeddings_batch
    from .vector_store import upsert_chunks

    resp = httpx.get(f"{settings.kr_base_url}/documents/{doc_id}", timeout=15)
    if resp.status_code != 200:
        logger.warning("Não consegui buscar o doc %s (HTTP %s)", doc_id, resp.status_code)
        return

    doc = resp.json()
    chunks = chunk_document(doc.get("title", ""), doc.get("content", ""))
    vectors = generate_embeddings_batch([c["text"] for c in chunks])
    upsert_chunks(
        doc_id=doc_id,
        chunks=chunks,
        vectors=vectors,
        title=doc.get("title", ""),
        slug=doc.get("slug"),
        project_id=doc.get("projectId"),
        domain=doc.get("domain"),
        tags=doc.get("tags", []),
    )


def _normalize_event(payload: dict) -> tuple[str | None, str | None]:
    """Extrai (evento, doc_id). O KR publica o campo 'event' (ex: DocumentCreated)."""
    event = payload.get("event") or payload.get("eventType")
    doc_id = payload.get("documentId") or payload.get("id") or payload.get("docId")
    return event, doc_id


def _on_message(ch, method, properties, body) -> None:
    from .vector_store import delete_document
    try:
        payload = json.loads(body)
        event, doc_id = _normalize_event(payload)
        if not doc_id:
            logger.debug("Evento sem doc_id, ignorado: %s", payload)
        elif event and "Deleted" in event:
            delete_document(doc_id)
            logger.info("Doc %s removido do índice", doc_id)
        else:
            _index_document(doc_id)
    except Exception as e:  # noqa: BLE001 — não derruba o consumer por um evento ruim
        logger.exception("Falha ao processar evento: %s", e)
    finally:
        ch.basic_ack(delivery_tag=method.delivery_tag)


def _run() -> None:
    params = pika.ConnectionParameters(
        host=settings.rabbitmq_host,
        port=settings.rabbitmq_port,
        credentials=pika.PlainCredentials(settings.rabbitmq_user, settings.rabbitmq_pass),
        heartbeat=60,
    )
    conn = pika.BlockingConnection(params)
    channel = conn.channel()
    channel.exchange_declare(exchange=settings.rabbitmq_exchange, exchange_type="topic", durable=True)
    channel.queue_declare(queue=settings.rabbitmq_queue, durable=True)
    channel.queue_bind(
        exchange=settings.rabbitmq_exchange, queue=settings.rabbitmq_queue, routing_key="#"
    )
    channel.basic_consume(queue=settings.rabbitmq_queue, on_message_callback=_on_message)
    logger.info("Consumindo eventos de %s → fila %s", settings.rabbitmq_exchange, settings.rabbitmq_queue)
    channel.start_consuming()


def start_consumer() -> None:
    """Inicia o consumer numa thread daemon (não bloqueia o FastAPI)."""
    t = threading.Thread(target=_run, name="rabbit-consumer", daemon=True)
    t.start()
