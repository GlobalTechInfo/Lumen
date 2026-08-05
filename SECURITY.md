# Security Policy

## Reporting a vulnerability

Please **do not open a public issue** for security problems. Report privately by opening
a [private security advisory](https://github.com/GlobalTechInfo/Lumen/security/advisories/new).

You'll receive an acknowledgment within 72 hours and a target fix timeline. Once a fix
ships, we'll publish the advisory after a grace period so users can update.

## Supported versions

| Version | Status |
|---|---|
| Latest release (tag `v*`) | Supported |
| Older releases | Best effort — please update |

## Security model

Lumen is offline-first by design: **no account, no server, no telemetry**. The main
surfaces worth auditing are:

### Local storage

- Usage history lives in a local Room database; settings in Preferences DataStore.
- No sensitive identifiers are collected; package names and usage aggregates are the
  coarsest data present.

### Watch peer-to-peer link

- **Pairing:** ECDH (X25519 or EC) key exchange with a QR/link code carrying only the
  public key and nickname. The channel authenticates via a constant-time MAC check of
  both public keys (`LumenCrypto.constantTimeEquals`).
- **Session:** HKDF-derived AES-GCM session key seals every report (`LUMEN1 R` lines).
- **Long-distance export:** PIN-derived key via PBKDF2WithHmacSHA256 (120k iterations,
  16-byte random salt) sealing an AES-GCM archive (`LUMENX1` format).
- **Knocks:** same session key, `LUMEN1 K` lines, 140-char plaintext limit.

### Permissions

The app requests usage access, notification access, notifications, Wi-Fi/nearby-device,
and camera — each with a stated purpose (`README.md`). Notification data is stored
locally for at most 45 days.

## Secrets handling (maintainers)

- `keystore.jks` is **git-ignored**; it must never be committed. If it is ever exposed,
  rotate it immediately.
- CI signing secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`,
  `KEY_ALIAS` (see `.github/workflows/release.yml`). Keep them out of logs.
- `local.properties` (SDK paths) is git-ignored as well.

## Scope

The `app/` module and the `.github/workflows/` build pipeline. Third-party dependencies
(AndroidX, Room, WorkManager, ZXing, etc.) are out of scope for this policy — report
upstream issues to those projects.
