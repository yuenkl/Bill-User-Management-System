# Sprint 2 - Offline Data and Synchronization

## Objective

Make SQLDelight the durable source of truth and implement the offline mutation outbox and synchronization coordinator. This sprint establishes data correctness before feature UI is added.

## Prerequisites

- Sprint 1 is complete and both platform graphs create a SQLDelight driver and Ktor client.
- The interfaces and schema follow [architecture](../architecture.md), and every operation follows [state transitions](../state-transitions.md).

## Implementation work

### Domain models and contracts

- Add stable `User`, `AddUserInput`, `Gender`, `UserStatus`, `UserRecord`, and typed domain errors.
- Keep deletion/hidden/deadline fields out of `User`; they are persistence state excluded from visible-user projections.
- Add the `UserRepository` contract and focused use-case contracts.
- Keep identifiers explicit: local database IDs are stable, increasing integers; remote IDs are nullable until POST succeeds.

### SQLDelight schema

- Create `users` and `pending_mutations` tables exactly as documented.
- Add queries for visible ordered users, user lookup, snapshot merge, create enqueue, delete deadline, Undo, expired-delete finalization, FIFO mutation selection, mutation failure updates, and successful reconciliation.
- Wrap every user/outbox transition that must be atomic in a SQLDelight transaction.
- Ensure the visible-users query excludes both undoable and pending deletes.

### Local data source

- Map SQLDelight records into data-layer records and then domain models.
- Preserve `observedAt` on remote upsert.
- Preserve pending local states during snapshot merge.
- Use stable ordering: pending/local creations first, then remote `serverPosition`.
- Provide test-only database recreation and deterministic fixture helpers.

### Remote and synchronization contracts

- Define a fakeable `UserRemoteDataSource` with final-page discovery, previous-page, create, and delete operations. Its responses never escape directly to UI/ViewModel state.
- Implement `SyncCoordinator` with an active-run handle. Use a coroutine `Mutex` only to inspect/publish/clear that handle; run the actual sync outside the lock. Overlapping triggers await the same handle and must not serialize into later full sync runs.
- Always finalize expired undo records before processing the outbox.
- Process mutations FIFO, then discover and merge the current final `/users` page; previous pages append through the serialized pagination path.
- Stop on connectivity loss without dropping or duplicating mutations.

### Mutation rules

- Add always inserts a local row and CREATE mutation transactionally.
- Deleting a pending create removes the row and create mutation locally.
- Deleting a synchronized user first records an undo deadline without a DELETE mutation.
- Deadline expiry creates one DELETE mutation.
- Undo clears the hidden/undo state only before expiry.
- CREATE success attaches the remote ID to the same local row.
- DELETE 204/404 removes the row and mutation.

### Retry and permanent failures

- I/O and 5xx persist exponential-backoff metadata and remain retryable; 429 also respects server retry/reset timing.
- 401/403 marks affected work blocked and removes it from automatic retry until configuration changes or explicit Retry.
- CREATE 422 removes its mutation, marks the row `CreateFailed`, retains the field error, and never loops automatically.
- Permanent DELETE failure removes the mutation, restores the row, records an error, and never loops automatically.

## Tests

- Database emits only visible rows in the correct order.
- Local create and outbox insert are atomic.
- CREATE success preserves local ID and applies remote ID once.
- Offline create survives database reopen and later syncs.
- Delete-before-create-sync cancels CREATE and sends no network operation.
- Undo before expiry restores and sends no DELETE.
- Expired delete after database reopen creates one mutation.
- DELETE 204 and 404 both complete.
- Concurrent sync triggers receive the same in-flight result and produce one remote sequence, with no queued second sequence after completion.
- Connectivity loss preserves remaining FIFO operations.
- Snapshot merge does not overwrite pending local state or original observed time.
- Every transition in `state-transitions.md` has deterministic success, retryable-failure, permanent-failure, cancellation, and edge-case coverage where applicable.

## Acceptance criteria

- Repository flows are backed entirely by SQLDelight.
- All mutation paths survive process/database reopen tests.
- The fake remote can verify request order and exactly-once effects.
- Domain/data code has no Compose or platform dependencies.
- Android host and iOS simulator tests compile and pass for shared logic.

## Out of scope

- Real GoRest DTOs and HTTP calls.
- Feature ViewModel and UI.
- OS background work while the process is terminated.

## Commit boundary

Commit the schema, data contracts, repository, sync coordinator, and their tests with:

`feat: add offline-first user data layer`
