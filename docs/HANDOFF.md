# HANDOFF — Mind Quest — 2026-09-07 (session 9)

> Overwrite at the end of every session. Must let any model resume in under 2 minutes.

## Where we are
- Offline Android app, 15 screens, no server. **The v0.1 backlog is fully closed** — MQ-1..20
  plus post-v0.1 #21–#27. `main` is `f7ec390`; the working branch is synced to it.
- Yash runs `com.mindquest.app.ci` (the CI-signed build). Latest release: `apk-8`, 84 MB.

## What happened this session
- **MQ-24 biometric unlock.** `domain/BiometricLock.kt` wraps androidx.biometric behind an
  availability check; `MainActivity` is now a `FragmentActivity`. Strictly an accelerator in
  front of the PIN — toggle only appears once a PIN exists, clearing the PIN disables it, every
  failure path returns to the PIN field.
- **MQ-27 theme uniformity.** ~60 leftover dark-theme literals (Tailwind emerald/rose/slate and
  49 `Color.Gray`) replaced by semantic tokens in `ui/Theme.kt`; `MindQuestLightColors` pins the
  full Material role set including the surfaceContainer ramp. Zero raw hex outside Theme.kt.
- **MQ-25 complete, in two stages.** First `domain/Retrieval.kt` — BM25 over stemmed tokens fused
  with the embedding cosine via Reciprocal Rank Fusion. Then `domain/TextEmbedder.kt` +
  `domain/WordPiece.kt` — real 384-dim MiniLM-L6-v2 through ONNX Runtime on device.
- **Build pipeline matured.** Model (22 MB) + vocab are downloaded by a Gradle task into
  `assets/` at build time, never committed. Local signing now also reads
  `sideloadKeystorePassword` from `~/.gradle/gradle.properties`.

## In-flight state
- CI green and *verified in the logs*, not just by exit code: model and vocab download, ONNX
  native libs (`libonnxruntime.so`, `libonnxruntime4j_jni.so`) package into the APK.
- **Never run on device.** ONNX inference and the WordPiece tokenizer can only be validated on
  real hardware. Symptom to watch for: search quality gets *worse* — that is the tokenizer
  disagreeing with what MiniLM trained on. Rollback is `apk-4` (pre-MiniLM), installs over the top.
- Reminders, biometrics and the retheme are also unverified on device as of this writing.

## Next action (starts next session)
1. Ask Yash what Settings → Search index reports. `Hashing (built-in)` means the model did not
   load — check logcat for the `MiniLmEmbedder` warning, likely an ABI or asset problem.
2. If search regressed, suspect `WordPiece.kt` first (compare token ids against a known
   HuggingFace tokenisation of the same sentence).
3. Otherwise open work: bump AGP (8.5.2 is only tested to compileSdk 34, project is on 35) —
   worth doing alone, not bundled with features.

## Open questions / waiting on Yash
- Does the MiniLM model load on his device, and does search actually improve?
- Do the reminder notifications, biometric prompt and new palette behave on device?
- Sarvam Narrator previously failed with "unable to resolve hostname api.sarvam.ai". The
  hostname is correct; it looked like device/network DNS. Unresolved, worth retrying now he is
  off mobile data.

## Build constraint (relaxed this session)
- CI compiles every push, so compile errors are caught before Yash pulls. Runtime behaviour
  still needs his device. He now has laptop access again, so Android Studio is available too:
  keystore at `android/sideload.keystore`, `sideloadKeystorePassword` in
  `~/.gradle/gradle.properties`, and **Build Variant must be `sideload`** — the default `debug`
  variant builds a different application id and looks like an empty app.

## Data-safety notes
- `git pull` and install-over never wipe app data. Only uninstalling does.
- Sarvam key and PIN live in EncryptedSharedPreferences and are NOT in the JSON export bundle.
- The signing keystore is the only thing that can update the installed app — if it is ever lost,
  the way back is export → uninstall → install → import.
