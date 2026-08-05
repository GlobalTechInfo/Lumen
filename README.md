# Lumen

![CI](https://github.com/GlobalTechInfo/Lumen/actions/workflows/ci.yml/badge.svg)
![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)

**Offline-first screen time. No account. No cloud. No shame.**

Lumen is a private screen-time companion for Android (min SDK 29). It lives entirely on
your device — usage data is stored in a local Room database and never leaves the phone,
except through two explicit, encrypted, consent-based paths you control:

- **Watch** — a direct peer-to-peer link between two phones (e.g. a parent's phone and a
  child's phone, or two friends) that syncs daily screen-time reports over your local
  network. Every link is encrypted end-to-end and can be removed at any time.
- **Export** — your history can be exported as a PIN-encrypted archive for another phone,
  or as a plain readable JSON backup to Downloads.

## Features

**Track**
- Daily screen time with a goal ring, pickup count, and notification count
- Weekly/monthly reports with insights, per-app breakdowns, and a 7×24 hourly heatmap
- Day mood tags, first-app vows, and a weekly time-bank of minutes saved under your goal

**Protect**
- Per-app daily limits with gentle nudges and mindful +15-minute extensions (logged with a reason)
- Bedtime wind-down, rest days for your streak, relaxed weekend goals, ignored apps
- Pattern-shift alert when today's usage runs far above your personal baseline
- Achievement badges for streaks, calm days, and focus milestones

**Focus**
- Timed focus sessions recorded as focus minutes in your reports
- Step-away challenges: a simple no-phone countdown

**Connect (Watch)**
- Pair two phones by QR code or link code — encrypted end-to-end, no account required
- Auto-sync reports while both phones are on the same network (plus a 15-minute background sync)
- 7-day watched history, per-app limits on the watched phone with alert notifications
- Weekly low-usage duel and mutual "sync celebration" when both stay under goal
- **Knocks** — a gentle encrypted message delivered as a notification to the linked phone
- Long-distance report export/import with a PIN (PBKDF2 + AES-GCM sealed archive)

**Your data**
- Everything offline: Room, DataStore, local files only
- JSON backup/restore via the system file picker
- Declutter: a weekly look at installed-but-unused apps, with one-tap uninstall

## Privacy & permissions

Lumen asks for the minimum needed to work, and each permission has a purpose:

| Permission | Why |
|---|---|
| Usage access | Count screen time, pickups, and per-app usage (the core of the app) |
| Notifications access | Count notifications per app (optional, can be disabled) |
| Notifications | Alert you about goals, knocks, recaps, and celebrations |
| Nearby devices / Wi-Fi | Find the linked phone on your local network for Watch sync |
| Camera | Scan the pairing QR code (optional — links can be entered by code) |

Your data is never uploaded anywhere. There is no server, no account, and no telemetry.

## Getting started

```bash
git clone https://github.com/GlobalTechInfo/Lumen.git
cd Lumen
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Requirements: JDK 17, Android SDK 37.

## Building & verifying

All checks must pass before any PR is merged:

```bash
./gradlew :app:assembleRelease        # release build (signed with keystore.jks)
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:lintDebug              # lint
```

### Release signing

The release APK is signed with `keystore.jks` at the repository root. **The keystore is
git-ignored and must never be committed.** In CI, everything comes from repository secrets:
`KEYSTORE_BASE64` (base64 of the keystore), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and
`KEY_PASSWORD` (see `.github/workflows/release.yml`).

## Releasing

Releases are driven by tags:

- `git tag v1.0.0 && git push origin v1.0.0` → stable release (signed APK attached to a GitHub Release)
- `git tag v1.0.0-beta.1` (any `-alpha`, `-beta`, `-rc` suffix) → prerelease (marked as such)

The tag version becomes the APK's `versionName`; `versionCode` stays at `1`. The workflow
builds, signs, verifies, and uploads the APK automatically. See `.github/workflows/release.yml`.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for setup, conventions, and the PR process, and
[AGENTS.md](AGENTS.md) for guidance used by AI coding assistants.

## Security

See [SECURITY.md](SECURITY.md) for the security model and how to report vulnerabilities.

## Tech stack

| Layer | Choice |
|---|---|
| UI | Jetpack Compose (Material 3), icons-extended |
| Architecture | MVVM with `StateFlow` view models, manual DI (`AppContainer`) |
| Storage | Room (schema v2), Preferences DataStore |
| Background | WorkManager (nightly summary, weekly recap, background sync) |
| P2P link | Local TCP sockets + NSD discovery, X25519/ECDH, HKDF, AES-GCM |
| QR | ZXing android-embedded |
| Build | AGP 9.3.1, Kotlin 2.4.10, KSP, R8 |

## License

[MIT](LICENSE) © 2026 GlobalTechInfo
