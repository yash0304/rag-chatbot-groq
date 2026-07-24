# HANDOFF — Mind Quest — 2026-07-21 (session 5)

> Overwrite at the end of every session. Must let any model resume in under 2 minutes.

## Where we are
- Offline Android app. Phases 0–2 merged to main. **Phase 3 (Documents/OCR/Search/Map) + docx
  support + Phase 4 (Narrator, Weekly Review, AI quest gen)** all on branch
  `claude/ai-second-brain-rpg-dv5o75` = **PR #23** (not yet merged; grew to cover Phase 3+4).
- Backlog: **18/20 done** (#1–#18). Only #19 (export/import) + #20 (real migrations, PIN) remain.
- GitHub issues: MQ-16/17/18 = issues #18/#19/#20 → closing now.

## What happened this session (Phase 4)
- `data/SettingsStore.kt`: encrypted Sarvam key + model + usage ledger.
- `domain/Sarvam.kt`: `SarvamClient` — `api-subscription-key` header, OpenAI-style body, endpoint
  `https://api.sarvam.ai/v1/chat/completions`, default model `sarvam-m`. Any failure →
  `SarvamException` so callers fall back offline. (VERIFY endpoint/model at docs.sarvam.ai if 4xx.)
- `domain/Narrator.kt`: prompts + pure helpers (citation strip/extract, JSON-array extraction,
  offline review narrative, offline quest template pool).
- DB v2→**v3**: `ChatMessageEntity`, `WeeklyReviewEntity` (+ ChatDao, ReviewDao). Destructive
  fallback still on → data resets again on this upgrade (pre-release).
- Repository: `sendNarratorMessage` (RAG: local search → Sarvam or retrieval-only, validated
  citations), `generateWeeklyReview` (idempotent per Monday-week; Sarvam or templated),
  `generateQuests` (Sarvam JSON or offline pool → draft quests), `acceptQuest`, chat/review
  observers + JSON (de)serialisers.
- UI: `ui/AiScreens.kt` (NarratorScreen, WeeklyReviewScreen, SettingsScreen); QuestsScreen gained
  a 🔮 Generate button + drafts section (Accept/Decline). Nav drawer now 13 destinations
  (added Narrator, Weekly Review, Settings).

## In-flight state
- All new Kotlin brace-balanced; **not compiler-verified**. Watch: Sarvam okhttp/serialization,
  `decodeFromString` reified inference in Repository, ML Kit (from Phase 3). AI failures are
  caught → offline fallback, never a crash.
- Last known good: committed & pushed; PR #23.

## Next action (starts next session)
- If Yash reports a compile error, fix first.
- Else Phase 5: #19 export/import all data (JSON + Markdown to share sheet; his data must outlive
  the app), then #20 release prep (replace `fallbackToDestructiveMigration` with real Room
  migrations 1→2→3; optional PIN/biometric lock). #20 is the LAST offline-app task.

## Open questions / waiting on Yash
- Does Phase 4 compile & run? Test offline first (no key): Narrator gives retrieval answers with
  citations; Weekly Review writes a templated chronicle; Quests → 🔮 Generate makes drafts.
- Then add the Sarvam key in Settings and retest Narrator for a generated answer.

## Not yet done (tracked, deferred)
- Move `backend/` + `frontend/` → `legacy/` (decision logged; still pending).
- PR #23 now bundles Phase 3+4 — consider merging to main before Phase 5.

## Build constraint
- No Kotlin compiler in cloud; each phase compiled by Yash in Android Studio. Small increments.
