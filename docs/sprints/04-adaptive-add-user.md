# Sprint 4 - Adaptive Add User

## Objective

Deliver a polished, reusable add-user flow that validates in real time, works identically online and offline, and places the new user at the top immediately.

## Prerequisites

- Feed ViewModel and SQLDelight source-of-truth flow are working.
- CREATE outbox reconciliation and typed API validation errors are covered by data tests.

## Implementation work

### Validation

- Add pure shared validators for name and email using the rules in [product requirements](../product-requirements.md).
- Normalize submitted values by trimming outer whitespace; preserve intentional internal characters.
- Model untouched, valid, and invalid field states separately so errors do not appear before interaction.
- Keep field interaction/touched flags in form UI state, never in the core `User` model.
- Map GoRest 422 field errors to the matching field when the form is still active, or to the failed local row when synchronization happens later.
- Represent gender and status as domain enums rather than free-form strings.

### Form state and events

- Extend `UserFeedUiState` with `AddUserFormUiState` containing field values, touched flags, errors, validity, and submitting state.
- ViewModel actions cover open, dismiss, each field change, gender/status selection, submit, and API error consumption.
- Reject a duplicate submit while the first local transaction is running.
- Preserve form state through recomposition, rotation, and width-class changes by keeping it in the ViewModel.

### Shared Material 3 form

- Build one `UserForm` composable with stateless fields and callbacks.
- Compact width presents it in `ModalBottomSheet`.
- Width at least 600dp presents the same content in a centered Material 3 dialog/card.
- Use appropriate keyboard options, IME actions, supporting error text, focus progression, and clear submit/cancel affordances.
- Gender is a required two-option selector matching GoRest values. Status is an active/inactive control defaulting to active.
- The FAB has accessible text/semantics and remains clear of safe-area/system UI.

### Submission and feedback

- On valid submit, call the repository once. It writes the local row and CREATE mutation transactionally.
- Close the form after the local transaction succeeds; do not wait for network success.
- The database flow places the temporary user first and marks it pending.
- Trigger synchronization immediately when connected; otherwise retain the pending state.
- Display a subtle pending-sync indicator. A permanent CREATE failure leaves automatic retry, displays a failed state and readable API reason, and preserves the user's input.
- Allow Retry for failed create after connectivity/authentication is corrected. Long-press deletion remains the removal path; editing is not introduced.

## Tests

- Name: empty, whitespace-only, one character, two characters, Unicode letters, punctuation within a real name, control characters, and over 80 characters.
- Email: empty parts, multiple `@`, whitespace, local part over 64, full address over 254, invalid/empty domain labels, short final label, and representative valid addresses.
- Errors appear only after interaction and clear immediately when corrected.
- Submit enabled state requires all fields and rejects duplicate taps.
- Online submission inserts locally before remote completion.
- Offline submission survives restart, stays first, and reconciles the remote ID after connectivity returns.
- 422 response marks the local user failed with the field reason.
- Permanent failures are not retried by startup/connectivity triggers; explicit Retry creates one new durable attempt.
- Compact sheet and wider dialog render the same values and preserve state when width changes.

## Acceptance criteria

- FAB opens a polished form on Android and iOS.
- All four GoRest fields are deliberately collected and validated.
- Valid submit makes the user visible at the top immediately.
- Add works without connectivity and synchronizes later.
- No duplicate network create occurs from repeated taps or overlapping sync triggers.
- Validation and repository logic remain outside composables.

## Out of scope

- Editing an existing or failed user.
- Arbitrary gender/status values unsupported by GoRest.
- Runtime API-token entry.

## Commit boundary

Commit validation, form state/UI, optimistic creation, and tests with:

`feat: add offline-capable user creation flow`
