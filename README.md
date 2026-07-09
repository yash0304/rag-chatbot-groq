# 🗺️ MindQuest — Your knowledge, made legend

**AI-first Second Brain × RPG progression.** Documents become territories on a world map,
tasks become quests, habits become daily missions, goals become story arcs — and an AI
Narrator answers questions from *your own* knowledge base with citations, generates quests,
and chronicles your week. All progression (XP, levels, skill trees, achievements,
collectibles) derives from real productivity and learning. The fantasy world is entirely
original — no copyrighted game assets, characters, music, or storylines.

[![CI](https://github.com/yash0304/rag-chatbot-groq/actions/workflows/ci.yml/badge.svg)](../../actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

> This repository began as a Streamlit RAG chatbot; that original app is preserved in
> [`legacy/streamlit-rag/`](legacy/streamlit-rag/). Everything else is the MindQuest platform.

---

## What's inside

| Piece | Stack | Path |
|---|---|---|
| API | FastAPI · SQLAlchemy 2 · PostgreSQL · Qdrant · Redis | [`backend/`](backend/) |
| Web app | Next.js 14 · TypeScript · Tailwind CSS | [`frontend/`](frontend/) |
| Android companion | Kotlin · Jetpack Compose | [`android/`](android/) |
| Infra | Docker Compose · GitHub Actions CI | [`docker-compose.yml`](docker-compose.yml) · [`.github/workflows/`](.github/workflows/) |
| AI | OpenAI **Responses API** / Groq / offline stub — one pluggable interface | [`backend/app/services/ai/`](backend/app/services/ai/) |

**Product features:** JWT auth (rotating refresh tokens) · document pipeline (extraction,
OCR fallback, chunking, AI summary/tags/domain, embeddings) · semantic search ·
citation-aware RAG chat · quests with difficulty-scaled XP · AI quest generation · habits
with streak multipliers · goals as story arcs · achievements, 4 skill trees, collectibles ·
knowledge-graph world map · analytics · AI weekly reviews · opt-in leaderboard ·
rate limiting, security headers, tenant isolation · 54 backend tests + frontend tests, all
runnable offline.

## Documentation (written before the code)

1. [Product Requirements (PRD)](docs/PRD.md)
2. [Architecture](docs/ARCHITECTURE.md)
3. [Database schema](docs/DATABASE_SCHEMA.md)
4. [API specification](docs/API_SPECIFICATION.md)
5. [Testing strategy](docs/TESTING_STRATEGY.md)
6. [Deployment guide](docs/DEPLOYMENT.md)
7. [Security checklist](docs/SECURITY.md)

## Quick start

### Full stack (Docker)

```bash
cp .env.example .env       # optionally add OPENAI_API_KEY / GROQ_API_KEY
docker compose up --build
# web http://localhost:3000 · API docs http://localhost:8000/docs
```

With no keys, `AI_PROVIDER=stub` runs the entire product offline with a deterministic
AI provider — ideal for trying the loop end to end.

### Local dev (no Docker)

```bash
# API — SQLite + in-memory vectors + stub AI by default
cd backend
python -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt -r requirements-dev.txt
uvicorn app.main:app --reload            # http://localhost:8000/docs

# Web
cd frontend && npm install && npm run dev # http://localhost:3000
```

### Tests

```bash
cd backend && pytest -q          # 54 tests, fully offline
cd frontend && npm run test && npm run typecheck && npm run build
```

## Switching AI providers

Set `AI_PROVIDER` to `openai` (Responses API + real embeddings), `groq`
(fast Llama chat; hashing-embedding fallback), or `stub` (offline). The interface lives in
`backend/app/services/ai/provider.py` — adding a provider means implementing `embed()` and
`complete()`.

## How progression works (the honest-XP rule)

Every XP point traces to a row in the append-only `xp_events` ledger, emitted only by real
actions: processing documents, completing quests, keeping streaks, finishing milestones,
consulting your knowledge (daily-capped), and weekly reviews. Levels follow
`xp(level) = 100·(level−1)^1.6`; level-ups grant skill points; achievements are declarative
rules over the same ledger. Nothing is purchasable.

## License

MIT — see [LICENSE](LICENSE).
