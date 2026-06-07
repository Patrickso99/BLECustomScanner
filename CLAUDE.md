# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & run

```bash
./gradlew :androidApp:assembleDebug                  # build Android APK
./gradlew :shared:compileKotlinIosSimulatorArm64     # compile shared for iOS simulator (no compileKotlinAndroid task — use assembleDebug instead)
./gradlew build                                       # all targets
```

iOS: open `iosApp/iosApp.xcodeproj` in Xcode and run. The Kotlin static framework is named **`Shared`**, consumed via `MainViewControllerKt.MainViewController()`.

## Tests

```bash
./gradlew :shared:allTests
./gradlew :shared:testDebugUnitTest                  # Android JVM host tests
./gradlew :shared:iosSimulatorArm64Test              # iOS simulator tests
# Single test:
./gradlew :shared:testDebugUnitTest --tests "com.preichert.blecustomscanner.MyClass.myMethod"
```

## Module structure

Three Gradle modules (`settings.gradle.kts`):
- **`:shared`** — all shared Kotlin, Compose UI, BLE logic, database, DI. Nearly all work happens here.
- **`:androidApp`** — thin host: `MainActivity` initializes Koin, creates `AndroidBleController`, calls `setContent { App(controller) }`.
- **`iosApp`** — Xcode project (not a Gradle module). `ContentView` → `MainViewController` → `App(controller)`.

Source sets inside `:shared`:
- `commonMain` — shared UI, ViewModels, BleController interface, models, repository, Koin common modules.
- `androidMain` — `AndroidBleController`, Room `getDatabaseBuilder`, `bleModule`/`databaseModule` Koin actuals.
- `iosMain` — `IosBleController`, Room iOS builder, Koin actuals, `MainViewController`.

## Architecture

### Data flow (top-down)

```
BleController (interface, commonMain)
    ↑ implemented by AndroidBleController / IosBleController
    ↓ injected into
ScannerViewModel / DeviceDetailViewModel   (commonMain, androidx.lifecycle.ViewModel)
    ↓ single StateFlow<State> + Channel<Event>
ScannerRoot / DeviceDetailRoot             (commonMain, @Composable — owns ViewModel)
    ↓ state + onAction lambda
ScannerScreen / DeviceDetailScreen         (commonMain, @Composable — pure UI, no ViewModel reference)
```

### MVI pattern (Root + Screen split)

Every screen has two composables:
- **Root** (`ScannerRoot`, `DeviceDetailRoot`) — gets the ViewModel via `koinViewModel(parameters = { parametersOf(controller) })`, collects `state` with `collectAsStateWithLifecycle()`, observes `viewModel.events` with `ObserveAsEvents`, passes `state + onAction` down.
- **Screen** (`ScannerScreen`, `DeviceDetailScreen`) — receives `@Immutable` state data class and `onAction: (XxxAction) -> Unit`. No ViewModel reference, no side effects.

Each ViewModel exposes:
- `state: StateFlow<XxxState>` — merged from multiple BleController flows via `combine(...)`
- `events: Flow<XxxEvent>` — `Channel.receiveAsFlow()` for one-shot navigation/side effects
- `fun onAction(action: XxxAction)` — all user intents go here

### BleController — critical Android constraint

`AndroidBleController` calls `registerForActivityResult()` as property initializers (before `init {}`), which means it **must be constructed during `Activity.onCreate()`** and must receive a `ComponentActivity`, not just a `Context`. Because of this:

- The Android Koin `bleModule` uses **`factory { (activity: ComponentActivity) -> AndroidBleController(activity, get()) }`** — NOT `single`, and NOT `androidContext()`.
- `MainActivity` calls `val controller = bleController` eagerly in `onCreate` (before `setContent`) to force registration while the Activity is in CREATED state.
- ViewModels receive the already-constructed controller via `parametersOf(controller)` at the composable call site in `App.kt`'s nav graph extensions — Koin does NOT resolve `BleController` by type from the container for this purpose.

### Koin DI

- `initKoin()` in `commonMain/di/Koin.kt` assembles: `databaseModule` + `repositoryModule` + `bleModule` + `viewModelModule`.
- `databaseModule` and `bleModule` are `expect val` — each platform provides an `actual`.
- `viewModelModule` (common): uses `org.koin.core.module.dsl.viewModel` (NOT `koin-compose-viewmodel.dsl.viewModel`, which is deprecated).
- `koinViewModel` in composables comes from `org.koin.compose.viewmodel.koinViewModel`.

### Persistence (Room + KMP)

`AppDatabase` is an `@Database` abstract class with an `expect` companion factory. KSP generates `AppDatabase_Impl` and `AppDatabaseConstructor` per target (under `build/generated/ksp/`). The `BundledSQLiteDriver` + `IO` dispatcher wires it cross-platform. iOS uses the Documents directory; Android uses the app-private DB directory.

### iOS BLE (CoreBluetooth / Kotlin/Native)

`IosBleController` uses CoreBluetooth via Kotlin/Native interop. Key gotchas:
- The CoreBluetooth delegate **must be a separate inner class extending `NSObject`** (not the controller itself), otherwise the Objective-C runtime won't dispatch delegate callbacks.
- Colliding delegate method overloads (e.g. `didDiscoverCharacteristicsFor` vs `didDiscoverServices`) require `@ObjCSignatureOverride`.
- Keep strong references to discovered `CBPeripheral` objects — CoreBluetooth releases them otherwise.

## Conventions & gotchas

- **Versions centralized** in `gradle/libs.versions.toml`. Reference via `libs.*` — never hardcode.
- **AGP 9** — `:shared` uses `com.android.kotlin.multiplatform.library` plugin with `androidLibrary { }` DSL. There is no `compileKotlinAndroid` task; use `:androidApp:assembleDebug` to verify Android compilation.
- **Type-safe project accessors** — use `projects.shared`, not `project(":shared")`.
- **Configuration cache + build cache are on**. If build logic changes cause stale behavior, add `--no-configuration-cache` to debug.
- **iOS targets**: `iosArm64` (device) + `iosSimulatorArm64` (Apple Silicon simulator) only — no Intel simulator.
- **Compose resources** go under `shared/src/commonMain/composeResources`; access via the generated `Res` class.
- **`@Immutable` state classes** — all `XxxState` data classes are annotated `@Immutable` to help Compose skip recomposition. Keep all fields deeply immutable.
- **`isDark` theme toggle** lives in `App.kt`'s local `remember` (not in a ViewModel) because it wraps the entire `NavHost`/`BleScannerTheme`. Pass it as a plain `Boolean` (not `() -> Boolean`) to avoid stability issues.
