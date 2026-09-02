"""Chunker de markdown por heading (idêntico ao serviço original).

Divide um documento em chunks semânticos por heading; seções grandes são
subdivididas por parágrafo. Cada chunk é prefixado com o título do documento
para dar contexto ao embedding.
"""

import re
import logging

logger = logging.getLogger(__name__)

MAX_CHUNK_CHARS = 2000  # ~500 tokens


def chunk_document(title: str, content: str) -> list[dict]:
    if not content or not content.strip():
        return [{"heading": "intro", "text": title, "chunk_index": 0}]

    sections = _split_by_headings(content)

    chunks: list[dict] = []
    for heading, body in sections:
        prefixed_text = (
            f"{title}\n\n## {heading}\n{body}" if heading != "intro" else f"{title}\n\n{body}"
        )
        if len(prefixed_text) <= MAX_CHUNK_CHARS:
            chunks.append({"heading": heading, "text": prefixed_text, "chunk_index": len(chunks)})
        else:
            for sc in _split_by_paragraphs(prefixed_text, heading):
                sc["chunk_index"] = len(chunks)
                chunks.append(sc)

    if not chunks:
        chunks = [{"heading": "intro", "text": f"{title}\n\n{content}", "chunk_index": 0}]

    logger.debug(f"'{title}' dividido em {len(chunks)} chunks")
    return chunks


def _split_by_headings(content: str) -> list[tuple[str, str]]:
    heading_pattern = re.compile(r"^(#{1,3})\s+(.+)$", re.MULTILINE)
    sections: list[tuple[str, str]] = []
    last_end = 0
    last_heading = "intro"

    for match in heading_pattern.finditer(content):
        body = content[last_end:match.start()].strip()
        if body or last_heading == "intro":
            sections.append((last_heading, body))
        last_heading = match.group(2).strip()
        last_end = match.end()

    remaining = content[last_end:].strip()
    if remaining:
        sections.append((last_heading, remaining))
    elif last_heading != "intro":
        sections.append((last_heading, ""))

    if sections and sections[0] == ("intro", ""):
        sections = sections[1:]
    return sections


def _split_by_paragraphs(text: str, heading: str) -> list[dict]:
    paragraphs = re.split(r"\n\n+", text)
    chunks: list[dict] = []
    current = ""
    for para in paragraphs:
        if len(current) + len(para) + 2 <= MAX_CHUNK_CHARS:
            current = f"{current}\n\n{para}" if current else para
        else:
            if current:
                chunks.append({"heading": heading, "text": current.strip()})
            current = para
    if current:
        chunks.append({"heading": heading, "text": current.strip()})
    return chunks
