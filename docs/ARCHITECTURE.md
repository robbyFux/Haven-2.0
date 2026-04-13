<!-- GSD:generated -->
<!-- generated-by: gsd-doc-writer -->

# Haven 2.0 Architecture

## System Overview

Haven 2.0 is a privacy-first, local-first Android security application paired with an optional
self-hosted cloud backend. The Android app (Kotlin, minSdk 26, targetSdk 35) runs a foreground
`MonitorService` that fuses data from multiple hardware sensors — accelerometer, gyroscope,
microphone, light sensor, and camera — into a unified stream of `TriggerEvent` values. Confirmed
events are persisted to an on-device Room database, optionally encrypted with AES-256-GCM via the
Android Keystore, and routed to one or more notification channels (Signal, Mattermost, Pushover,
or the self-hosted cloud server). The optional server component is a FastAPI (REST API) + Django
(Web UI) stack backed by PostgreSQL and Redis/Celery, containerised with Docker Compose. No Google
Cloud services, Firebase, or third-party analytics are used anywhere in the system.

---

## Component Diagram

```
Android App                               Self-Hosted Server
───────────────────────────────────       ──────────────────────────────────────────
 Sensors ─────────────────────────┐       ┌─ FastAPI (port 8000)
  FusedMotionMonitor (Accel+Gyro) │       │    /api/v1/auth        (JWT + TOTP)
  LightMonitor (EMA baseline)     │       │    /api/v1/devices     (App-Key mgmt)
  MicrophoneMonitor (dB abs.)     │       │    /api/v1/devices/{key}/events  ◄── CloudChannel
  CameraAnalyzer                  │       │    /api/v1/events      (list/get/delete)
    └─ LuminanceMotionDetector    │       │    /api/v1/notifications
    └─ PerceptualHashDetector     │       │    /api/v1/admin
    └─ HavenObjectDetector (MP)   │       │
                                  │       ├─ Celery Worker
                    Flow<TriggerEvent>    │    analyze_event_task (TFLite / OpenRouter)
                                  │       │    send_notification_task (email/Signal/Pushover)
 MonitorService (Foreground Svc)  │       │
  │  merge(sensor flows)          │       ├─ Django WebUI (port 8080)
  │  → EventRepository (Room)     │       │    HTMX + Tailwind CSS
  │  → NotificationRouter         │       │    Events / Notifications / Admin
  │      ├─ CloudChannel ─────────┼──────►│
  │      ├─ MattermostChannel     │       ├─ PostgreSQL 16
  │      ├─ SignalRestChannel     │       │
  │      └─ PushoverChannel       │       └─ Redis 7
                                  │           (Celery broker + result backend)
 UI Layer (Jetpack Compose)       │
  MonitorScreen                   │
  TimelineScreen                  │
  SettingsScreen ─ ZoneEditorScreen│
  DiagnosticsScreen               │
  ExpertSettingsScreen            │
  PinLockScreen                   │
```

---

## Data Flow

### Android: Sensor Event Path

1. User taps "Start" → `MonitorViewModel.startMonitoring()` fires `Intent(ACTION_START)` → `startForegroundService(MonitorService)`.
2. `MonitorService` reads settings snapshot from `SettingsRepository` (DataStore) via `.first()`.
3. Service transitions through the state machine: `IDLE → COUNTDOWN → CALIBRATING → ACTIVE`.
4. During `CALIBRATING` (default 10 s), each sensor monitor samples its noise floor:
   - `FusedMotionMonitor`: collects accel samples, sets `noiseFloor` to the 90th percentile.
   - `LightMonitor`: seeds the EMA baseline (`alpha = 0.02`).
5. In `ACTIVE`, `MonitorService` merges all sensor `Flow<TriggerEvent>` streams into a single flow.
6. On each `TriggerEvent`: media clip is recorded by `ClipRecorder` (CameraX VideoCapture), file is optionally encrypted by `MediaEncryptionManager` (AES-256-GCM, Android Keystore), and the event is persisted via `EventRepository` (Room).
7. `NotificationRouter` evaluates the event against the configured `NotificationRule` and dispatches to any enabled `HavenAlertChannel` instances (Cloud, Mattermost, Signal, Pushover).
8. `MonitorService` exposes four companion-object `StateFlow`s (`state`, `countdownSeconds`, `calibrationSecondsRemaining`, `calibrationResults`) that ViewModels observe without holding a service reference.
9. ViewModels convert these flows to per-screen `UiState` via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), default)`.
10. Compose screens re-compose on state changes via `collectAsStateWithLifecycle()`.

### Camera Analysis Pipeline (3-Stage)

```
CameraX ImageProxy
  └─ Stage 1: LuminanceMotionDetector
       Luma-Diff fraction ≥ threshold?  ──No──► discard frame
       Yes (or ≥ 2× threshold) ─────────────────► Stage 3 skip (direct trigger)
  └─ Stage 2: PerceptualHashDetector
       Hamming distance of 8×8 aHash < 4?  ──► discard (lighting flicker)
       ≥ 4  ──► confirmed motion
  └─ Stage 3: HavenObjectDetector (MediaPipe, max 1 call per 1500 ms)
       EfficientDet Lite 0 → CAMERA_PERSON / CAMERA_PET / CAMERA_VEHICLE
       Model absent → CAMERA (fallback, no crash)
```

Zone-based ROI (`DetectionZone`) clips the luma array before both Stage 1 and Stage 2; the detectors themselves are zone-unaware.

### Server: Event Upload and Processing

1. Android `CloudChannel` sends `POST /api/v1/devices/{app_key}/events` (multipart: JSON metadata + optional video).
2. FastAPI router authenticates via `X-App-Key` header, checks event and storage quotas, optionally AES-GCM encrypts the video server-side, persists `Event` + `EventTrigger` rows.
3. If `AI_BACKEND != "none"`, enqueues `analyze_event_task` on Redis/Celery; otherwise enqueues `send_notification_task` directly.
4. `analyze_event_task`: loads + optionally decrypts media, runs TFLite inference or calls OpenRouter vision API, writes `AnalysisResult` row, chains to `send_notification_task`.
5. `send_notification_task`: loads event + user notification preferences, dispatches to email (SMTP/aiosmtplib), Signal (signal-cli REST), or Pushover.

---

## Android Module Breakdown

### `sensor/` — Sensor Monitors

| File | Purpose |
|---|---|
| `SensorMonitor.kt` | Interface: `observe(sensitivity, warmupMs, expert): Flow<TriggerEvent>` |
| `FusedMotionMonitor.kt` | Accelerometer + Gyroscope via complementary filter (alpha=0.7); exposes `noiseFloor: StateFlow<Float?>` |
| `LightMonitor.kt` | EMA baseline (alpha=0.02); exposes `emaBaseline: StateFlow<Float?>`; 30s cooldown + EMA snap after trigger |
| `MicrophoneMonitor.kt` | Absolute dB threshold detection |
| `Sensitivity.kt` | Enum `OFF/LOW/MEDIUM/HIGH` — single source of truth for all sensor thresholds |
| `ExpertThresholds.kt` | Per-sensor MEDIUM-tier overrides; `DEFAULT` = all null (use enum values) |
| `MonitorState.kt` | State machine: `IDLE / COUNTDOWN / CALIBRATING / ACTIVE` |
| `CameraPosition.kt` | `BACK / FRONT` enum |
| `RecentTriggerState.kt` | Process-global `ConcurrentHashMap` of last trigger timestamps per type; used by `LightMonitor` for cross-sensor priority gate |

Legacy files `AccelerometerMonitor.kt` and `GyroscopeMonitor.kt` remain in the tree but are not wired into `MonitorService` — `FusedMotionMonitor` replaces both.

### `media/` — Camera Analysis and Recording

| File | Purpose |
|---|---|
| `CameraAnalyzer.kt` | `ImageAnalysis.Analyzer` implementing the 3-stage pipeline; emits `Flow<TriggerEvent>` via a `Channel`; exposes `lastJpegFrame: ByteArray?` for notification attachments |
| `LuminanceMotionDetector.kt` | Stage 1: pixel-wise luma comparison returning changed-pixel fraction |
| `ClipRecorder.kt` | CameraX `VideoCapture` wrapper; `startClip()` records fixed-duration MP4; guards against parallel recordings |

### `detection/` — Detection Algorithms

| File | Purpose |
|---|---|
| `SensorFusionEngine.kt` | Complementary filter: `score = 0.7 × gyroMag + 0.3 × accelDelta` |
| `PerceptualHashDetector.kt` | 8×8 average hash; Hamming distance threshold = 4 |
| `HavenObjectDetector.kt` | MediaPipe Tasks Vision wrapper (`ObjectDetector`); `@Singleton`; gracefully degrades when model file `efficientdet_lite0.tflite` is absent |
| `DetectionMode.kt` | `MOTION_ONLY / PERSON / PET / VEHICLE / ALL` |
| `DetectionZone.kt` | Normalized rectangle (0.0–1.0); `toPixelBounds(w, h)` and `serialize()`/`fromString()` |

### `events/` — Domain Types

Pure Kotlin; no Android framework dependencies.

| File | Purpose |
|---|---|
| `TriggerEvent.kt` | `data class`: `type`, `timestamp`, `sensorValue`, `mediaPath`, `severity` |
| `TriggerType.kt` | Enum with 16 values (IDs 0–15); IDs 0–8 compatible with Haven 0.2.1; 9–11 are ML camera types |
| `Severity.kt` | `LOW / MEDIUM / HIGH / CRITICAL` |

### `storage/` — Persistence

| File | Purpose |
|---|---|
| `HavenDatabase.kt` | Room database; two entities: `EventEntity`, `EventTriggerEntity` |
| `EventRepository.kt` | Wraps DAOs; `discardTriggersSince()` for 30 s stop-cooldown |
| `SettingsRepository.kt` | DataStore wrapper for all user preferences; exposes each setting as `Flow<T>` |
| `AppLogger.kt` | Ring-buffer logger (max 500 entries), `StateFlow<List<Entry>>`; surfaced in DiagnosticsScreen |

### `notify/` — Notification Engine

| File | Purpose |
|---|---|
| `HavenAlertChannel.kt` | Interface: `id`, `isEnabled`, `deferresToVideo`, `send(event, attachment, mime)`, `sendHeartbeat(message)` |
| `NotificationRouter.kt` | Evaluates `NotificationRule` (severity gate, trigger-type whitelist, anti-flood cooldown); routes to enabled channels; handles deferred video upload via `uploadVideo()` |
| `NotificationRule.kt` | `data class`: `minSeverity`, `cooldownMs`, `triggerTypes`, `attachMedia` |
| `CloudChannel.kt` | Multipart POST to `{serverUrl}/api/v1/devices/{appKey}/events` with `X-App-Key` auth; `deferresToVideo=true` |
| `MattermostChannel.kt` | Incoming webhook POST `{"text": "...", "username": "Haven"}` |
| `SignalRestChannel.kt` | `POST /v2/send` to signal-cli REST API, Bearer JWT, base64 attachments |
| `PushoverChannel.kt` | Pushover HTTP API with user key + app token |

### `security/` — Encryption and App Lock

| File | Purpose |
|---|---|
| `MediaEncryptionManager.kt` | AES-256-GCM via Android Keystore; key alias `haven_media_key`; file format: `[12-byte IV][ciphertext+GCM tag]`; `encryptInPlace()` writes `.enc` sibling then deletes original |
| `PinHashManager.kt` | PBKDF2-based PIN hashing stored in DataStore |
| `AppLockState.kt` | `StateFlow<Boolean>` for locked/unlocked state; consumed by nav graph to show `PinLockScreen` |

### `ui/` — Jetpack Compose UI

| Screen | Route | Purpose |
|---|---|---|
| `MonitorScreen` | `monitor` | Live status (IDLE/COUNTDOWN/CALIBRATING/ACTIVE), calibration wizard, recent trigger indicator |
| `TimelineScreen` | `timeline` | `LazyColumn` of past events from Room |
| `EventDetailScreen` | `event/{eventId}` | Full event detail with trigger list and media preview |
| `SettingsScreen` | `settings` | Global settings: sensitivity, camera, sensors, detection mode, notifications |
| `ZoneEditorScreen` | `zone_editor` | Live camera preview with draggable ROI rectangle (`AndroidView(PreviewView)` + Canvas overlay) |
| `ExpertSettingsScreen` | `expert_settings` | Four `Slider` controls for per-sensor MEDIUM-tier threshold overrides |
| `DiagnosticsScreen` | `diagnostics` | Calibration results, sensor algorithm parameters, live AppLogger ring buffer |
| `PinLockScreen` | (modal) | PIN entry gate; shown by nav graph when `AppLockState.locked == true` |

Navigation uses `NavHost` with routes defined in `Routes` object in `HavenNavGraph.kt`. Bottom nav bar has three primary destinations (Monitor, Timeline, Settings); Diagnostics and Expert Settings are accessed from Settings.

### `di/` — Dependency Injection (Hilt)

| Module | Provides |
|---|---|
| `AppModule` | `SensorManager` (`@Singleton`) |
| `DatabaseModule` | `HavenDatabase`, `EventDao`, `EventTriggerDao` (all `@Singleton`) |
| `DataStoreModule` | `DataStore<Preferences>` (`@Singleton`) |
| `NetworkModule` | `OkHttpClient` (`@Singleton`, timeouts: 15 s connect/read, 30 s write) |

All sensor monitors, repositories, `HavenObjectDetector`, `NotificationRouter`, and `AppLogger` use `@Singleton @Inject constructor`. `MonitorService` receives them via `@Inject lateinit var`.

---

## Key Abstractions

### `SensorMonitor` Interface

```kotlin
interface SensorMonitor {
    fun observe(
        sensitivity: Sensitivity,
        warmupMs: Long = 10_000L,
        expert: ExpertThresholds = ExpertThresholds.DEFAULT
    ): Flow<TriggerEvent>
}
```

All active monitors (`FusedMotionMonitor`, `LightMonitor`, `MicrophoneMonitor`) implement this interface. `MonitorService` collects the merged flow uniformly. Flows are cold; sensor listeners are unregistered when collection is cancelled.

### `TriggerEvent`

```kotlin
data class TriggerEvent(
    val type: TriggerType,
    val timestamp: Long = System.currentTimeMillis(),
    val sensorValue: Float? = null,
    val mediaPath: String? = null,
    val severity: Severity = Severity.MEDIUM,
)
```

The universal event token that flows from sensors → `MonitorService` → Room → ViewModel → UI.

### `Sensitivity` Enum (Single Source of Truth)

```kotlin
enum class Sensitivity(
    val accelerometerMultiplier: Float,
    val microphoneThresholdDb: Float,
    val lightDeltaLux: Float,
    val cameraMotionThreshold: Float,
) {
    OFF(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE),
    LOW(4.0f, 65f, 80f, 0.18f),
    MEDIUM(2.0f, 55f, 40f, 0.08f),
    HIGH(1.2f, 45f, 20f, 0.04f),
}
```

Extension functions `effectiveAccelMultiplier(expert)`, `effectiveMicDb(expert)`, `effectiveLightLux(expert)`, `effectiveCameraFraction(expert)` apply optional per-sensor MEDIUM overrides from `ExpertThresholds` when set.

### `DetectionZone`

```kotlin
data class DetectionZone(val left: Float, val top: Float, val right: Float, val bottom: Float)
```

Normalized coordinates (0.0–1.0). `toPixelBounds(width, height)` converts to pixel bounds at analysis time. Serialized as `"left,top,right,bottom"` in DataStore. Applied by `CameraAnalyzer.cropLuma()` before both luma and pHash detectors; the detectors are zone-unaware.

### `MonitorState`

```kotlin
enum class MonitorState { IDLE, COUNTDOWN, CALIBRATING, ACTIVE }
```

Exposed as `MonitorService.state: StateFlow<MonitorState>` (companion object). Drives all UI state transitions without ViewModels holding a service reference.

### `HavenObjectDetector`

`@Singleton` wrapping the MediaPipe Tasks Vision `ObjectDetector` API. Model: `assets/efficientdet_lite0.tflite` (EfficientDet Lite 0, COCO 80 classes, ~4 MB). Lazy `initialize()` is called by `CameraAnalyzer` when ML mode is active. Graceful degradation: if the model file is absent, `isAvailable = false` and `CameraAnalyzer` falls back to a generic `CAMERA` trigger. `availabilityFlow: StateFlow<Boolean>` for reactive UI updates in `DiagnosticsScreen`.

---

## State Management

### Companion-Object StateFlows (Process-Wide)

`MonitorService.companion` exposes four `StateFlow`s readable by any ViewModel without a service reference:

| Flow | Type | Description |
|---|---|---|
| `state` | `StateFlow<MonitorState>` | Current lifecycle phase |
| `countdownSeconds` | `StateFlow<Int>` | Live countdown display |
| `calibrationSecondsRemaining` | `StateFlow<Int>` | Calibration progress |
| `calibrationResults` | `StateFlow<CalibrationResults?>` | `motionNoiseFloor` + `lightEmaBaseline` after warmup |

### Sensor-Level StateFlows

| Flow | Owner | Semantics |
|---|---|---|
| `noiseFloor: StateFlow<Float?>` | `FusedMotionMonitor` | `null` until warmup completes; reset to `null` on each `observe()` call |
| `emaBaseline: StateFlow<Float?>` | `LightMonitor` | Same pattern |
| `availabilityFlow: StateFlow<Boolean>` | `HavenObjectDetector` | True when MediaPipe model is loaded |

### Settings Persistence

`SettingsRepository` exposes every setting as a `Flow<T>` backed by DataStore. `MonitorService` takes a `.first()` snapshot at session start. ViewModels hold settings as `StateFlow` via `stateIn(...)`.

---

## Server Architecture

### Services (Docker Compose)

| Service | Image / Dockerfile | Port | Role |
|---|---|---|---|
| `db` | `postgres:16-alpine` | 5432 | Primary data store |
| `redis` | `redis:7-alpine` | 6379 | Celery broker |
| `app` | `Dockerfile` (FastAPI) | 8000 | REST API (`uvicorn app.main:app`) |
| `worker` | `Dockerfile` (Celery) | — | AI analysis + notification dispatch |
| `webui` | `Dockerfile.webui` (Django) | 8080 | Human-facing web UI |

The `media/` directory is volume-mounted into both `app` and `worker` so the API can write files that the worker reads for AI analysis.

### FastAPI REST API Routes

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/auth/register` | Create account, returns `user_key` |
| POST | `/api/v1/auth/login` | Username + password (+ TOTP), returns JWT pair |
| POST | `/api/v1/auth/refresh` | Rotate access + refresh tokens |
| GET | `/api/v1/auth/me` | Current user profile |
| POST | `/api/v1/auth/2fa/setup` | Generate TOTP secret + QR code |
| POST | `/api/v1/auth/2fa/verify` | Enable 2FA after confirming TOTP code |
| POST | `/api/v1/devices/` | Register device, returns App-Key (`hav_<64hex>`) |
| GET | `/api/v1/devices/` | List devices |
| DELETE | `/api/v1/devices/{device_id}` | Revoke device (soft-delete, `is_active=False`) |
| POST | `/api/v1/devices/{app_key}/events` | Upload event + optional video (`X-App-Key` auth) |
| GET | `/api/v1/events` | Paginated event list (JWT auth) |
| GET | `/api/v1/events/{event_id}` | Single event |
| DELETE | `/api/v1/events/{event_id}` | Delete event + media, update quotas |
| GET/PATCH | `/api/v1/notifications` | Notification settings |
| GET | `/health` | Liveness probe |

Authentication: devices authenticate with `X-App-Key: hav_<32hex>` header. Web clients use `Authorization: Bearer <JWT>` (access tokens expire in 30 minutes; refresh tokens in 7 days). Optional TOTP 2FA per user; secrets are Fernet-encrypted with the server `SECRET_KEY`.

### Database Models

```
users
  id, username, password_hash, user_key (haven_u_<32hex>)
  is_admin, is_active
  totp_secret (Fernet-encrypted), totp_enabled
  storage_quota_mb (default 1024), max_events (default 10000)
  current_storage_bytes, current_event_count
  notification_email, notification_signal_number
  pushover_user_key, pushover_app_token, notifications_enabled

devices
  id, user_id (FK → users), name, app_key (hav_<32hex>), is_active, last_seen_at

events
  id, user_id (FK), device_id (FK)
  event_type (mirrors TriggerType names), severity, timestamp
  sensor_value, media_path, media_size_bytes, is_encrypted

event_triggers
  id, event_id (FK → events), trigger_type, sensor_value, media_path

analysis_results
  id, event_id (FK, unique), backend ("tflite" | "openrouter")
  labels (JSON array string), confidence, description, raw_result
```

Cascade deletes: deleting a `User` removes all `Device`, `Event`, `EventTrigger`, and `AnalysisResult` rows. Deleting an `Event` removes its `EventTrigger` and `AnalysisResult` rows.

### Celery Task Pipeline

```
Event upload
    │
    ├─ (AI_BACKEND != "none") ──► analyze_event_task
    │                              1. Read media from MEDIA_ROOT
    │                              2. Decrypt (AES-GCM) if encryption_key_hex provided
    │                              3a. AI_BACKEND=tflite: run EfficientDet Lite 0 (ai-edge-litert)
    │                              3b. AI_BACKEND=openrouter: POST frame to vision LLM API
    │                              4. Write AnalysisResult row
    │                              5. Chain ──►
    │                                          send_notification_task
    └─ (AI_BACKEND == "none") ──► send_notification_task
                                   Load event + user notification prefs
                                   email   → aiosmtplib (SMTP+STARTTLS)
                                   signal  → httpx POST /v2/send (signal-cli REST)
                                   pushover → httpx POST (Pushover API)
```

### Django Web UI

A separate Django application (`server/webui/`) runs on port 8080 and shares the same PostgreSQL database. It provides a browser-based dashboard with event browsing, AI analysis summaries, video playback, notification settings, device management, and admin quota controls. Rendered with HTMX + Tailwind CSS (dark theme).

| Django App | Description |
|---|---|
| `accounts/` | Login, logout, profile |
| `devices/` | Device list and revocation |
| `events/` | Paginated event timeline with filter support |
| `notifications/` | Per-user notification channel configuration |
| `admin_panel/` | Admin quota and user management |

---

## Android ↔ Server Integration

`CloudChannel` is the sole component with a direct server dependency. It is constructed by `MonitorService` from settings (`KEY_CLOUD_SERVER_URL`, `KEY_CLOUD_APP_KEY`). If either is blank, `CloudChannel.isEnabled` returns `false` and `NotificationRouter` skips it silently.

**Event upload flow:**
1. A video clip finishes recording. `NotificationRouter.uploadVideo()` is called with raw MP4 bytes (before `encryptInPlace()`).
2. `CloudChannel.send(event, videoBytes, "video/mp4")` posts multipart/form-data to `POST /api/v1/devices/{appKey}/events`.
   - Form field `metadata`: JSON with `event_type`, `severity`, `timestamp`, `sensor_value`.
   - Form field `video`: raw MP4 bytes.
   - Header `X-App-Key: {appKey}`.
3. The server optionally re-encrypts the video server-side if `X-Encryption-Password` is provided.
4. On HTTP 201 the upload succeeds. On any error, `Result.failure` is returned and the router logs it.

**Heartbeat:** `CloudChannel.sendHeartbeat(message)` posts `{"message": "Haven alive - vX.Y.Z - ACTIVE - HH:mm"}` to `POST /api/v1/devices/{appKey}/heartbeat`. Interval is configurable per session.

**Encryption boundary:** On-device encryption uses an Android Keystore key that cannot be exported. Video bytes are uploaded *before* `encryptInPlace()` runs, so the server receives the original MP4.

---

## Directory Structure

```
Haven 2.0/
├── app/
│   └── src/main/java/org/havenapp/main/
│       ├── MainActivity.kt              # AppCompatActivity, Hilt entry point
│       ├── MonitorService.kt            # Foreground service, state machine coordinator
│       ├── HavenApplication.kt          # @HiltAndroidApp
│       ├── sensor/                      # Sensor monitors + Sensitivity + ExpertThresholds
│       ├── media/                       # CameraAnalyzer, ClipRecorder
│       ├── detection/                   # MediaPipe wrapper, pHash, SensorFusionEngine, zone/mode types
│       ├── events/                      # TriggerEvent, TriggerType, Severity (pure Kotlin)
│       ├── storage/                     # Room, DataStore, AppLogger
│       ├── notify/                      # HavenAlertChannel + channel implementations
│       ├── security/                    # AES-GCM media encryption, PIN lock
│       ├── di/                          # Hilt modules (App, Database, DataStore, Network)
│       └── ui/                          # Jetpack Compose screens and ViewModels
└── server/
    ├── app/                             # FastAPI application
    │   ├── main.py                      # App factory, router registration
    │   ├── config.py                    # Pydantic Settings (env vars)
    │   ├── database.py                  # Async SQLAlchemy engine + session factory
    │   ├── celery_app.py                # Celery instance
    │   ├── routers/                     # auth, devices, events, notifications, admin, health
    │   ├── models/                      # SQLAlchemy ORM: user, device, event
    │   ├── schemas/                     # Pydantic request/response schemas
    │   ├── services/                    # crypto, jwt, totp, notify, storage utilities
    │   ├── tasks/                       # analysis.py, notifications.py (Celery tasks)
    │   ├── ml/                          # detector.py (TFLite), openrouter.py (LLM API)
    │   └── dependencies/               # FastAPI DI: auth.py, quota.py
    ├── webui/                           # Django Web UI (port 8080)
    │   ├── config/                      # Django settings, URL root
    │   ├── accounts/                    # Login, profile
    │   ├── events/                      # Event browser views + templates
    │   ├── notifications/               # Notification settings views
    │   ├── devices/                     # Device list view
    │   └── admin_panel/                 # Admin quota management
    ├── alembic/                         # Database schema migrations
    ├── docker-compose.yml               # db + redis + app + worker + webui
    ├── Dockerfile                       # FastAPI + Celery worker image
    └── Dockerfile.webui                 # Django Web UI image
```
