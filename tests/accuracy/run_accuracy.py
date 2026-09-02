#!/usr/bin/env python3
"""
Runner de acertividade da busca semântica (métricas padrão de IR).

Para cada query do golden_set, chama POST /search no knowledge-search, verifica
em que posição o documento esperado apareceu, e calcula:

  - Precision@1        : doc certo veio em 1º?
  - Recall@k (1,3,5)   : doc certo está no top-k?
  - MRR@10             : média de 1/rank do acerto (padrão navegacional)
  - nDCG@10            : ganho cumulativo descontado normalizado (padrão-ouro ranking)
  - Score médio        : confiança nos acertos
  - Matriz de confusão : quando erra, com qual doc confunde

Gera relatório no terminal + JSON + Markdown (report.md).

Uso:
    python run_accuracy.py
    python run_accuracy.py --top-k 10 --report report.md

Requer: knowledge-search em http://localhost:8001 (SEARCH_URL para mudar) e
        seed.py já executado (usa .seed_state.json para mapear slug->key).
"""

import argparse
import json
import math
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

import httpx

SEARCH = os.getenv("SEARCH_URL", "http://localhost:8001")
HERE = Path(__file__).parent
GOLDEN = HERE / "golden_set.json"
STATE = HERE / ".seed_state.json"


def load_json(p: Path) -> dict:
    return json.loads(p.read_text(encoding="utf-8"))


def search(client: httpx.Client, query: str, top_k: int, project_id: str | None) -> list[dict]:
    body = {"query": query, "top_k": top_k}
    if project_id:
        body["project_id"] = project_id
    resp = client.post(f"{SEARCH}/search", json=body, timeout=30)
    resp.raise_for_status()
    return resp.json().get("results", [])


def rank_of_expected(results: list[dict], expected_key: str, id_to_key: dict) -> int:
    """1-based rank do doc esperado nos resultados, ou 0 se ausente.

    Casa pelo doc_id (o MVP retorna doc_id em cada resultado); id_to_key mapeia
    doc_id -> key do golden set.
    """
    for i, r in enumerate(results, start=1):
        key = id_to_key.get(r.get("doc_id"))
        if key == expected_key:
            return i
    return 0


def dcg_at_k(rank: int, k: int, rel: float = 1.0) -> float:
    """DCG com um único item relevante na posição `rank` (relevância rel)."""
    if rank == 0 or rank > k:
        return 0.0
    return rel / math.log2(rank + 1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--top-k", type=int, default=10)
    ap.add_argument("--report", default=str(HERE / "report.md"))
    ap.add_argument("--no-filter", action="store_true",
                    help="não filtrar por project_id do Test Bench")
    args = ap.parse_args()

    golden = load_json(GOLDEN)["queries"]

    project_id = None
    id_to_key = {}
    if STATE.exists():
        st = load_json(STATE)
        project_id = None if args.no_filter else st.get("project_id")
        # doc_ids no state é key -> doc_id; invertemos para doc_id -> key.
        id_to_key = {v: k for k, v in st.get("doc_ids", {}).items()}
    else:
        print("AVISO: .seed_state.json não encontrado. Rode seed.py primeiro.", file=sys.stderr)
        sys.exit(1)

    ks = [1, 3, 5]
    n = len(golden)
    hits_at = {k: 0 for k in ks}
    rr_sum = 0.0
    ndcg_sum = 0.0
    idcg = dcg_at_k(1, args.top_k)  # ideal: relevante na 1ª posição
    p_at_1 = 0
    score_hits = []
    rows = []
    confusion = {}  # expected_key -> {got_key: count}

    with httpx.Client() as client:
        for q in golden:
            results = search(client, q["query"], args.top_k, project_id)
            expected = q["expected"]
            rank = rank_of_expected(results, expected, id_to_key)

            # métricas
            if rank == 1:
                p_at_1 += 1
            for k in ks:
                if rank and rank <= k:
                    hits_at[k] += 1
            rr_sum += (1.0 / rank) if rank else 0.0
            ndcg_sum += (dcg_at_k(rank, args.top_k) / idcg) if idcg else 0.0

            top1 = results[0] if results else None
            top1_key = id_to_key.get(top1.get("doc_id")) if top1 else None
            top1_score = round(top1.get("score", 0), 4) if top1 else None

            if rank == 1 and top1:
                score_hits.append(top1.get("score", 0))

            # confusão: quando não acertou no top-1
            if rank != 1:
                confusion.setdefault(expected, {})
                got = top1_key or "(vazio)"
                confusion[expected][got] = confusion[expected].get(got, 0) + 1

            rows.append({
                "query": q["query"],
                "expected": expected,
                "rank": rank,
                "top1": top1_key,
                "top1_score": top1_score,
            })

    precision_1 = p_at_1 / n
    recall = {k: hits_at[k] / n for k in ks}
    mrr = rr_sum / n
    ndcg = ndcg_sum / n
    avg_hit_score = (sum(score_hits) / len(score_hits)) if score_hits else 0.0

    # ---- relatório terminal ----
    print("\n" + "=" * 60)
    print("RELATÓRIO DE ACERTIVIDADE — Busca Semântica")
    print("=" * 60)
    print(f"Queries: {n}  |  top_k: {args.top_k}  |  filtro projeto: {'não' if not project_id else 'sim'}")
    print("-" * 60)
    print(f"Precision@1 : {precision_1:.1%}  ({p_at_1}/{n})")
    print(f"Recall@1    : {recall[1]:.1%}")
    print(f"Recall@3    : {recall[3]:.1%}")
    print(f"Recall@5    : {recall[5]:.1%}")
    print(f"MRR@{args.top_k}     : {mrr:.3f}")
    print(f"nDCG@{args.top_k}    : {ndcg:.3f}")
    print(f"Score médio (acertos top-1): {avg_hit_score:.3f}")
    print("-" * 60)

    failures = [r for r in rows if r["rank"] != 1]
    if failures:
        print(f"\nFALHAS (não vieram em 1º): {len(failures)}")
        for r in failures:
            pos = f"rank {r['rank']}" if r["rank"] else "AUSENTE no top-k"
            print(f"  [{pos}] \"{r['query']}\"")
            print(f"        esperado: {r['expected']}  |  veio 1º: {r['top1']} ({r['top1_score']})")
    else:
        print("\n✅ Sem falhas: todas as queries retornaram o doc certo em 1º.")

    if confusion:
        print("\nMATRIZ DE CONFUSÃO (esperado -> o que veio no 1º):")
        for exp, gots in confusion.items():
            for got, cnt in gots.items():
                print(f"  {exp:16s} confundido com {got}  x{cnt}")

    # ---- relatório markdown ----
    ts = datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")
    md = []
    md.append(f"# Relatório de Acertividade — Busca Semântica\n")
    md.append(f"_Gerado em {ts}_\n")
    md.append(f"- Queries: **{n}** | top_k: {args.top_k}\n")
    md.append("## Métricas (padrão IR)\n")
    md.append("| Métrica | Valor |")
    md.append("|---|---|")
    md.append(f"| Precision@1 | {precision_1:.1%} ({p_at_1}/{n}) |")
    md.append(f"| Recall@1 | {recall[1]:.1%} |")
    md.append(f"| Recall@3 | {recall[3]:.1%} |")
    md.append(f"| Recall@5 | {recall[5]:.1%} |")
    md.append(f"| MRR@{args.top_k} | {mrr:.3f} |")
    md.append(f"| nDCG@{args.top_k} | {ndcg:.3f} |")
    md.append(f"| Score médio (acertos) | {avg_hit_score:.3f} |")
    md.append("\n## Resultado por query\n")
    md.append("| Query | Esperado | Rank | Top-1 | Score |")
    md.append("|---|---|---|---|---|")
    for r in rows:
        mark = "✅" if r["rank"] == 1 else ("🔸" if r["rank"] else "❌")
        md.append(f"| {r['query']} | {r['expected']} | {mark} {r['rank']} | {r['top1']} | {r['top1_score']} |")
    if failures:
        md.append("\n## Falhas\n")
        for r in failures:
            pos = f"rank {r['rank']}" if r["rank"] else "ausente no top-k"
            md.append(f"- **{r['query']}** — esperado `{r['expected']}`, veio `{r['top1']}` ({pos})")
    Path(args.report).write_text("\n".join(md) + "\n", encoding="utf-8")
    print(f"\nRelatório Markdown salvo em: {args.report}")

    # JSON para automação
    json_out = HERE / "report.json"
    json_out.write_text(json.dumps({
        "timestamp": ts, "n": n, "top_k": args.top_k,
        "precision_at_1": precision_1, "recall": recall,
        "mrr": mrr, "ndcg": ndcg, "avg_hit_score": avg_hit_score,
        "rows": rows, "confusion": confusion,
    }, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Relatório JSON salvo em: {json_out}")


if __name__ == "__main__":
    try:
        main()
    except httpx.RequestError as e:
        print(f"ERRO: não consegui conectar ao search em {SEARCH}: {e}", file=sys.stderr)
        sys.exit(1)
