from app.services.ai.hashing import hash_embed
from app.services.rag import _extract_citations, strip_invalid_markers
from app.workers.ingestion import chunk_text


def test_hash_embed_similarity_orders_correctly():
    doc = hash_embed("the treaty of westphalia ended the thirty years war")
    close = hash_embed("when was the treaty of westphalia signed")
    far = hash_embed("chocolate cake recipe with vanilla frosting")

    def cos(a, b):
        return sum(x * y for x, y in zip(a, b, strict=True))

    assert cos(doc, close) > cos(doc, far)


def test_chunker_respects_size_and_location():
    pages = [(1, "One sentence. " * 200), (2, "Second page here. Short.")]
    chunks = list(chunk_text(pages, size=500, overlap=50))
    assert all(len(text) <= 600 for _, text, _ in chunks)
    assert chunks[0][2] == "p. 1"
    assert chunks[-1][2] == "p. 2"
    seqs = [seq for seq, _, _ in chunks]
    assert seqs == list(range(len(chunks)))


def test_invalid_citation_markers_stripped():
    results = [{"document_id": "d", "chunk_id": "c", "title": "T", "snippet": "s", "location": "p. 1"}]
    answer = "Real citation [1] but fabricated [4] and [12]."
    cleaned = strip_invalid_markers(answer, len(results))
    assert "[1]" in cleaned
    assert "[4]" not in cleaned and "[12]" not in cleaned
    citations = _extract_citations(cleaned, results)
    assert [c["index"] for c in citations] == [1]
