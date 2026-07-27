from datetime import UTC, datetime

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.deps import get_current_user, rate_limit_ai
from app.db.session import get_db
from app.models import Quest, User
from app.models.quest import DIFFICULTY_XP
from app.schemas.quest import QuestCompleteOut, QuestGenerateIn, QuestIn, QuestOut, QuestUpdate
from app.services import gamification
from app.services.narrator import generate_quests

router = APIRouter(prefix="/quests", tags=["quests"])


@router.post("", response_model=QuestOut, status_code=status.HTTP_201_CREATED)
def create_quest(body: QuestIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    quest = Quest(
        user_id=user.id,
        goal_id=body.goal_id,
        title=body.title,
        description=body.description,
        difficulty=body.difficulty,
        xp_reward=DIFFICULTY_XP[body.difficulty],
        due_at=body.due_at,
        status="active",
        source="manual",
    )
    db.add(quest)
    db.commit()
    return quest


@router.get("", response_model=list[QuestOut])
def list_quests(
    status_filter: str | None = None,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    stmt = select(Quest).where(Quest.user_id == user.id)
    if status_filter:
        stmt = stmt.where(Quest.status == status_filter)
    return db.scalars(stmt.order_by(Quest.created_at.desc())).all()


@router.post("/generate", response_model=list[QuestOut])
def generate(body: QuestGenerateIn, user: User = Depends(rate_limit_ai), db: Session = Depends(get_db)):
    return generate_quests(db, user, body.goal_id, body.count)


def _owned_quest(db: Session, user: User, quest_id: str) -> Quest:
    quest = db.get(Quest, quest_id)
    if quest is None or quest.user_id != user.id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Quest not found")
    return quest


@router.post("/{quest_id}/accept", response_model=QuestOut)
def accept_quest(quest_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    quest = _owned_quest(db, user, quest_id)
    if quest.status != "draft":
        raise HTTPException(status.HTTP_409_CONFLICT, "Only draft quests can be accepted")
    quest.status = "active"
    db.commit()
    return quest


@router.post("/{quest_id}/complete", response_model=QuestCompleteOut)
def complete_quest(quest_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    quest = _owned_quest(db, user, quest_id)
    if quest.status == "completed":
        raise HTTPException(status.HTTP_409_CONFLICT, "Quest already completed")
    if quest.status not in {"active", "draft"}:
        raise HTTPException(status.HTTP_409_CONFLICT, f"Cannot complete a {quest.status} quest")
    quest.status = "completed"
    quest.completed_at = datetime.now(UTC)
    result = gamification.award(
        db, user.id, "quest_completed", amount=quest.xp_reward, ref_id=quest.id,
        meta={"difficulty": quest.difficulty},
    )
    db.commit()
    return QuestCompleteOut(
        quest=QuestOut.model_validate(quest),
        xp_awarded=result.xp_awarded,
        level_up=result.level_up,
        new_level=result.new_level,
        achievements_unlocked=result.achievements_unlocked,
    )


@router.patch("/{quest_id}", response_model=QuestOut)
def update_quest(
    quest_id: str, body: QuestUpdate, user: User = Depends(get_current_user), db: Session = Depends(get_db)
):
    quest = _owned_quest(db, user, quest_id)
    if quest.status == "completed":
        raise HTTPException(status.HTTP_409_CONFLICT, "Completed quests are immutable")
    updates = body.model_dump(exclude_unset=True)
    for field, value in updates.items():
        setattr(quest, field, value)
    if "difficulty" in updates:
        quest.xp_reward = DIFFICULTY_XP[quest.difficulty]
    db.commit()
    return quest


@router.delete("/{quest_id}", status_code=status.HTTP_204_NO_CONTENT)
def abandon_quest(quest_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    quest = _owned_quest(db, user, quest_id)
    quest.status = "abandoned"
    db.commit()
