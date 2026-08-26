# Sprint 3 - Smart User Feed

## Objective

Deliver the real GoRest-backed feed with incremental pagination, durable caching, shared relative time, polished loading/error states, and a reusable user-card presentation.

## Prerequisites

- Sprint 2 repository and synchronization tests pass.
- A local GoRest token is configured for manual write verification, although public GET must still work when authentication is not required.

## Implementation work

### GoRest client

- Add Kotlinx-serializable DTOs for users and API field errors.
- Configure Ktor JSON to ignore unknown fields and reject structurally invalid required fields.
- Probe page 1 with `per_page=20` to parse `X-Pagination-Pages`, display the last page, and fetch each preceding page when the feed reaches its end.
- Record response order as `serverPosition` and use the current injected clock only for newly observed rows.
- Map I/O, timeout, 401/403, 422, 429, 5xx, serialization, and malformed-pagination failures into typed data/domain errors.
- Redact bearer tokens from debug logging.

### Relative time

- Add a pure formatter using injected current time.
- Minimum labels: `Just now`, singular/plural minutes, hours, days, and a stable older-date fallback.
- Clamp future observed times to `Just now` instead of displaying a negative value.
- Drive a lightweight minute tick in the ViewModel so visible labels refresh without database writes.

### ViewModel

- Expose one immutable `UserFeedUiState` from repository, connectivity, sync, and minute-tick flows.
- Build that state only from database-derived repository flows plus presentation state; never publish raw Ktor payloads.
- Initial load triggers synchronization once.
- Manual refresh triggers the coordinator and exposes `refreshing` without clearing cached users.
- Prevent duplicate refresh jobs.
- Keep refresh success, retryable failure, permanent failure, and overlapping-trigger behaviour aligned with `state-transitions.md`.
- Keep one-off messages consumable and non-replaying.

### Material 3 feed UI

- Add the shared top app bar, feed container, FAB placeholder, and reusable `UserCard`.
- Implement compact `LazyColumn`; reserve the wider grid integration for Sprint 6 while keeping the card width-independent.
- Add shimmer cards matching final card geometry.
- Add distinct empty, no-cache offline, cached-offline, authentication, and generic retry states.
- Show pending/failed sync status subtly on affected cards without replacing the primary identity content.
- Add pull-to-refresh or the current supported Material 3 equivalent.

## Tests

- DTO and error payload serialization fixtures.
- Pagination pages: last-page discovery, ordered preceding pages, missing/non-numeric headers, empty pages, retry, and end-of-list behavior.
- Remote order is preserved in the database.
- Observed time is preserved on later refresh.
- Relative-time boundaries, pluralization, future times, and older dates.
- ViewModel cold start, cached refresh, no-cache offline, cached offline, authentication failure, retry, and refresh deduplication.
- Compose semantics for shimmer, data, empty, error, offline banner, card identity, and refresh action.

## Acceptance criteria

- Online cold start displays the last page and scrolling appends `last-1`, `last-2`, and earlier pages from GoRest.
- Offline cold start displays cache or the explicit offline state.
- A failed refresh never removes valid cached content.
- Every visible user shows name, email, and a shared-logic relative timestamp.
- Shimmer appears only when no cache exists.
- UI and data tests use fakes; no unit test calls the live API.
- Composables contain presentation and action wiring only; relative time, refresh decisions, and error classification remain testable shared logic.

## Out of scope

- Functional add form.
- Delete interaction.
- Final two-column layout and visual polish pass.

## Commit boundary

Commit network feed, ViewModel, UI states, reusable cards, and tests with:

`feat: implement cached smart user feed`
