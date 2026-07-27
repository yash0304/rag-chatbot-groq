from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class GoalIn(BaseModel):
    title: str = Field(min_length=1, max_length=255)
    narrative: str | None = Field(default=None, max_length=4000)
    milestones: list[str] = Field(default_factory=list, max_length=20)


class MilestoneOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: str
    seq: int
    title: str
    completed: bool
    completed_at: datetime | None


class GoalOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    title: str
    narrative: str | None
    arc_theme: str | None
    status: str
    created_at: datetime
    milestones: list[MilestoneOut] = []


class MilestoneCompleteOut(BaseModel):
    goal: GoalOut
    xp_awarded: int
    goal_completed: bool
    achievements_unlocked: list[dict]
