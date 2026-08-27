# Product Requirements

## Goal

Build a production-minded Kotlin Multiplatform user directory that demonstrates shared architecture, database-backed cached viewing, adaptive Compose UI, and clear recovery from network failures. The intended audience is a technical evaluator reviewing Android/KMP quality, UX polish, and AI-assisted engineering discipline.

There are three primary capabilities:

1. Smart User Feed.
2. Adaptive Add User.
3. Delete with Undo.

All behaviour must preserve the [engineering invariants](README.md#engineering-invariants) and the explicit [state-transition contract](state-transitions.md).

## Smart User Feed

### Behaviour

- Fetch `GET /public/v2/users` only to read `X-Pagination-Pages`, then fetch and display that final page. Follow `X-Links-Previous` (`page=296`, `page=295`, and so on) as the user reaches the end of the feed.
- Read `X-Links-Previous` from each displayed page. Its absence marks the end of the feed.
- Merge each remote page into SQLDelight before exposing it to the UI. The UI observes SQLDelight only and never displays a network response directly.
- Each row displays name, email, gender/status indicators where useful, and a relative timestamp based on when the record was first observed locally.
- Preserve server order across appended pages. A newly created user is merged at the top only after GoRest returns HTTP 201.
- Refresh is available through pull-to-refresh or an equivalent explicit Material 3 action; it discovers the current final page before restarting previous-page loading.

### Loading and failure states

- Show shimmer placeholders only when the database has no visible users and the initial refresh is running.
- If cached users exist, keep them visible during refresh and use a non-blocking refresh indicator.
- With no cache and no network, show a full offline state. A later connectivity restoration or pull-to-refresh retries the initial request.
- With cached users and no network, show a compact offline banner or supporting message without blocking the feed.
- Empty data is distinct from loading and failure.
- Authentication, rate-limit, validation, server, and connectivity failures map to user-readable messages while retaining diagnostic detail for logs.

### Acceptance criteria

- Cold start online displays the current final page and scrolling progressively appends each previous page in descending page order.
- Cold start offline displays cached users, or the offline empty state if no cache exists.
- Refresh never clears valid cached content before updated page data is committed.
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
- Confirming calls DELETE immediately. Only HTTP 204 or an already-absent HTTP 404 removes the local row, with an item-removal animation.
- After a successful deletion, show a Snackbar with Undo. Dismissing the Snackbar leaves the deletion complete.
- Undo creates a replacement user through POST and inserts that server-confirmed response at the top of the feed.
- If DELETE fails, keep the row visible and show the classified failure.
- New users are displayed only after HTTP 201, so every visible user has a remote ID and deletion calls the remote endpoint.

### Acceptance criteria

- Confirmation produces one DELETE request.
- HTTP 204 and HTTP 404 remove the local row; any other failure leaves it visible.
- Undo produces a new POST request and scrolls the feed to the new top row on success.
- The Undo affordance is transient and does not survive process death.

## Adaptive layout and shared UI

- All feature UI is Compose Multiplatform and shared between Android and iOS.
- Available window orientation determines layout. Portrait uses a single list; landscape uses
  exactly two grid columns.
- Cards, forms, loading placeholders, error surfaces, dialogs, and empty states are reusable composables.
- Support system light/dark mode with one shared Material 3 theme. Do not depend on Android-only dynamic colour for core appearance.
- Interactive elements have semantics, readable contrast, adequate touch targets, and meaningful content descriptions where required.

## Offline and synchronization contract

- SQLDelight is authoritative for visible state.
- Feed synchronization runs at app startup, return to foreground, connectivity restoration, and pull-to-refresh. There is no top-bar Refresh button.
- Create, delete, and Undo are direct server-confirmed operations. Offline mutations do not create a local row or durable outbox entry.
- Only one synchronization run may execute at a time.
- Database updates for each remote result are transactional, and database flows update the UI automatically afterward.
- A failed final-page load preserves the current cache and can be retried by pull-to-refresh or the next lifecycle/connectivity synchronization. A failed previous page exposes an explicit Retry without advancing its cursor.
- Permanent failures preserve the cache and surface a clear reason. Create failures keep the form open for correction or resubmission.
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
