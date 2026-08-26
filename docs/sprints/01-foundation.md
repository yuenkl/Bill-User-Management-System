# Sprint 1 - Foundation

## Objective

Replace the generated sample with a buildable architectural skeleton shared by Android and iOS. Establish dependencies, package boundaries, Material 3 theme, Koin composition roots, platform configuration, and deterministic test foundations without implementing feature behaviour yet.

## Prerequisites

- Read [product requirements](../product-requirements.md), [architecture](../architecture.md), and [state transitions](../state-transitions.md).
- Confirm the repository is on the intended feature branch and the working tree contains no unrelated changes.
- Obtain a personal GoRest access token for local verification, but never place it in a tracked file.

## Implementation work

### Dependencies

Add compatible versions through `gradle/libs.versions.toml` rather than inline coordinates:

- Ktor client core, content negotiation, Kotlinx JSON serialization, Android OkHttp engine, and iOS Darwin engine.
- Kotlinx serialization, coroutines, and datetime.
- SQLDelight Gradle plugin, common runtime/coroutines support, Android driver, and native driver.
- Koin core, Compose integration, and ViewModel integration needed by current Compose Multiplatform APIs.
- Test coroutine support and any SQLDelight test driver required for common/host tests.

Do not add a connectivity library. Define a small platform abstraction and implement it with platform APIs in the relevant source sets.

### Package skeleton

Create the packages documented in `architecture.md`. Add only contracts and minimal placeholders required to compile. Remove the generated greeting/sample UI once the new shared `App` entry point renders the themed user-feed shell.

### Theme and app shell

- Add a shared Material 3 theme with deliberate light and dark colour schemes.
- Follow system theme by default on both platforms.
- Add a shared `UserFeedRoute` placeholder inside the theme and safe-area handling.
- Keep the placeholder presentation-only; it may forward UI actions but must not perform validation, persistence, networking, or synchronization.
- Do not add Android-only dynamic colour as a dependency of the shared design.

### Koin and configuration

- Define common Koin modules separately from Android/iOS platform modules.
- Create `AppConfig` with `apiToken` and GoRest base URL.
- Android: read `GOREST_ACCESS_TOKEN` from ignored `local.properties`, expose it through generated Android build configuration, and pass it to shared Koin initialization.
- iOS: include an ignored `Secrets.xcconfig` from the tracked configuration, substitute its value into `Info.plist`, and pass it from Swift to shared Koin initialization.
- Track `local.properties.example` and `Secrets.xcconfig.example` containing placeholders only.
- Add ignore rules for the real iOS secret file.
- Missing/blank tokens produce a typed configuration state rather than a crash.

### Platform abstractions

Define common interfaces for:

- Ktor engine creation.
- SQLDelight driver creation.
- Connectivity observation.
- Foreground lifecycle observation.
- Clock/time access when Kotlinx `Clock` cannot be injected directly.

Keep implementations thin and place them in Android/iOS source sets.

## Tests

- Common Koin graph resolves every common dependency with fakes.
- Platform graph smoke tests resolve the Ktor engine and SQL driver where supported.
- Blank API token maps to the documented configuration error.
- Shared theme/app shell composes without the generated sample classes.

## Acceptance criteria

- Android debug build succeeds.
- Android shared host tests succeed.
- iOS simulator test compilation succeeds.
- Android and iOS launch the same Material 3 shell.
- No sample greeting UI remains.
- No real token or machine-specific configuration is tracked.
- No application class performs Koin service-location after startup.

## Out of scope

- SQL tables and real repository logic.
- GoRest requests.
- Feed, form, or delete behaviour.

## Commit boundary

Commit only foundation/configuration changes with:

`build: establish KMP architecture foundation`
