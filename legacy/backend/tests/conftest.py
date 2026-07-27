import os
import tempfile

_tmp = tempfile.mkdtemp(prefix="mindquest-test-")
os.environ.update(
    {
        "ENVIRONMENT": "test",
        "DATABASE_URL": f"sqlite:///{_tmp}/test.db",
        "AI_PROVIDER": "stub",
        "VECTOR_BACKEND": "memory",
        "REDIS_URL": "",
        "STORAGE_DIR": f"{_tmp}/uploads",
        "JWT_SECRET": "test-secret-not-for-production",
        "RATE_LIMIT_GLOBAL": "100000",
        "RATE_LIMIT_AI": "100000",
        "RATE_LIMIT_AUTH": "100000",
    }
)

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from app.core import ratelimit  # noqa: E402
from app.db.base import Base  # noqa: E402
from app.db.session import SessionLocal, engine  # noqa: E402
from app.main import app  # noqa: E402
from app.services.ai.provider import reset_provider  # noqa: E402
from app.services.gamification import seed_catalogs  # noqa: E402
from app.services.vector import reset_vector_store  # noqa: E402


@pytest.fixture(autouse=True)
def fresh_state():
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    db = SessionLocal()
    seed_catalogs(db)
    db.close()
    reset_vector_store()
    reset_provider()
    ratelimit.reset_local()
    yield


@pytest.fixture
def client():
    with TestClient(app) as c:
        yield c


def make_user(client: TestClient, email: str = "hero@example.com", name: str = "Hero") -> dict:
    r = client.post(
        "/api/v1/auth/register",
        json={"email": email, "password": "swordfish123", "display_name": name},
    )
    assert r.status_code == 201, r.text
    r = client.post("/api/v1/auth/login", json={"email": email, "password": "swordfish123"})
    assert r.status_code == 200, r.text
    return r.json()


@pytest.fixture
def auth_client(client):
    tokens = make_user(client)
    client.headers["Authorization"] = f"Bearer {tokens['access_token']}"
    client.tokens = tokens
    return client


def upload_text(client, content: str, filename: str = "notes.txt", title: str | None = None):
    files = {"file": (filename, content.encode(), "text/plain")}
    data = {"title": title} if title else {}
    r = client.post("/api/v1/documents", files=files, data=data)
    assert r.status_code == 202, r.text
    return r.json()
