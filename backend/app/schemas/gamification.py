from datetime import date, datetime

from pydantic import BaseModel, ConfigDict


class ProfileOut(BaseModel):
    xp: int
    level: int
    xp_for_current_level: int
    xp_for_next_level: int
    progress_pct: float
    skill_points: int
    current_streak_max: int


class XPEventOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: str
    kind: str
    amount: int
    ref_id: str | None
    meta: dict | None
    created_at: datetime


class AchievementOut(BaseModel):
    code: str
    name: str
    description: str
    icon: str
    xp_bonus: int
    secret: bool
    unlocked_at: datetime | None


class SkillOut(BaseModel):
    code: str
    tree: str
    tier: int
    name: str
    description: str
    cost: int
    parent_code: str | None
    owned: bool
    available: bool


class CollectibleOut(BaseModel):
    code: str
    name: str
    rarity: str
    lore: str
    source: str
    acquired_at: datetime


class LeaderboardEntry(BaseModel):
    hero_name: str
    level: int
    xp: int


class WeeklyReviewOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: str
    week_start: date
    stats: dict
    narrative: str
    suggestions: list
    created_at: datetime
