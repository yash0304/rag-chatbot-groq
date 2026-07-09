from datetime import UTC, datetime

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.db.session import get_db
from app.models import Habit, HabitCheckin, User
from app.schemas.habit import HabitCheckinOut, HabitIn, HabitOut
from app.services import gamification

router = APIRouter(prefix="/habits", tags=["habits"])


def _today():
    return datetime.now(UTC).date()


def _to_out(habit: Habit) -> HabitOut:
    out = HabitOut.model_validate(habit)
    out.checked_in_today = habit.last_checkin_date == _today()
    return out


@router.post("", response_model=HabitOut, status_code=status.HTTP_201_CREATED)
def create_habit(body: HabitIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    habit = Habit(user_id=user.id, title=body.title, cadence=body.cadence)
    db.add(habit)
    db.commit()
    return _to_out(habit)


@router.get("", response_model=list[HabitOut])
def list_habits(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    habits = db.scalars(
        select(Habit).where(Habit.user_id == user.id).order_by(Habit.created_at)
    ).all()
    return [_to_out(h) for h in habits]


def _owned_habit(db: Session, user: User, habit_id: str) -> Habit:
    habit = db.get(Habit, habit_id)
    if habit is None or habit.user_id != user.id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Habit not found")
    return habit


@router.post("/{habit_id}/checkin", response_model=HabitCheckinOut)
def checkin(habit_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    habit = _owned_habit(db, user, habit_id)
    today = _today()
    existing = db.scalar(
        select(HabitCheckin).where(HabitCheckin.habit_id == habit.id, HabitCheckin.date == today)
    )
    if existing is not None:
        raise HTTPException(status.HTTP_409_CONFLICT, "Already checked in today")

    habit.streak = gamification.compute_streak(habit, today)
    habit.best_streak = max(habit.best_streak, habit.streak)
    habit.last_checkin_date = today
    multiplier = gamification.streak_multiplier(habit.streak)
    xp = int(habit.xp_base * multiplier)
    db.add(HabitCheckin(habit_id=habit.id, user_id=user.id, date=today, xp_awarded=xp))
    result = gamification.award(
        db, user.id, "habit_checkin", amount=xp, ref_id=habit.id,
        meta={"streak": habit.streak, "multiplier": multiplier},
    )
    db.commit()
    return HabitCheckinOut(
        habit=_to_out(habit),
        xp_awarded=xp,
        multiplier=multiplier,
        level_up=result.level_up,
        achievements_unlocked=result.achievements_unlocked,
    )


@router.delete("/{habit_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_habit(habit_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    habit = _owned_habit(db, user, habit_id)
    db.delete(habit)
    db.commit()
