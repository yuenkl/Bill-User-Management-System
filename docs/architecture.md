# Architecture

## Architectural shape

Keep one `shared` Gradle module for challenge-sized delivery while enforcing boundaries with packages, interfaces, constructor injection, and internal visibility.

The twelve engineering invariants in [README](README.md#engineering-invariants) are mandatory. Architecture and implementation decisions must preserve them even when a shortcut appears simpler.

```text
shared/src/commonMain/kotlin/com/bill/usermanagmentsystem/
|-- ui/
|   |-- theme/
|   `-- users/
|       |-- components/
|       |-- UserFeedScreen.kt
|       |-- UserFeedUiState.kt
|       `-- UserFeedViewModel.kt
|-- domain/
|   |-- model/
|   |-- repository/
|   `-- usecase/
|-- data/
|   |-- local/
|   |-- remote/
|   |-- repository/
|   `-- sync/
|-- di/
`-- platform/
```

Android and iOS source sets contain SQLDelight drivers, Ktor engines, lifecycle/connectivity adapters, and platform configuration. Platform types must not leak into common domain or UI contracts.

## Dependency direction

```text
Compose UI -> ViewModel -> Use cases -> Repository interface
                                         ^
                                         |
                       Repository implementation
                         /       |       \
                  SQLDelight   GoRest   Sync coordinator
```

- UI depends on domain-facing ViewModel state and actions.
- Domain defines models, repository contracts, and use cases without Ktor, SQLDelight, Koin, or platform types.
- Data implements domain contracts and owns mapping among network, database, and domain forms.
- Koin assembles dependencies at composition roots; application code uses constructor injection rather than service-location calls.

## Core models

### Domain

```kotlin
data class User(
    val localId: String,
    val remoteId: Long?,
    val name: String,
    val email: String,
    val gender: Gender,
    val status: UserStatus,
    val observedAt: Instant,
)

data class AddUserInput(
    val name: String,
    val email: String,
    val gender: Gender,
    val status: UserStatus,
)

data class UserRecord(
    val user: User,
    val synchronization: UserSynchronization,
)

sealed interface UserSynchronization {
    data object Synced : UserSynchronization
    data object PendingCreate : UserSynchronization
    data class CreateFailed(val reason: String) : UserSynchronization
}
```

`User` contains stable business data only. It does not contain `isDeleted`, `hidden`, dialog visibility, Snackbar state, or other temporary UI/persistence lifecycle flags. `UserRecord` is a repository projection that pairs a user with synchronization metadata required by presentation. Delete lifecycle fields remain internal to persistence and are excluded from visible-user results.

`Gender` and `UserStatus` encode the GoRest-supported values. Network DTOs mirror JSON. SQLDelight rows include persistence-only fields. UI models add rendered relative time, field errors, and display flags.

### Repository contract

```kotlin
interface UserRepository {
    fun observeUsers(): Flow<List<UserRecord>>
    fun observeSyncState(): Flow<SyncState>
    suspend fun refresh(): Result<Unit>
    suspend fun addUser(input: AddUserInput): Result<String>
    suspend fun deleteImmediately(localId: String): Result<DeletedUserUndo>
    suspend fun restoreDeletedUser(input: AddUserInput): Result<String>
    suspend fun syncPending(): Result<Unit>
}
```

Use cases wrap these operations where they contain business rules: validation, refresh orchestration, relative time, immediate deletion, restoration, and synchronization triggers.

The UI only receives repository flows derived from SQLDelight queries. Ktor responses are mapped and committed in the data layer before database emissions can change UI state.

## UI state and events

The ViewModel exposes one immutable `StateFlow<UserFeedUiState>` and accepts explicit actions.

```kotlin
data class UserFeedUiState(
    val users: List<UserItemUiModel>,
    val initialLoading: Boolean,
    val refreshing: Boolean,
    val offline: Boolean,
    val emptyState: EmptyState?,
    val addForm: AddUserFormUiState?,
    val deleteConfirmation: UserItemUiModel?,
    val snackbar: SnackbarMessage?,
)
```

Actions include initial load, refresh, add-form open/close, field changes, submit, user long-press, delete confirm/cancel, Undo, Retry, and message consumption. One-off messages use an explicit event/channel contract and are not encoded as permanently replayed state.

## SQLDelight design

### `users`

- `local_id INTEGER PRIMARY KEY AUTOINCREMENT`
- `remote_id INTEGER UNIQUE NULL`
- `name`, `email`, `gender`, `status`
- `observed_at_epoch_ms INTEGER`
- `server_position INTEGER NULL`
- `sync_status TEXT`
- `hidden INTEGER AS BOOLEAN`
- `undo_deadline_epoch_ms INTEGER NULL`
- `last_sync_error TEXT NULL`

Visible-user queries exclude hidden rows and order pending local users newest-first, followed by server position. Successfully created users are optimistic until the next successful refresh, which adopts the API snapshot order. Upserts preserve the original `observed_at` and do not overwrite pending local mutations.

### `pending_mutations`

- `mutation_id TEXT PRIMARY KEY`
- `user_local_id INTEGER`
- `kind TEXT` constrained to `CREATE` or `DELETE`
- `created_at_epoch_ms INTEGER`
- `attempt_count INTEGER`
- `state TEXT` constrained to `PENDING`, `RETRYABLE_WAIT`, or `BLOCKED`
- `retry_at_epoch_ms INTEGER NULL`
- `last_error TEXT NULL`
- Unique constraint preventing duplicate active operations of the same kind for one local user.

Create state changes and their outbox rows occur in the same database transaction. Deletion is an immediate remote operation: after the DELETE succeeds, the local row and any mutation for it are removed in one transaction.

## Synchronization algorithm

`SyncCoordinator` keeps one active-run handle. A `Mutex` protects only the short critical section that reads, creates, publishes, or clears that handle. The network/database synchronization work runs outside the mutex. If a run already exists, every new trigger captures and awaits that same run and receives its result. Triggers must not wait for the mutex and then start complete synchronization runs back-to-back. When the run completes, clear the handle only if it still refers to that completed run.

1. Read pending mutations FIFO.
2. For each `CREATE`, POST the current local row. On 201, attach the remote ID, mark synchronized, and remove the mutation transactionally.
3. Stop processing on connectivity loss. Keep remaining work durable.
4. After mutations, fetch `GET /users` without pagination parameters, read `X-Links-Next`, and set the next-page cursor from that link when it is present.
5. Transactionally replace the remote snapshot with the initial response while preserving pending and failed local rows. A successfully created row is optimistic until this transaction; then the API response determines its presence and server position. Each successful scroll fetch follows the returned `X-Links-Next` value (`page=2`, `page=3`, and so on). Every refresh fetches the initial response again and resets the cursor from its link.

Failure policy:

- I/O, 5xx, and 429 are retryable. Persist attempt count and next retry time using exponential backoff from two seconds up to five minutes; for 429, respect the server reset/retry header when it is later.
- Automatic sync selects only pending mutations or retryable mutations whose persisted retry time is due.
- 401/403 marks affected mutations `BLOCKED`, exposes a configuration/authentication error, and prevents automatic retries until configuration changes or the user explicitly retries.
- CREATE 422 removes the mutation and becomes `CreateFailed` with the API field message retained. It is retried only by an explicit user action.
- An explicit DELETE treats HTTP 204 and 404 as success. The row remains visible when that request fails.
- Undo is an explicit POST using the deleted data; a successful response is merged as a new user with its server-assigned remote ID.
- Serialization or invariant failures are permanent for that attempt: stop the affected operation, surface a diagnostic error, and preserve cached state without an automatic retry loop.

The complete transition contract is defined in [state-transitions.md](state-transitions.md).

## Networking

- Ktor common client configuration: JSON content negotiation, Kotlin serialization, timeouts, default GoRest base URL, bearer authentication, and status-to-domain error mapping.
- Android provides the OkHttp engine; iOS provides Darwin.
- The initial `/users` response returns typed next-page metadata. Header parsing is case-insensitive; an absent `X-Links-Next` ends pagination and an invalid link is a controlled data-contract error. The initial response and all subsequent pages retain server display order.
- Network logging is debug-only and must redact `Authorization`.

## Connectivity and lifecycle

Define platform-neutral `ConnectivityObserver` and `AppLifecycleObserver` interfaces. Android implementations use platform lifecycle/connectivity APIs; iOS implementations use the equivalent Apple lifecycle and network path APIs. They trigger synchronization but never determine correctness: every network call must still handle connectivity races.

Triggers are startup, foreground, transition to connected, manual refresh, and successful local mutation while connected.

## Koin graph

- Common modules provide clock, validators, formatters, remote/local data sources, repository, sync coordinator, use cases, and ViewModel.
- Platform modules provide Ktor engine, SQLDelight driver, connectivity/lifecycle observers, dispatcher providers where needed, and `AppConfig`.
- ViewModels and domain/data classes use constructor injection.
- Tests load the common graph with fake remote, clock, connectivity, configuration, and in-memory SQL driver overrides.
- Add a graph smoke test so missing or cyclic definitions fail early.

## Adaptive Material 3 UI

- Use one screen and one ViewModel across layouts.
- Below 600dp, render a `LazyColumn`. At 600dp or wider, render a two-column `LazyVerticalGrid`.
- Reuse `UserCard`, `UserForm`, shimmer card, empty/error surface, sync indicator, and delete confirmation components.
- Use `AnimatedVisibility` or item placement/removal animation without delaying database truth.
- Compact add form uses `ModalBottomSheet`; wider form uses a Material 3 dialog/card with the same state and callbacks.
- System light/dark selection feeds a shared colour scheme and typography. Core appearance is identical across platforms.

## Configuration

`AppConfig(apiToken: String, baseUrl: String)` is passed by Android and iOS composition roots. Use ignored local configuration with tracked examples and setup documentation. The token is embedded in a development binary, so documentation must state that this is acceptable only for the challenge/demo and is not production secret storage.

## Testing strategy

- Pure common tests: validation, relative time, reducers/state transitions, use cases, sync decisions, error mapping.
- Data tests: in-memory SQLDelight driver, fake remote service, fake clock, fake connectivity, transaction and restart scenarios.
- ViewModel tests: deterministic dispatchers with immediate remote-delete and remote-restore outcomes.
- Compose tests: semantics and layout assertions for loading, offline, validation, confirmation, Undo, and column count.
- Contract tests: deserialize representative GoRest success/error payloads and parse pagination headers.
- Platform smoke checks: Android debug build/host tests and iOS simulator tests.

## Architectural constraints

- Do not return network DTOs or SQLDelight models beyond the data layer.
- Do not call Koin lookup functions inside application classes or composables.
- Do not let composables perform repository work or contain business validation.
- Do not clear cached users before a remote refresh succeeds.
- Do not use in-memory-only mutation queues. The Undo Snackbar is intentionally temporary because it recreates a successfully deleted remote user through POST.
- Do not let a permanent failure remain eligible for automatic retry.
- Keep the existing package name spelling unless a separate rename task is approved.
