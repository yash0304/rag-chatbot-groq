# MindQuest — Product Requirements Document (PRD)

**Version:** 1.0 · **Status:** Approved for build · **Owner:** Product

---

## 1. Vision

MindQuest is an **AI-first Second Brain** fused with an **RPG progression system**.
Users capture knowledge (documents, notes) and manage real life (tasks, habits, goals);
the product turns that reality into an original fantasy adventure:

| Real life | In MindQuest |
|---|---|
| Knowledge base | **World map** (regions = knowledge domains) |
| Uploaded document | **Discovered territory / lore tome** |
| Task | **Quest** |
| Habit | **Daily mission** |
| Goal | **Story arc** (multi-chapter quest line) |
| Productivity & learning | **XP, levels, skills, achievements, collectibles** |
| AI assistant | **The Narrator** — quest generator, coach, and citation-aware sage |

All fantasy content (names, lore, art direction) is **original** — no copyrighted game
assets, characters, music, or storylines are used or imitated.

## 2. Problem

- Notes apps store knowledge but don't make it *usable* (no semantic recall, no synthesis).
- Task/habit apps have weak retention: no intrinsic motivation loop.
- Gamified apps use shallow, arbitrary points detached from real value creation.

**MindQuest's bet:** progression that is *derived from verifiable productive activity*
(documents processed, quests completed, streaks kept, goals advanced) is durable motivation,
and a RAG-grounded AI narrator makes the knowledge base feel alive.

## 3. Target users & personas

1. **The Scholar** (student / lifelong learner): uploads course PDFs, wants semantic search,
   citation-backed answers, and visible learning progress.
2. **The Builder** (knowledge worker / indie hacker): manages projects as story arcs,
   tasks as quests, wants weekly AI reviews.
3. **The Habit-Forger**: cares about streaks, daily missions, and the dopamine loop —
   the RPG layer is the primary draw.

## 4. Goals & success metrics

| Goal | Metric | Target (6 mo post-launch) |
|---|---|---|
| Activation | % new users who upload a doc AND complete a quest in week 1 | ≥ 40% |
| Retention | D30 retention | ≥ 25% |
| Engagement | Median daily missions completed / active user | ≥ 2 |
| AI value | % chat answers with ≥1 citation clicked | ≥ 30% |
| Reliability | API p95 latency (non-AI endpoints) | < 300 ms |

## 5. Scope

### 5.1 MVP (this build)

**Second Brain**
- Document upload (PDF, TXT, MD, images) with pipeline: extraction → OCR fallback →
  chunking → summarization → auto-tagging → embeddings → vector index (Qdrant).
- Semantic search across the knowledge base.
- **Citation-aware RAG chat**: every answer cites source chunks (doc title + location).
- Knowledge graph API + visualization (documents ↔ tags ↔ domains).

**RPG layer**
- XP events derived from real actions; level curve; character sheet.
- Quests (CRUD + AI generation from goals/documents), difficulty → XP.
- Habits as daily missions with streaks and streak-multiplier XP.
- Goals as story arcs with milestones (chapters).
- Achievements (rule-based), skill trees (Scholar / Explorer / Strategist / Forger),
  collectibles awarded by the Narrator.
- Optional leaderboard (opt-in, privacy-safe display names).

**Platform**
- Auth (email/password, JWT access+refresh), user profiles.
- Dashboard, analytics (XP over time, activity heatmap, domain distribution).
- Weekly review: aggregated stats + AI coach narrative.
- Responsive Next.js web app; Android companion app (Kotlin/Compose scaffold consuming the same API).
- Production-grade: tests, CI/CD, Docker, security hardening, docs.

### 5.2 Post-MVP (roadmap)
- OAuth (Google/GitHub), teams/guilds, shared campaigns.
- Browser clipper, email-in ingestion, audio transcription.
- Marketplace of quest templates; seasonal events.
- Native mobile parity (offline mode, push notifications).

### 5.3 Out of scope
- Real-money economy, PvP, user-generated public content moderation.

## 6. Functional requirements (numbered)

- **FR-1** Users can register, log in, refresh tokens, and manage their profile.
- **FR-2** Users can upload documents ≤ 25 MB; pipeline produces text, summary, tags, embeddings.
- **FR-3** Scanned PDFs/images run through OCR when native text extraction is empty.
- **FR-4** Semantic search returns ranked chunks with document metadata in < 1.5 s p95.
- **FR-5** RAG chat answers include inline citations `[n]` mapped to source chunks; the model must refuse to fabricate sources.
- **FR-6** Every qualifying action emits an XP event; XP → level via a published curve; level-ups are surfaced.
- **FR-7** Quests support manual creation and AI generation; completing a quest awards XP by difficulty.
- **FR-8** Habits support scheduled recurrence; a check-in awards XP × streak multiplier; missing a day resets streak.
- **FR-9** Goals contain ordered milestones; milestone completion advances the story arc and awards XP.
- **FR-10** Achievements unlock automatically from rules evaluated on XP events.
- **FR-11** Skill trees allocate skill points earned on level-up; skills give cosmetic/QoL perks (never pay-to-win).
- **FR-12** Weekly review aggregates the last 7 days and generates an AI narrative + next-week quest suggestions.
- **FR-13** Knowledge graph endpoint returns nodes (documents, tags, domains) and weighted edges.
- **FR-14** Leaderboard is opt-in; users appear under a chosen alias only.
- **FR-15** AI provider is pluggable (OpenAI Responses API, Groq, local stub) via configuration.

## 7. Non-functional requirements

- **Security:** OWASP ASVS L2 intent — hashed passwords (PBKDF2-SHA256, 600k iters), JWT with short-lived access tokens, rate limiting, input validation, no secrets in code, CORS allowlist, file-type validation.
- **Privacy:** user data isolated per account; deletion cascades including vectors; AI calls carry no PII beyond the user's own content.
- **Performance:** non-AI p95 < 300 ms; ingestion async; vector search p95 < 500 ms.
- **Availability:** stateless API, horizontal scaling; 99.5% target.
- **Portability:** Docker Compose for dev; images deployable to any container platform.
- **Testability:** deterministic AI stub provider so the full loop is testable offline in CI.

## 8. Monetization (SaaS)

- **Free:** 50 documents, 100 AI messages/mo, core RPG.
- **Adventurer ($8/mo):** 1,000 docs, 2,000 AI messages, weekly reviews, knowledge graph.
- **Guildmaster ($20/mo):** unlimited docs, priority models, API access, leaderboard guilds.
- Enforced via `plan` field + usage counters (billing integration is post-MVP; Stripe-ready fields exist).

## 9. Original theme guardrails (legal)

- All proper nouns, item names, lore text are generated/authored for MindQuest.
- Art direction: abstract geometric "cartography & runes" style, produced in-house (CSS/SVG).
- The Narrator's system prompt forbids referencing existing game franchises.

## 10. Release criteria

- All FR-1..FR-15 implemented and covered by automated tests.
- CI green: backend lint+tests, frontend lint+typecheck+build.
- `docker compose up` yields a working full stack with seeded demo data.
- Security checklist (docs/SECURITY.md) passes.
