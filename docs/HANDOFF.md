# HANDOFF — Mind Quest — 2026-09-05 (session 8)

> Overwrite at the end of every session. Must let any model resume in under 2 minutes.

## Where we are
- Offline Android app, 15 screens, no server. Branch `claude/ai-second-brain-rpg-dv5o75`
  carries this session's work on top of `main`.
- Backlog: 20/20 core done; post-v0.1 #21–#23 + #26 (Inbox) done; #24 (biometric) and
  #25 (real embeddings) still open.

## What happened this session
- **Retheme to the "marginalia console" palette** (from Yash's uploaded .obj/.mtl): `ui/Theme.kt`
  now maps the SAME variable names to a warm paper/terracotta scheme — `Rune` terracotta accent,
  `Abyss` paper background, `Realm` cream card, `Parchment` ink text, plus `Brass`/`Sage`. Because
  the names were kept, all screens re-skinned with no per-screen edits.
- **New launcher icon**: adaptive vector console (`res/drawable/ic_launcher_bg.xml` +
  `ic_launcher_foreground.xml`, `mipmap-anydpi-v26/ic_launcher{,_round}.xml`).
- **Inbox (MQ-26)** — the feature Yash asked for: a chat window for errands/checklists with
  date-based notifications.
  - `data/Entities.kt`: `NoteEntity` (text, done, remindAt, questId, docId, createdAt).
  - `data/Daos.kt`: `NoteDao`. DB → **v4** with additive `MIGRATION_3_4` (no data loss).
  - `domain/Reminders.kt`: `Reminders` (channel, permission check, schedule/cancel by tag
    `note-reminder-<id>`) + `ReminderWorker` posting the notification.
  - `ui/InboxScreen.kt`: chat-style list + composer, ⏰ picker (native DatePicker → TimePicker),
    per-note Remind/Unremind, → Quest, → Archive, Delete, done toggle.
  - Repository: `observeNotes/addNote/setNoteDone/setNoteReminder/deleteNote/noteToQuest/
    noteToArchive/openNoteCount`; notes added to `ExportBundle` + export/import.
  - `MainActivity`: `Dest.Inbox` ("📥", second in the drawer) → `InboxScreen`.
  - Deps: `androidx.work:work-runtime-ktx`, `androidx.core:core-ktx`; manifest
    `POST_NOTIFICATIONS`.

- **GitHub Actions now builds installable APKs** (`.github/workflows/android-apk.yml`) — Yash was
  travelling with no laptop. New `sideload` build type, `applicationIdSuffix ".ci"`, so it installs
  ALONGSIDE the Android Studio build and can't endanger existing data. Signing key comes from repo
  secrets (public repo — nothing committed); no secrets = compile-only, no Release published.
  Gradle wrapper pinned 9.0-milestone-1 → 8.9 (AGP 8.5.2 doesn't support Gradle 9).

## In-flight state
- **The Inbox code now compiles** — run 1 of the APK workflow went green (~4 min, 38 MB APK).
  This is the first real Kotlin compiler in the loop; before this, cloud changes were only
  brace-balanced. Runtime behaviour is still unverified: `WorkManager` scheduling, the native
  DatePicker→TimePicker chain, the `POST_NOTIFICATIONS` request.
- Committed & pushed to `claude/ai-second-brain-rpg-dv5o75`.
- **Blocked on Yash:** add secrets `SIDELOAD_KEYSTORE_BASE64` + `SIDELOAD_KEYSTORE_PASSWORD`
  (Settings → Secrets and variables → Actions), then Actions → Android APK → Run workflow. The
  keystore was generated in session 8; if lost, generate a new one and redo the export/import.
- Data migration to the `.ci` app: export JSON from the old app → install new APK (installs
  alongside) → import → verify → only then delete the old app. Sarvam key + PIN are NOT in the
  export bundle (EncryptedSharedPreferences) and must be re-entered.

## Next action (starts next session)
- If Yash reports a compile/runtime error, fix that first.
- Otherwise: merge to `main`, then #24 biometric unlock (needs FragmentActivity +
  androidx.biometric) or #25 real on-device embedding model.
- Optional polish: screens still carrying hardcoded dark-theme accents (analytics heatmap empty
  cells, chart backgrounds) under the new light palette.

## Open questions / waiting on Yash
- Does the build compile? Test: Inbox → type "call the plumber" → ⏰ pick a time 2 min out → Add
  → allow notifications → notification fires. Then "→ Quest" and "→ Archive" on a note.
- Confirm the new palette + launcher icon look right on device (reboot refreshes the icon cache).
- Sarvam Narrator still reported "unable to resolve hostname api.sarvam.ai" — the hostname is
  correct (resolves to 20.235.220.20 from here), so this looks like device/network DNS. Unresolved.

## Build constraint
- No Kotlin compiler in cloud; each change compiled by Yash in Android Studio.

## Data-safety note (asked last session)
- `git pull` + install-over never wipes app data. Only **uninstalling** does. Take an in-app
  Backup export before anything risky.
