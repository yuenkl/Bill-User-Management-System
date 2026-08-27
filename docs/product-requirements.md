# Product Requirements

## Goal

Build a production-minded Kotlin Multiplatform user directory that demonstrates shared architecture, offline-first behaviour, adaptive Compose UI, and clear recovery from network failures. The intended audience is a technical evaluator reviewing Android/KMP quality, UX polish, and AI-assisted engineering discipline.

There are three primary capabilities:

1. Smart User Feed.
2. Adaptive Add User.
3. Delete with Undo.

All behaviour must preserve the [engineering invariants](README.md#engineering-invariants) and the explicit [state-transition contract](state-transitions.md).

## Smart User Feed

### Behaviour

- Fetch `GET /public/v2/users` as the initial, newest page, then follow `X-Links-Next` (`page=2`, `page=3`, and so on) as the user reaches the end of the feed.
- Read `X-Links-Next` from each response. Its absence marks the end of the feed.
- Persist the remote snapshot before exposing it to the UI. The UI observes SQLDelight only and never displays a network response directly.
- Each row displays name, email, gender/status indicators where useful, and a relative timestamp based on when the record was first observed locally.
- Preserve server order across appended pages. A newly created user is merged at the top only after GoRest returns HTTP 201.
- Refresh is available through pull-to-refresh or an equivalent explicit Material 3 action; it refreshes the initial response before restarting next-page loading.

### Loading and failure states

- Show shimmer placeholders only when the database has no visible users and the initial refresh is running.
- If cached users exist, keep them visible during refresh and use a non-blocking refresh indicator.
- With no cache and no network, show a full offline state with Retry.
- With cached users and no network, show a compact offline banner or supporting message without blocking the feed.
- Empty data is distinct from loading and failure.
- Authentication, rate-limit, validation, server, and connectivity failures map to user-readable messages while retaining diagnostic detail for logs.

### Acceptance criteria

- Cold start online displays the initial response and scrolling progressively appends each subsequent page in server order.
- Cold start offline displays cached users, or the offline empty state if no cache exists.
- Refresh never clears valid cached content before replacement data is committed.
- Relative-time boundaries are correct and update while the screen remains open.

## Adaptive Add User

### Form

- The Material 3 FAB opens a shared form component.
- Compact layouts host the form in a modal bottom sheet. Wider layouts host the same form content in a centered dialog/card.
- Fields are name, email, gender, and status because GoRest requires all four.
- Gender uses the API-supported `male` and `female` values. Status uses `active` and `inactive`.
- Submit is disabled until the complete form is valid and no submission is already running.

### Validation

- Trim leading/trailing whitespace before validation and submission.
- Name must be 2-80 characters after trimming, contain at least one Unicode letter, and contain no control characters. Do not restrict legitimate names to ASCII.
- Email must be at most 254 characters, contain exactly one `@`, contain no whitespace, have a non-empty local part of at most 64 characters, and have a domain composed of valid non-empty labels with a final label of at least two characters.
- Show errors after field interaction and update them in real time. Keep API field errors separate from local syntax errors.

### Server-confirmed creation

- Submission POSTs the normalized form directly to GoRest without first inserting a local user.
- Only HTTP 201 merges the returned user into the local database, where it appears at the top of the feed.
- Any failed create leaves the feed unchanged and keeps the form open.
- HTTP 422 presents an alert that lists the API field and its message, while retaining the values for correction and resubmission.

### Acceptance criteria

- HTTP 201 closes the form and shows the returned user at the top.
- Rotation or layout change preserves form state while the form is open.
- A failed or offline submission creates no local row.
- Repeated taps cannot create duplicate remote requests.

## Delete with Undo

### Behaviour

- Long-pressing a user opens a confirmation dialog containing enough identity information to avoid deleting the wrong row.
- Confirming hides the row with an animation and persists an undoable-delete state with a five-second deadline.
- Show a Snackbar with Undo for the same five-second window.
- Undo before the deadline restores the row and prevents a remote DELETE.
- When the deadline expires, create a `DELETE` outbox entry. Synchronize immediately if online or later if offline.
- HTTP 204 and 404 both complete deletion. A permanent authentication failure restores the row and reports the failure; transient failures remain queued.
- New users are displayed only after HTTP 201, so every visible user has a remote ID and deletion calls the remote endpoint.

### Acceptance criteria

- Undo produces no remote DELETE.
- No Undo produces exactly one DELETE after the deadline.
- The deadline survives process death; expired deletions finalize on the next startup.
- Offline deletion remains hidden, survives restart, and completes after reconnection.

## Adaptive layout and shared UI

- All feature UI is Compose Multiplatform and shared between Android and iOS.
- Available width, not device orientation alone, determines layout.
- Width below 600dp uses a single list. Width at least 600dp uses exactly two grid columns.
- Cards, forms, loading placeholders, error surfaces, dialogs, and empty states are reusable composables.
- Support system light/dark mode with one shared Material 3 theme. Do not depend on Android-only dynamic colour for core appearance.
- Interactive elements have semantics, readable contrast, adequate touch targets, and meaningful content descriptions where required.

## Offline and synchronization contract

- SQLDelight is authoritative for visible state.
- Mutations follow one local-first code path regardless of current connectivity.
- Synchronization runs at app startup, return to foreground, connectivity restoration, and manual refresh.
- Only one synchronization run may execute at a time.
- Pending mutations are processed FIFO before fetching the latest remote snapshot.
- Database updates for each remote result are transactional, and database flows update the UI automatically afterward.
- Retryable operations remain durable rather than being lost with a coroutine or process.
- Permanent failures leave the automatic retry set, surface a clear reason, and require configuration change or explicit user action before another attempt.
- Core `User` domain data never carries `isDeleted`, `hidden`, dialog, Snackbar, or other temporary lifecycle flags.
- Compose renders state and forwards actions; validation, mutation, synchronization, and retry decisions remain in shared ViewModel/domain/data code.

## Authentication and privacy

- GoRest writes require a bearer token.
- Android and iOS composition roots provide an `AppConfig` to shared code from ignored local build configuration.
- Track example configuration and setup instructions only. Never commit a real token.
- A missing token produces a clear configuration error; it must not crash the application or silently send an empty credential.

## Out of scope

- Editing an existing remote user.
- User detail or master-detail navigation.
- More than two columns.
- Account/login management or token entry UI.
- Background operating-system jobs while the application is terminated.
- General-purpose conflict resolution beyond the documented create/delete rules.
