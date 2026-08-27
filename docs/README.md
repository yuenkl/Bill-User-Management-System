# User Management System Documentation

This directory is the implementation source of truth for the Sliide Kotlin Multiplatform challenge. The challenge brief supplies product context; the current user request and the repository `AGENTS.md` remain the controlling instructions.

## Document map

- [Product requirements](product-requirements.md) defines user-visible behaviour and acceptance criteria.
- [Architecture](architecture.md) defines boundaries, data flow, interfaces, persistence, dependency injection, and testing strategy.
- [State transitions](state-transitions.md) defines every business transition and its success, retryable failure, permanent failure, and edge-case outcome.
- [Sprint 1 - Foundation](sprints/01-foundation.md)
- [Sprint 2 - Offline data and synchronization](sprints/02-offline-data-and-sync.md)
- [Sprint 3 - Smart user feed](sprints/03-smart-user-feed.md)
- [Sprint 4 - Adaptive add user](sprints/04-adaptive-add-user.md)
- [Sprint 5 - Delete and undo](sprints/05-delete-and-undo.md)
- [Sprint 6 - Adaptive polish and delivery](sprints/06-adaptive-polish-and-delivery.md)

## Fixed decisions

| Area | Decision |
| --- | --- |
| Platforms | Android and iOS through Kotlin Multiplatform |
| Shared UI | Compose Multiplatform with Material 3 |
| Architecture | MVVM with UI, domain, repository, local, remote, and DI boundaries |
| Gradle structure | One `shared` KMP module with strict packages; no premature multi-module split |
| Dependency injection | Koin with constructor injection |
| Networking | Ktor with platform engines |
| Persistence | SQLDelight; the database is the UI source of truth |
| Offline behaviour | Feed caching; create and delete are server-confirmed before the database changes |
| Refresh triggers | Startup, foreground, and connectivity restoration |
| Delete undo | DELETE first, remove locally after 204/404, then offer an in-memory Undo that POSTs a new user |
| Compact layout | Single `LazyColumn` |
| Landscape layout | Two-column `LazyVerticalGrid` |
| Relative timestamp | Time first observed locally, because GoRest supplies no creation timestamp |
| Secret handling | Bearer token injected from ignored local configuration; never committed |

## Engineering invariants

1. SQLDelight is the single source of truth.
2. UI observes database-derived state, never raw network responses.
3. Create and delete are server-confirmed before the database changes.
4. The repository serializes remote calls and transactional database updates.
5. Network and server failures preserve cached data and are retried explicitly.
6. Permanent failures are surfaced clearly.
7. Undo recreates a successfully deleted user through POST.
8. Core domain models do not contain temporary UI or persistence lifecycle flags such as `isDeleted` or `hidden`.
9. Shared logic stays in `commonMain` whenever platform APIs are not required.
10. Every meaningful business rule is independently testable.
11. Every state transition has a defined success, retryable failure, permanent failure, cancellation, and edge-case path where applicable.
12. Compose owns presentation and interaction wiring, not business logic.

## Delivery sequence

Sprints are completed in numeric order. Each sprint must leave the project building and its own acceptance tests passing. A sprint may refine internals from an earlier sprint, but it must not silently change the product or architecture decisions above.

Each sprint is one logical Git unit. Before committing it:

1. Re-read that sprint's acceptance criteria.
2. Verify affected transitions against `state-transitions.md`.
3. Run the sprint's narrow tests and the affected platform build checks.
4. Review the staged diff and exclude unrelated files.
5. Use the meaningful commit message proposed by the sprint document.

## Project definition of done

- All three capabilities work on Android and iOS: feed, add user, and delete with undo.
- The UI displays cached data when offline; mutations require a server response.
- Compact and wider layouts meet their documented adaptive behaviour.
- Loading, empty, offline, retry, validation, and failure states are accessible and polished.
- Shared logic has deterministic tests for success, boundaries, failures, cancellation, and recovery.
- Android debug build, Android host tests, and iOS simulator tests pass.
- No access token, local path, generated build output, or IDE state is committed.
