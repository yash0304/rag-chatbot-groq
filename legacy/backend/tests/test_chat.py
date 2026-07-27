from tests.conftest import upload_text

DOC_TEXT = (
    "The Treaty of Westphalia was signed in 1648, ending the Thirty Years War. "
    "It established the principle of state sovereignty in European politics. "
    "Historians consider Westphalia the beginning of the modern international system."
)


def _make_session(client) -> str:
    r = client.post("/api/v1/chat/sessions", json={"title": "History questions"})
    assert r.status_code == 201
    return r.json()["id"]


def test_chat_answers_with_citations(auth_client):
    upload_text(auth_client, DOC_TEXT, title="Westphalia Notes")
    session_id = _make_session(auth_client)
    r = auth_client.post(
        f"/api/v1/chat/sessions/{session_id}/messages",
        json={"content": "When was the Treaty of Westphalia signed?"},
    )
    assert r.status_code == 200
    msg = r.json()
    assert msg["role"] == "assistant"
    assert len(msg["citations"]) >= 1
    cite = msg["citations"][0]
    assert cite["title"] == "Westphalia Notes"
    assert f"[{cite['index']}]" in msg["content"]


def test_chat_with_empty_knowledge_base(auth_client):
    session_id = _make_session(auth_client)
    r = auth_client.post(
        f"/api/v1/chat/sessions/{session_id}/messages", json={"content": "What is in my notes?"}
    )
    assert r.status_code == 200
    assert r.json()["citations"] == []


def test_chat_history_persisted(auth_client):
    upload_text(auth_client, DOC_TEXT)
    session_id = _make_session(auth_client)
    auth_client.post(f"/api/v1/chat/sessions/{session_id}/messages", json={"content": "Question one?"})
    auth_client.post(f"/api/v1/chat/sessions/{session_id}/messages", json={"content": "Question two?"})
    messages = auth_client.get(f"/api/v1/chat/sessions/{session_id}/messages").json()
    assert [m["role"] for m in messages] == ["user", "assistant", "user", "assistant"]


def test_chat_session_isolation(auth_client, client):
    session_id = _make_session(auth_client)
    r = auth_client.post("/api/v1/chat/sessions", json={})
    assert r.status_code == 201
    # bogus session id -> 404
    r = auth_client.get("/api/v1/chat/sessions/00000000-0000-0000-0000-000000000000/messages")
    assert r.status_code == 404
    assert session_id  # created session listed
    sessions = auth_client.get("/api/v1/chat/sessions").json()
    assert any(s["id"] == session_id for s in sessions)


def test_chat_awards_knowledge_xp(auth_client):
    upload_text(auth_client, DOC_TEXT)
    before = auth_client.get("/api/v1/users/me").json()["xp"]
    session_id = _make_session(auth_client)
    auth_client.post(f"/api/v1/chat/sessions/{session_id}/messages", json={"content": "Westphalia?"})
    after = auth_client.get("/api/v1/users/me").json()["xp"]
    assert after > before
