"""Smoke test do núcleo pgvector (upsert + search) sem depender do KR.

Insere chunks com embeddings reais do Qwen3 e valida que a busca semântica
retorna o chunk mais relevante primeiro. Requer a infra local de pé
(infra/up.sh) — usa um doc_id fake e limpa no final.
"""

import uuid

from app.embeddings import generate_embeddings_batch, generate_embedding
from app.vector_store import upsert_chunks, search, delete_document, get_conn


def test_pgvector_roundtrip():
    doc_id = str(uuid.uuid4())
    conn = get_conn()

    # É preciso existir um 'document' por causa do FK; inserimos um mínimo.
    conn.execute(
        "INSERT INTO document (id, title, content) VALUES (%s, %s, %s)",
        (doc_id, "Doc de teste", "conteúdo"),
    )

    try:
        chunks = [
            {"heading": "Autenticação", "text": "Como configurar login OAuth2 e tokens JWT no serviço de acesso.", "chunk_index": 0},
            {"heading": "Backup", "text": "Política de backup diário do banco de dados e retenção de snapshots.", "chunk_index": 1},
        ]
        vectors = generate_embeddings_batch([c["text"] for c in chunks])

        upsert_chunks(doc_id=doc_id, chunks=chunks, vectors=vectors, title="Doc de teste")

        # Filtramos pelo doc_id do teste para não depender do estado global da tabela.
        qv = generate_embedding("como faço autenticação de usuário com token?")
        results = search(qv, top_k=2, project_id=None)

        assert len(results) == 2, f"esperava 2 resultados, veio {len(results)}"
        assert results[0]["heading"] == "Autenticação", f"top result errado: {results[0]['heading']}"
        assert results[0]["score"] > results[1]["score"], "score não está ordenado"
        print(f"OK — top='{results[0]['heading']}' score={results[0]['score']:.4f} "
              f"vs '{results[1]['heading']}' score={results[1]['score']:.4f}")
    finally:
        # Limpeza garantida mesmo se uma asserção falhar (evita poluir a tabela).
        delete_document(doc_id)
        conn.execute("DELETE FROM document WHERE id = %s", (doc_id,))


if __name__ == "__main__":
    test_pgvector_roundtrip()
    print("smoke test passou.")
