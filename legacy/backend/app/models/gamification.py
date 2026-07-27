from datetime import date, datetime

from sqlalchemy import (
    JSON,
    Boolean,
    Date,
    DateTime,
    ForeignKey,
    Integer,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import GUID, Base, TimestampMixin, new_id, utcnow


class XPEvent(Base, TimestampMixin):
    """Append-only ledger; source of truth for all progression."""

    __tablename__ = "xp_events"

    id: Mapped[str] = mapped_column(GUID, primary_key=True, default=new_id)
    user_id: Mapped[str] = mapped_column(GUID, ForeignKey("users.id", ondelete="CASCADE"), index=True)
    kind: Mapped[str] = mapped_column(String(40), index=True)
    amount: Mapped[int] = mapped_column(Integer)
    ref_id: Mapped[str | None] = mapped_column(GUID)
    meta: Mapped[dict | None] = mapped_column(JSON, default=dict)


class Achievement(Base):
    __tablename__ = "achievements"

    id: Mapped[str] = mapped_column(GUID, primary_key=True, default=new_id)
    code: Mapped[str] = mapped_column(String(50), unique=True)
    name: Mapped[str] = mapped_column(String(120))
    description: Mapped[str] = mapped_column(Text)
    icon: Mapped[str] = mapped_column(String(20), default="🏆")
    xp_bonus: Mapped[int] = mapped_column(Integer, default=0)
    secret: Mapped[bool] = mapped_column(Boolean, default=False)


class UserAchievement(Base):
    __tablename__ = "user_achievements"
    __table_args__ = (UniqueConstraint("user_id", "achievement_id", name="uq_user_achievement"),)

    id: Mapped[str] = mapped_column(GUID, primary_key=True, default=new_id)
    user_id: Mapped[str] = mapped_column(GUID, ForeignKey("users.id", ondelete="CASCADE"), index=True)
    achievement_id: Mapped[str] = mapped_column(GUID, ForeignKey("achievements.id", ondelete="CASCADE"))
    unlocked_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class Skill(Base):
    __tablename__ = "skills"

    id: Mapped[str] = mapped_column(GUID, primary_key=True, default=new_id)
    code: Mapped[str] = mapped_column(String(50), unique=True)
    tree: Mapped[str] = mapped_column(String(20))  # scholar|explorer|strategist|forger
    tier: Mapped[int] = mapped_column(Integer, default=1)
    name: Mapped[str] = mapped_column(String(120))
    description: Mapped[str] = mapped_column(Text)
    cost: Mapped[int] = mapped_column(Integer, default=1)
    parent_code: Mapped[str | None] = mapped_column(String(50))


class UserSkill(Base):
    __tablename__ = "user_skills"
    __table_args__ = (UniqueConstraint("user_id", "skill_id", name="uq_user_skill"),)

    id: Mapped[str] = mapped_column(GUID, primary_key=True, default=new_id)
    user_id: Mapped[str] = mapped_column(GUID, ForeignKey("users.id", ondelete="CASCADE"), index=True)
    skill_id: Mapped[str] = mapped_column(GUID, ForeignKey("skills.id", ondelete="CASCADE"))
    unlocked_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class Collectible(Base):
    __tablename__ = "collectibles"

    id: Mapped[str] = mapped_column(GUID, primary_key=True, default=new_id)
    code: Mapped[str] = mapped_column(String(50), unique=True)
    name: Mapped[str] = mapped_column(String(120))
    rarity: Mapped[str] = mapped_column(String(12), default="common")  # common|rare|epic|legendary
    lore: Mapped[str] = mapped_column(Text)


class UserCollectible(Base):
    __tablename__ = "user_collectibles"
    __table_args__ = (UniqueConstraint("user_id", "collectible_id", name="uq_user_collectible"),)

    id: Mapped[str] = mapped_column(GUID, primary_key=True, default=new_id)
    user_id: Mapped[str] = mapped_column(GUID, ForeignKey("users.id", ondelete="CASCADE"), index=True)
    collectible_id: Mapped[str] = mapped_column(GUID, ForeignKey("collectibles.id", ondelete="CASCADE"))
    source: Mapped[str] = mapped_column(String(30), default="achievement")
    acquired_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class WeeklyReview(Base, TimestampMixin):
    __tablename__ = "weekly_reviews"
    __table_args__ = (UniqueConstraint("user_id", "week_start", name="uq_weekly_review"),)

    id: Mapped[str] = mapped_column(GUID, primary_key=True, default=new_id)
    user_id: Mapped[str] = mapped_column(GUID, ForeignKey("users.id", ondelete="CASCADE"), index=True)
    week_start: Mapped[date] = mapped_column(Date)
    stats: Mapped[dict] = mapped_column(JSON, default=dict)
    narrative: Mapped[str] = mapped_column(Text)
    suggestions: Mapped[list] = mapped_column(JSON, default=list)
