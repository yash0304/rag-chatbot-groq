# MindQuest — System Architecture

## 1. Overview

```
┌────────────────────────┐     ┌──────────────────────────┐
│  Next.js Web App (TS)  │     │  Android App (Kotlin)    │
│  App Router, Tailwind  │     │  Jetpack Compose         │
└───────────┬────────────┘     └───────────┬──────────────┘
            │  HTTPS/JSON (JWT bearer)     │
            ▼                              ▼
┌──────────────────────────────────────────────────────────┐
│                 FastAPI (backend/app)                    │
│  api/v1: auth, users, documents, search, chat, quests,   │
│  habits, goals, gamification, graph, analytics, reviews, │
│  leaderboard                                             │
│  ┌────────────────────────────────────────────────────┐  │
│  │ services: ai (provider abstraction), rag, ingestion│  │
│  │ gamification engine, quest generator, narrator,    │  │
│  │ knowledge graph, weekly review, rate limiter       │  │
│  └────────────────────────────────────────────────────┘  │
└──────┬───────────────┬───────────────┬───────────────────┘
       │               │               │
       ▼               ▼               ▼
 ┌──────────┐    ┌──────────┐    ┌──────────┐     ┌─────────────────────┐
 │PostgreSQL│    │  Qdrant  │    │  Redis   │     │ AI Provider          │
 │ (SoT)    │    │ (vectors)│    │(cache/rl)│     │ OpenAI Responses API │
 └──────────┘    └──────────┘    └──────────┘     │ | Groq | Local stub  │
                                                  └─────────────────────┘
```

- **PostgreSQL** is the source of truth (users, documents, chunks, XP events, quests…).
- **Qdrant** stores chunk embeddings; payload carries `{user_id, document_id, chunk_id}` for
  filtered per-user search. Deleting a document deletes its vectors.
- **Redis** backs rate limiting and hot caches (leaderboard, analytics). The code degrades
  to in-process fallbacks when Redis is absent (dev/test).
- **AI provider abstraction** (`services/ai`): one interface (`complete`, `embed`) with
  interchangeable backends — OpenAI (Responses API), Groq (OpenAI-compatible), and a
  deterministic local stub used in tests/dev so the entire product works offline.

## 2. Key flows

### 2.1 Document ingestion (async-ready pipeline)
1. `POST /documents` stores the file, creates a `Document(status=processing)` row.
2. Pipeline (in-process via FastAPI background task; drop-in swappable for a queue worker —
   the pipeline function is pure): extract text (pypdf / plain) → **OCR fallback**
   (pytesseract if installed) → chunk (≈1000 chars, 150 overlap, sentence-aware) →
   summarize + auto-tag (AI) → embed chunks → upsert to Qdrant → status `ready`.
3. Emits XP events `document_uploaded` and `document_processed`; may unlock achievements;
   updates the knowledge graph implicitly (tags/domains are relational data).

### 2.2 Citation-aware RAG chat
1. `POST /chat/{session}/messages` → embed query → Qdrant search (filter `user_id`, top-k=6).
2. Prompt assembly: numbered context blocks `[1]..[k]` + narrator persona + history window.
3. Model answers with inline `[n]` markers; the service maps markers back to chunk metadata
   and returns `citations: [{index, document_id, title, snippet, location}]`.
4. Guardrail: citations not present in the retrieved set are stripped; if retrieval is empty
   the narrator must say the archives hold no answer.
5. Emits `knowledge_consulted` XP event (rate-capped per day).

### 2.3 Gamification engine
- **Single write path:** `gamification.award(user, kind, ref)` inserts an `XPEvent`,
  recomputes level (`level = floor((xp/100) ** (1/1.6)) + 1` — published curve),
  grants skill points on level-up, and runs the **achievement rule table**
  (declarative: predicate over aggregates → unlock + bonus XP + optional collectible).
- Streaks: habit check-ins compute `streak = consecutive days`, XP multiplier
  `1 + min(streak, 30) * 0.05`, capped ×2.5.
- Idempotency: unique constraint (user, kind, ref_id, day) for daily-capped kinds.

### 2.4 AI narrator & quest generation
- `QuestGenerator` builds a context pack (active goals, recent docs/tags, level) and asks the
  provider for N structured quests (JSON schema enforced; stub provider returns deterministic
  fixtures). Generated quests are drafts the user accepts.
- `WeeklyReview` aggregates 7-day stats then asks the narrator for a review + next-arc hooks.

## 3. Backend layout

```
backend/
├── app/
│   ├── main.py            # app factory, middleware, router mounting
│   ├── core/              # config (pydantic-settings), security (JWT/PBKDF2), rate limit
│   ├── db/                # engine/session, portable GUID type, base
│   ├── models/            # SQLAlchemy 2.0 models (see DATABASE_SCHEMA.md)
│   ├── schemas/           # Pydantic request/response models
│   ├── api/v1/            # thin routers → services
│   ├── services/          # business logic (unit-testable, no HTTP types)
│   │   └── ai/            # provider.py (interface+factory), openai.py, groq.py, stub.py
│   └── workers/           # ingestion pipeline
├── alembic/               # migrations (autogenerate against models)
└── tests/                 # pytest; SQLite + stub AI + in-memory vectors
```

**Dependency rule:** api → services → (db, ai, vector). Services never import routers.

## 4. Frontend layout (Next.js App Router)

```
frontend/src/
├── app/(marketing)/page.tsx        # landing
├── app/(auth)/login, register
├── app/(app)/dashboard, documents, chat, quests, habits, goals,
│              map, skills, achievements, analytics, review, leaderboard
├── components/                     # QuestCard, XPBar, StatTile, GraphCanvas…
└── lib/                            # api client (fetch + refresh), auth store, xp math
```

- Auth: access token in memory + refresh token in `localStorage`; single-flight refresh.
- Knowledge graph rendered on `<canvas>` with a small force layout (no heavy graph deps).
- Theme: original "cartography & runes" design in Tailwind; dark-first.

## 5. Android app

Kotlin + Jetpack Compose scaffold (`android/`) consuming the same REST API: login,
quest hub, habit missions, character sheet. Shares no code with web; contract is the
OpenAPI spec. Built with Gradle; excluded from server CI (separate workflow builds it
when the Android SDK runner is configured).

## 6. Scaling & operations

- API is stateless → horizontal scale behind a load balancer; sticky sessions not needed.
- Ingestion pipeline is a pure function → lift into Celery/RQ worker + Redis broker when
  volume demands (interface already isolated in `workers/ingestion.py`).
- Qdrant collection per environment; HNSW defaults; payload index on `user_id`.
- Observability: structured JSON logs, `/healthz` (liveness) and `/readyz` (deps) endpoints.
- Backups: Postgres PITR; Qdrant snapshots nightly; object storage for raw files in prod
  (local disk volume in compose).

## 7. Configuration

Everything via env vars (12-factor): `DATABASE_URL`, `QDRANT_URL`, `REDIS_URL`,
`AI_PROVIDER=openai|groq|stub`, `OPENAI_API_KEY`, `GROQ_API_KEY`, `JWT_SECRET`, etc.
See `.env.example`. Test/dev defaults run fully offline (SQLite + stub + in-memory vectors).
