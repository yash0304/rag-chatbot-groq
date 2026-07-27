# 🗺️ MindQuest — Your knowledge, made legend

**AI-first Second Brain × RPG progression.** Documents become territories on a world map,
tasks become quests, habits become daily missions, goals become story arcs — and an AI
Narrator answers questions from *your own* knowledge base with citations, generates quests,
and chronicles your week. All progression (XP, levels, skill trees, achievements,
collectibles) derives from real productivity and learning. The fantasy world is entirely
original — no copyrighted game assets, characters, music, or storylines.

[![CI](https://github.com/yash0304/rag-chatbot-groq/actions/workflows/ci.yml/badge.svg)](../../actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

> **The active product is the fully offline Android app** in [`android/`](android/) — Kotlin +
> Jetpack Compose + Room, no server required. The original web stack (FastAPI + Next.js) is
> **archived** under [`legacy/`](legacy/) and no longer maintained (see `docs/DECISIONS.md`).
> The repo also began as a Streamlit RAG demo, preserved in [`legacy/streamlit-rag/`](legacy/streamlit-rag/).

---

## What's inside

| Piece | Stack | Path |
|---|---|---|
| **Android app (active)** | Kotlin · Jetpack Compose · Room (on-device) | [`android/`](android/) |
| AI (optional) | Sarvam AI for chat / reviews / quest-gen; on-device hashing embeddings + ML Kit OCR | [`android/app/src/main/java/com/mindquest/app/domain/`](android/) |
| _Archived_ web API | FastAPI · PostgreSQL · Qdrant · Redis | [`legacy/backend/`](legacy/backend/) |
| _Archived_ web app | Next.js 14 · TypeScript · Tailwind | [`legacy/frontend/`](legacy/frontend/) |

**Android app (offline, feature-complete):** local hero profile · dashboard · quests with
difficulty-scaled XP · habits with streak multipliers · goals as story arcs · 4 skill trees ·
achievements + collectibles · analytics + activity heatmap · personal bests · document import
with on-device OCR (ML Kit) · offline semantic search · knowledge-graph world map · citation-aware
Narrator (retrieval offline, Sarvam when keyed) · weekly review · AI quest generation · full
data export/import · optional PIN lock. Progression math is a direct Kotlin port of the archived
backend, so numbers match. See [`android/README.md`](android/README.md) and
[`docs/BACKLOG_v0.1_offline.md`](docs/BACKLOG_v0.1_offline.md).

## Run the Android app

Open the [`android/`](android/) folder in Android Studio, let Gradle sync, and Run ▶ on an
emulator or device. First launch asks for a hero name — then everything is offline, on-device.
No server, no Docker, no login. To enable the AI Narrator/reviews/quest-generation, add your
**Sarvam API key** in the app's Settings; without it those features fall back to offline
retrieval/templates. Full guide: [`android/README.md`](android/README.md).

## Project docs

- Offline app: [`docs/BACKLOG_v0.1_offline.md`](docs/BACKLOG_v0.1_offline.md) ·
  [`docs/DECISIONS.md`](docs/DECISIONS.md) · [`docs/HANDOFF.md`](docs/HANDOFF.md)
- Archived web stack (historical): [`docs/PRD.md`](docs/PRD.md),
  [`ARCHITECTURE.md`](docs/ARCHITECTURE.md), [`DATABASE_SCHEMA.md`](docs/DATABASE_SCHEMA.md),
  [`API_SPECIFICATION.md`](docs/API_SPECIFICATION.md), [`DEPLOYMENT.md`](docs/DEPLOYMENT.md),
  [`SECURITY.md`](docs/SECURITY.md) — these describe the FastAPI/Next.js app now under `legacy/`.

## Archived web stack

The original web app + API still run from [`legacy/`](legacy/):
`cp legacy/.env.example legacy/.env && docker compose -f legacy/docker-compose.yml up --build`
(web on :3000, API on :8000). It is no longer maintained — the Android app is the product.

## How progression works (the honest-XP rule)

Every XP point traces to a row in the append-only `xp_events` ledger, emitted only by real
actions: processing documents, completing quests, keeping streaks, finishing milestones,
consulting your knowledge (daily-capped), and weekly reviews. Levels follow
`xp(level) = 100·(level−1)^1.6`; level-ups grant skill points; achievements are declarative
rules over the same ledger. Nothing is purchasable.

## License

MIT — see [LICENSE](LICENSE).
