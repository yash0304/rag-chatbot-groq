from tests.conftest import make_user, upload_text

LONG_TEXT = (
    "The mitochondria is the powerhouse of the cell. Cellular respiration converts glucose "
    "into adenosine triphosphate. Biology students study organelles and their functions. "
    "Photosynthesis in chloroplasts captures sunlight to build sugars."
)


def test_upload_and_process_txt(auth_client):
    doc = upload_text(auth_client, LONG_TEXT, title="Cell Biology Notes")
    assert doc["status"] == "processing"
    r = auth_client.get(f"/api/v1/documents/{doc['id']}")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "ready"
    assert body["summary"]
    assert body["domain"]
    assert body["chunk_count"] >= 1
    assert body["ocr_used"] is False
    assert len(body["tags"]) >= 1

    r = auth_client.get(f"/api/v1/documents/{doc['id']}/chunks")
    assert r.status_code == 200
    chunks = r.json()
    assert len(chunks) == body["chunk_count"]
    assert "mitochondria" in chunks[0]["text"]


def test_upload_awards_xp_and_achievement(auth_client):
    upload_text(auth_client, LONG_TEXT)
    me = auth_client.get("/api/v1/users/me").json()
    assert me["xp"] > 0  # upload + processed + first_light bonus
    achievements = auth_client.get("/api/v1/gamification/achievements").json()
    unlocked = {a["code"] for a in achievements if a["unlocked_at"]}
    assert "first_light" in unlocked


def test_unsupported_extension_rejected(auth_client):
    files = {"file": ("malware.exe", b"MZ", "application/octet-stream")}
    r = auth_client.post("/api/v1/documents", files=files)
    assert r.status_code == 400


def test_empty_file_fails_gracefully(auth_client):
    doc = upload_text(auth_client, "   ")
    body = auth_client.get(f"/api/v1/documents/{doc['id']}").json()
    assert body["status"] == "failed"
    assert body["error"]


def test_delete_document_removes_from_search(auth_client):
    doc = upload_text(auth_client, LONG_TEXT)
    r = auth_client.post("/api/v1/search", json={"query": "mitochondria powerhouse"})
    assert len(r.json()["results"]) > 0
    assert auth_client.delete(f"/api/v1/documents/{doc['id']}").status_code == 204
    r = auth_client.post("/api/v1/search", json={"query": "mitochondria powerhouse"})
    assert len(r.json()["results"]) == 0


def test_tenant_isolation(client):
    tokens_a = make_user(client, "a@example.com", "A")
    client.headers["Authorization"] = f"Bearer {tokens_a['access_token']}"
    doc = upload_text(client, LONG_TEXT)

    tokens_b = make_user(client, "b@example.com", "B")
    client.headers["Authorization"] = f"Bearer {tokens_b['access_token']}"
    assert client.get(f"/api/v1/documents/{doc['id']}").status_code == 404
    assert client.get("/api/v1/documents").json() == []
    results = client.post("/api/v1/search", json={"query": "mitochondria powerhouse"}).json()["results"]
    assert results == []
