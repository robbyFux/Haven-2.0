<!-- GSD:generated -->
<!-- generated-by: gsd-doc-writer -->

# Haven 2.0 Development Guide

## Environment Setup

### Android

| Tool | Version |
|---|---|
| Android Studio | Current stable |
| JDK | 17 (required — `jvmTarget = "17"`) |
| Android SDK | compileSdk 35, minSdk 26 |
| Kotlin | 2.0.20 |
| AGP | 8.6.0 |

Clone the repo and open the root in Android Studio. The Gradle wrapper (`gradlew`) is checked in — no separate Gradle installation needed.

```bash
git clone <repo-url>
cd "Haven 2.0"
./gradlew :app:assembleDebug   # verify the build works
```

JVM heap is set to 2 GB in `gradle.properties` (`org.gradle.jvmargs=-Xmx2048m`). Parallel builds are enabled by default.

**Optional: TFLite model**

The EfficientDet Lite 0 model (~4 MB) must be placed in `app/src/main/assets/` manually. Without it the app runs in motion-only mode (no crash).

```bash
curl -L -o app/src/main/assets/efficientdet_lite0.tflite \
  "https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/object_detection/android/lite-model_efficientdet_lite0_detection_metadata_1.tflite"
```

---

### Python Server

| Tool | Version |
|---|---|
| Python | >= 3.12 |
| Docker + Docker Compose | Current stable |

```bash
cd server
cp .env.example .env          # edit values as needed
docker compose up -d          # starts db, redis, app (8000), worker, webui (8080)
```

To run the server stack without Docker (for development iteration):

```bash
cd server
pip install -e ".[dev,ml,web]"
alembic upgrade head          # apply all migrations
uvicorn app.main:app --reload --port 8000   # FastAPI
# separate terminal:
celery -A app.celery_app worker --loglevel=info --concurrency=2
# separate terminal:
cd webui && python manage.py runserver 0.0.0.0:8080
```

See `docs/CONFIGURATION.md` for the full list of required environment variables.

---

## Android Build Commands

| Command | Description | Notes |
|---|---|---|
| `./gradlew :app:compileDebugKotlin` | Compile Kotlin only | ~35 s with daemon; use for fast syntax checks |
| `./gradlew :app:assembleDebug` | Full debug APK | Output: `app/build/outputs/apk/debug/` |
| `./gradlew :app:assembleRelease` | Release APK (minified + shrunk) | Requires signing config |
| `./gradlew :app:installDebug` | Build and install to connected device | Requires `adb` in `PATH` |
| `./gradlew :app:test` | Unit tests | JVM, no device needed |
| `./gradlew :app:connectedDebugAndroidTest` | Instrumented tests | Requires connected device or emulator |

The `debug` build appends `.debug` to the application ID (`org.havenapp.main.debug`), so debug and release builds can coexist on the same device.

---

## Android Project Structure

```
app/src/main/java/org/havenapp/main/
├── MainActivity.kt              # @HiltAndroidApp entry point (AppCompatActivity)
├── MonitorService.kt            # Foreground service; state machine coordinator
├── HavenApplication.kt          # @HiltAndroidApp
│
├── sensor/                      # Hardware sensor wrappers
├── media/                       # Camera analysis and video recording
├── detection/                   # Detection algorithms (no Android deps)
├── events/                      # Pure Kotlin domain types
├── storage/                     # Room database + DataStore + AppLogger
├── notify/                      # Notification channels and router
├── security/                    # AES-GCM encryption + PIN lock
├── di/                          # Hilt modules
└── ui/                          # Jetpack Compose screens and ViewModels
```

### Module Responsibilities

**`sensor/`** — Each monitor implements `SensorMonitor` and emits a cold `Flow<TriggerEvent>`. `FusedMotionMonitor` (accelerometer + gyroscope via complementary filter) is the active motion monitor. `LightMonitor` uses an EMA baseline. `MicrophoneMonitor` uses absolute dB thresholds. Legacy `AccelerometerMonitor` and `GyroscopeMonitor` remain but are not wired into `MonitorService`.

**`media/`** — `CameraAnalyzer` implements `ImageAnalysis.Analyzer` and runs the 3-stage detection pipeline (luma diff → pHash → MediaPipe TFLite). `ClipRecorder` wraps CameraX `VideoCapture` for fixed-duration MP4 recording.

**`detection/`** — Pure algorithm code: `SensorFusionEngine` (complementary filter), `PerceptualHashDetector` (8×8 aHash), `HavenObjectDetector` (MediaPipe wrapper), `DetectionZone`, `DetectionMode`.

**`events/`** — Pure Kotlin. `TriggerEvent`, `TriggerType` (16 values, IDs 0–15), `Severity` (LOW/MEDIUM/HIGH/CRITICAL). No Android framework imports.

**`storage/`** — `HavenDatabase` (Room, two entities), `EventRepository`, `SettingsRepository` (DataStore), `AppLogger` (ring buffer, 500 entries, `StateFlow`).

**`notify/`** — `HavenAlertChannel` interface + four implementations: `CloudChannel`, `MattermostChannel`, `SignalRestChannel`, `PushoverChannel`. `NotificationRouter` evaluates `NotificationRule` and dispatches to enabled channels.

**`security/`** — `MediaEncryptionManager` (AES-256-GCM, Android Keystore, key alias `haven_media_key`), `PinHashManager` (PBKDF2), `AppLockState`.

**`di/`** — Four Hilt modules: `AppModule` (SensorManager), `DatabaseModule` (Room + DAOs), `DataStoreModule` (DataStore), `NetworkModule` (OkHttpClient).

**`ui/`** — One screen per nav destination. All screens use `Scaffold + CenterAlignedTopAppBar`. ViewModels use `@HiltViewModel`. State is collected with `collectAsStateWithLifecycle()`.

---

## Adding a New Sensor Monitor

1. **Create the file** in `sensor/`, e.g. `BarometerMonitor.kt`.

2. **Implement `SensorMonitor`:**

```kotlin
@Singleton
class BarometerMonitor @Inject constructor(
    private val sensorManager: SensorManager,
) : SensorMonitor {

    override fun observe(
        sensitivity: Sensitivity,
        warmupMs: Long,
        expert: ExpertThresholds,
    ): Flow<TriggerEvent> {
        if (sensitivity == Sensitivity.OFF) return emptyFlow()

        return callbackFlow {
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
            if (sensor == null) { close(); return@callbackFlow }

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    // derive TriggerEvent from event.values[0]
                    val trigger = TriggerEvent(
                        type = TriggerType.PRESSURE,
                        sensorValue = event.values[0],
                        severity = Severity.MEDIUM,
                    )
                    trySend(trigger)
                }
                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }

            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            awaitClose { sensorManager.unregisterListener(listener) }
        }
    }
}
```

Key rules:
- Annotate with `@Singleton` and `@Inject constructor`.
- Return `emptyFlow()` when `sensitivity == Sensitivity.OFF`.
- Use `callbackFlow { ... awaitClose { unregisterListener(...) } }` — sensor listeners are unregistered on flow cancellation.
- If hardware is unavailable, call `close()` immediately without crashing.
- Read thresholds from the `Sensitivity` enum (or `ExpertThresholds` overrides). Do not hardcode numeric values in the monitor.

3. **Add a `TriggerType` entry** in `events/TriggerType.kt` if using a new event type.

4. **Inject into `MonitorService`** via `@Inject lateinit var`:

```kotlin
@Inject lateinit var barometerMonitor: BarometerMonitor
```

Then merge its flow alongside the others in `MonitorService.startMonitoring()`.

5. **Add the required permission** to `AndroidManifest.xml` if the sensor needs one.

---

## Adding a New Notification Channel

1. **Create the file** in `notify/`, e.g. `TelegramChannel.kt`.

2. **Implement `HavenAlertChannel`:**

```kotlin
class TelegramChannel(
    private val httpClient: OkHttpClient,
    private val botToken: String,
    private val chatId: String,
) : HavenAlertChannel {

    override val id: String = "telegram"
    override val isEnabled: Boolean = botToken.isNotBlank() && chatId.isNotBlank()
    // Set deferresToVideo = true if this channel should wait for a video clip
    // before sending; NotificationRouter will call send() from uploadVideo() instead.
    override val deferresToVideo: Boolean = false

    override suspend fun send(
        event: TriggerEvent,
        attachment: ByteArray?,
        attachmentMime: String?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // build and execute OkHttp request
        }
    }

    override suspend fun sendHeartbeat(message: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                // heartbeat-specific request
            }
        }
}
```

Key rules:
- `isEnabled` must return `false` when required config values are blank — `NotificationRouter` checks this before calling `send()`.
- Wrap the network call in `runCatching { }` and dispatch on `Dispatchers.IO`.
- `id` must be unique across all channels.
- If the channel needs to wait for a video clip, set `deferresToVideo = true`; `NotificationRouter` will route it through `uploadVideo()` instead of `route()`.

3. **Expose the channel settings** as DataStore keys in `SettingsRepository`. Follow the `KEY_` prefix convention (e.g., `KEY_TELEGRAM_BOT_TOKEN`).

4. **Instantiate the channel** in `MonitorService.buildChannels()` (or equivalent init block) using settings snapshots:

```kotlin
val telegram = TelegramChannel(httpClient, settings.telegramBotToken, settings.telegramChatId)
notificationRouter.initialize(rule, listOf(..., telegram))
```

5. **Add the channel settings to `SettingsScreen`** following the same pattern as the existing Mattermost/Signal/Pushover entries.

---

## Python Server Development

### Running Tests

```bash
cd server
pip install -e ".[dev]"
pytest                        # all tests
pytest tests/test_auth.py     # single file
pytest -x                     # stop on first failure
```

Test configuration is in `pyproject.toml`:
```toml
[tool.pytest.ini_options]
asyncio_mode = "auto"
```

Async test support requires `pytest-asyncio >= 1.0`. An in-memory SQLite engine (`aiosqlite`) is used for test database isolation.

### Database Migrations (Alembic)

```bash
cd server

# Apply all pending migrations
alembic upgrade head

# Create a new migration after changing a SQLAlchemy model
alembic revision --autogenerate -m "add_my_column"

# Roll back one step
alembic downgrade -1
```

Migration scripts live in `server/alembic/versions/`. The current schema is established by the `initial_schema` migration; subsequent revisions are additive.

### Adding a New API Route

1. Create a router file in `server/app/routers/`, e.g. `clips.py`.
2. Register it in `server/app/main.py` via `app.include_router(clips.router, prefix="/api/v1/clips")`.
3. Add SQLAlchemy model in `server/app/models/` and Pydantic schemas in `server/app/schemas/`.
4. Generate and apply a migration: `alembic revision --autogenerate -m "add_clips_table" && alembic upgrade head`.

---

## Code Conventions

### Naming

| Category | Convention | Example |
|---|---|---|
| Source files | PascalCase matching class name | `FusedMotionMonitor.kt` |
| Screens | Suffix `Screen` | `MonitorScreen.kt` |
| ViewModels | Suffix `ViewModel` | `MonitorViewModel.kt` |
| Room entities | Suffix `Entity` | `EventEntity.kt` |
| DAOs | Suffix `Dao` | `EventDao.kt` |
| Hilt modules | Suffix `Module`, declared as `object` | `object DatabaseModule` |
| Constants | SCREAMING_SNAKE_CASE in `companion object` | `ACTION_START`, `STOP_COOLDOWN_SECONDS` |
| DataStore keys | `KEY_` prefix | `KEY_SENSITIVITY`, `KEY_DETECTION_ZONE` |
| Private `MutableStateFlow` | `_` prefix | `_noiseFloor`, `_state` |
| Public `StateFlow` | No prefix | `noiseFloor`, `state` |

### Kotlin Style

- Standard 4-space indentation, K&R braces.
- Single-expression functions written inline: `fun serialize(): String = "$left,$top,$right,$bottom"`
- `Unit` return type omitted on overrides: `override fun onAccuracyChanged(...) = Unit`
- Use `entries` instead of deprecated `values()` on enums.
- Trailing lambdas outside parentheses.
- No new Java files — Kotlin only.

### Hilt Patterns

- All singletons: `@Singleton @Inject constructor(...)`.
- All ViewModels: `@HiltViewModel @Inject constructor(...)`.
- `MonitorService` and `MainActivity`: `@AndroidEntryPoint` + `@Inject lateinit var`.
- System services (e.g. `SensorManager`) provided via `@Module object` in `di/`, not instantiated manually.
- Use `@ApplicationContext` qualifier when a context is needed in a singleton.

### StateFlow / Compose Patterns

- ViewModels expose `StateFlow`, never `MutableStateFlow`, to the UI.
- Private backing flows use the `_` prefix: `private val _state = MutableStateFlow(...)`.
- All settings have a `?: defaultValue` fallback in DataStore reads; enum parsing uses `runCatching { Sensitivity.valueOf(it) }.getOrNull() ?: Sensitivity.MEDIUM`.
- `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), defaultValue)` in all ViewModels.
- Composables collect state with `collectAsStateWithLifecycle()` — never `collectAsState()`.
- No UI logic in Composables; all state originates from ViewModels.

### Error Handling

- Use `runCatching { }.getOrNull()` / `getOrDefault()` for hardware and I/O operations.
- Sensor absence: close the flow immediately without crashing — callers handle empty flows.
- Missing TFLite model: `HavenObjectDetector.isAvailable` returns `false`; fallback to `MOTION_ONLY` happens in `CameraAnalyzer`.
- `Log.w` for soft failures (model missing, load failure); `Log.e` with full exception for inference errors.
- Do not use `Runtime.exec("logcat")` — unreliable on API 26+.

### Documentation

- KDoc on every domain class explaining its algorithm and purpose.
- `@param` tags on public functions with non-obvious parameters.
- Algorithm rationale as inline comments with real-world examples (e.g., threshold values justified by expected sensor readings).
- Comments may be mixed German/English; stay consistent within a file.

---

## Architectural Constraints

These are non-negotiable project constraints. Do not route around them.

| Constraint | Detail |
|---|---|
| **Kotlin only** | No new Java files anywhere in `app/`. |
| **No Google Cloud / Firebase** | No Firebase Analytics, FCM, AdServices, or any Google Cloud SDK. |
| **No unencrypted media** | Media files must be encrypted via `MediaEncryptionManager` before persistence (Phase 5+). |
| **Hilt for DI** | No manual object graphs; no service locators. All singletons via `@Inject constructor`. |
| **DataStore, not SharedPreferences** | All persistent settings through `SettingsRepository` (DataStore). |
| **CameraX only** | Use `ImageAnalysis` and `VideoCapture` APIs. No direct Camera1/Camera2 usage. |
| **Coroutines + Flow** | No Messenger/Handler IPC. Sensor data flows as cold `Flow<TriggerEvent>`. |
| **Sensitivity enum is the threshold SSoT** | All sensor thresholds come from `Sensitivity` fields. No hardcoded numeric thresholds in monitors or analyzers. |
| **AppCompatActivity for MainActivity** | Required for per-app locale switching via AppCompat locale delegate. |

---

## Key Version Reference

| Dependency | Version |
|---|---|
| AGP | 8.6.0 |
| Kotlin | 2.0.20 |
| KSP | 2.0.20-1.0.25 |
| Compose BOM | 2024.09.03 |
| Hilt | 2.51.1 |
| Room | 2.6.1 |
| CameraX | 1.5.2 |
| MediaPipe Tasks Vision | 0.10.29 |
| Lifecycle | 2.8.6 |
| Coroutines | 1.8.1 |
| Python (server) | >= 3.12 |
| FastAPI | 0.135.3 |
| Django | >= 5.2, < 5.3 |
| SQLAlchemy | 2.0.49 |
| Alembic | 1.18.4 |
| Celery | 5.6.3 |

All Android dependency versions are managed via `gradle/libs.versions.toml` (single source of truth). Python dependency versions are in `server/pyproject.toml`.
