# State Transitions

This document is the normative business-state contract. Implementations may rename internal types, but they must preserve every transition and outcome below. SQLDelight persists durable state; Compose never invents or owns business transitions.

## Failure classes

| Class | Examples | Persistence | Retry policy | User visibility |
| --- | --- | --- | --- | --- |
| Retryable | Offline, timeout, I/O, HTTP 5xx | Mutation remains durable with attempts and next retry time | Automatic when due; exponential backoff from 2 seconds to 5 minutes | Pending/offline status; non-blocking error when useful |
| Rate limited | HTTP 429 | Mutation remains durable with server reset time and attempts | Automatic no earlier than server reset/backoff | Rate-limit message without losing local state |
| Authentication blocked | HTTP 401/403 | Mutation marked `BLOCKED` | No automatic retry until token/configuration changes or explicit Retry | Clear configuration error |
| Validation permanent | CREATE HTTP 422 | CREATE mutation removed; user becomes `CreateFailed` | Explicit user Retry only after correction/review | Failed-sync reason on the user |
| Delete already absent | DELETE HTTP 404 | Row and mutation removed | No retry; treated as success | Normal completed deletion |
| Permanent client/invariant | Malformed payload, impossible mapping, unsupported request | Operation stopped; cached state preserved; mutation blocked or removed according to operation table | No automatic retry | Clear diagnostic failure |

Backoff metadata is stored in SQLDelight so process death cannot reset a failing operation into a tight loop.

## Refresh and snapshot

| Current state | Event | Success | Retryable failure | Permanent failure / edge case |
| --- | --- | --- | --- | --- |
| Empty, idle | Initial load | Commit last-page snapshot; emit visible users | Remain empty; show offline/retry state | Show authentication or data-contract error; no loop |
| Cached, idle | Initial load/refresh | Commit snapshot; keep original `observedAt` | Keep cached users; show non-blocking stale/offline state | Keep cached users; show blocking reason where relevant |
| Refreshing | Another refresh trigger | Join/coalesce active sync | Same active result | Never start a second concurrent refresh |
| Any | Page-count header missing/invalid | None | None | Keep cache; surface data-contract error; do not clear database |
| Any | Last page is empty | Commit an empty remote snapshot while preserving pending/failed local rows | Keep prior snapshot | Never delete pending local intent |

Snapshot results affect UI only after their database transaction commits.

## Create user

| Current state | Event | Success | Retryable failure | Permanent failure / edge case |
| --- | --- | --- | --- | --- |
| No local row | Submit valid form | Transaction inserts user as `PendingCreate` plus CREATE mutation; DB emits user at top | Local transaction failure keeps form open and shows error | Duplicate submit is ignored while transaction runs |
| `PendingCreate` | Sync CREATE | HTTP 201 attaches remote ID, removes mutation, emits `Synced` | Persist `RETRYABLE_WAIT`; keep user visible | 401/403 -> `BLOCKED`; 422 -> remove mutation and emit `CreateFailed` |
| `RetryableWait` | Retry time/explicit refresh | Return to CREATE attempt | Persist later retry using backoff | Reclassify using response; never loop immediately |
| `Blocked` | Automatic sync trigger | No operation | Not applicable | Remain blocked until configuration changes or explicit Retry |
| `CreateFailed` | Explicit Retry | Create one new mutation and emit `PendingCreate` | Follow retryable path | Remain failed with new reason |
| `PendingCreate`/`CreateFailed` | Confirm delete | Remove local row and CREATE mutation | Local transaction failure leaves prior state | Send neither POST nor DELETE after successful cancellation |

## Delete and Undo

| Current state | Event | Success | Retryable failure | Permanent failure / edge case |
| --- | --- | --- | --- | --- |
| Visible synchronized user | Confirm delete | Persist hidden undoable state and five-second deadline; DB removes row from visible query | Local transaction failure leaves row visible and reports error | Repeated confirmation is ignored |
| Undoable, before deadline | Undo | Clear hidden/deadline state; DB restores row | Local transaction failure reports error and keeps durable state | No DELETE mutation exists |
| Undoable, at/after deadline | Undo | None | Not applicable | Return `TooLate`; finalization owns the next transition |
| Undoable, deadline expires | Finalize | Transaction creates one DELETE mutation and marks pending delete | Local transaction failure remains undoable/expired for next finalization | Unique constraint prevents duplicate DELETE mutation |
| Pending delete | Sync DELETE | HTTP 204/404 removes row and mutation | Persist `RETRYABLE_WAIT`; row remains hidden | 401/403 -> `BLOCKED`; other permanent failure removes mutation, restores row, surfaces reason |
| Pending delete | Remote snapshot contains user | Keep local row hidden/pending | Not applicable | Snapshot merge must not resurrect it |
| Undoable | Process death/restart | Before deadline remains undoable; after deadline finalizes once | Database remains authoritative | Snackbar may be recreated only when deadline is still active |

If multiple users are deleted, each deadline is persisted independently. UI Snackbar sequencing must never discard a durable deadline.

## Synchronization coordinator

| Current state | Event | Outcome |
| --- | --- | --- |
| Idle | Startup, foreground, connectivity restored, refresh, or connected mutation | Under the coordination mutex, publish one active-run handle; execute the sync outside the mutex |
| Running | Another trigger | Under the coordination mutex, capture the existing handle and await that same result; never queue a follow-up full sync |
| Running | Connectivity lost | Stop after the current safe boundary; persist remaining intent |
| Running | Retryable mutation failure | Persist retry schedule; continue only when ordering and dependency safety allow |
| Running | Authentication blocked | Mark affected work blocked and stop remote processing |
| Running | Outbox drained | Fetch and transactionally merge the latest last-page snapshot |
| Any | Process death | No intent is lost because users, deadlines, mutations, attempts, and retry times are persisted |

## Presentation states

- Compose renders ViewModel state derived from repository/database flows.
- Shimmer exists only for empty database plus active initial load.
- Cached users remain visible during refresh and failures.
- Dialog, sheet, focus, and Snackbar visibility are UI state; they do not change domain models.
- User actions call ViewModel methods. Composables do not validate business rules, write SQLDelight, call Ktor, or coordinate retries.
- One-off messages are consumed explicitly and do not become durable business truth.

## Required transition tests

For every row above, add at least one deterministic test for the success or defined outcome. Add separate tests for retryable and permanent failures wherever both are present. Use an injected clock, fake remote data source, in-memory SQLDelight driver, and controlled dispatchers; never use real delays or the live API in unit tests.
