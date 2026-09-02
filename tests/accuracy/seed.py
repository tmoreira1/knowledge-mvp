#!/usr/bin/env python3
"""
Seed do Test Bench de acertividade.

Cria um projeto isolado "Test Bench - Acertividade" no Knowledge Repository e
cadastra os documentos de fixtures/docs.json. Cada create dispara o evento
RabbitMQ que o knowledge-search consome e indexa no Qdrant automaticamente.

Uso:
    python seed.py                 # cria projeto + docs, salva state
    python seed.py --cleanup       # remove os docs criados (via state)

Requer: Knowledge Repository em http://localhost:8080 (KR_BASE_URL para mudar).
"""

import argparse
import json
import os
import sys
import time
from pathlib import Path

import httpx

KR = os.getenv("KR_BASE_URL", "http://localhost:8080")
ACTOR = os.getenv("KR_ACTOR", "accuracy-test")
HERE = Path(__file__).parent
FIXTURES = HERE / "fixtures" / "docs.json"
STATE = HERE / ".seed_state.json"


def _headers():
    return {"Content-Type": "application/json", "X-Actor": ACTOR}


def load_fixtures() -> dict:
    return json.loads(FIXTURES.read_text(encoding="utf-8"))


def create_project(client: httpx.Client, name: str, description: str) -> str:
    resp = client.post(f"{KR}/projects", headers=_headers(),
                       json={"name": name, "description": description})
    resp.raise_for_status()
    return resp.json()["id"]


def create_doc(client: httpx.Client, project_id: str, doc: dict) -> str:
    body = {
        "title": doc["title"],
        "slug": doc["slug"],
        "content": doc["content"],
        "projectId": project_id,
        "domain": doc.get("domain", "backend"),
        "owner": ACTOR,
        "tags": doc.get("tags", []),
    }
    resp = client.post(f"{KR}/documents", headers=_headers(), json=body)
    resp.raise_for_status()
    return resp.json()["id"]


def seed():
    fx = load_fixtures()
    state = {"project_id": None, "doc_ids": {}, "slug_to_key": {}}

    with httpx.Client(timeout=30) as client:
        pid = create_project(client, fx["project_name"], fx["project_description"])
        state["project_id"] = pid
        print(f"Projeto criado: {pid}")

        for doc in fx["docs"]:
            did = create_doc(client, pid, doc)
            state["doc_ids"][doc["key"]] = did
            state["slug_to_key"][doc["slug"]] = doc["key"]
            print(f"  + {doc['key']:16s} -> {did}  ({doc['title']})")

    STATE.write_text(json.dumps(state, indent=2), encoding="utf-8")
    print(f"\nEstado salvo em {STATE}")

    # MVP: indexação determinística via /reindex (em vez de esperar o consumer
    # RabbitMQ). Puxa todos os docs do KR e grava os embeddings no pgvector.
    search_url = os.getenv("SEARCH_URL", "http://localhost:8001")
    print(f"Indexando no search ({search_url}/reindex)...")
    with httpx.Client(timeout=120) as client:
        r = client.post(f"{search_url}/reindex")
        r.raise_for_status()
        print(f"  {r.json()}")
    print("Pronto. Rode: python run_accuracy.py")


def cleanup():
    if not STATE.exists():
        print("Sem state para limpar.")
        return
    state = json.loads(STATE.read_text(encoding="utf-8"))
    with httpx.Client(timeout=30) as client:
        for key, did in state.get("doc_ids", {}).items():
            try:
                client.delete(f"{KR}/documents/{did}", headers=_headers())
                print(f"  - removido {key} ({did})")
            except Exception as e:
                print(f"  ! falha ao remover {key}: {e}")
    STATE.unlink()
    print("Cleanup concluído (projeto vazio permanece; remova manualmente se quiser).")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--cleanup", action="store_true", help="remove os docs criados")
    args = ap.parse_args()
    try:
        cleanup() if args.cleanup else seed()
    except httpx.HTTPStatusError as e:
        print(f"ERRO HTTP: {e.response.status_code} {e.response.text}", file=sys.stderr)
        sys.exit(1)
    except httpx.RequestError as e:
        print(f"ERRO: não consegui conectar ao KR em {KR}: {e}", file=sys.stderr)
        sys.exit(1)
