# Mind Quest — Decisions Log

Newest first. Format: `YYYY-MM-DD — [AREA] Decision. (Why.)`
Never delete a superseded decision — add a new dated line.

2026-07-20 — [ARCH] **Pivot to offline-first Android app.** The Android app becomes the
primary product and runs fully on-device with no backend server: all logic (auth,
gamification/XP, quests, habits, goals, documents, search) reimplemented in Kotlin against a
local Room/SQLite database. (Yash uses the phone as primary; profile ethos is "local-first,
his data must outlive any app." Removes the whole class of "can't reach server" problems.)

2026-07-20 — [AUTH] **Offline app uses a single local hero profile, not email/password/JWT.**
No server means no multi-user auth. First launch creates a local profile (hero name only);
optional device PIN/biometric lock is a later polish item. (A personal second brain on your
own phone doesn't need account login; it was pure friction — the source of the sign-in bugs.)

2026-07-20 — [SCOPE] **Leaderboard becomes "Personal Bests" offline.** A cross-user ranking
board is inherently online/multi-user; offline it's replaced by personal records (best streak,
level milestones, totals). (Keeps the screen useful without a server.)

2026-07-21 — [DB] **Phase 3 bumps DB to v2 (documents + chunks).** With destructive fallback,
existing local data (quests/habits/goals/XP from Phase 1–2 testing) resets ONCE on first Phase 3
launch. Accepted pre-release (test data only); real additive migrations come at MQ-20. A hand
migration was considered but skipped — a wrong migration crashes on launch, worse than a reset,
and there is no compiler in the cloud session to verify it.

2026-07-20 — [DB] **Room, pre-release: `fallbackToDestructiveMigration()`.** Schema still
evolves across phases; destructive fallback is acceptable while unreleased but MUST be replaced
with real migrations before the first release. (Skill rule: never ship destructive fallback.)

2026-07-20 — [AI] **Narrator + all generative AI use Sarvam AI** (Yash has a paid Sarvam API
subscription). On-device retrieval builds the context (RAG stays offline); Sarvam does the
generation. All Sarvam traffic goes through one `SarvamClient.kt` with a local credit ledger
and graceful offline fallback (per references/sarvam.md). Embeddings for search stay on-device
(hashing) — Sarvam is for chat/summary/quest-gen/weekly-review. Verify exact endpoint + model
IDs against docs.sarvam.ai at build time (Phase 4). (India-hosted, fits the project ethos; Yash
already pays for it.)

2026-07-20 — [ARCH] **Archive web + FastAPI backend; go Android-only.** `backend/` and
`frontend/` move under `legacy/` and stop being maintained; all new work is the offline Android
app. (Yash: "mostly I will use app only.")
