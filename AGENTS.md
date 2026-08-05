# AGENTS.md

Guidance for AI coding assistants (and humans) working in this repository.

## Project overview

Lumen is an **offline-first Android screen-time app**. Core identity:

- **No account, no cloud, no telemetry.** All data lives on-device (Room + DataStore).
  The only two explicit data-sharing paths are the peer-to-peer Watch link (encrypted
  local TCP sockets) and user-initiated exports (PIN-sealed archive or plain JSON backup).
- **Gentle by design.** Copy never shames the user; failures are framed as "it happens".
- **Watch copy is relationship-agnostic.** Never write "child"/"parent" wording — links
  are between arbitrary people ("the linked phone", "Friend's phone").

## Critical constraints

1. **Zero tolerance for warnings.** Never introduce deprecation warnings, unused imports,
   or new lint errors. The codebase is lint- and warning-free and must stay that way.
2. **Gradle tasks run strictly sequentially.** Never run two Gradle invocations in
   parallel in the same shell/session.
3. **Every change is verified** before being considered done:
   `./gradlew :app:assembleRelease` → `./gradlew :app:testDebugUnitTest` →
   `./gradlew :app:lintDebug`, in that order, each green.
4. **Room migrations are hand-written.** Never use `fallbackToDestructiveMigration`.
   Schema changes require a new version + a manual `Migration` (see `MIGRATION_1_2`).
5. **Never touch `keystore.jks`** or commit it (git-ignored).

## Build environment

- JDK 17, Android SDK 37 (`/opt/android-sdk`), AGP 9.3.1, Kotlin 2.4.10, Compose BOM 2026.06.01.
- `versionCode`/`versionName` defaults live in `app/build.gradle.kts`; they can be
  overridden with `-PversionCode=… -PversionName=…` (CI does this from release tags).
- Keystore credentials can be overridden with env vars `KEYSTORE_PATH`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (defaults match `keystore.jks`).
- Builds are slow (assemble can take 12+ minutes). Use a generous timeout and redirect
  output: `./gradlew :app:assembleRelease > /tmp/opencode/build.log 2>&1; echo EXIT=$?`.
- Verify a signed APK with `/opt/android-sdk/build-tools/37.0.0/apksigner verify`.
- Deliverables are copied to `/root/Lumen-vX.Y.apk`.

## Conventions

- **Layout:** all Kotlin under `app/src/main/java/com/lumen/app/`:
  - `domain/` — pure logic (StreakCalculator, InsightEngine, Achievements, TimeUtils, models)
  - `data/` — Room (`db/`), DataStore (`prefs/`), usage (`usage/`), P2P link (`link/`),
    WorkManager workers, notification capture
  - `ui/` — screens + view models (`dashboard/`, `reports/`, `focus/`, `family/`, `settings/`),
    shared `ui/components/` (GlassCard, SectionTitle, EmptyState, AppIcon, GlowRing),
    theme colors in `ui/theme/`
- **Theme:** use the palette (`Amber`, `Mint`, `Rose`, `Periwinkle`, `Ink`, `TextMuted`)
  rather than raw colors. Cards use `GlassCard`; section headers use `SectionTitle`.
- **View models** expose `StateFlow<XState>` data classes with defaults; screens
  `collectAsStateWithLifecycle()`. DI is manual via `AppContainer` + `viewModelFactoryFor`.
- **DataStore** for settings/preferences (`SettingsRepository`, string-set keys encode
  compound entries like `"$epochDay:$mood"`). **Room** for history, sessions, peers.
- **Notification channels** (created in `LumenApp`): `lumen_goals`, `lumen_watch`,
  `lumen_recap`, `lumen_knock`, `lumen_celebration`. Notification IDs are stable ranges:
  goals 2001, limit alerts 3000+, weekly recap 4001, knocks 5000, celebrations 6000.
  Always check `POST_NOTIFICATIONS` before notifying.
- **Widget snapshot** is pushed by `DashboardViewModel` into prefs `lumen_widget`
  (minutes/goal/streak), then `LumenWidgetProvider.updateAll(context)`.
- **Watch protocol** (`data/link/LinkProtocol.kt`): line-based `LUMEN1 <C|S|A|B|R|K>` over
  TCP, X25519/ECDH + HKDF session key + AES-GCM sealing. Add new lines only with a versioned
  JSON payload and tolerant parsing (`optX` fallbacks) so old/new builds interop.
- **Strings:** hardcoded in composables today (no resource extraction yet) — keep copy
  warm, short, and judgment-free.

## Definition of done

- [ ] Code compiles with zero warnings
- [ ] `assembleRelease`, `testDebugUnitTest`, `lintDebug` all green
- [ ] Room migrations added (never destructive fallback) when entities change
- [ ] Notification permission checked where applicable
- [ ] Version bumped + APK delivered when asked
