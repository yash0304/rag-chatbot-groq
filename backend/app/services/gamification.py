"""Gamification engine: XP ledger, level curve, achievements, skills, collectibles.

Single write path: `award()` — inserts an XPEvent, updates the cached level,
grants skill points on level-up, and evaluates achievement rules. All progression
is derived from real user activity; nothing is purchasable.
"""

from dataclasses import dataclass, field
from datetime import UTC, datetime, timedelta

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.models import (
    Achievement,
    Collectible,
    Document,
    Habit,
    Quest,
    Skill,
    User,
    UserAchievement,
    UserCollectible,
    XPEvent,
)

# ---------- level curve (published in the PRD) ----------


def level_for_xp(xp: int) -> int:
    level = 1
    while xp_required_for_level(level + 1) <= xp:
        level += 1
    return level


def xp_required_for_level(level: int) -> int:
    """Total XP needed to reach `level`. Level 1 = 0 XP, level 2 = 100, then ×(n-1)^1.6."""
    if level <= 1:
        return 0
    return int(100 * (level - 1) ** 1.6)


def streak_multiplier(streak: int) -> float:
    """+5% per consecutive day, capped at ×2.5."""
    return min(1 + min(streak, 30) * 0.05, 2.5)


# ---------- catalogs (seeded at startup; original MindQuest lore) ----------

ACHIEVEMENTS = [
    ("first_light", "First Light", "Upload your first document to the archives.", "📜", 20),
    ("archivist", "Archivist", "Have 10 documents processed into the archives.", "🗄️", 50),
    ("lorekeeper", "Lorekeeper", "Have 50 documents processed into the archives.", "🏛️", 200),
    ("first_quest", "The First Step", "Complete your first quest.", "⚔️", 20),
    ("quest_veteran", "Quest Veteran", "Complete 25 quests.", "🛡️", 100),
    ("epic_slayer", "Epic Undertaking", "Complete an epic-difficulty quest.", "🐉", 100),
    ("week_of_iron", "Week of Iron", "Reach a 7-day streak on any daily mission.", "🔥", 60),
    ("month_of_iron", "Month of Iron", "Reach a 30-day streak on any daily mission.", "🌋", 250),
    ("seeker", "Seeker of Answers", "Consult the Narrator about your knowledge base.", "🔮", 15),
    ("cartographer", "Cartographer", "Chart 5 distinct knowledge domains.", "🗺️", 80),
    ("arc_closer", "End of an Arc", "Complete a full story arc (goal).", "📖", 120),
    ("level_5", "Rising Hero", "Reach level 5.", "⭐", 50),
    ("level_10", "Renowned Hero", "Reach level 10.", "🌟", 150),
]

# achievement code -> collectible granted alongside it
ACHIEVEMENT_COLLECTIBLES = {
    "first_light": "ember_quill",
    "week_of_iron": "iron_hourglass",
    "cartographer": "astral_compass",
    "arc_closer": "arcstone",
    "level_10": "crown_of_daybreak",
}

COLLECTIBLES = [
    ("ember_quill", "Ember Quill", "rare", "A quill that glows when new knowledge enters the archive."),
    ("iron_hourglass", "Iron Hourglass", "rare",
     "Forged from seven unbroken days; its sand never runs out."),
    ("astral_compass", "Astral Compass", "epic",
     "Points not north, but toward the realm you have studied least."),
    ("arcstone", "Arcstone", "epic", "A crystallized story arc — proof a long journey reached its end."),
    ("crown_of_daybreak", "Crown of Daybreak", "legendary",
     "Worn only by those whose deeds fill ten levels of ledger."),
]

SKILLS = [
    # (code, tree, tier, name, description, cost, parent)
    ("scholar_1", "scholar", 1, "Keen Reading", "Highlights key terms in document summaries.", 1, None),
    ("scholar_2", "scholar", 2, "Deep Recall", "Semantic search returns 2 extra results.", 2, "scholar_1"),
    ("scholar_3", "scholar", 3, "Sage's Synthesis",
     "Weekly reviews include cross-document insights.", 3, "scholar_2"),
    ("explorer_1", "explorer", 1, "Wayfinding", "Knowledge map shows unexplored domains.", 1, None),
    ("explorer_2", "explorer", 2, "Trailblazer", "New domains grant a discovery banner.", 2, "explorer_1"),
    ("explorer_3", "explorer", 3, "Realm Charter", "Unlock custom domain naming.", 3, "explorer_2"),
    ("strategist_1", "strategist", 1, "Quartermaster", "Quest board shows XP forecasts.", 1, None),
    ("strategist_2", "strategist", 2, "Campaign Planner",
     "AI generates quests tuned to your goals.", 2, "strategist_1"),
    ("strategist_3", "strategist", 3, "Grand Strategist",
     "Story arcs get AI chapter outlines.", 3, "strategist_2"),
    ("forger_1", "forger", 1, "Kindling", "Streak flames appear on the dashboard.", 1, None),
    ("forger_2", "forger", 2, "Steady Flame", "Streak grace: one missed day per 14 kept.", 2, "forger_1"),
    ("forger_3", "forger", 3, "Unbreakable", "Best-streak trophies on your character sheet.", 3, "forger_2"),
]


def seed_catalogs(db: Session) -> None:
    if db.scalar(select(func.count()).select_from(Achievement)) == 0:
        for code, name, desc, icon, bonus in ACHIEVEMENTS:
            db.add(Achievement(code=code, name=name, description=desc, icon=icon, xp_bonus=bonus))
    if db.scalar(select(func.count()).select_from(Collectible)) == 0:
        for code, name, rarity, lore in COLLECTIBLES:
            db.add(Collectible(code=code, name=name, rarity=rarity, lore=lore))
    if db.scalar(select(func.count()).select_from(Skill)) == 0:
        for code, tree, tier, name, desc, cost, parent in SKILLS:
            db.add(
                Skill(
                    code=code, tree=tree, tier=tier, name=name,
                    description=desc, cost=cost, parent_code=parent,
                )
            )
    db.commit()


# ---------- award path ----------


@dataclass
class AwardResult:
    xp_awarded: int = 0
    level_up: bool = False
    new_level: int = 1
    achievements_unlocked: list[dict] = field(default_factory=list)


DEFAULT_AMOUNTS = {
    "document_uploaded": lambda s: s.xp_document_uploaded,
    "document_processed": lambda s: s.xp_document_processed,
    "milestone_completed": lambda s: s.xp_milestone,
    "goal_completed": lambda s: s.xp_goal_bonus,
    "knowledge_consulted": lambda s: s.xp_knowledge_consulted,
    "weekly_review": lambda s: s.xp_weekly_review,
}

DAILY_CAPPED = {"knowledge_consulted"}


def award(
    db: Session,
    user_id: str,
    kind: str,
    *,
    amount: int | None = None,
    ref_id: str | None = None,
    meta: dict | None = None,
) -> AwardResult:
    """Insert an XP event and roll forward level, skill points, achievements.

    Caller commits (award participates in the caller's transaction), except the
    background pipeline which manages its own session.
    """
    settings = get_settings()
    result = AwardResult()

    if kind in DAILY_CAPPED:
        today_start = datetime.now(UTC).replace(hour=0, minute=0, second=0, microsecond=0)
        count_today = db.scalar(
            select(func.count())
            .select_from(XPEvent)
            .where(XPEvent.user_id == user_id, XPEvent.kind == kind, XPEvent.created_at >= today_start)
        )
        if count_today >= settings.knowledge_consulted_daily_cap:
            return result

    if amount is None:
        amount_fn = DEFAULT_AMOUNTS.get(kind)
        amount = amount_fn(settings) if amount_fn else 0
    if amount <= 0:
        return result

    user = db.get(User, user_id)
    if user is None:
        return result

    db.add(XPEvent(user_id=user_id, kind=kind, amount=amount, ref_id=ref_id, meta=meta or {}))
    old_level = user.level
    user.xp += amount
    user.level = level_for_xp(user.xp)
    result.xp_awarded = amount
    result.new_level = user.level
    if user.level > old_level:
        result.level_up = True
        user.skill_points += user.level - old_level
    db.flush()

    result.achievements_unlocked = _evaluate_achievements(db, user)
    return result


# ---------- achievement rules ----------


def _stats_for_rules(db: Session, user: User) -> dict:
    def count(stmt) -> int:
        return db.scalar(stmt) or 0

    return {
        "documents_ready": count(
            select(func.count()).select_from(Document).where(
                Document.user_id == user.id, Document.status == "ready"
            )
        ),
        "quests_completed": count(
            select(func.count()).select_from(Quest).where(
                Quest.user_id == user.id, Quest.status == "completed"
            )
        ),
        "epic_completed": count(
            select(func.count()).select_from(Quest).where(
                Quest.user_id == user.id, Quest.status == "completed", Quest.difficulty == "epic"
            )
        ),
        "best_streak": count(select(func.max(Habit.best_streak)).where(Habit.user_id == user.id)),
        "consulted": count(
            select(func.count()).select_from(XPEvent).where(
                XPEvent.user_id == user.id, XPEvent.kind == "knowledge_consulted"
            )
        ),
        "domains": count(
            select(func.count(func.distinct(Document.domain))).where(
                Document.user_id == user.id, Document.status == "ready", Document.domain.isnot(None)
            )
        ),
        "goals_completed": count(
            select(func.count())
            .select_from(XPEvent)
            .where(XPEvent.user_id == user.id, XPEvent.kind == "goal_completed")
        ),
        "level": user.level,
        "documents_any": count(
            select(func.count()).select_from(Document).where(Document.user_id == user.id)
        ),
    }


RULES = {
    "first_light": lambda s: s["documents_any"] >= 1,
    "archivist": lambda s: s["documents_ready"] >= 10,
    "lorekeeper": lambda s: s["documents_ready"] >= 50,
    "first_quest": lambda s: s["quests_completed"] >= 1,
    "quest_veteran": lambda s: s["quests_completed"] >= 25,
    "epic_slayer": lambda s: s["epic_completed"] >= 1,
    "week_of_iron": lambda s: s["best_streak"] >= 7,
    "month_of_iron": lambda s: s["best_streak"] >= 30,
    "seeker": lambda s: s["consulted"] >= 1,
    "cartographer": lambda s: s["domains"] >= 5,
    "arc_closer": lambda s: s["goals_completed"] >= 1,
    "level_5": lambda s: s["level"] >= 5,
    "level_10": lambda s: s["level"] >= 10,
}


def _evaluate_achievements(db: Session, user: User) -> list[dict]:
    owned_ids = set(
        db.scalars(select(UserAchievement.achievement_id).where(UserAchievement.user_id == user.id))
    )
    catalog = db.scalars(select(Achievement)).all()
    stats = _stats_for_rules(db, user)
    unlocked: list[dict] = []
    for ach in catalog:
        if ach.id in owned_ids:
            continue
        rule = RULES.get(ach.code)
        if rule is None or not rule(stats):
            continue
        db.add(UserAchievement(user_id=user.id, achievement_id=ach.id))
        if ach.xp_bonus:
            db.add(
                XPEvent(
                    user_id=user.id, kind="achievement_bonus", amount=ach.xp_bonus, ref_id=ach.id,
                    meta={"code": ach.code},
                )
            )
            old_level = user.level
            user.xp += ach.xp_bonus
            user.level = level_for_xp(user.xp)
            if user.level > old_level:
                user.skill_points += user.level - old_level
        collectible_code = ACHIEVEMENT_COLLECTIBLES.get(ach.code)
        if collectible_code:
            coll = db.scalar(select(Collectible).where(Collectible.code == collectible_code))
            already = db.scalar(
                select(UserCollectible).where(
                    UserCollectible.user_id == user.id, UserCollectible.collectible_id == coll.id
                )
            )
            if coll is not None and already is None:
                db.add(
                    UserCollectible(user_id=user.id, collectible_id=coll.id, source="achievement")
                )
        unlocked.append({"code": ach.code, "name": ach.name, "icon": ach.icon, "xp_bonus": ach.xp_bonus})
    db.flush()
    return unlocked


# ---------- streaks ----------


def compute_streak(habit: Habit, checkin_date) -> int:
    """New streak value given the previous check-in date (weekly cadence: 7-day window)."""
    if habit.last_checkin_date is None:
        return 1
    gap = (checkin_date - habit.last_checkin_date).days
    max_gap = {"daily": 1, "weekdays": 3, "weekly": 7}.get(habit.cadence, 1)
    return habit.streak + 1 if 0 < gap <= max_gap else 1


def week_start(d: datetime | None = None):
    now = d or datetime.now(UTC)
    monday = now - timedelta(days=now.weekday())
    return monday.date()
