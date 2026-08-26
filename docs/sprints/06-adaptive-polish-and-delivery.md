# Sprint 6 - Adaptive Polish and Delivery

## Objective

Complete the adaptive two-column experience, accessibility, dark mode, cross-feature recovery, final verification, and evaluator-facing README without changing established feature semantics.

## Prerequisites

- Feed, add, delete, and synchronization sprints meet their acceptance criteria.
- No unresolved permanent data-loss or duplicate-mutation defects remain.

## Implementation work

### Adaptive layout

- Derive a shared compact/wide layout mode from available content width.
- Below 600dp, use one `LazyColumn`.
- At 600dp or wider, use one `LazyVerticalGrid` with exactly two equal columns.
- Keep stable keys by local ID and preserve scroll/form state across layout changes when practical.
- Apply consistent spacing, safe-area insets, maximum readable widths, FAB placement, and Snackbar host placement.
- Verify portrait phones, landscape phones, tablets, iOS split layouts, and Android split-screen widths rather than testing orientation labels alone.

### Material 3 polish

- Finalize shared light/dark colour schemes, typography, shapes, elevation, and status colours.
- Ensure shimmer geometry matches loaded cards and respects reduced-motion expectations where available.
- Polish item insertion/removal, pending-sync indicators, refresh feedback, form transitions, and error surfaces.
- Avoid decorative animation that blocks input, synchronization, or test determinism.

### Accessibility and content

- Provide semantic headings, field/error relationships, long-click labels, button roles, and live announcements for important Snackbar/error feedback.
- Verify touch targets, contrast, text scaling, keyboard traversal where supported, and non-colour sync/error indicators.
- Ensure names/emails wrap or ellipsize deliberately without overlapping controls.

### Recovery and integration

- Verify startup, foreground, connectivity restoration, and manual refresh all trigger one coalesced synchronization path.
- Verify transient failures honor persisted retry timing and permanent failures do not loop across lifecycle/connectivity triggers.
- Ensure cached data remains visible during refresh and authentication/rate-limit failures.
- Verify missing token setup guidance and error recovery after configuration is corrected.
- Confirm logging never exposes the token or full sensitive request headers.

### README and delivery

Update the root README with:

- Architecture summary and data-flow diagram.
- Setup for Android/iOS local token files using placeholder examples.
- Run/test commands.
- Offline synchronization and Undo semantics.
- Major technology choices and tradeoffs.
- How AI assistance was curated and verified.
- Known limitations: GoRest resets data, token is embedded in demo binaries, and observed time is local because the API has no timestamp.

## Tests and verification

- Compose layout tests assert one column below 600dp and two columns at/above 600dp.
- Theme tests/previews cover light, dark, loading, empty, offline, error, pending, and failed states.
- Accessibility semantics cover add, submit errors, long-click delete, confirmation, Undo, and Retry.
- End-to-end fake integration scenarios:
  - Cold start online.
  - Cold start offline with and without cache.
  - Offline add, restart, reconnect, reconcile.
  - Offline delete, Undo.
  - Offline delete, timeout, restart, reconnect.
  - Concurrent refresh and connectivity events.
  - Authentication, 422, 429, 5xx, malformed payload, and recovery.
- Audit every row in `state-transitions.md` against deterministic automated coverage.
- Run `./gradlew :shared:testAndroidHostTest`.
- Run `./gradlew :shared:iosSimulatorArm64Test`.
- Run `./gradlew :androidApp:assembleDebug`.
- Launch both platform apps for a final manual smoke test.
- Inspect tracked files for tokens, local paths, build output, and IDE state.

## Acceptance criteria

- Compact and wide layouts meet the exact column rules without duplicated feature logic.
- Android and iOS share feature UI and behaviour.
- Light/dark, accessibility, loading, error, and offline states are production-presentable.
- All automated checks and both manual platform smoke tests pass.
- README lets a reviewer configure, run, understand, and evaluate the project without private guidance.
- Known limitations and tradeoffs are documented honestly.

## Out of scope

- New product capabilities introduced during polish.
- Desktop/web targets.
- CI/CD deployment pipelines unless separately requested.

## Commit boundary

Commit adaptive UI, polish, delivery documentation, and final test updates with:

`feat: complete adaptive KMP user experience`
