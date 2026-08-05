## Summary

<!-- What does this PR do and why? Link the issue it resolves: Closes #123 -->

## Changes

- 
- 
- 

## Test plan

All three checks must pass (run sequentially, in this order):

- [ ] `./gradlew :app:assembleRelease` — builds clean, zero warnings
- [ ] `./gradlew :app:testDebugUnitTest` — all tests pass
- [ ] `./gradlew :app:lintDebug` — no lint errors

## Notes for reviewers

- [ ] Room schema changes include a hand-written `Migration` (never `fallbackToDestructiveMigration`)
- [ ] Watch-protocol changes parse tolerantly so old and new builds interop
- [ ] Watch copy stays relationship-agnostic (no "child"/"parent" wording)
- [ ] New notifications register their channel in `LumenApp` and check `POST_NOTIFICATIONS`

## Screenshots

<!-- For UI changes: light + dark mode where relevant -->
