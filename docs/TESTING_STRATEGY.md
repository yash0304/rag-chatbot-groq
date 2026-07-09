# MindQuest — Testing Strategy

## Principles
1. **The whole product loop must run offline.** The `stub` AI provider is deterministic,
   the vector store has an in-memory implementation, and tests use SQLite — so CI needs no
   keys, no network, no services.
2. **Test behavior through the API**, unit-test the tricky pure logic (XP curve, streak
   multiplier, citation mapping, chunking).
3. **Every FR in the PRD maps to at least one test.**

## Layers

| Layer | Tooling | Scope |
|---|---|---|
| Backend unit | pytest | XP curve, streak math, chunker, citation extraction, achievement rules |
| Backend API | pytest + FastAPI TestClient | auth flows, documents pipeline, search, RAG chat citations, quests/habits/goals XP, achievements, skills, analytics, reviews, leaderboard, tenant isolation |
| Frontend | vitest | xp/level math, API client behavior |
| Frontend build | `tsc --noEmit` + `next build` in CI | type safety & build health |
| E2E (staging) | Playwright (post-MVP gate) | register → upload → chat → quest loop |
| Security | pip-audit / npm audit in CI (non-blocking report), secret scan | dependencies & leaks |

## Backend test fixtures (`backend/tests/conftest.py`)
- Fresh SQLite DB per test (`create_all`), dependency-override of `get_db`.
- `AI_PROVIDER=stub`, `VECTOR_BACKEND=memory`, rate limits relaxed.
- `auth_client` fixture: registered+logged-in `TestClient` with bearer header.

## Critical scenarios (implemented)
- **Auth:** register/login/refresh rotation/revoked-refresh rejection/wrong password.
- **Tenant isolation:** user B cannot read user A's documents, quests, or search results.
- **Ingestion:** upload txt + pdf → status `ready`, summary/tags/chunks present; failed
  extraction → status `failed` with error.
- **RAG:** answer cites only retrieved chunks; empty knowledge base → graceful "no sources".
- **Gamification:** quest completion awards difficulty XP exactly once (idempotent);
  habit double check-in → 409; streak multiplier applied; level-up grants skill points;
  achievements unlock on thresholds; skill unlock enforces cost + parent.
- **Weekly review:** aggregates and is idempotent per week.

## CI (GitHub Actions, `.github/workflows/ci.yml`)
1. `backend`: ruff lint + pytest (matrix: 3.11).
2. `frontend`: npm ci, eslint, `tsc --noEmit`, vitest, `next build`.
3. `docker`: compose config validation.
Branch protection: all jobs must pass to merge.

## Quality gates
- New endpoints require API tests in the same PR.
- Coverage watermark: backend ≥ 80% lines on services (`pytest --cov`).
