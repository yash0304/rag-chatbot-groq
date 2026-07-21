"""Vector store abstraction: Qdrant in production, in-memory for dev/tests.

Every search is filtered by user_id — hard tenant isolation at the index level.
"""

import math
import threading
from abc import ABC, abstractmethod
from dataclasses import dataclass

from app.core.config import get_settings


@dataclass
class VectorPoint:
    id: str
    vector: list[float]
    payload: dict  # {user_id, document_id, chunk_id, seq}


@dataclass
class VectorHit:
    id: str
    score: float
    payload: dict


class VectorStore(ABC):
    @abstractmethod
    def upsert(self, points: list[VectorPoint]) -> None: ...

    @abstractmethod
    def search(self, user_id: str, vector: list[float], limit: int = 8) -> list[VectorHit]: ...

    @abstractmethod
    def delete_document(self, user_id: str, document_id: str) -> None: ...

    @abstractmethod
    def delete_user(self, user_id: str) -> None: ...


class MemoryVectorStore(VectorStore):
    def __init__(self) -> None:
        self._points: dict[str, VectorPoint] = {}

    def upsert(self, points: list[VectorPoint]) -> None:
        for p in points:
            self._points[p.id] = p

    def search(self, user_id: str, vector: list[float], limit: int = 8) -> list[VectorHit]:
        hits = []
        for p in self._points.values():
            if p.payload.get("user_id") != user_id:
                continue
            hits.append(VectorHit(id=p.id, score=_cosine(vector, p.vector), payload=p.payload))
        hits.sort(key=lambda h: h.score, reverse=True)
        return hits[:limit]

    def delete_document(self, user_id: str, document_id: str) -> None:
        self._points = {
            k: v for k, v in self._points.items() if v.payload.get("document_id") != document_id
        }

    def delete_user(self, user_id: str) -> None:
        self._points = {k: v for k, v in self._points.items() if v.payload.get("user_id") != user_id}


def _cosine(a: list[float], b: list[float]) -> float:
    if len(a) != len(b):
        return -1.0
    dot = sum(x * y for x, y in zip(a, b, strict=True))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    if na == 0 or nb == 0:
        return 0.0
    return dot / (na * nb)


class QdrantVectorStore(VectorStore):
    def __init__(self) -> None:
        from qdrant_client import QdrantClient

        settings = get_settings()
        self._client = QdrantClient(url=settings.qdrant_url)
        self._collection = settings.qdrant_collection
        self._ensure_collection()

    def _ensure_collection(self) -> None:
        from qdrant_client.http.exceptions import UnexpectedResponse
        from qdrant_client.models import Distance, PayloadSchemaType, VectorParams

        from app.services.ai.provider import get_ai_provider

        dim = get_ai_provider().embed_dim
        if self._client.collection_exists(self._collection):
            return
        try:
            self._client.create_collection(
                self._collection, vectors_config=VectorParams(size=dim, distance=Distance.COSINE)
            )
        except UnexpectedResponse as exc:
            # Lost a create race (409): another request/replica made it — that's fine.
            if exc.status_code != 409:
                raise
            return
        self._client.create_payload_index(
            self._collection, field_name="user_id", field_schema=PayloadSchemaType.KEYWORD
        )

    def upsert(self, points: list[VectorPoint]) -> None:
        from qdrant_client.models import PointStruct

        self._client.upsert(
            self._collection,
            points=[PointStruct(id=p.id, vector=p.vector, payload=p.payload) for p in points],
        )

    def search(self, user_id: str, vector: list[float], limit: int = 8) -> list[VectorHit]:
        from qdrant_client.models import FieldCondition, Filter, MatchValue

        res = self._client.query_points(
            self._collection,
            query=vector,
            limit=limit,
            query_filter=Filter(must=[FieldCondition(key="user_id", match=MatchValue(value=user_id))]),
        )
        return [VectorHit(id=str(p.id), score=p.score, payload=p.payload or {}) for p in res.points]

    def _delete_by(self, key: str, value: str) -> None:
        from qdrant_client.models import FieldCondition, Filter, FilterSelector, MatchValue

        self._client.delete(
            self._collection,
            points_selector=FilterSelector(
                filter=Filter(must=[FieldCondition(key=key, match=MatchValue(value=value))])
            ),
        )

    def delete_document(self, user_id: str, document_id: str) -> None:
        self._delete_by("document_id", document_id)

    def delete_user(self, user_id: str) -> None:
        self._delete_by("user_id", user_id)


_store: VectorStore | None = None
_store_lock = threading.Lock()


def get_vector_store() -> VectorStore:
    global _store
    settings = get_settings()
    with _store_lock:  # concurrent first calls must not construct two stores
        if _store is None:
            _store = QdrantVectorStore() if settings.vector_backend == "qdrant" else MemoryVectorStore()
        return _store


def reset_vector_store() -> None:
    global _store
    _store = None
