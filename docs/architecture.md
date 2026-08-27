# Architecture

## Architectural shape

Keep one `shared` Gradle module for challenge-sized delivery while enforcing boundaries with packages, interfaces, constructor injection, and internal visibility.

Architecture and implementation decisions must preserve clear ownership, a database-backed feed, server-confirmed mutations, and deterministic failure handling.

```text
shared/src/commonMain/kotlin/com/bill/usermanagmentsystem/
|-- ui/
|   |-- theme/
|   `-- users/
|       |-- components/
|       |-- UserFeedScreen.kt
|       |-- UserFeedEvent.kt
|       |-- UserFeedUiState.kt
|       |-- UserFeedViewModel.kt
|       `-- presentation/
|           |-- UserErrorMessages.kt
|           |-- UserFeedPresentationState.kt
|           `-- UserFeedUiStateMapper.kt
|-- domain/
|   |-- model/
|   |-- repository/
|   `-- usecase/
|-- data/
|   |-- local/
|   |-- remote/
|   |-- repository/
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
                  SQLDelight   GoRest
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
)
```

`User` contains stable business data only. It does not contain dialog visibility, Snackbar state, or other temporary UI/persistence lifecycle flags. `UserRecord` is the repository projection emitted from SQLDelight.

`Gender` and `UserStatus` encode the GoRest-supported values. Network DTOs mirror JSON. SQLDelight rows include persistence-only fields. UI models add rendered relative time, field errors, and display flags.

### Repository contract

```kotlin
interface UserRepository {
    fun observeUsers(): Flow<List<UserRecord>>
    suspend fun refresh(): Result<Unit>
    suspend fun loadNextPage(): Result<PageLoadResult>
    suspend fun addUser(input: AddUserInput): Result<String>
    suspend fun deleteImmediately(localId: String): Result<DeletedUserUndo>
    suspend fun restoreDeletedUser(input: AddUserInput): Result<String>
}
```

Use cases wrap these operations where they contain business rules: validation, refresh, pagination, relative time, immediate deletion, and restoration.

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
)
```

The screen automatically synchronizes at startup, foreground, and restored connectivity. User actions include pull-to-refresh, add-form open/close, field changes, submit, user long-press, delete confirm/cancel, Undo, and next-page Retry. One-off Snackbar effects are emitted as `SharedFlow<UserFeedEvent>` values, so they are not replayed by the screen state after recomposition. The ViewModel keeps only the current Undo input needed to validate a restore action.

## SQLDelight design

### `users`

- `local_id INTEGER PRIMARY KEY AUTOINCREMENT`
- `remote_id INTEGER UNIQUE NULL`
- `name`, `email`, `gender`, `status`
- `observed_at_epoch_ms INTEGER`
- `server_position INTEGER NULL`
Visible-user queries place server-confirmed creates (which have no server position until the next refresh) above the pages returned by GoRest. New form submissions create no pending row: HTTP 201 merges the returned remote user at the top. Snapshot and page merges run transactionally.

New create submissions are direct remote operations: only HTTP 201 is merged into the local database. Deletion is an immediate remote operation: after DELETE succeeds, the local row is removed.

## Repository operation algorithm

`UserRepositoryImpl` owns remote calls, pagination state, and SQLDelight writes. A `Mutex` serializes refresh, page, create, delete, and restore operations so a response cannot race a database update.

1. Refresh fetches `GET /users` without pagination parameters, reads `X-Links-Next`, and commits the received snapshot in one database transaction.
2. Each successful scroll fetch follows the returned `X-Links-Next` value (`page=2`, `page=3`, and so on), appends the page transactionally, and advances the cursor only after the write succeeds.
3. Create and restore POST first; only HTTP 201 merges the returned user locally.
4. Delete calls DELETE first; HTTP 204 and 404 then remove the local row. Undo POSTs the retained input as a new server user.

Failure policy:

- I/O, 5xx, and 429 keep the existing database contents and surface a retryable failure. Pull-to-refresh, the next automatic synchronization, and a failed page retry can try again without clearing cached users.
- 401/403 preserve the database contents and expose a configuration/authentication error.
- CREATE 422 creates no local row and presents the returned field errors while retaining the form values.
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

Define platform-neutral `ConnectivityObserver` and `AppLifecycleObserver` interfaces. Android implementations use platform lifecycle/connectivity APIs; iOS implementations use the equivalent Apple lifecycle and network path APIs. They trigger refreshes but never determine correctness: every network call must still handle connectivity races.

Triggers are startup, foreground, transition to connected, and pull-to-refresh.

## Koin graph

- Common modules provide clock, validators, formatters, remote/local data sources, repository, use cases, and ViewModel.
- Platform modules provide Ktor engine, SQLDelight driver, connectivity/lifecycle observers, dispatcher providers where needed, and `AppConfig`.
- ViewModels and domain/data classes use constructor injection.
- Tests load the common graph with fake remote, clock, connectivity, configuration, and in-memory SQL driver overrides.
- Add a graph smoke test so missing or cyclic definitions fail early.

## Adaptive Material 3 UI

- Use one screen and one ViewModel across layouts.
- In portrait, render a `LazyColumn`. In landscape, render a two-column `LazyVerticalGrid`.
- Reuse `UserCard`, `UserForm`, shimmer card, empty/error surface, and delete confirmation components.
- Use `AnimatedVisibility` or item placement/removal animation without delaying database truth.
- Compact add form uses `ModalBottomSheet`; wider form uses a Material 3 dialog/card with the same state and callbacks.
- System light/dark selection feeds a shared colour scheme and typography. Core appearance is identical across platforms.

## Configuration

`AppConfig(apiToken: String, baseUrl: String)` is passed by Android and iOS composition roots. Use ignored local configuration with tracked examples and setup documentation. The token is embedded in a development binary, so documentation must state that this is acceptable only for the challenge/demo and is not production secret storage.

## Testing strategy

- Pure common tests: validation, relative time, ViewModel state, use cases, and error mapping.
- Data tests: in-memory SQLDelight driver, fake remote service, fake clock, fake connectivity, and transactional snapshot/page/mutation scenarios.
- ViewModel tests: deterministic dispatchers with immediate remote-delete and remote-restore outcomes.
- Compose tests: semantics and layout assertions for loading, offline, validation, confirmation, Undo, and column count.
- Contract tests: deserialize representative GoRest success/error payloads and parse pagination headers.
- Platform smoke checks: Android debug build/host tests and iOS simulator tests.

## Architectural constraints

- Do not return network DTOs or SQLDelight models beyond the data layer.
- Do not call Koin lookup functions inside application classes or composables.
- Do not let composables perform repository work or contain business validation.
- Do not clear cached users before a remote refresh succeeds.
- The Undo Snackbar is intentionally temporary because it recreates a successfully deleted remote user through POST.
- Keep the existing package name spelling unless a separate rename task is approved.
