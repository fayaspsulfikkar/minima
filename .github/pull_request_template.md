## Summary of Changes

Describe the intent and context of this change. What issue does it address or what capability does it introduce?

## Architectural & Behavioral Verification

Minima follows strict product and engineering rules to maintain intentional phone usage:

- [ ] **No Unnecessary Dependencies**: Adheres to modern Android dependencies without bloat.
- [ ] **Strict Monochrome Identity**: No colorful icons, badges, or distracting UI accents (#000000 / #FFFFFF only).
- [ ] **Zero Internet / Privacy Preserved**: No network permissions introduced; all processing remains 100% on-device.
- [ ] **No Addictive Mechanics**: No gamification, streaks, engagement notifications, or infinite feeds.
- [ ] **Separation of Concerns**: UI, ViewModel, Domain UseCases, and Data Sources remain decoupled.
- [ ] **Graceful Permission Handling**: Degrades gracefully if `PACKAGE_USAGE_STATS` is unavailable.

## Testing & Quality

- [ ] Unit tests added or updated (`./gradlew testDebugUnitTest` passes).
- [ ] Lint checks pass (`./gradlew lintDebug`).
- [ ] Verified manually on emulator or physical device.
