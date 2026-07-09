"""Narrator flows beyond chat: quest generation, arc themes, weekly review prose."""

import json

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Document, Goal, Quest, User
from app.models.quest import DIFFICULTY_XP
from app.services.ai.provider import get_ai_provider

QUESTMASTER_SYSTEM = (
    "You are the Questmaster of MindQuest, an original fantasy productivity world. "
    "Generate real-life, actionable quests (tasks) for the hero based on their goals and "
    "recent studies. Respond ONLY with a JSON array of objects with keys: title (imperative, "
    "concrete, doable in one sitting), description (1-2 sentences, light original-fantasy "
    "flavor), difficulty (one of: trivial, easy, normal, hard, epic). Never reference "
    "existing game franchises or copyrighted characters."
)


def generate_quests(db: Session, user: User, goal_id: str | None, count: int = 3) -> list[Quest]:
    context_parts = [f"Hero level: {user.level}."]
    if goal_id:
        goal = db.get(Goal, goal_id)
        if goal and goal.user_id == user.id:
            pending = [m.title for m in goal.milestones if not m.completed][:5]
            context_parts.append(f"Active story arc: {goal.title}. Pending chapters: {pending}")
    recent_docs = db.scalars(
        select(Document)
        .where(Document.user_id == user.id, Document.status == "ready")
        .order_by(Document.created_at.desc())
        .limit(5)
    ).all()
    if recent_docs:
        context_parts.append("Recent studies: " + "; ".join(d.title for d in recent_docs))

    raw = get_ai_provider().complete(
        QUESTMASTER_SYSTEM,
        f"{' '.join(context_parts)}\nGenerate {count} quests.",
        purpose="quests",
        json_mode=True,
    )
    try:
        items = json.loads(raw)
        if isinstance(items, dict):
            items = next((v for v in items.values() if isinstance(v, list)), [])
    except json.JSONDecodeError:
        items = []

    quests: list[Quest] = []
    for item in items[:count]:
        difficulty = str(item.get("difficulty", "normal")).lower()
        if difficulty not in DIFFICULTY_XP:
            difficulty = "normal"
        quests.append(
            Quest(
                user_id=user.id,
                goal_id=goal_id,
                title=str(item.get("title", "Unnamed quest"))[:255],
                description=str(item.get("description", ""))[:2000],
                difficulty=difficulty,
                xp_reward=DIFFICULTY_XP[difficulty],
                status="draft",
                source="ai",
            )
        )
    db.add_all(quests)
    db.commit()
    return quests


def arc_theme_for_goal(title: str, narrative: str | None) -> str:
    return (
        get_ai_provider()
        .complete(
            "Invent a short, original fantasy story-arc title (4-7 words) for this real-life goal. "
            "Output only the title. No references to existing franchises.",
            f"Goal: {title}. {narrative or ''}",
            purpose="arc_theme",
        )
        .strip()
        .strip('"')[:120]
    )


def weekly_review_narrative(stats: dict) -> str:
    return get_ai_provider().complete(
        "You are the Narrator of MindQuest. Write an encouraging 3-6 sentence weekly review of the "
        "hero's real productivity, in a light original-fantasy voice, followed by one concrete "
        "suggestion for next week. Be honest about weak weeks. No copyrighted references.",
        f"This week's statistics: {json.dumps(stats)}",
        purpose="review",
    )
