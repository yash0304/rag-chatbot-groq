# Mind Quest — Offline Android Backlog v0.1

Rebuild the Android app as a fully offline, on-device app (Kotlin + Jetpack Compose + Room).
Checked box = shipped & runs on device. Build in phases; verify each in Android Studio before
moving on (no compiler in the cloud session, so each phase must compile on Yash's machine).

Legend: screens marked ✅ already exist in the web app and are being ported to on-device logic.

## Phase 0 — Offline foundation (no UI yet)
- [x] #1 Room database: entities + DAOs for profile, xp_events, quests, habits, goals,
      milestones, documents, chunks, tags, achievements, skills, collectibles, chat.
- [x] #2 Port gamification engine to Kotlin (level curve, streak multiplier, award path,
      achievement rules) — direct port of `backend/app/services/gamification.py`.
- [x] #3 Idempotent catalog seeding (achievements, skills, collectibles) on first launch.
- [x] #4 Local hero profile + first-run onboarding (hero name); replace login/register screens.

## Phase 1 — Daily-use screens
- [x] #5 Dashboard ✅ (level, XP bar, today's missions, active quests, streaks).
- [x] #6 Quests ✅ (create, difficulty→XP, complete, abandon; AI-generate deferred to Phase 4).
- [x] #7 Habits / Daily Missions ✅ (create, check-in, streak multiplier, 1/day guard).

## Phase 2 — Progression screens
- [x] #8 Goals / Story Arcs ✅ (milestones, arc completion bonus).
- [x] #9 Skills ✅ (4 trees, unlock with points, parent prerequisite).
- [x] #10 Achievements + Collectibles ✅ (rule-based unlocks, lore).
- [x] #11 Analytics ✅ (XP-over-time, activity heatmap, totals).
- [x] #12 Personal Bests (offline replacement for Leaderboard).

## Phase 3 — Second brain (on-device)
- [x] #13 Documents ✅: import (file picker / share sheet), on-device OCR (ML Kit) for
      images/scans, text extraction for pdf/txt/md, chunking.
- [x] #14 On-device embeddings (port hashing embeddings from `services/ai/hashing.py`) +
      local vector search table; Search screen ✅.
- [x] #15 World Map ✅ (knowledge graph: domains ↔ documents ↔ tags on Canvas).

## Phase 4 — Narrator + reviews (per DECISIONS pending item)
- [x] #16 Narrator ✅ per chosen strategy (retrieval-only offline v1; optional cloud key).
- [x] #17 Weekly Review ✅ (aggregate stats; narrative offline-templated or via optional key).
- [x] #18 AI quest generation (offline template pool; optional cloud key).

## Phase 5 — Data ownership & polish
- [x] #19 Export / import all data as JSON + Markdown ("his data must outlive the app").
- [x] #20 Optional PIN/biometric lock; backup reminder.

## Beyond v0.1 (post-completion)
- [x] #21 Archive web stack (backend/ + frontend/) to `legacy/`; Android-only.
- [x] #22 Fix Android-15 edge-to-edge status-bar overlap (top bar + drawer insets).
- [x] #23 Sarvam voice capture — record on-device → Saarika STT → import as a note.
- [ ] #24 Biometric unlock (needs FragmentActivity) — PIN shipped; biometric deferred.
- [ ] #25 Real embedding model for sharper search (optional upgrade over hashing embeddings).

## Explicitly out (offline)
- Multi-user accounts, JWT, cross-device sync, hosted leaderboard — dropped or deferred.
