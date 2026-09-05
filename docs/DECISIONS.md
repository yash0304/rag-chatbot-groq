# Mind Quest — Decisions Log

Newest first. Format: `YYYY-MM-DD — [AREA] Decision. (Why.)`
Never delete a superseded decision — add a new dated line.

2026-09-05 — [BUILD] **APKs are built by GitHub Actions; sideload build type is `.ci`.** Yash was
travelling with no laptop and needed to update the app from his phone. A CI-built APK can never
install over an Android-Studio build (different signing key → `INSTALL_FAILED_UPDATE_INCOMPATIBLE`,
and the only way through is uninstalling, which wipes data). So the CI build carries
`applicationIdSuffix = ".ci"` and installs as a *separate* app: the existing install is never at
risk, and data moves across via the app's own JSON export/import. The workflow publishes a Release
so the APK is a direct tap-to-install link on the phone. (Side benefit, and a big one: this is the
first real Kotlin compiler in the loop — previously nothing was compile-verified in the cloud
session, only brace-balanced. CI now catches compile errors before Yash ever pulls.)

2026-09-05 — [SECURITY] **Signing key lives in GitHub Actions secrets, never in the repo.** The
repo is public, so a committed keystore would let anyone sign an "update" to this app. Two secrets:
`SIDELOAD_KEYSTORE_BASE64` + `SIDELOAD_KEYSTORE_PASSWORD`. Local builds fall back to the debug key,
so nothing about the Android Studio workflow changes. Without the secrets CI still compiles but
publishes no Release — a throwaway key would produce an APK that can never be updated in place.

2026-09-05 — [BUILD] **Gradle pinned 9.0-milestone-1 → 8.9.** AGP 8.5.2 does not support a Gradle 9
milestone; it would have broken the CI build and was a latent local hazard too.

2026-09-05 — [SCOPE] **Errands are notes, not documents — new Inbox screen (MQ-26).** Yash asked
for a chat window where short lines ("call the plumber", "milk, eggs, rice") get captured and can
alert him on a date. Rather than pushing every scribble through the document pipeline (OCR →
chunk → embed → World Map), quick capture gets its own lightweight `notes` table and a chat-style
Inbox. A note can then *graduate*: "→ Quest" makes it a real quest that pays XP, "→ Archive" runs
it through the document pipeline so it becomes searchable and shows on the map. (Keeps the
Archives meaningful — grocery lists would otherwise pollute search and the knowledge graph —
while making the fast path for a passing thought one tap.)

2026-09-05 — [TECH] **Reminders use WorkManager, not AlarmManager.** A note's reminder is a
`OneTimeWorkRequest` tagged `note-reminder-<id>`, so it survives app death and reboot without a
`RECEIVE_BOOT_COMPLETED` receiver, and cancels cleanly when the note is completed or deleted.
Exact-alarm permissions (`SCHEDULE_EXACT_ALARM`) are deliberately avoided — a reminder that fires
within a few minutes of its time is fine for errands, and Play Store policy on exact alarms is
restrictive. Notifications need runtime `POST_NOTIFICATIONS` on API 33+; the Inbox asks the first
time a reminder is set, and a missing permission degrades to a silent no-op rather than a crash.

2026-09-05 — [DB] **DB v4 with additive `MIGRATION_3_4` (notes table).** No destructive fallback —
Yash now has real data on the device. Notes are included in the JSON export/import bundle so they
outlive the app like everything else.

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

2026-07-21 — [ARCH] **Web stack archived to `legacy/`; voice capture added.** Moved
`backend/`, `frontend/`, `docker-compose.yml`, `Makefile`, `.env.example` under `legacy/`
(CI + README updated to the new paths). Added Sarvam **speech-to-text voice notes** (record WAV
on-device → Saarika STT → import as a document) and fixed the Android-15 edge-to-edge status-bar
overlap (`statusBarsPadding` on top bar + drawer). Voice needs a Sarvam key; STT endpoint
`https://api.sarvam.ai/speech-to-text`, model `saarika:v2` — verify at docs.sarvam.ai if it 4xx.

2026-07-21 — [DB] **Real Room migrations replace destructive fallback (MQ-20).** Added additive
`MIGRATION_1_2` (documents+chunks) and `MIGRATION_2_3` (chat+reviews); builder now uses
`addMigrations(...)` + `fallbackToDestructiveMigrationOnDowngrade()`. Upgrades preserve data.
(Yash's device is already v3, so these are inert for him but correct for any older install and
for the record — no more data resets on future additive schema changes.)

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
