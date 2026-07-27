from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class QuestIn(BaseModel):
    title: str = Field(min_length=1, max_length=255)
    description: str | None = Field(default=None, max_length=2000)
    difficulty: str = Field(default="normal", pattern="^(trivial|easy|normal|hard|epic)$")
    goal_id: str | None = None
    due_at: datetime | None = None


class QuestUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=255)
    description: str | None = Field(default=None, max_length=2000)
    difficulty: str | None = Field(default=None, pattern="^(trivial|easy|normal|hard|epic)$")
    due_at: datetime | None = None


class QuestOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    goal_id: str | None
    title: str
    description: str | None
    difficulty: str
    xp_reward: int
    status: str
    source: str
    due_at: datetime | None
    completed_at: datetime | None
    created_at: datetime


class QuestGenerateIn(BaseModel):
    goal_id: str | None = None
    count: int = Field(default=3, ge=1, le=5)


class QuestCompleteOut(BaseModel):
    quest: QuestOut
    xp_awarded: int
    level_up: bool
    new_level: int
    achievements_unlocked: list[dict]
