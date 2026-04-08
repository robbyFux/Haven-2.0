# Code Conventions

**Analysis Date:** 2026-03-30

## Naming

**Files:**
- PascalCase for all Kotlin source files matching the primary class/object they contain
- One class/interface per file (enforced by project structure)
- Examples: `FusedMotionMonitor.kt`, `HavenObjectDetector.kt`, `SettingsRepository.kt`
- Screen files end with `Screen`: `MonitorScreen.kt`, `ZoneEditorScreen.kt`
- ViewModel files end with `ViewModel`: `MonitorViewModel.kt`, `DiagnosticsViewModel.kt`
- Entity files end with `Entity`: `EventEntity.kt`, `EventTriggerEntity.kt`
- DAO files end with `Dao`: `EventDao.kt`, `EventTriggerDao.kt`

**Classes and Interfaces:**
- PascalCase throughout: `SensorMonitor`, `LightMonitor`, `CameraAnalyzer`
- Interfaces use noun names (not `I`-prefix): `SensorMonitor` not `ISensorMonitor`
- DI modules end with `Module`: `AppModule`, `DatabaseModule`, `DataStoreModule`
- Hilt components are `object`: `object AppModule`, `object DatabaseModule`

**Functions:**
- camelCase for all functions: `startMonitoring()`, `acquireWakeLock()`, `buildNotification()`
- Private helpers use verb-noun pattern: `buildBitmap()`, `cropLuma()`, `deriveSeverity()`
- Suspend functions have no special suffix — just camelCase: `openEvent()`, `recordTrigger()`
- ViewModel setters use `set` prefix: `setSensitivity()`, `setCameraPosition()`, `setDetectionMode()`

**Variables and Properties:**
- camelCase for all variables
- Private backing `MutableStateFlow` uses `_` prefix: `_noiseFloor`, `_state`, `_calibrationResults`
- Public `StateFlow` exposes without prefix: `noiseFloor`, `state`, `calibrationResults`
- Constants use SCREAMING_SNAKE_CASE in `companion object`: `ACTION_START`, `STOP_COOLDOWN_SECONDS`, `TFLITE_MIN_INTERVAL_MS`
- DataStore preference keys use `KEY_` prefix: `KEY_SENSITIVITY`, `KEY_DETECTION_ZONE`

**Enums:**
- PascalCase enum class, SCREAMING_SNAKE_CASE entries: `Sensitivity.MEDIUM`, `MonitorState.CALIBRATING`
- Enum values have typed constructor params (no bare ints): see `Sensitivity` with `accelerometerMultiplier`, `microphoneThresholdDb`, etc.

**Packages:**
- lowercase, dot-separated, feature-grouped under `org.havenapp.main`:
  - `org.havenapp.main.sensor`
  - `org.havenapp.main.detection`
  - `org.havenapp.main.media`
  - `org.havenapp.main.events`
  - `org.havenapp.main.storage`
  - `org.havenapp.main.storage.dao`
  - `org.havenapp.main.storage.entity`
  - `org.havenapp.main.di`
  - `org.havenapp.main.ui.monitor`
  - `org.havenapp.main.ui.timeline`
  - `org.havenapp.main.ui.settings`
  - `org.havenapp.main.ui.diagnostics`
  - `org.havenapp.main.ui.theme`

## Code Style

**Formatting:**
- No `.editorconfig` or `.ktlint` config found — style is enforced by convention and code review
- Standard Kotlin indentation: 4 spaces
- Opening braces on same line (K&R style)
- Single-expression functions written inline where appropriate: `fun serialize(): String = "$left,$top,$right,$bottom"`
- Trailing lambdas outside parentheses: `dataStore.data.map { prefs -> ... }`
- `Unit` return type omitted on overrides: `override fun onAccuracyChanged(...) = Unit`

**Language Version:**
- Kotlin 2.0.20, JVM target 17
- `entries` used instead of deprecated `values()` on enums: `TriggerType.entries.find { it.id == id }`

**Import Order (observed pattern):**
1. `android.*` and `androidx.*`
2. `dagger.*` / `hilt.*`
3. `kotlinx.*`
4. `org.havenapp.*` (project-internal)
5. `javax.inject.*`
6. `kotlin.*` / standard library

No wildcard imports used.

## Patterns Used

**MVVM with Clean Architecture:**
- `ViewModel` holds only `StateFlow` — no business logic
- Business logic lives in Repositories (`SettingsRepository`, `EventRepository`) and domain classes (`SensorFusionEngine`, `CameraAnalyzer`)
- Composable screens only call ViewModel functions; never repositories directly
- State is read via `collectAsStateWithLifecycle()` — never `collectAsState()`

**StateFlow Pattern for UI State:**
```kotlin
// In ViewModel:
val uiState: StateFlow<SettingsUiState> = combine(...).stateIn(
    viewModelScope, SharingStarted.Eagerly, SettingsUiState()
)
// In Composable:
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

**MutableStateFlow with private backing field:**
```kotlin
private val _noiseFloor = MutableStateFlow<Float?>(null)
val noiseFloor: StateFlow<Float?> = _noiseFloor
```

**Service-level static state via companion object:**
`MonitorService.Companion` holds the app-wide `MutableStateFlow` instances (`_state`, `_countdownSeconds`, `_calibrationResults`). ViewModels read directly from `MonitorService.state` — no event bus or broadcast.

**callbackFlow for sensor streams:**
All `SensorMonitor` implementations use `callbackFlow { ... awaitClose { ... } }` to bridge the Android `SensorEventListener` callback API into a Kotlin `Flow<TriggerEvent>`. Pattern is consistent across `FusedMotionMonitor`, `LightMonitor`, `MicrophoneMonitor`.

**Graceful degradation:**
- Missing hardware (no gyroscope): `latestGyroMagnitude` stays 0, fused score degrades to 30% accel delta
- Missing TFLite model file: `HavenObjectDetector.isAvailable` returns false, `CameraAnalyzer` falls back to `MOTION_ONLY`
- Missing sensor: flow is closed immediately with `close()`

**runCatching for defensive parsing:**
Used consistently instead of try/catch for operations that should not crash the app:
```kotlin
?.let { runCatching { Sensitivity.valueOf(it) }.getOrNull() } ?: Sensitivity.MEDIUM
```
Also used in `DetectionZone.fromString()`, `HavenObjectDetector.initialize()`, `buildBitmap()`.

**Enum as single source of truth:**
`Sensitivity` enum carries all per-level numeric thresholds as constructor parameters. No `when` expressions scattered through sensor code — each monitor reads `sensitivity.microphoneThresholdDb` etc. directly. This prevents the `Integer.parseInt("Off")` bug from Haven 0.2.1.

**data class for UI state:**
Each screen has a dedicated `*UiState` data class:
- `SettingsUiState` in `SettingsViewModel.kt`
- `DiagnosticsUiState` in `DiagnosticsViewModel.kt`
UI state is never a raw primitive or a list of individual StateFlows in the Composable.

**Hilt DI:**
- `@Singleton` on all monitors and repositories
- `@HiltViewModel` on all ViewModels
- `@AndroidEntryPoint` on `MonitorService` and `MainActivity`
- System services (e.g. `SensorManager`) provided via `@Module` objects in `di/`
- `@ApplicationContext` qualifier used when context is needed in singletons

**Room DAO pattern:**
- DAOs are interfaces annotated with `@Dao`
- Reactive queries return `Flow<List<...>>` for live observation
- Mutating operations are `suspend fun`
- No raw SQL for inserts/updates where annotations suffice (`@Insert`, `@Query` for custom logic only)

**Navigation:**
- All routes defined as `const val` in `Routes` object in `HavenNavGraph.kt`
- Helper functions for parameterized routes: `Routes.eventDetail(eventId: Long)`
- Navigation uses `popUpTo + saveState + restoreState` pattern for bottom nav tab switching

**Compose UI conventions:**
- All screens use `Scaffold + CenterAlignedTopAppBar`
- Cards use `shape = RoundedCornerShape(12.dp)` and `surfaceVariant` container
- Lists use `LazyColumn`, never `Column`
- Sensor/timestamp values: `FontFamily.Monospace`
- `Spacer(Modifier.height(X.dp))` for spacing — not `weight(1f)` as spacer
- Icons from `Icons.Filled.*` only
- `contentDescription` on all icons

## Error Handling

**No exception throwing in business logic:**
Errors are expressed as nullable returns or `Result<*>` rather than thrown exceptions.

**`runCatching` as boundary:**
All I/O and parsing operations use `runCatching { }.getOrNull()` or `getOrDefault(...)`:
- DataStore enum deserialization: `runCatching { Sensitivity.valueOf(it) }.getOrNull()`
- TFLite model loading: `runCatching { ... }.onFailure { initError = ... }`
- Bitmap conversion: `runCatching { ... }.getOrNull()`
- Logcat read: `runCatching { ... }.getOrDefault(listOf("(logcat unavailable)"))`

**Sensor unavailability:**
If a sensor is null, the `Flow` is simply closed: `close(); return@callbackFlow`. No exception emitted.

**Camera setup:**
`runCatching { cameraProvider.unbindAll(); cameraProvider.bindToLifecycle(...) }` — errors silently ignored (graceful degradation).

**Logging on errors:**
`android.util.Log` used directly in `HavenObjectDetector`:
- `Log.w(TAG, ...)` for warnings (model not found)
- `Log.i(TAG, ...)` for successful init
- `Log.e(TAG, "...", it)` for inference failures with full exception

**No centralized error handler** — each class manages its own failure paths.

## Logging

**Framework:** `android.util.Log` used directly (no wrapper library)

**Pattern:**
- Only `HavenObjectDetector` uses structured logging with a `TAG` constant: `private const val TAG = "HavenObjectDetector"`
- Other classes do not log — sensor monitors and the service emit state via Flow/StateFlow instead
- Diagnostics screen surfaces logs to the user via `DiagnosticsViewModel.refreshLogs()` which reads `logcat` process output (WARN level and above, last 200 lines, filtered to app PID)

**Levels used:**
- `Log.i` — successful model initialization
- `Log.w` — soft failures (model file missing, load failure)
- `Log.e` — inference-time exceptions

**New code should:** add a `TAG` constant and use the same three-level pattern when adding classes that perform I/O or external library calls.

## Documentation

**KDoc style:**
Block KDoc (`/** ... */`) used on all public classes, interfaces, and non-obvious public functions. Single-line `//` comments for inline implementation notes.

**What is documented:**
- Class-level KDoc on every domain class explaining its purpose and algorithm: `SensorFusionEngine`, `PerceptualHashDetector`, `CameraAnalyzer`, `LightMonitor`, `FusedMotionMonitor`, `HavenObjectDetector`
- `@param` tags on public functions with non-obvious parameters
- Companion object constants documented inline when their value needs justification (e.g. EMA alpha, cooldown values)
- Algorithm rationale in comments: numerical thresholds explained with real-world examples (e.g. "Sonnenauf/-untergang (~0.5 lux/s) erzeugt nur ~5 lux EMA-Drift")

**Language:**
- KDoc and inline comments are mixed German/English. Class-level docs trend English; inline algorithm notes trend German. New code should be consistent within a file.

**What is NOT documented:**
- `@return` tags often omitted on simple functions
- No module-level `package-info` files
- No generated API docs setup
