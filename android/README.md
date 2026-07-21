# MindQuest — Offline Android App

A fully **offline, on-device** second-brain RPG (Kotlin + Jetpack Compose + Room). No server,
no login, no network required — all logic and data live on the phone. This is the primary
product; the web app + FastAPI backend are archived (see `docs/DECISIONS.md`).

The only feature that reaches the network is the AI Narrator (Phase 4), which will call
**Sarvam AI** when online and degrade gracefully offline.

## Architecture

```
UI (Compose)  →  MindQuestRepository  →  Room (SQLite, "mindquest.db")
                        │
                        └─ GameEngine (XP curve, streaks, achievement rules)
                           Catalogs   (achievements / skills / collectibles lore)
```

- `data/` — Room entities, DAOs, database, and `MindQuestRepository` (the single data API).
- `domain/` — `GameEngine` (pure progression math, ported from the old Python backend) and
  `Catalogs` (seed data). Both are plain Kotlin, unit-testable.
- `ui/` — Compose screens + theme.

Progression math is a direct port of the former `backend/app/services/gamification.py`, so
numbers match the original web app exactly.

## Build & run

Requires Android Studio (JDK 17 bundled). Open the **`android/`** folder, let Gradle sync
(downloads Room, KSP, Compose), then Run ▶ on an emulator or a USB-connected device.
No backend, no `docker compose`, no server URL — it just runs.

First launch asks for a hero name and creates a local profile. Everything else is offline.

## Status (see `docs/BACKLOG_v0.1_offline.md`)
- [x] Phase 0 — Room DB, ported game engine, seeding, local profile + onboarding
- [x] Phase 1 — Dashboard, Quests, Habits (the daily loop, fully offline)
- [ ] Phase 2 — Goals, Skills, Achievements, Analytics, Personal Bests
- [ ] Phase 3 — Documents (on-device OCR), Search, World Map
- [ ] Phase 4 — Narrator + Weekly Review via Sarvam AI

## Notes
- Pre-release, the DB uses `fallbackToDestructiveMigration()` — schema changes wipe local data.
  Replace with real Room migrations before any release (`docs/DECISIONS.md`).
- No Android SDK in the cloud dev environment, so each phase is compiled/verified in Android
  Studio on-device before the next begins.
