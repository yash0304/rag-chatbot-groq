from tests.conftest import make_user


def test_register_login_me(client):
    tokens = make_user(client)
    r = client.get("/api/v1/users/me", headers={"Authorization": f"Bearer {tokens['access_token']}"})
    assert r.status_code == 200
    body = r.json()
    assert body["email"] == "hero@example.com"
    assert body["level"] == 1 and body["xp"] == 0


def test_duplicate_email_rejected(client):
    make_user(client)
    r = client.post(
        "/api/v1/auth/register",
        json={"email": "HERO@example.com", "password": "swordfish123", "display_name": "Again"},
    )
    assert r.status_code == 409


def test_wrong_password_rejected(client):
    make_user(client)
    r = client.post("/api/v1/auth/login", json={"email": "hero@example.com", "password": "wrong-pass"})
    assert r.status_code == 401


def test_short_password_rejected(client):
    r = client.post(
        "/api/v1/auth/register",
        json={"email": "x@example.com", "password": "short", "display_name": "X"},
    )
    assert r.status_code == 422


def test_refresh_rotation(client):
    tokens = make_user(client)
    r = client.post("/api/v1/auth/refresh", json={"refresh_token": tokens["refresh_token"]})
    assert r.status_code == 200
    new_tokens = r.json()
    assert new_tokens["refresh_token"] != tokens["refresh_token"]
    # old refresh token is single-use
    r = client.post("/api/v1/auth/refresh", json={"refresh_token": tokens["refresh_token"]})
    assert r.status_code == 401
    # new one works
    r = client.post("/api/v1/auth/refresh", json={"refresh_token": new_tokens["refresh_token"]})
    assert r.status_code == 200


def test_logout_revokes_refresh(client):
    tokens = make_user(client)
    r = client.post("/api/v1/auth/logout", json={"refresh_token": tokens["refresh_token"]})
    assert r.status_code == 204
    r = client.post("/api/v1/auth/refresh", json={"refresh_token": tokens["refresh_token"]})
    assert r.status_code == 401


def test_protected_route_requires_token(client):
    assert client.get("/api/v1/users/me").status_code == 401
    assert client.get("/api/v1/users/me", headers={"Authorization": "Bearer garbage"}).status_code == 401


def test_update_profile(client):
    tokens = make_user(client)
    headers = {"Authorization": f"Bearer {tokens['access_token']}"}
    r = client.patch(
        "/api/v1/users/me",
        json={"hero_name": "Thalor of the Quiet Vale", "leaderboard_opt_in": True},
        headers=headers,
    )
    assert r.status_code == 200
    assert r.json()["hero_name"] == "Thalor of the Quiet Vale"
    assert r.json()["leaderboard_opt_in"] is True
