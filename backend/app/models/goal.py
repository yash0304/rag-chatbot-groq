from datetime import datetime

from sqlalchemy import Boolean, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import GUID, Base, TimestampMixin, new_id


class Goal(Base, TimestampMixin):
    __tablename__ = "goals"

    id: Mapped[str] = mapped_column(GUID, primary_key=True, default=new_id)
    user_id: Mapped[str] = mapped_column(GUID, ForeignKey("users.id", ondelete="CASCADE"), index=True)
    title: Mapped[str] = mapped_column(String(255))
    narrative: Mapped[str | None] = mapped_column(Text)
    arc_theme: Mapped[str | None] = mapped_column(String(120))
    status: Mapped[str] = mapped_column(String(12), default="active")  # active|completed|archived

    milestones: Mapped[list["GoalMilestone"]] = relationship(
        back_populates="goal", cascade="all, delete-orphan", order_by="GoalMilestone.seq"
    )


class GoalMilestone(Base, TimestampMixin):
    __tablename__ = "goal_milestones"

    id: Mapped[str] = mapped_column(GUID, primary_key=True, default=new_id)
    goal_id: Mapped[str] = mapped_column(GUID, ForeignKey("goals.id", ondelete="CASCADE"), index=True)
    seq: Mapped[int] = mapped_column(Integer)
    title: Mapped[str] = mapped_column(String(255))
    completed: Mapped[bool] = mapped_column(Boolean, default=False)
    completed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    goal: Mapped[Goal] = relationship(back_populates="milestones")
