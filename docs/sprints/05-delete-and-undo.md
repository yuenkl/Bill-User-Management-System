# Sprint 5 - Delete and Undo

## Objective

Deliver safe long-press deletion with confirmation, animated removal, a durable five-second Undo window, and exactly-once offline synchronization.

## Prerequisites

- Repository supports undoable deadlines, delete outbox entries, and cancellation of unsynchronized creates.
- Feed cards expose stable local IDs and sync status.

## Implementation work

### Interaction and confirmation

- Add a semantic long-click action to `UserCard` without interfering with scrolling or normal card accessibility.
- Store the selected local ID in ViewModel state and resolve the current user from the latest state before confirming.
- Show a Material 3 confirmation dialog with user name/email and explicit Cancel/Delete actions.
- Ignore repeated confirmation while the local deletion transaction is running.

### Durable Undo window

- On confirmation, calculate `undoDeadline = clock.now() + 5.seconds` in shared domain logic.
- Keep delete visibility, deadline, and outbox status in SQLDelight/repository state; do not add `isDeleted` or similar flags to `User`.
- Persist `UndoableDelete`, the deadline, and hidden state atomically.
- Let the database flow remove the card; use shared item removal animation.
- Show a five-second Snackbar containing the user name and Undo action.
- Undo calls the repository, which restores only if the persisted deadline has not expired.
- Snackbar timeout finalizes the delete immediately. Startup/foreground sync also finalizes any expired deadline in case the process stopped.

### Remote synchronization

- Finalizing a synchronized user inserts one DELETE mutation if none exists.
- Finalizing a pending local creation cancels CREATE and removes the row without a remote DELETE.
- Trigger sync after finalization when connected; offline mutations remain durable.
- Treat 204 and 404 as successful completion.
- Keep transient failures hidden and durably scheduled for retry. If authentication/permanent failure makes deletion impossible, remove it from automatic retry, restore the row, and show the failure.
- Never recreate a deleted remote user to implement Undo.

### Concurrency and edge handling

- Exactly one delete state transition may win for a local user.
- Undo racing with timeout is resolved transactionally using the persisted deadline; after expiry, Undo returns a typed `TooLate` result.
- Refresh snapshot merge cannot reinsert a locally hidden/pending-delete row.
- A second user deletion replaces the visible Snackbar only after the first deletion's deadline has been deterministically finalized; do not lose either operation.

## Tests

- Long-click opens confirmation for the correct user.
- Cancel changes nothing.
- Confirm hides and persists an undo deadline.
- Undo before expiry restores and sends no DELETE.
- Undo/timeout race has one deterministic outcome and no duplicate mutation.
- Timeout creates one DELETE mutation.
- Process restart before and after expiry restores or finalizes correctly.
- Offline deletion remains hidden and later sends one DELETE.
- Pending CREATE followed by delete sends neither POST nor DELETE after cancellation.
- DELETE 204/404 completes; transient error stays pending; permanent authentication error restores the row.
- Multiple deletions do not lose a deadline or mutation.

## Acceptance criteria

- Deletion always requires confirmation.
- Card removal is animated and the Snackbar offers Undo for five seconds.
- Undo means no remote deletion occurred.
- Offline delete and process restart cannot lose or duplicate work.
- Remote and cached state converge after reconnection.
- Undo remains local restoration before finalization; it never recreates a remotely deleted user.

## Out of scope

- Restoring a remote user after DELETE.
- Bulk deletion.
- Swipe-to-delete.

## Commit boundary

Commit confirmation, durable Undo, delete synchronization, animations, and tests with:

`feat: add durable delete with undo`
