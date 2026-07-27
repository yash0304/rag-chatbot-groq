from datetime import date, datetime

from pydantic import BaseModel, ConfigDict, Field


class HabitIn(BaseModel):
    title: str = Field(min_length=1, max_length=255)
    cadence: str = Field(default="daily", pattern="^(daily|weekdays|weekly)$")


class HabitOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    title: str
    cadence: str
    streak: int
    best_streak: int
    last_checkin_date: date | None
    xp_base: int
    created_at: datetime
    checked_in_today: bool = False


class HabitCheckinOut(BaseModel):
    habit: HabitOut
    xp_awarded: int
    multiplier: float
    level_up: bool
    achievements_unlocked: list[dict]
