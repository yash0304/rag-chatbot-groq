from datetime import date, timedelta

from app.models import Habit
from app.services.gamification import compute_streak, streak_multiplier


def test_checkin_awards_xp_once_per_day(auth_client):
    r = auth_client.post("/api/v1/habits", json={"title": "Morning reading", "cadence": "daily"})
    assert r.status_code == 201
    habit = r.json()
    r = auth_client.post(f"/api/v1/habits/{habit['id']}/checkin")
    assert r.status_code == 200
    body = r.json()
    assert body["habit"]["streak"] == 1
    assert body["habit"]["checked_in_today"] is True
    assert body["xp_awarded"] >= 15
    # same-day double check-in rejected
    assert auth_client.post(f"/api/v1/habits/{habit['id']}/checkin").status_code == 409


def test_streak_math():
    habit = Habit(cadence="daily", streak=5, last_checkin_date=date(2026, 7, 8))
    assert compute_streak(habit, date(2026, 7, 9)) == 6  # consecutive day
    assert compute_streak(habit, date(2026, 7, 11)) == 1  # missed a day -> reset

    weekly = Habit(cadence="weekly", streak=3, last_checkin_date=date(2026, 7, 1))
    assert compute_streak(weekly, date(2026, 7, 8)) == 4  # within 7-day window
    assert compute_streak(weekly, date(2026, 7, 20)) == 1

    fresh = Habit(cadence="daily", streak=0, last_checkin_date=None)
    assert compute_streak(fresh, date.today()) == 1


def test_streak_multiplier_curve():
    assert streak_multiplier(0) == 1.0
    assert streak_multiplier(10) == 1.5
    assert streak_multiplier(30) == 2.5
    assert streak_multiplier(300) == 2.5  # capped


def test_streak_multiplier_applied_in_checkin(auth_client, monkeypatch):
    r = auth_client.post("/api/v1/habits", json={"title": "练习", "cadence": "daily"})
    habit_id = r.json()["id"]
    # simulate an existing 9-day streak ending yesterday
    from app.db.session import SessionLocal

    db = SessionLocal()
    row = db.get(Habit, habit_id)
    row.streak = 9
    row.best_streak = 9
    row.last_checkin_date = date.today() - timedelta(days=1)
    db.commit()
    db.close()

    body = auth_client.post(f"/api/v1/habits/{habit_id}/checkin").json()
    assert body["habit"]["streak"] == 10
    assert body["multiplier"] == 1.5
    assert body["xp_awarded"] == int(15 * 1.5)


def test_week_of_iron_achievement(auth_client):
    r = auth_client.post("/api/v1/habits", json={"title": "Forge", "cadence": "daily"})
    habit_id = r.json()["id"]
    from app.db.session import SessionLocal

    db = SessionLocal()
    row = db.get(Habit, habit_id)
    row.streak = 6
    row.best_streak = 6
    row.last_checkin_date = date.today() - timedelta(days=1)
    db.commit()
    db.close()

    body = auth_client.post(f"/api/v1/habits/{habit_id}/checkin").json()
    unlocked = {a["code"] for a in body["achievements_unlocked"]}
    assert "week_of_iron" in unlocked
