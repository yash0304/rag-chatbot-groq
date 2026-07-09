from datetime import date

from sqlalchemy import Date, ForeignKey, Integer, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import GUID, Base, TimestampMixin, new_id


class Habit(Base, TimestampMixin):
    __tablename__ = "habits"

    id: Mapped[str] = mapped_column(GUID, primary_key=True, default=new_id)
    user_id: Mapped[str] = mapped_column(GUID, ForeignKey("users.id", ondelete="CASCADE"), index=True)
    title: Mapped[str] = mapped_column(String(255))
    cadence: Mapped[str] = mapped_column(String(10), default="daily")  # daily|weekdays|weekly
    streak: Mapped[int] = mapped_column(Integer, default=0)
    best_streak: Mapped[int] = mapped_column(Integer, default=0)
    last_checkin_date: Mapped[date | None] = mapped_column(Date)
    xp_base: Mapped[int] = mapped_column(Integer, default=15)


class HabitCheckin(Base, TimestampMixin):
    __tablename__ = "habit_checkins"
    __table_args__ = (UniqueConstraint("habit_id", "date", name="uq_habit_checkin_day"),)

    id: Mapped[str] = mapped_column(GUID, primary_key=True, default=new_id)
    habit_id: Mapped[str] = mapped_column(GUID, ForeignKey("habits.id", ondelete="CASCADE"), index=True)
    user_id: Mapped[str] = mapped_column(GUID, ForeignKey("users.id", ondelete="CASCADE"), index=True)
    date: Mapped[date] = mapped_column(Date)
    xp_awarded: Mapped[int] = mapped_column(Integer, default=0)
