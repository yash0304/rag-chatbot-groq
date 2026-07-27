"""Document ingestion pipeline.

extract → OCR fallback → chunk → summarize + tag + domain (AI) → embed → vector upsert.

Runs as a FastAPI background task today; the entrypoint (`process_document`) opens its
own DB session and is a pure function of (document_id), so it can be lifted into a
Celery/RQ worker without changes to callers.
"""

import io
import json
import logging
import re
from pathlib import Path

from sqlalchemy import select

from app.db.session import SessionLocal
from app.models import Document, DocumentChunk, DocumentTag, Tag
from app.services import gamification
from app.services.ai.provider import get_ai_provider
from app.services.vector import VectorPoint, get_vector_store

logger = logging.getLogger(__name__)

CHUNK_SIZE = 1000
CHUNK_OVERLAP = 150


# ---------- extraction ----------


def _extract_pdf(data: bytes) -> tuple[str, list[tuple[int, str]], bool]:
    """Return (full_text, [(page_no, page_text)], ocr_used)."""
    from pypdf import PdfReader

    reader = PdfReader(io.BytesIO(data))
    pages = [(i + 1, (page.extract_text() or "").strip()) for i, page in enumerate(reader.pages)]
    if any(text for _, text in pages):
        return "\n\n".join(t for _, t in pages), pages, False
    # No native text layer — try OCR page images if tesseract is available.
    ocr_pages = []
    for i, page in enumerate(reader.pages):
        page_text = ""
        for image in getattr(page, "images", []):
            page_text += _ocr_image(image.data) + "\n"
        ocr_pages.append((i + 1, page_text.strip()))
    return "\n\n".join(t for _, t in ocr_pages), ocr_pages, True


def _ocr_image(data: bytes) -> str:
    try:
        import pytesseract
        from PIL import Image

        return pytesseract.image_to_string(Image.open(io.BytesIO(data)))
    except Exception as exc:  # tesseract binary missing, unreadable image, …
        logger.warning("OCR unavailable or failed: %s", exc)
        return ""


def extract_text(path: str, mime_type: str) -> tuple[str, list[tuple[int, str]], bool]:
    data = Path(path).read_bytes()
    suffix = Path(path).suffix.lower()
    if suffix == ".pdf":
        return _extract_pdf(data)
    if suffix in {".png", ".jpg", ".jpeg"}:
        text = _ocr_image(data)
        return text, [(1, text)], True
    text = data.decode("utf-8", errors="replace")
    return text, [(1, text)], False


# ---------- chunking ----------


def chunk_text(pages: list[tuple[int, str]], size: int = CHUNK_SIZE, overlap: int = CHUNK_OVERLAP):
    """Sentence-aware sliding window over page texts. Yields (seq, text, location)."""
    seq = 0
    for page_no, page_text in pages:
        if not page_text.strip():
            continue
        sentences = re.split(r"(?<=[.!?])\s+", page_text)
        buf = ""
        for sentence in sentences:
            if buf and len(buf) + len(sentence) + 1 > size:
                yield seq, buf.strip(), f"p. {page_no}"
                seq += 1
                buf = buf[-overlap:] if overlap else ""
            buf += (" " if buf else "") + sentence
        if buf.strip():
            yield seq, buf.strip(), f"p. {page_no}"
            seq += 1


# ---------- enrichment ----------


def _summarize_and_tag(text: str) -> tuple[str, list[str], str]:
    ai = get_ai_provider()
    sample = text[:6000]
    summary = ai.complete(
        "You are an archivist. Summarize the document in 2-4 sentences. Output only the summary.",
        sample,
        purpose="summary",
    ).strip()
    raw_tags = ai.complete(
        "Extract 3-6 topical tags for this document. Respond with a JSON array of "
        'lowercase strings, e.g. ["history","rome"].',
        sample,
        purpose="tags",
        json_mode=True,
    )
    try:
        parsed = json.loads(raw_tags)
        if isinstance(parsed, dict):  # tolerate {"tags": [...]}
            parsed = next((v for v in parsed.values() if isinstance(v, list)), [])
        tags = [str(t).strip().lower()[:80] for t in parsed if str(t).strip()][:6]
    except (json.JSONDecodeError, TypeError):
        tags = []
    domain = ai.complete(
        "Name the single knowledge domain this document belongs to, as a short original "
        "fantasy-flavored region name (2-3 words). Output only the name.",
        sample[:2000],
        purpose="domain",
    ).strip()[:100]
    return summary, tags, domain or "Uncharted Lands"


# ---------- pipeline entrypoint ----------


def process_document(document_id: str) -> None:
    db = SessionLocal()
    try:
        doc = db.get(Document, document_id)
        if doc is None:
            return
        try:
            text, pages, ocr_used = extract_text(doc.storage_path, doc.mime_type)
            if not text.strip():
                raise ValueError(
                    "No text could be extracted (for scanned files, OCR requires the tesseract binary)"
                )
            chunks = list(chunk_text(pages))
            summary, tag_names, domain = _summarize_and_tag(text)

            doc.summary = summary
            doc.domain = domain
            doc.ocr_used = ocr_used
            doc.char_count = len(text)
            doc.chunk_count = len(chunks)

            chunk_rows = [
                DocumentChunk(
                    document_id=doc.id, user_id=doc.user_id, seq=seq, text=chunk, location=loc
                )
                for seq, chunk, loc in chunks
            ]
            db.add_all(chunk_rows)
            for name in tag_names:
                tag = db.scalar(select(Tag).where(Tag.user_id == doc.user_id, Tag.name == name))
                if tag is None:
                    tag = Tag(user_id=doc.user_id, name=name)
                    db.add(tag)
                    db.flush()
                db.add(DocumentTag(document_id=doc.id, tag_id=tag.id))
            db.flush()

            ai = get_ai_provider()
            vectors = ai.embed([c.text for c in chunk_rows]) if chunk_rows else []
            get_vector_store().upsert(
                [
                    VectorPoint(
                        id=c.id,
                        vector=v,
                        payload={
                            "user_id": doc.user_id,
                            "document_id": doc.id,
                            "chunk_id": c.id,
                            "seq": c.seq,
                        },
                    )
                    for c, v in zip(chunk_rows, vectors, strict=True)
                ]
            )

            doc.status = "ready"
            doc.error = None
            gamification.award(db, doc.user_id, "document_processed", ref_id=doc.id)
            db.commit()
        except Exception as exc:
            db.rollback()
            doc = db.get(Document, document_id)
            if doc is not None:
                doc.status = "failed"
                doc.error = str(exc)[:2000]
                db.commit()
            logger.exception("Ingestion failed for document %s", document_id)
    finally:
        db.close()


def reindex_user(user_id: str) -> int:
    """Re-embed all of a user's chunks (disaster recovery / provider migration)."""
    db = SessionLocal()
    try:
        chunks = db.scalars(select(DocumentChunk).where(DocumentChunk.user_id == user_id)).all()
        if not chunks:
            return 0
        ai = get_ai_provider()
        vectors = ai.embed([c.text for c in chunks])
        get_vector_store().upsert(
            [
                VectorPoint(
                    id=c.id,
                    vector=v,
                    payload={
                        "user_id": c.user_id,
                        "document_id": c.document_id,
                        "chunk_id": c.id,
                        "seq": c.seq,
                    },
                )
                for c, v in zip(chunks, vectors, strict=True)
            ]
        )
        return len(chunks)
    finally:
        db.close()
