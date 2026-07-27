from datetime import UTC, datetime

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.db.session import get_db
from app.models import Goal, GoalMilestone, User
from app.schemas.goal import GoalIn, GoalOut, MilestoneCompleteOut
from app.services import gamification
from app.services.narrator import arc_theme_for_goal

router = APIRouter(prefix="/goals", tags=["goals"])


@router.post("", response_model=GoalOut, status_code=status.HTTP_201_CREATED)
def create_goal(body: GoalIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    goal = Goal(
        user_id=user.id,
        title=body.title,
        narrative=body.narrative,
        arc_theme=arc_theme_for_goal(body.title, body.narrative),
    )
    db.add(goal)
    db.flush()
    for i, title in enumerate(body.milestones):
        db.add(GoalMilestone(goal_id=goal.id, seq=i, title=title[:255]))
    db.commit()
    db.refresh(goal)
    return goal


@router.get("", response_model=list[GoalOut])
def list_goals(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return db.scalars(
        select(Goal).where(Goal.user_id == user.id).order_by(Goal.created_at.desc())
    ).all()


def _owned_goal(db: Session, user: User, goal_id: str) -> Goal:
    goal = db.get(Goal, goal_id)
    if goal is None or goal.user_id != user.id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Goal not found")
    return goal


@router.get("/{goal_id}", response_model=GoalOut)
def get_goal(goal_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return _owned_goal(db, user, goal_id)


@router.post("/{goal_id}/milestones/{milestone_id}/complete", response_model=MilestoneCompleteOut)
def complete_milestone(
    goal_id: str,
    milestone_id: str,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    goal = _owned_goal(db, user, goal_id)
    milestone = db.get(GoalMilestone, milestone_id)
    if milestone is None or milestone.goal_id != goal.id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Milestone not found")
    if milestone.completed:
        raise HTTPException(status.HTTP_409_CONFLICT, "Milestone already completed")

    milestone.completed = True
    milestone.completed_at = datetime.now(UTC)
    result = gamification.award(db, user.id, "milestone_completed", ref_id=milestone.id)
    xp_awarded = result.xp_awarded
    achievements = list(result.achievements_unlocked)

    goal_completed = all(m.completed for m in goal.milestones)
    if goal_completed and goal.status == "active":
        goal.status = "completed"
        bonus = gamification.award(db, user.id, "goal_completed", ref_id=goal.id)
        xp_awarded += bonus.xp_awarded
        achievements.extend(bonus.achievements_unlocked)

    db.commit()
    db.refresh(goal)
    return MilestoneCompleteOut(
        goal=GoalOut.model_validate(goal),
        xp_awarded=xp_awarded,
        goal_completed=goal_completed,
        achievements_unlocked=achievements,
    )
