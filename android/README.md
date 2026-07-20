# MindQuest — Android Companion App

Kotlin + Jetpack Compose app consuming the same MindQuest REST API as the web client
(contract: `docs/API_SPECIFICATION.md`). Current scope: **register a new hero or sign
in** with **persistent sessions** (encrypted refresh-token storage, silent restore on
launch, automatic refresh-and-retry on 401), an **in-app configurable server URL**,
character sheet (level/XP/streaks), quest hub with one-tap completion, daily-mission
check-ins, and **Narrator chat** with per-answer citations.

## Build

Requires Android Studio (or the Android SDK + JDK 17):

```bash
cd android
./gradlew assembleDebug        # or open in Android Studio
```

No Gradle wrapper is committed; generate one with `gradle wrapper --gradle-version 8.9`
or let Android Studio create it on first sync.

## Configuration

The backend URL is set **in the app** — tap **Server settings** on the sign-in screen.
It is persisted, so you set it once:

- **Emulator** → `http://10.0.2.2:8000` (the default; `10.0.2.2` is the emulator's alias
  for the host machine's `localhost`).
- **Physical device** → `http://<your-PC-LAN-IP>:8000` (e.g. `http://192.168.1.25:8000`);
  the phone and PC must share a Wi-Fi network.
- **Deployed API** → `https://your-api.example.com`.

`API_BASE_URL` in `app/build.gradle.kts` only supplies the initial default. For a
production build over HTTPS you can remove `android:usesCleartextTraffic` from the
manifest (cleartext is needed only for local `http://` testing).

## Roadmap
- [x] Register / sign in from the app, with in-app server URL configuration
- [x] Refresh-token persistence (EncryptedSharedPreferences) and auto-refresh
- [x] Narrator chat with citations
- [ ] Document upload via the system share sheet
- [ ] Push notifications for daily missions (post-MVP; see docs/PRD.md §5.2)

CI note: the server CI intentionally does not build this module (no Android SDK on the
default runners). Add a separate workflow with `android-actions/setup-android` when
Android builds should gate merges.
