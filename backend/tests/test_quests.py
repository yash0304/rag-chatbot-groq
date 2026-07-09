def _create_quest(client, difficulty="normal", title="Slay the inbox"):
    r = client.post("/api/v1/quests", json={"title": title, "difficulty": difficulty})
    assert r.status_code == 201, r.text
    return r.json()


def test_create_and_complete_quest(auth_client):
    quest = _create_quest(auth_client, "hard")
    assert quest["xp_reward"] == 100
    r = auth_client.post(f"/api/v1/quests/{quest['id']}/complete")
    assert r.status_code == 200
    body = r.json()
    assert body["xp_awarded"] == 100
    assert body["quest"]["status"] == "completed"
    unlocked = {a["code"] for a in body["achievements_unlocked"]}
    assert "first_quest" in unlocked


def test_complete_is_idempotent_guarded(auth_client):
    quest = _create_quest(auth_client)
    assert auth_client.post(f"/api/v1/quests/{quest['id']}/complete").status_code == 200
    assert auth_client.post(f"/api/v1/quests/{quest['id']}/complete").status_code == 409
    me = auth_client.get("/api/v1/users/me").json()
    # 50 quest XP + 20 first_quest bonus, exactly once
    assert me["xp"] == 70


def test_epic_quest_unlocks_epic_slayer(auth_client):
    quest = _create_quest(auth_client, "epic")
    body = auth_client.post(f"/api/v1/quests/{quest['id']}/complete").json()
    unlocked = {a["code"] for a in body["achievements_unlocked"]}
    assert "epic_slayer" in unlocked


def test_level_up_grants_skill_points(auth_client):
    for i in range(3):
        quest = _create_quest(auth_client, "hard", title=f"Quest {i}")
        auth_client.post(f"/api/v1/quests/{quest['id']}/complete")
    me = auth_client.get("/api/v1/users/me").json()
    assert me["level"] >= 2
    assert me["skill_points"] >= 1


def test_ai_quest_generation_flow(auth_client):
    r = auth_client.post("/api/v1/quests/generate", json={"count": 3})
    assert r.status_code == 200
    drafts = r.json()
    assert len(drafts) == 3
    assert all(q["status"] == "draft" and q["source"] == "ai" for q in drafts)
    quest_id = drafts[0]["id"]
    assert auth_client.post(f"/api/v1/quests/{quest_id}/accept").json()["status"] == "active"
    assert auth_client.post(f"/api/v1/quests/{quest_id}/complete").status_code == 200


def test_abandon_and_immutable_completed(auth_client):
    quest = _create_quest(auth_client)
    assert auth_client.delete(f"/api/v1/quests/{quest['id']}").status_code == 204
    listed = auth_client.get("/api/v1/quests", params={"status_filter": "abandoned"}).json()
    assert len(listed) == 1
    assert auth_client.post(f"/api/v1/quests/{quest['id']}/complete").status_code == 409

    done = _create_quest(auth_client, title="Done deal")
    auth_client.post(f"/api/v1/quests/{done['id']}/complete")
    r = auth_client.patch(f"/api/v1/quests/{done['id']}", json={"title": "rewrite history"})
    assert r.status_code == 409


def test_difficulty_change_rescales_xp(auth_client):
    quest = _create_quest(auth_client, "easy")
    r = auth_client.patch(f"/api/v1/quests/{quest['id']}", json={"difficulty": "epic"})
    assert r.json()["xp_reward"] == 250
