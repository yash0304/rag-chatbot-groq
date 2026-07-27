def _create_goal(client):
    r = client.post(
        "/api/v1/goals",
        json={
            "title": "Learn Spanish",
            "narrative": "Conversational fluency by winter.",
            "milestones": ["Finish beginner course", "Hold a 10-minute conversation"],
        },
    )
    assert r.status_code == 201, r.text
    return r.json()


def test_goal_gets_arc_theme_and_milestones(auth_client):
    goal = _create_goal(auth_client)
    assert goal["arc_theme"]
    assert len(goal["milestones"]) == 2
    assert goal["milestones"][0]["seq"] == 0


def test_milestone_completion_and_goal_arc(auth_client):
    goal = _create_goal(auth_client)
    m1, m2 = goal["milestones"]

    r = auth_client.post(f"/api/v1/goals/{goal['id']}/milestones/{m1['id']}/complete")
    assert r.status_code == 200
    body = r.json()
    assert body["goal_completed"] is False
    assert body["xp_awarded"] == 40

    # double completion rejected
    assert auth_client.post(f"/api/v1/goals/{goal['id']}/milestones/{m1['id']}/complete").status_code == 409

    r = auth_client.post(f"/api/v1/goals/{goal['id']}/milestones/{m2['id']}/complete")
    body = r.json()
    assert body["goal_completed"] is True
    assert body["xp_awarded"] == 40 + 150  # milestone + goal bonus
    assert body["goal"]["status"] == "completed"
    unlocked = {a["code"] for a in body["achievements_unlocked"]}
    assert "arc_closer" in unlocked


def test_goal_isolation(auth_client, client):
    goal = _create_goal(auth_client)
    from tests.conftest import make_user

    tokens_b = make_user(client, "intruder@example.com", "Intruder")
    r = client.get(
        f"/api/v1/goals/{goal['id']}", headers={"Authorization": f"Bearer {tokens_b['access_token']}"}
    )
    assert r.status_code == 404
