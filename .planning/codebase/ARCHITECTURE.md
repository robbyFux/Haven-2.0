# Architecture

**Analysis Date:** 2026-03-30

## Pattern

**MVVM + Clean Architecture** on Android (Kotlin).

UI layer (Jetpack Compose + ViewModels) is fully decoupled from domain/data layers.
Business logic lives in a `LifecycleService` (`MonitorService`) that drives sensor pipelines
via Kotlin Coroutines and Flow. The UI observes `StateFlow`s; it never calls sensor or storage
APIs directly.

**Key Characteristics:**
- Unidirectional data flow: sensors → `Flow<TriggerEvent>` → `MonitorService` → Room + `StateFlow` → ViewModel → Compose UI
- `MonitorService` is the single runtime coordinator; it owns the sensor lifecycle
- Service state is exposed as process-wide `companion object` `StateFlow`s (not injected), so ViewModels can observe it without holding a reference to the service
- Hilt provides all singleton dependencies; no manual object graphs
- DataStore (not SharedPreferences) for all persistent settings

---

## Layers

**UI Layer:**
- Purpose: render state, accept user actions, navigate between screens
- Location: `app/src/main/java/org/havenapp/main/ui/`
- Contains: Composable screens, ViewModels (`@HiltViewModel`), theme, nav graph
- Depends on: ViewModel only; never calls repositories or sensors directly
- Observes: `StateFlow` / `Flow` collected with `collectAsStateWithLifecycle()`

**Service Layer (Runtime Coordinator):**
- Purpose: orchestrate the monitoring session lifecycle — countdown → calibration → active sensing
- Location: `app/src/main/java/org/havenapp/main/MonitorService.kt`
- Contains: `MonitorService` (`LifecycleService`, `@AndroidEntryPoint`)
- Depends on: `EventRepository`, `SettingsRepository`, `FusedMotionMonitor`, `LightMonitor`, `MicrophoneMonitor`, `CameraAnalyzer`, `HavenObjectDetector`
- Exposes: companion-object `StateFlow`s (`state`, `countdownSeconds`, `calibrationSecondsRemaining`, `calibrationResults`) readable by any ViewModel

**Sensor Layer:**
- Purpose: wrap Android hardware into cold `Flow<TriggerEvent>` streams with built-in warmup/calibration
- Location: `app/src/main/java/org/havenapp/main/sensor/`
- Contains: `SensorMonitor` interface + `FusedMotionMonitor`, `LightMonitor`, `MicrophoneMonitor` implementations; legacy `AccelerometerMonitor`, `GyroscopeMonitor` (kept but not wired)
- Depends on: Android `SensorManager`, `SensorFusionEngine`
- Contract: `SensorMonitor.observe(sensitivity, warmupMs): Flow<TriggerEvent>` — flow is cold, unregisters listeners on collection cancellation

**Media / Camera Layer:**
- Purpose: per-frame camera analysis via CameraX `ImageAnalysis`
- Location: `app/src/main/java/org/havenapp/main/media/`
- Contains: `CameraAnalyzer` (implements `ImageAnalysis.Analyzer`), `LuminanceMotionDetector`
- Depends on: `PerceptualHashDetector`, `HavenObjectDetector`, `DetectionZone`, `Sensitivity`
- Exposes: `CameraAnalyzer.events: Flow<TriggerEvent>` backed by a `Channel`

**Detection Layer:**
- Purpose: detection algorithms and configuration types; no Android framework dependencies
- Location: `app/src/main/java/org/havenapp/main/detection/`
- Contains: `SensorFusionEngine` (complementary filter), `PerceptualHashDetector` (aHash), `HavenObjectDetector` (TFLite wrapper), `DetectionMode`, `DetectionZone`
- Depends on: TFLite Task Library for `HavenObjectDetector`; pure Kotlin for others

**Events Layer:**
- Purpose: shared domain types emitted by sensors and persisted to storage
- Location: `app/src/main/java/org/havenapp/main/events/`
- Contains: `TriggerEvent` (data class), `TriggerType` (enum, IDs 0–15), `Severity` (LOW/MEDIUM/HIGH/CRITICAL)
- Depends on: nothing (pure Kotlin)

**Storage Layer:**
- Purpose: persist events and settings; provide reactive `Flow` access
- Location: `app/src/main/java/org/havenapp/main/storage/`
- Contains: `HavenDatabase` (Room), `EventRepository`, `SettingsRepository`, DAOs, entities
- Depends on: Room, DataStore
- Used by: `MonitorService` (write path), ViewModels (read path via repository flows)

**DI Layer:**
- Purpose: wire singletons via Hilt modules
- Location: `app/src/main/java/org/havenapp/main/di/`
- Contains: `AppModule` (SensorManager), `DatabaseModule` (Room, DAOs), `DataStoreModule` (DataStore)

---

## Data Flow

**Sensor trigger path:**

1. Android hardware callback fires (SensorEventListener or AudioRecord poll)
2. `SensorMonitor` implementation computes fused score / dB / lux delta
3. During warmup (`warmupMs`): samples accumulated for noise-floor calculation (90th percentile)
4. After warmup: threshold crossed → `TriggerEvent` emitted into `callbackFlow`
5. `MonitorService` collects merged `Flow` of all enabled sensor + camera flows
6. After calibration delay: `EventRepository.openEvent()` creates a DB row; subsequent triggers are recorded via `EventRepository.recordTrigger(eventId, trigger)`
7. On stop: last `STOP_COOLDOWN_SECONDS = 30` of triggers are deleted before closing the event

**Camera analysis path (3-stage pipeline):**

1. CameraX delivers `ImageProxy` frames to `CameraAnalyzer.analyze()` on a dedicated executor thread
2. Stage 1 — `LuminanceMotionDetector`: pixel-wise luma diff → fraction of changed pixels; gates further processing
3. Stage 2 — `PerceptualHashDetector`: 8×8 aHash, Hamming distance ≥ 4 required (unless luma diff is 2× threshold, which bypasses pHash)
4. Stage 3 — `HavenObjectDetector` (TFLite, only if `DetectionMode.requiresML` and model loaded): EfficientDet Lite 0 → COCO labels mapped to `CAMERA_PERSON` / `CAMERA_PET` / `CAMERA_VEHICLE`
5. Result sent to `CameraAnalyzer.events` `Channel`; `MonitorService` collects it in the merged flow
6. If no TFLite model: graceful fallback to generic `CAMERA` trigger type

**Settings read path:**

- `SettingsRepository` exposes each setting as a `Flow` backed by DataStore
- `MonitorService` reads settings once at session start via `.first()` (snapshot)
- ViewModels hold settings as `StateFlow` via `.stateIn(viewModelScope, ...)`

**UI state path:**

- User taps "Start" → `MonitorViewModel.startMonitoring()` → `Intent(ACTION_START)` → `startForegroundService()`
- `MonitorService.state` (`companion MutableStateFlow`) updates: IDLE → COUNTDOWN → CALIBRATING → ACTIVE
- `MonitorViewModel` exposes `MonitorService.state` as a `StateFlow` collected by `MonitorScreen`
- UI re-composes on state changes; no polling

---

## Key Abstractions

**`SensorMonitor` interface** (`app/src/main/java/org/havenapp/main/sensor/SensorMonitor.kt`):
- Single method: `observe(sensitivity: Sensitivity, warmupMs: Long): Flow<TriggerEvent>`
- All three active sensor monitors implement this interface
- `MonitorService` treats all sensors uniformly: collect the merged flow

**`TriggerEvent` data class** (`app/src/main/java/org/havenapp/main/events/TriggerEvent.kt`):
- The universal event token flowing from sensors → service → repository
- Fields: `type: TriggerType`, `timestamp`, `sensorValue: Float?`, `mediaPath: String?`, `severity: Severity`

**`Sensitivity` enum** (`app/src/main/java/org/havenapp/main/sensor/Sensitivity.kt`):
- Single source of truth for all sensor thresholds: `accelerometerMultiplier`, `microphoneThresholdDb`, `lightDeltaLux`, `cameraMotionThreshold`
- Eliminates the `parseInt("Off")` bug class from Haven 0.2.1
- Values: `OFF`, `LOW(6.5, 70dB, 100lux, 0.22)`, `MEDIUM(4.5, 60dB, 60lux, 0.12)`, `HIGH(2.5, 50dB, 30lux, 0.06)`

**`MonitorState` enum** (`app/src/main/java/org/havenapp/main/sensor/MonitorState.kt`):
- State machine states: `IDLE` → `COUNTDOWN` → `CALIBRATING` → `ACTIVE`
- Exposed as `MonitorService.state: StateFlow<MonitorState>` (companion object)

**`DetectionZone` data class** (`app/src/main/java/org/havenapp/main/detection/DetectionZone.kt`):
- Normalized coordinates (0.0–1.0); `toPixelBounds(w, h)` converts at analysis time
- Serialized as `"left,top,right,bottom"` string in DataStore
- `CameraAnalyzer.cropLuma()` applies zone before both luma and pHash detectors — detectors are zone-unaware

**`HavenObjectDetector`** (`app/src/main/java/org/havenapp/main/detection/HavenObjectDetector.kt`):
- `@Singleton`, lazy `initialize()` call
- Graceful degradation: if `efficientdet_lite0.tflite` is absent → `isAvailable = false`, `initError` set, no crash
- `availabilityFlow: StateFlow<Boolean>` for reactive UI updates

---

## State Management

**Service state** (process-wide, accessible without binding):
- `MonitorService.state: StateFlow<MonitorState>` — current lifecycle phase
- `MonitorService.countdownSeconds: StateFlow<Int>` — live countdown display
- `MonitorService.calibrationSecondsRemaining: StateFlow<Int>` — calibration progress
- `MonitorService.calibrationResults: StateFlow<CalibrationResults?>` — noise-floor values after warmup

**Per-sensor noise-floor state:**
- `FusedMotionMonitor.noiseFloor: StateFlow<Float?>` — null until warmup finishes; reset to null on each `observe()` call
- `LightMonitor.emaBaseline: StateFlow<Float?>` — same pattern

**Settings state:**
- All settings are `Flow<T>` in `SettingsRepository` backed by DataStore; persisted across process restarts

**ViewModel state:**
- Each ViewModel converts repository/service flows into `StateFlow` via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), default)`
- UI state exposed as a single `uiState: StateFlow<XxxUiState>` data class where multiple flows are combined (see `DiagnosticsViewModel`)

---

## Dependency Injection

**Framework:** Hilt (`@HiltAndroidApp` on `HavenApplication`, `@AndroidEntryPoint` on `MonitorService` and `MainActivity`, `@HiltViewModel` on all ViewModels)

**Modules:**
- `AppModule` (`di/AppModule.kt`): provides `SensorManager` as `@Singleton`
- `DatabaseModule` (`di/DatabaseModule.kt`): provides `HavenDatabase`, `EventDao`, `EventTriggerDao` as singletons
- `DataStoreModule` (`di/DataStoreModule.kt`): provides `DataStore<Preferences>` as `@Singleton`

**Singleton scope:**
- `EventRepository`, `SettingsRepository`, `FusedMotionMonitor`, `LightMonitor`, `MicrophoneMonitor`, `HavenObjectDetector` — all `@Singleton @Inject constructor`

**Service injection:**
- `MonitorService` receives `EventRepository`, `SettingsRepository`, `HavenObjectDetector`, `FusedMotionMonitor`, `LightMonitor`, `MicrophoneMonitor` via `@Inject lateinit var`

---

## Error Handling

**Strategy:** fail-silent with graceful degradation, no crashes on missing optional hardware/assets.

**Patterns:**
- `runCatching { }.getOrNull()` / `getOrDefault()` used in `HavenObjectDetector`, `DetectionZone.fromString()`, `CameraAnalyzer.buildBitmap()`
- Sensor absence: `FusedMotionMonitor` closes the flow if no accelerometer; gyroscope absence sets `latestGyroMagnitude = 0` (fusion still works)
- TFLite model absence: `HavenObjectDetector.isAvailable` stays false; `CameraAnalyzer` falls back to generic `CAMERA` trigger type
- Camera binding failures: wrapped in `runCatching {}` in `MonitorService.startCamera()`
- DataStore reads: all settings have `?: default` fallbacks; enum parsing uses `runCatching { Sensitivity.valueOf(it) }.getOrNull() ?: Sensitivity.MEDIUM`

---

*Architecture analysis: 2026-03-30*
