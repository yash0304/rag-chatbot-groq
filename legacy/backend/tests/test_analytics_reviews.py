from tests.conftest import upload_text


def test_analytics_summary(auth_client):
    upload_text(auth_client, "Stoicism teaches focus on what you control. " * 10)
    q = auth_client.post("/api/v1/quests", json={"title": "Do", "difficulty": "easy"}).json()
    auth_client.post(f"/api/v1/quests/{q['id']}/complete")

    s = auth_client.get("/api/v1/analytics/summary").json()
    assert s["documents"] == 1
    assert s["quests_completed"] == 1
    assert s["xp_7d"] > 0
    assert s["xp_total"] == s["xp_7d"]


def test_xp_daily_series(auth_client):
    q = auth_client.post("/api/v1/quests", json={"title": "Do", "difficulty": "normal"}).json()
    auth_client.post(f"/api/v1/quests/{q['id']}/complete")
    series = auth_client.get("/api/v1/analytics/xp-daily", params={"days": 7}).json()
    assert len(series) == 8
    assert sum(p["xp"] for p in series) > 0


def test_knowledge_graph(auth_client):
    upload_text(auth_client, "Ancient Rome built aqueducts and roads across the empire. " * 5)
    graph = auth_client.get("/api/v1/graph").json()
    types = {n["type"] for n in graph["nodes"]}
    assert {"domain", "document", "tag"} <= types
    assert len(graph["edges"]) >= 2
    doc_nodes = [n for n in graph["nodes"] if n["type"] == "document"]
    assert len(doc_nodes) == 1


def test_weekly_review_idempotent(auth_client):
    q = auth_client.post("/api/v1/quests", json={"title": "Weekly", "difficulty": "easy"}).json()
    auth_client.post(f"/api/v1/quests/{q['id']}/complete")

    r1 = auth_client.post("/api/v1/reviews/weekly")
    assert r1.status_code == 200
    review = r1.json()
    assert review["narrative"]
    assert review["stats"]["quests_completed"] == 1

    r2 = auth_client.post("/api/v1/reviews/weekly")
    assert r2.json()["id"] == review["id"]  # same week -> same review

    listed = auth_client.get("/api/v1/reviews").json()
    assert len(listed) == 1


def test_health_endpoints(client):
    assert client.get("/healthz").json() == {"status": "ok"}
    ready = client.get("/readyz").json()
    assert ready["database"] is True and ready["vectors"] is True
