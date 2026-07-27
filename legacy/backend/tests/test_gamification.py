import pytest

from app.services.gamification import level_for_xp, xp_required_for_level


def test_level_curve_monotonic():
    assert xp_required_for_level(1) == 0
    assert xp_required_for_level(2) == 100
    prev = -1
    for level in range(1, 30):
        needed = xp_required_for_level(level)
        assert needed > prev
        prev = needed


@pytest.mark.parametrize(
    ("xp", "expected"),
    [(0, 1), (99, 1), (100, 2), (302, 2), (303, 3), (10_000, 18)],
)
def test_level_for_xp(xp, expected):
    assert level_for_xp(xp) == expected


def test_profile_endpoint(auth_client):
    profile = auth_client.get("/api/v1/gamification/profile").json()
    assert profile["level"] == 1
    assert profile["xp_for_next_level"] == 100
    assert profile["progress_pct"] == 0.0


def _grind_to_skill_points(client, count=6):
    for i in range(count):
        q = client.post("/api/v1/quests", json={"title": f"Grind {i}", "difficulty": "hard"}).json()
        client.post(f"/api/v1/quests/{q['id']}/complete")


def test_skill_tree_rules(auth_client):
    skills = auth_client.get("/api/v1/gamification/skills").json()
    assert len(skills) == 12
    # no points yet -> nothing affordable
    assert all(not s["available"] for s in skills)
    r = auth_client.post("/api/v1/gamification/skills/scholar_1/unlock")
    assert r.status_code == 409  # not enough points

    _grind_to_skill_points(auth_client)
    me = auth_client.get("/api/v1/users/me").json()
    assert me["skill_points"] >= 3

    # child locked before parent even with points
    assert auth_client.post("/api/v1/gamification/skills/scholar_2/unlock").status_code == 409
    assert auth_client.post("/api/v1/gamification/skills/scholar_1/unlock").status_code == 200
    assert auth_client.post("/api/v1/gamification/skills/scholar_1/unlock").status_code == 409  # owned
    r = auth_client.post("/api/v1/gamification/skills/scholar_2/unlock")
    assert r.status_code == 200
    assert r.json()["owned"] is True


def test_xp_ledger(auth_client):
    q = auth_client.post("/api/v1/quests", json={"title": "Ledger", "difficulty": "easy"}).json()
    auth_client.post(f"/api/v1/quests/{q['id']}/complete")
    events = auth_client.get("/api/v1/gamification/xp-events").json()
    kinds = {e["kind"] for e in events}
    assert "quest_completed" in kinds
    assert "achievement_bonus" in kinds  # first_quest
    me = auth_client.get("/api/v1/users/me").json()
    assert me["xp"] == sum(e["amount"] for e in events)


def test_collectible_granted_with_achievement(auth_client):
    from tests.conftest import upload_text

    upload_text(auth_client, "A tome about ancient forests and their guardians. " * 5)
    collectibles = auth_client.get("/api/v1/gamification/collectibles").json()
    assert any(c["code"] == "ember_quill" for c in collectibles)  # first_light companion


def test_leaderboard_opt_in_only(auth_client, client):
    from tests.conftest import make_user

    tokens_b = make_user(client, "visible@example.com", "Vis")
    headers_b = {"Authorization": f"Bearer {tokens_b['access_token']}"}
    client.patch(
        "/api/v1/users/me", json={"hero_name": "Bright Banner", "leaderboard_opt_in": True},
        headers=headers_b,
    )
    board = auth_client.get("/api/v1/leaderboard").json()
    names = [e["hero_name"] for e in board]
    assert "Bright Banner" in names
    assert len(names) == 1  # auth_client user did not opt in
