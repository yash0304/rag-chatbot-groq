from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    email: str
    display_name: str
    hero_name: str | None
    plan: str
    xp: int
    level: int
    skill_points: int
    leaderboard_opt_in: bool
    created_at: datetime


class UserUpdate(BaseModel):
    display_name: str | None = Field(default=None, min_length=1, max_length=100)
    hero_name: str | None = Field(default=None, max_length=100)
    leaderboard_opt_in: bool | None = None
