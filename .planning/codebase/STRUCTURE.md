# Project Structure

**Analysis Date:** 2026-03-30

## Root Layout

```
Haven 2.0/
├── app/                        # Single Android application module
│   ├── build.gradle.kts        # App-level build config (deps, sdk versions)
│   └── src/main/
│       ├── assets/             # TFLite model file (efficientdet_lite0.tflite)
│       ├── java/org/havenapp/main/   # All Kotlin source
│       └── res/                # Android resources (strings, drawables, xml)
├── build/                      # Gradle build output (generated, not committed)
├── gradle/wrapper/             # Gradle wrapper JAR + properties
├── referenzen/                 # Reference codebases (read-only, not compiled)
│   ├── haven-0.2.1/            # Original Haven app (Java)
│   └── AlfredCamera/           # Decompiled Alfred APK
├── CLAUDE.md                   # Project context and architecture decisions
└── PROJECT.md                  # Project overview document
```

## Module Breakdown

All production code lives in the single package `org.havenapp.main` under
`app/src/main/java/org/havenapp/main/`.

### Root package (`org.havenapp.main`)

| File | Purpose |
|---|---|
| `HavenApplication.kt` | `@HiltAndroidApp` — Hilt entry point, Application subclass |
| `MainActivity.kt` | `AppCompatActivity` (required for AppCompat locale delegate), hosts Compose content, requests runtime permissions |
| `MonitorService.kt` | `LifecycleService` — foreground service, WakeLock, state machine, merges sensor flows, drives Room writes |

### `sensor/`

Core sensor abstractions and monitor implementations.

| File | Purpose |
|---|---|
| `SensorMonitor.kt` | Interface: `observe(sensitivity, warmupMs): Flow<TriggerEvent>` |
| `MonitorState.kt` | Enum: `IDLE / COUNTDOWN / CALIBRATING / ACTIVE` |
| `Sensitivity.kt` | Enum with threshold parameters for every sensor type (single source of truth) |
| `CameraPosition.kt` | Enum: `BACK / FRONT` |
| `FusedMotionMonitor.kt` | `@Singleton` — accelerometer + gyroscope via complementary filter (Sensor Fusion Engine); exposes `noiseFloor: StateFlow<Float?>` |
| `LightMonitor.kt` | EMA-baseline light sensor monitor; exposes `emaBaseline: StateFlow<Float?>` |
| `MicrophoneMonitor.kt` | Absolute dB-threshold microphone monitor |
| `AccelerometerMonitor.kt` | Legacy (kept, not wired into MonitorService) |
| `GyroscopeMonitor.kt` | Legacy (kept, not wired into MonitorService) |

### `media/`

CameraX image analysis pipeline.

| File | Purpose |
|---|---|
| `CameraAnalyzer.kt` | `ImageAnalysis.Analyzer` — 3-stage pipeline: luma diff → pHash → TFLite; exposes `events: Flow<TriggerEvent>` |
| `LuminanceMotionDetector.kt` | Stage 1: pixel-wise luma comparison, returns fraction of changed pixels |

### `detection/`

Detection algorithms and configuration types (no Android framework dependencies except `HavenObjectDetector`).

| File | Purpose |
|---|---|
| `SensorFusionEngine.kt` | Complementary filter (alpha=0.7): fuses accel delta + gyro magnitude |
| `PerceptualHashDetector.kt` | 8×8 average hash (aHash), Hamming distance — filters brightness flicker |
| `HavenObjectDetector.kt` | `@Singleton` TFLite wrapper (EfficientDet Lite 0); lazy init; graceful degradation if model absent |
| `DetectionMode.kt` | Enum: `MOTION_ONLY / PERSON / PET / VEHICLE / ALL`; flags `requiresML`, `detectsPerson`, etc. |
| `DetectionZone.kt` | Normalized rectangle (0.0–1.0); `toPixelBounds()` and `serialize()` / `fromString()` |

### `events/`

Pure-Kotlin domain types; no Android dependencies.

| File | Purpose |
|---|---|
| `TriggerEvent.kt` | Data class: `type`, `timestamp`, `sensorValue`, `mediaPath`, `severity` |
| `TriggerType.kt` | Enum with integer IDs 0–15 (IDs 0–8 compatible with Haven 0.2.1) |
| `Severity.kt` | Enum: `LOW / MEDIUM / HIGH / CRITICAL` |

### `storage/`

Room database and DataStore persistence.

| File | Purpose |
|---|---|
| `HavenDatabase.kt` | `@Database` — Room database, version 1, tables: `events`, `event_triggers` |
| `EventRepository.kt` | `@Singleton` — wraps `EventDao` + `EventTriggerDao`; `openEvent()`, `recordTrigger()`, `closeEvent()`, `discardTriggersSince()`, reactive `Flow` queries |
| `SettingsRepository.kt` | `@Singleton` — wraps DataStore; exposes each setting as `Flow<T>` with typed defaults |
| `dao/EventDao.kt` | Room DAO: insert, closeEvent, observeRecent, deleteAll |
| `dao/EventTriggerDao.kt` | Room DAO: insert, deleteSince, observeByEvent, observeRecent |
| `entity/EventEntity.kt` | Room entity: `id`, `startTime`, `endTime?` |
| `entity/EventTriggerEntity.kt` | Room entity: `eventId` (FK→events), `type` (TriggerType.id), `timestamp`, `sensorValue`, `mediaPath`, `severity` (Severity.ordinal) |

### `di/`

Hilt dependency injection modules.

| File | Purpose |
|---|---|
| `AppModule.kt` | Provides `SensorManager` as `@Singleton` |
| `DatabaseModule.kt` | Provides `HavenDatabase`, `EventDao`, `EventTriggerDao` |
| `DataStoreModule.kt` | Provides `DataStore<Preferences>` |

### `ui/`

Jetpack Compose UI — screens, ViewModels, navigation, theme.

| File | Purpose |
|---|---|
| `HavenNavGraph.kt` | Root `NavHost` + bottom navigation bar; defines `Routes` object with all route constants |

#### `ui/monitor/`

| File | Purpose |
|---|---|
| `MonitorScreen.kt` | Main screen: state-aware display (idle/countdown/calibrating/active), calibration wizard, start/stop control |
| `MonitorViewModel.kt` | Bridges `MonitorService` companion `StateFlow`s + `SettingsRepository.detectionZone`; sends start/stop intents |

#### `ui/timeline/`

| File | Purpose |
|---|---|
| `TimelineScreen.kt` | `LazyColumn` list of monitoring sessions |
| `TimelineViewModel.kt` | Observes `EventRepository.observeRecentEvents()` |
| `EventDetailScreen.kt` | Detail view for a single session: list of triggers |
| `EventDetailViewModel.kt` | Observes `EventRepository.observeTriggersForEvent(eventId)` |

#### `ui/settings/`

| File | Purpose |
|---|---|
| `SettingsScreen.kt` | Sensitivity, camera position, countdown, calibration duration, detection mode, sensor toggles; links to ZoneEditor and Diagnostics |
| `SettingsViewModel.kt` | Reads/writes all settings via `SettingsRepository` |
| `ZoneEditorScreen.kt` | Live camera preview (`AndroidView(PreviewView)`) + touch-to-draw Canvas overlay for ROI rectangle |
| `ZoneEditorViewModel.kt` | Reads/writes `DetectionZone` via `SettingsRepository` |

#### `ui/diagnostics/`

| File | Purpose |
|---|---|
| `DiagnosticsScreen.kt` | Settings summary, calibration strategy (per-sensor thresholds + runtime noise-floor), trigger log, share export |
| `DiagnosticsViewModel.kt` | Combines `SettingsRepository` + `EventRepository` + `MonitorService.calibrationResults` + `HavenObjectDetector` status into `DiagnosticsUiState` |

#### `ui/theme/`

| File | Purpose |
|---|---|
| `Color.kt` | Severity colors + static fallback palette (teal/dark) |
| `Theme.kt` | `HavenTheme` composable: Material 3, Dynamic Color on API 31+, dark-first |

---

## Key Files

| File | One-Line Description |
|---|---|
| `MonitorService.kt` | Foreground service, session lifecycle state machine, merges all sensor flows |
| `sensor/SensorMonitor.kt` | Core interface: every sensor monitor implements this |
| `sensor/Sensitivity.kt` | Single source of truth for all detection thresholds |
| `media/CameraAnalyzer.kt` | 3-stage luma→pHash→TFLite camera pipeline |
| `detection/HavenObjectDetector.kt` | TFLite EfficientDet wrapper with graceful degradation |
| `detection/DetectionZone.kt` | Normalized ROI rectangle; serialized in DataStore |
| `events/TriggerType.kt` | All 16 trigger type IDs (sensor + ML + audio) |
| `storage/EventRepository.kt` | All event read/write operations including stop-cooldown deletion |
| `storage/SettingsRepository.kt` | DataStore-backed reactive settings access |
| `ui/HavenNavGraph.kt` | All routes and bottom navigation wiring |
| `HavenApplication.kt` | Hilt application entry point |

---

## Entry Points

**Application:**
- `HavenApplication` — `@HiltAndroidApp`, no custom logic; triggers Hilt component generation

**Activity:**
- `MainActivity` — `AppCompatActivity`, `@AndroidEntryPoint`; requests permissions, sets Compose content with `HavenTheme { HavenNavGraph(...) }`

**Service:**
- `MonitorService` — `LifecycleService`, `@AndroidEntryPoint`; started via explicit `Intent` with `ACTION_START` or `ACTION_STOP`; declared with `FOREGROUND_SERVICE_CAMERA` and `FOREGROUND_SERVICE_MICROPHONE` types

**Navigation start destination:**
- `Routes.MONITOR` (`"monitor"`) — `MonitorScreen` is the root of the nav graph

---

## Naming Conventions

**Files:**
- `PascalCase.kt` for all Kotlin files, matching the primary class/object/interface name
- Screens: `XxxScreen.kt` (Composable)
- ViewModels: `XxxViewModel.kt` (`@HiltViewModel`)
- Entities: `XxxEntity.kt` (Room `@Entity`)
- DAOs: `XxxDao.kt` (Room `@Dao`)

**Packages:**
- Feature-first under `org.havenapp.main`: `sensor`, `media`, `detection`, `events`, `storage`, `di`, `ui`
- UI sub-packages by screen: `ui/monitor`, `ui/timeline`, `ui/settings`, `ui/diagnostics`, `ui/theme`

---

## Where to Add New Code

**New sensor monitor:**
- Implement `SensorMonitor` interface in `sensor/`
- Annotate `@Singleton`, inject via constructor
- Wire into `MonitorService.startMonitoring()` sensor flow list
- Add DI binding in `AppModule` if needed

**New detection algorithm:**
- Pure logic class in `detection/`
- No Android framework dependency; reference from `CameraAnalyzer` or a sensor monitor

**New TriggerType:**
- Add to `events/TriggerType.kt` with next sequential ID
- Update `CameraAnalyzer` or the relevant sensor monitor to emit it

**New setting:**
- Add `PreferencesKey` + `Flow<T>` property + `suspend fun setXxx()` in `storage/SettingsRepository.kt`
- Expose in relevant ViewModel via `stateIn()`
- Render in `ui/settings/SettingsScreen.kt`

**New screen:**
- Create `ui/xxx/XxxScreen.kt` + `XxxViewModel.kt`
- Add route constant to `Routes` object in `ui/HavenNavGraph.kt`
- Add `composable(Routes.XXX) { ... }` in `HavenNavGraph`
- Add `NavigationBarItem` only if it belongs in the bottom navigation (primary destinations only)

**New notification channel (Phase 3):**
- Implement `NotificationChannel` interface in `notify/channel/`
- Register in `NotificationRouter`

---

## Special Directories

**`app/src/main/assets/`:**
- Purpose: TFLite model file `efficientdet_lite0.tflite` (~4 MB, EfficientDet Lite 0 COCO)
- Generated: No (must be manually added; see build.gradle.kts comment for curl command)
- Committed: No (large binary; absent = graceful degradation to `MOTION_ONLY`)

**`referenzen/`:**
- Purpose: read-only reference codebases (Haven 0.2.1 Java source, Alfred APK decompiled)
- Generated: No
- Part of compilation: No — excluded from app build

**`.planning/`:**
- Purpose: GSD planning documents (phase plans, codebase analysis)
- Generated: Yes (by Claude GSD tools)
- Committed: Yes

---

*Structure analysis: 2026-03-30*
