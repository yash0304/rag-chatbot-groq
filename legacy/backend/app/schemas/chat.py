from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class ChatSessionIn(BaseModel):
    title: str | None = Field(default=None, max_length=255)


class ChatSessionOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: str
    title: str
    created_at: datetime


class ChatMessageIn(BaseModel):
    content: str = Field(min_length=1, max_length=8000)


class Citation(BaseModel):
    index: int
    document_id: str
    chunk_id: str
    title: str
    snippet: str
    location: str | None


class ChatMessageOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: str
    role: str
    content: str
    citations: list[Citation] = []
    created_at: datetime
