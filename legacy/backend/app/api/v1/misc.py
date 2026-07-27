"""Graph, analytics, weekly reviews, leaderboard."""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.deps import get_current_user, rate_limit_ai
from app.core.config import get_settings
from app.db.session import get_db
from app.models import User, WeeklyReview
from app.schemas.gamification import LeaderboardEntry, WeeklyReviewOut
from app.services import analytics, gamification
from app.services.graph import build_graph
from app.services.narrator import weekly_review_narrative

router = APIRouter(tags=["graph, analytics & reviews"])


@router.get("/graph")
def knowledge_graph(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return build_graph(db, user.id)


@router.get("/analytics/summary")
def analytics_summary(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return analytics.summary(db, user)


@router.get("/analytics/xp-daily")
def analytics_xp_daily(
    days: int = 30, user: User = Depends(get_current_user), db: Session = Depends(get_db)
):
    return analytics.xp_daily(db, user.id, days=min(days, 180))


@router.get("/analytics/activity-heatmap")
def analytics_heatmap(
    weeks: int = 12, user: User = Depends(get_current_user), db: Session = Depends(get_db)
):
    return analytics.activity_heatmap(db, user.id, weeks=min(weeks, 52))


@router.post("/reviews/weekly", response_model=WeeklyReviewOut)
def weekly_review(user: User = Depends(rate_limit_ai), db: Session = Depends(get_db)):
    start = gamification.week_start()
    existing = db.scalar(
        select(WeeklyReview).where(WeeklyReview.user_id == user.id, WeeklyReview.week_start == start)
    )
    if existing is not None:
        return existing
    stats = analytics.week_stats(db, user, start)
    narrative = weekly_review_narrative(stats)
    review = WeeklyReview(
        user_id=user.id,
        week_start=start,
        stats=stats,
        narrative=narrative,
        suggestions=["Pick one avoided milestone and schedule it first thing next week."],
    )
    db.add(review)
    db.flush()  # assign review.id before it is used as the XP event ref
    gamification.award(db, user.id, "weekly_review", ref_id=review.id)
    db.commit()
    return review


@router.get("/reviews", response_model=list[WeeklyReviewOut])
def list_reviews(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return db.scalars(
        select(WeeklyReview)
        .where(WeeklyReview.user_id == user.id)
        .order_by(WeeklyReview.week_start.desc())
    ).all()


@router.get("/leaderboard", response_model=list[LeaderboardEntry])
def leaderboard(db: Session = Depends(get_db), _user: User = Depends(get_current_user)):
    if not get_settings().leaderboard_enabled:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Leaderboard disabled")
    rows = db.scalars(
        select(User)
        .where(User.leaderboard_opt_in.is_(True), User.hero_name.isnot(None))
        .order_by(User.xp.desc())
        .limit(100)
    ).all()
    return [LeaderboardEntry(hero_name=u.hero_name, level=u.level, xp=u.xp) for u in rows]
