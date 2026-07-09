"""Aggregations for the dashboard, analytics pages, and weekly reviews."""

from datetime import UTC, datetime, timedelta

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models import Document, Habit, Quest, User, XPEvent


def _count(db: Session, stmt) -> int:
    return db.scalar(stmt) or 0


def summary(db: Session, user: User) -> dict:
    now = datetime.now(UTC)
    week_ago = now - timedelta(days=7)
    return {
        "documents": _count(
            db,
            select(func.count()).select_from(Document).where(
                Document.user_id == user.id, Document.status == "ready"
            ),
        ),
        "quests_completed": _count(
            db,
            select(func.count()).select_from(Quest).where(
                Quest.user_id == user.id, Quest.status == "completed"
            ),
        ),
        "quests_active": _count(
            db,
            select(func.count()).select_from(Quest).where(
                Quest.user_id == user.id, Quest.status == "active"
            ),
        ),
        "habits_active": _count(
            db, select(func.count()).select_from(Habit).where(Habit.user_id == user.id)
        ),
        "current_streak_max": _count(db, select(func.max(Habit.streak)).where(Habit.user_id == user.id)),
        "xp_7d": _count(
            db,
            select(func.coalesce(func.sum(XPEvent.amount), 0)).where(
                XPEvent.user_id == user.id, XPEvent.created_at >= week_ago
            ),
        ),
        "xp_total": user.xp,
        "level": user.level,
    }


def xp_daily(db: Session, user_id: str, days: int = 30) -> list[dict]:
    since = datetime.now(UTC) - timedelta(days=days)
    events = db.scalars(
        select(XPEvent).where(XPEvent.user_id == user_id, XPEvent.created_at >= since)
    ).all()
    buckets: dict[str, int] = {}
    for i in range(days, -1, -1):
        buckets[(datetime.now(UTC) - timedelta(days=i)).date().isoformat()] = 0
    for e in events:
        key = e.created_at.date().isoformat()
        if key in buckets:
            buckets[key] += e.amount
    return [{"date": d, "xp": v} for d, v in buckets.items()]


def activity_heatmap(db: Session, user_id: str, weeks: int = 12) -> list[dict]:
    since = datetime.now(UTC) - timedelta(weeks=weeks)
    events = db.scalars(
        select(XPEvent).where(XPEvent.user_id == user_id, XPEvent.created_at >= since)
    ).all()
    counts: dict[str, int] = {}
    for e in events:
        key = e.created_at.date().isoformat()
        counts[key] = counts.get(key, 0) + 1
    return [{"date": d, "count": c} for d, c in sorted(counts.items())]


def week_stats(db: Session, user: User, week_start_date) -> dict:
    start = datetime(week_start_date.year, week_start_date.month, week_start_date.day, tzinfo=UTC)
    end = start + timedelta(days=7)

    def in_window(col):
        return (col >= start) & (col < end)

    events = db.scalars(
        select(XPEvent).where(XPEvent.user_id == user.id, in_window(XPEvent.created_at))
    ).all()
    by_kind: dict[str, int] = {}
    for e in events:
        by_kind[e.kind] = by_kind.get(e.kind, 0) + 1
    return {
        "week_start": week_start_date.isoformat(),
        "xp_earned": sum(e.amount for e in events),
        "quests_completed": by_kind.get("quest_completed", 0),
        "habit_checkins": by_kind.get("habit_checkin", 0),
        "documents_processed": by_kind.get("document_processed", 0),
        "milestones_completed": by_kind.get("milestone_completed", 0),
        "level": user.level,
        "events_by_kind": by_kind,
    }
