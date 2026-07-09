from datetime import datetime

from pydantic import BaseModel, ConfigDict


class TagOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: str
    name: str


class DocumentOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    title: str
    filename: str
    mime_type: str
    status: str
    error: str | None
    summary: str | None
    domain: str | None
    ocr_used: bool
    chunk_count: int
    created_at: datetime
    tags: list[TagOut] = []


class ChunkOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: str
    seq: int
    text: str
    location: str | None


class SearchIn(BaseModel):
    query: str
    limit: int = 8


class SearchResult(BaseModel):
    chunk_id: str
    document_id: str
    title: str
    snippet: str
    location: str | None
    score: float


class SearchOut(BaseModel):
    results: list[SearchResult]
