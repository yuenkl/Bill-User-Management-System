# State Transitions

This document is the normative business-state contract. Implementations may rename internal types, but they must preserve every transition and outcome below. SQLDelight persists durable state; Compose never invents or owns business transitions.

## Failure classes

| Class | Examples | Persistence | Retry policy | User visibility |
| --- | --- | --- | --- | --- |
| Retryable | Offline, timeout, I/O, HTTP 5xx | Existing cache remains unchanged | Explicit retry | Non-blocking error when useful |
| Rate limited | HTTP 429 | Existing cache remains unchanged | Explicit retry after the limit clears | Rate-limit message without losing local state |
| Authentication | HTTP 401/403 | Existing cache remains unchanged | Correct token and retry | Clear configuration error |
| Validation permanent | CREATE HTTP 422 | No local row is written | Correct and resubmit manually | Alert lists the API field and message |
| Delete already absent | DELETE HTTP 404 | Row removed | No retry; treated as success | Normal completed deletion |
| Permanent client/invariant | Malformed payload, impossible mapping, unsupported request | Operation stopped; cached state preserved | Explicit user action starts a new request | Clear diagnostic failure |

## Refresh and snapshot

| Current state | Event | Success | Retryable failure | Permanent failure / edge case |
| --- | --- | --- | --- | --- |
| Empty, idle | Initial load | Fetch and commit the initial `/users` snapshot; emit visible users and enable the `X-Links-Next` cursor when available | Remain empty; show offline state | Show authentication or data-contract error; no loop |
| Cached, idle | Automatic synchronization or pull-to-refresh | Commit snapshot; keep original `observedAt` | Keep cached users; show non-blocking stale/offline state | Keep cached users; show blocking reason where relevant |
| Synchronizing | Another refresh trigger | Ignore the duplicate trigger | Same active result | Never start a second concurrent refresh |
| Any | Next-page link invalid | None | None | Keep cache; surface data-contract error; do not clear database |
| Page loaded | Feed reaches end | Fetch and transactionally append the page named by `X-Links-Next` in display order | Keep loaded users; show explicit page Retry without advancing the cursor | Stop when `X-Links-Next` is absent; never loop or duplicate a page |
| Any | Initial page is empty | Commit an empty remote snapshot | Keep prior snapshot | No local intent is retained because mutations are server-confirmed |

Snapshot results affect UI only after their database transaction commits.

## Create user

| Current state | Event | Success | Retryable failure | Permanent failure / edge case |
| --- | --- | --- | --- | --- |
| No local row | Submit valid form | POST succeeds with HTTP 201; merge returned user and close form | Keep form open; do not insert a row | Duplicate submit is ignored while request runs |
| No local row | POST returns HTTP 422 | Keep form values | Not applicable | Show an alert with every returned field/message pair; no local row is created |
| No local row | POST fails for authentication, network, or server reasons | Keep form values and feed unchanged | User may resubmit manually | Show the classified failure; no local row is created |

## Delete and Undo

| Current state | Event | Success | Retryable failure | Permanent failure / edge case |
| --- | --- | --- | --- | --- |
| Visible synchronized user | Confirm delete | Call DELETE; HTTP 204/404 removes the local row and presents Undo | Keep the row visible and show the failure | Repeated confirmation is ignored while the request runs |
| Successfully deleted user | Undo | POST the saved user data, merge the response, and show the new remote ID | Keep the row deleted and show the failure | Authentication, validation, and permanent failures keep the row deleted |
| Successfully deleted user | Snackbar dismissed or process restart | Keep the deletion; Undo is no longer available | Not applicable | Undo is an in-memory UI affordance, not a durable deletion state |

## Repository serialization

| Current state | Event | Outcome |
| --- | --- | --- |
| Idle | Refresh, page load, create, delete, or restore | Acquire the repository operation mutex; complete the remote/database operation before releasing it |
| Running | Another operation | Wait for the active operation, then perform the requested operation against its latest database state |
| Running | Connectivity lost or remote failure | Preserve the completed cache and report the classified failure; no local mutation is queued |
| Any | Process death | Committed database rows remain; a dismissed Undo is not restored |

## Presentation states

- Compose renders ViewModel state derived from repository/database flows.
- Shimmer exists only for empty database plus active initial load.
- Cached users remain visible during refresh and failures.
- Dialog, sheet, focus, and Snackbar visibility are UI state; they do not change domain models.
- User actions call ViewModel methods. Composables do not validate business rules, write SQLDelight, call Ktor, or coordinate retries.
- One-off messages are consumed explicitly and do not become durable business truth.

## Required transition tests

For every row above, add at least one deterministic test for the success or defined outcome. Add separate tests for retryable and permanent failures wherever both are present. Use an injected clock, fake remote data source, in-memory SQLDelight driver, and controlled dispatchers; never use real delays or the live API in unit tests.
