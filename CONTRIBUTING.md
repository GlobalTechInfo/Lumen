# Contributing to Lumen

Thanks for considering a contribution. Lumen is a small, privacy-first app, and every
line of code is held to a high bar. Please read this before opening a pull request.

## Code of conduct

Be kind. This project is about helping people use their phones less — keep review
comments constructive and respectful.

## Getting started

1. Fork and clone: `git clone https://github.com/GlobalTechInfo/Lumen.git`
2. Install JDK 17 and Android SDK 37 (see `README.md`).
3. Create the signing keystore (release builds only): if you don't have `keystore.jks`
   at the repo root, debug builds still work — release builds need the keystore or the
   env overrides described in `AGENTS.md`.

## Branch model

- `main` is the protected trunk. No direct pushes — all changes land via pull request.
- Work on short-lived branches: `feat/<short-name>`, `fix/<short-name>`, `chore/<short-name>`.
- `main` must always build green: unit tests and lint pass.

## Before you open a PR

Run these **sequentially** and confirm each one is green:

```bash
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

PRs that introduce lint warnings, deprecations, or test failures will be asked to fix
them before review. There is no separate lint exemption list.

## Commit style

Use [conventional commits](https://www.conventionalcommits.org/):

```
feat: add first-app vow with morning check-in
fix: clamp day boundary for pre-midnight usage
chore: bump compose bom
refactor: extract knock protocol helpers
```

## Pull request checklist

- [ ] Description explains the *why* (not just the *what*)
- [ ] Links the issue it resolves (`Closes #12`)
- [ ] All three Gradle checks pass (build, tests, lint)
- [ ] Room schema changes include a hand-written `Migration` — never
      `fallbackToDestructiveMigration`
- [ ] UI changes include a screenshot (light and dark if colors are involved)
- [ ] Watch-related changes keep copy relationship-agnostic and protocol parsing tolerant
      (old/new builds interop)

## Branch protection

`main` is protected: required PR review, required status checks (`CI / Unit tests & lint`),
and no direct pushes. It must be configured by a repo admin in
**Settings → Branches → Branch protection rules** (target `main`).

## Releasing

Maintainers release by pushing tags:

- `vX.Y.Z` → stable GitHub Release (signed APK attached)
- `vX.Y.Z-alpha|beta|rc*` → prerelease

The CI workflow `.github/workflows/release.yml` builds, signs, and uploads. Never create
a release by hand; always use a tag so the version name on the APK matches the release.

## Dependency updates

Dependabot is enabled (`.github/dependabot.yml`). Please keep PRs small and prefer
grouped, minor-level updates.
