from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.db.session import get_db
from app.models import (
    Achievement,
    Collectible,
    Habit,
    Skill,
    User,
    UserAchievement,
    UserCollectible,
    UserSkill,
    XPEvent,
)
from app.schemas.gamification import (
    AchievementOut,
    CollectibleOut,
    ProfileOut,
    SkillOut,
    XPEventOut,
)
from app.services.gamification import xp_required_for_level

router = APIRouter(prefix="/gamification", tags=["gamification"])


@router.get("/profile", response_model=ProfileOut)
def profile(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    current_floor = xp_required_for_level(user.level)
    next_needed = xp_required_for_level(user.level + 1)
    span = max(next_needed - current_floor, 1)
    return ProfileOut(
        xp=user.xp,
        level=user.level,
        xp_for_current_level=current_floor,
        xp_for_next_level=next_needed,
        progress_pct=round(100 * (user.xp - current_floor) / span, 1),
        skill_points=user.skill_points,
        current_streak_max=db.scalar(select(func.max(Habit.streak)).where(Habit.user_id == user.id))
        or 0,
    )


@router.get("/xp-events", response_model=list[XPEventOut])
def xp_events(
    limit: int = 50, offset: int = 0, user: User = Depends(get_current_user), db: Session = Depends(get_db)
):
    return db.scalars(
        select(XPEvent)
        .where(XPEvent.user_id == user.id)
        .order_by(XPEvent.created_at.desc())
        .limit(min(limit, 200))
        .offset(offset)
    ).all()


@router.get("/achievements", response_model=list[AchievementOut])
def achievements(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    unlocked = {
        ua.achievement_id: ua.unlocked_at
        for ua in db.scalars(select(UserAchievement).where(UserAchievement.user_id == user.id))
    }
    out = []
    for ach in db.scalars(select(Achievement)).all():
        unlocked_at = unlocked.get(ach.id)
        if ach.secret and unlocked_at is None:
            continue
        out.append(
            AchievementOut(
                code=ach.code, name=ach.name, description=ach.description, icon=ach.icon,
                xp_bonus=ach.xp_bonus, secret=ach.secret, unlocked_at=unlocked_at,
            )
        )
    return out


@router.get("/skills", response_model=list[SkillOut])
def skills(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    owned_ids = set(db.scalars(select(UserSkill.skill_id).where(UserSkill.user_id == user.id)))
    catalog = db.scalars(select(Skill).order_by(Skill.tree, Skill.tier)).all()
    owned_codes = {s.code for s in catalog if s.id in owned_ids}
    return [
        SkillOut(
            code=s.code, tree=s.tree, tier=s.tier, name=s.name, description=s.description,
            cost=s.cost, parent_code=s.parent_code,
            owned=s.id in owned_ids,
            available=s.id not in owned_ids
            and (s.parent_code is None or s.parent_code in owned_codes)
            and user.skill_points >= s.cost,
        )
        for s in catalog
    ]


@router.post("/skills/{code}/unlock", response_model=SkillOut)
def unlock_skill(code: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    skill = db.scalar(select(Skill).where(Skill.code == code))
    if skill is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Skill not found")
    owned_ids = set(db.scalars(select(UserSkill.skill_id).where(UserSkill.user_id == user.id)))
    if skill.id in owned_ids:
        raise HTTPException(status.HTTP_409_CONFLICT, "Skill already unlocked")
    if skill.parent_code:
        parent = db.scalar(select(Skill).where(Skill.code == skill.parent_code))
        if parent is None or parent.id not in owned_ids:
            raise HTTPException(status.HTTP_409_CONFLICT, "Prerequisite skill not unlocked")
    if user.skill_points < skill.cost:
        raise HTTPException(status.HTTP_409_CONFLICT, "Not enough skill points")
    user.skill_points -= skill.cost
    db.add(UserSkill(user_id=user.id, skill_id=skill.id))
    db.commit()
    return SkillOut(
        code=skill.code, tree=skill.tree, tier=skill.tier, name=skill.name,
        description=skill.description, cost=skill.cost, parent_code=skill.parent_code,
        owned=True, available=False,
    )


@router.get("/collectibles", response_model=list[CollectibleOut])
def collectibles(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    rows = db.execute(
        select(UserCollectible, Collectible)
        .join(Collectible, Collectible.id == UserCollectible.collectible_id)
        .where(UserCollectible.user_id == user.id)
        .order_by(UserCollectible.acquired_at.desc())
    ).all()
    return [
        CollectibleOut(
            code=c.code, name=c.name, rarity=c.rarity, lore=c.lore,
            source=uc.source, acquired_at=uc.acquired_at,
        )
        for uc, c in rows
    ]
