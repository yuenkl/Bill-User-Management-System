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
| Empty, idle | Initial load | Fetch and commit the initial `/users` snapshot; emit visible users and enable the `X-Links-Next` cursor when available | Remain empty; show offline/retry state | Show authentication or data-contract error; no loop |
| Cached, idle | Initial load/refresh | Commit snapshot; keep original `observedAt` | Keep cached users; show non-blocking stale/offline state | Keep cached users; show blocking reason where relevant |
| Refreshing | Another refresh trigger | Join/coalesce active sync | Same active result | Never start a second concurrent refresh |
| Any | Next-page link invalid | None | None | Keep cache; surface data-contract error; do not clear database |
| Page loaded | Feed reaches end | Fetch and transactionally append the page named by `X-Links-Next` in display order | Keep loaded users; show explicit page Retry without advancing the cursor | Stop when `X-Links-Next` is absent; never loop or duplicate a page |
| Any | Initial page is empty | Commit an empty remote snapshot while preserving pending/failed local rows | Keep prior snapshot | Never delete pending local intent |

Snapshot results affect UI only after their database transaction commits.

## Create user

| Current state | Event | Success | Retryable failure | Permanent failure / edge case |
| --- | --- | --- | --- | --- |
| No local row | Submit valid form | Transaction inserts user as `PendingCreate` plus CREATE mutation; DB emits user at top | Local transaction failure keeps form open and shows error | Duplicate submit is ignored while transaction runs |
| `PendingCreate` | Sync CREATE | HTTP 201 attaches remote ID, removes mutation, emits `Synced` | Persist `RETRYABLE_WAIT`; keep user visible | 401/403 -> `BLOCKED`; 422 -> remove mutation and emit `CreateFailed` |
| `RetryableWait` | Retry time/explicit refresh | Return to CREATE attempt | Persist later retry using backoff | Reclassify using response; never loop immediately |
| `Blocked` (authentication) | Next sync after configuration is corrected | Reset to pending and retry CREATE | Follow retryable path | Block again if credentials remain invalid |
| `Blocked` (other permanent failure) | Automatic sync trigger | No operation | Not applicable | Remain blocked until explicit Retry |
| `CreateFailed` | Explicit Retry | Create one new mutation and emit `PendingCreate` | Follow retryable path | Remain failed with new reason |
| `PendingCreate`/`CreateFailed` | Confirm delete | Remove local row and CREATE mutation | Local transaction failure leaves prior state | Send neither POST nor DELETE after successful cancellation |

## Delete and Undo

| Current state | Event | Success | Retryable failure | Permanent failure / edge case |
| --- | --- | --- | --- | --- |
| Visible synchronized user | Confirm delete | Call DELETE; HTTP 204/404 removes the local row and presents Undo | Keep the row visible and show the failure | Repeated confirmation is ignored while the request runs |
| Visible local-only user | Confirm delete | Remove the local row and its CREATE mutation; no DELETE is needed | Local transaction failure leaves the row visible | No remote request is made |
| Successfully deleted user | Undo | POST the saved user data, merge the response, and show the new remote ID | Keep the row deleted and show the failure | Authentication, validation, and permanent failures keep the row deleted |
| Successfully deleted user | Snackbar dismissed or process restart | Keep the deletion; Undo is no longer available | Not applicable | Undo is an in-memory UI affordance, not a durable deletion state |

## Synchronization coordinator

| Current state | Event | Outcome |
| --- | --- | --- |
| Idle | Startup, foreground, connectivity restored, refresh, or connected mutation | Under the coordination mutex, publish one active-run handle; execute the sync outside the mutex |
| Running | Another trigger | Under the coordination mutex, capture the existing handle and await that same result; never queue a follow-up full sync |
| Running | Connectivity lost | Stop after the current safe boundary; persist remaining intent |
| Running | Retryable mutation failure | Persist retry schedule; continue only when ordering and dependency safety allow |
| Running | Authentication blocked | Mark affected work blocked and stop remote processing |
| Running | Outbox drained | Fetch and transactionally merge the initial `/users` page; subsequent pages load through the serialized pagination path |
| Any | Process death | No create intent is lost because users, mutations, attempts, and retry times are persisted; a dismissed Undo is not restored |

## Presentation states

- Compose renders ViewModel state derived from repository/database flows.
- Shimmer exists only for empty database plus active initial load.
- Cached users remain visible during refresh and failures.
- Dialog, sheet, focus, and Snackbar visibility are UI state; they do not change domain models.
- User actions call ViewModel methods. Composables do not validate business rules, write SQLDelight, call Ktor, or coordinate retries.
- One-off messages are consumed explicitly and do not become durable business truth.

## Required transition tests

For every row above, add at least one deterministic test for the success or defined outcome. Add separate tests for retryable and permanent failures wherever both are present. Use an injected clock, fake remote data source, in-memory SQLDelight driver, and controlled dispatchers; never use real delays or the live API in unit tests.
