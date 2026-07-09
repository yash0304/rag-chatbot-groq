# MindQuest — Android Companion App

Kotlin + Jetpack Compose app consuming the same MindQuest REST API as the web client
(contract: `docs/API_SPECIFICATION.md`). MVP scope: sign-in, character sheet
(level/XP/streaks), quest hub with one-tap completion, and daily-mission check-ins.

## Build

Requires Android Studio (or the Android SDK + JDK 17):

```bash
cd android
./gradlew assembleDebug        # or open in Android Studio
```

No Gradle wrapper is committed; generate one with `gradle wrapper --gradle-version 8.9`
or let Android Studio create it on first sync.

## Configuration

`API_BASE_URL` is a `buildConfigField` in `app/build.gradle.kts`. The default
`http://10.0.2.2:8000` reaches a locally running backend from the Android emulator.
For a real device or production build, point it at your deployed API over HTTPS and
remove `android:usesCleartextTraffic` from the manifest.

## Roadmap
- Refresh-token persistence (EncryptedSharedPreferences) and auto-refresh
- Document upload via the system share sheet
- Narrator chat with citations
- Push notifications for daily missions (post-MVP; see docs/PRD.md §5.2)

CI note: the server CI intentionally does not build this module (no Android SDK on the
default runners). Add a separate workflow with `android-actions/setup-android` when
Android builds should gate merges.
