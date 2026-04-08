# Integrations

**Analysis Date:** 2026-03-30

## External Services

**Currently integrated (Phases 1–2):** None. The app is fully local and offline at this stage.

**Planned (Phase 3 – NotificationEngine):**
- signal-cli REST API (self-hosted, Docker: `bbernhard/signal-cli-rest-api`)
  - Endpoint: `POST /v2/send`
  - Auth: Bearer JWT token
  - Purpose: Alert messages + media attachments via Signal messenger
  - Config: Backend URL + JWT stored encrypted in DataStore (Android Keystore)
  - Planned implementation: `notify/channel/SignalRestChannel.kt`
- Mattermost Incoming Webhooks (self-hosted Mattermost server)
  - Endpoint: `POST <webhook-url>` (URL contains auth)
  - Purpose: Markdown-formatted alert messages and heartbeats
  - No separate backend required — uses existing Mattermost server
  - Planned implementation: `notify/channel/MattermostChannel.kt`

**Planned (Phase 4 – Networking):**
- Self-hosted WebRTC Signaling + TURN server
  - Same infrastructure as signal-cli REST API backend
  - Purpose: Peer-to-peer live video streaming between devices
- signal-cli health check: `GET /v1/health`

## SDKs

**TensorFlow Lite Task Vision 0.4.4**
- Package: `org.tensorflow:tensorflow-lite-task-vision`
- Used in: `detection/HavenObjectDetector.kt`
- Purpose: On-device object detection (persons, pets, vehicles)
- Model: EfficientDet Lite 0, COCO-trained (80 classes), ~4 MB
- Model file: `app/src/main/assets/efficientdet_lite0.tflite` (must be downloaded manually, not committed)
- Download command documented in `app/build.gradle.kts` comments
- Graceful degradation: if model file absent, `HavenObjectDetector.isAvailable` returns false and detection falls back to motion-only

**Hilt (Dagger) 2.51.1**
- Package: `com.google.dagger:hilt-android`
- Used throughout: `di/AppModule.kt`, `di/DatabaseModule.kt`, `di/DataStoreModule.kt`
- Purpose: Dependency injection; all `@Singleton` sensor monitors, repositories, and detector

**Room 2.6.1**
- Package: `androidx.room`
- Used in: `storage/HavenDatabase.kt`, `storage/dao/`, `storage/entity/`
- Purpose: Local SQLite persistence for events and triggers
- Database name: `haven.db` (set in `di/DatabaseModule.kt`)
- Schema export: disabled (`exportSchema = false`)
- Migration strategy: `fallbackToDestructiveMigration()` (current development phase)

## Hardware / Platform APIs

**Camera (CameraX 1.4.0)**
- Used in: `MonitorService.kt` (`ProcessCameraProvider`, `ImageAnalysis`), `ZoneEditorScreen.kt` (`PreviewView`)
- Resolution: 640×480 for `ImageAnalysis`
- Backpressure: `STRATEGY_KEEP_ONLY_LATEST`
- Camera executor: dedicated single-thread executor in `MonitorService`
- Supports: front and back camera (`CameraPosition.BACK` / `CameraPosition.FRONT`)
- Permissions required: `android.permission.CAMERA`, `FOREGROUND_SERVICE_CAMERA`

**Accelerometer**
- Used in: `sensor/FusedMotionMonitor.kt`
- Sensor type: `Sensor.TYPE_ACCELEROMETER_UNCALIBRATED` with fallback to `TYPE_ACCELEROMETER`
- Rate: `SENSOR_DELAY_NORMAL`

**Gyroscope**
- Used in: `sensor/FusedMotionMonitor.kt`
- Sensor type: `Sensor.TYPE_GYROSCOPE`
- Optional: graceful degradation if not present (fused score uses accelerometer-only path)
- Rate: `SENSOR_DELAY_NORMAL`

**Light Sensor**
- Used in: `sensor/LightMonitor.kt`
- Sensor type: `Sensor.TYPE_LIGHT`
- Baseline: EMA with alpha=0.02

**Microphone (Android AudioRecord API)**
- Used in: `sensor/MicrophoneMonitor.kt`
- Format: PCM 16-bit mono, 44100 Hz
- Source: `MediaRecorder.AudioSource.MIC`
- Detection: absolute dB threshold (not relative level change)
- Permissions required: `android.permission.RECORD_AUDIO`, `FOREGROUND_SERVICE_MICROPHONE`

**WakeLock**
- Used in: `MonitorService.kt`
- Type: `PowerManager.PARTIAL_WAKE_LOCK`
- Tag: `Haven:MonitorWakeLock`
- Max duration: 12 hours
- Permission: `android.permission.WAKE_LOCK`

**Android Notification System**
- Used in: `MonitorService.kt`
- Channel ID: `haven_monitor`, importance LOW
- Type: persistent foreground notification with Stop action

**Android Keystore (planned)**
- Phase 5: encrypt all media with AES-GCM via Android Keystore
- Phase 3: encrypt DataStore settings (signal-cli JWT, Mattermost webhook URL)

## Data Storage

**Room SQLite (local)**
- Database: `haven.db`
- Entities: `EventEntity` (events), `EventTriggerEntity` (sensor triggers per event)
- DAOs: `EventDao`, `EventTriggerDao` at `storage/dao/`
- Accessed via `EventRepository` at `storage/EventRepository.kt`

**DataStore Preferences (local)**
- Used in: `storage/SettingsRepository.kt`
- Provided via: `di/DataStoreModule.kt`
- Keys: sensitivity, camera position, countdown seconds, calibration seconds, detection mode, detection zone, per-sensor enable flags
- Serialization: enum names as strings, detection zone as comma-separated floats (`"left,top,right,bottom"`)

**File Storage**
- No persistent media files stored yet (Phases 1–2)
- Phase 5: encrypted local media storage planned (`storage/` module)

## Data Formats & Protocols

**Internal sensor events:**
- `TriggerEvent` data class: `type: TriggerType`, `sensorValue: Float`, `severity: Severity`
- Emitted as Kotlin `Flow<TriggerEvent>` from all sensor monitors
- Merged via `kotlinx.coroutines.flow.merge()` in `MonitorService`

**Detection zone serialization:**
- Format: plain string `"left,top,right,bottom"` (normalized 0.0–1.0 floats)
- Serialized/deserialized in `detection/DetectionZone.kt` via `serialize()` / `fromString()`
- Stored in DataStore under key `"detection_zone"`

**TFLite model format:**
- `.tflite` flat buffer (EfficientDet Lite 0 with metadata)
- Loaded via `ObjectDetector.createFromFileAndOptions()` from `assets/`
- Score threshold: 0.45, max results: 5

**Planned REST (Phase 3):**
- signal-cli REST API v2: JSON POST to `/v2/send`, Bearer JWT auth
  - Payload fields: `message`, `recipients` (E.164), optional `base64_attachments`
- Mattermost Incoming Webhook: JSON POST, field `text` (Markdown), `username`, `icon_emoji`

**Planned (Phase 4):**
- WebRTC (SRTP/DTLS for media, signaling protocol TBD)
- QR code for device pairing (format TBD)

## Authentication & Identity

**Current (Phases 1–2):** No authentication — fully local, single-device app.

**Planned (Phase 3):**
- signal-cli backend: JWT Bearer token stored encrypted in DataStore
- Mattermost: auth embedded in webhook URL, stored encrypted in DataStore

**Planned (Phase 4):**
- Multi-device roles: Admin / Viewer / ReadOnly
- QR-code-based pairing for trust establishment

## Monitoring & Observability

**Error Tracking:** None. `Log.w` / `Log.e` used for internal errors (e.g., TFLite init failure in `HavenObjectDetector.kt`).

**Diagnostics Screen:** `ui/diagnostics/DiagnosticsScreen.kt` — runtime diagnostics exposed in-app:
- Settings summary
- Per-sensor calibration results (noise floor, thresholds, EMA baseline)
- TFLite availability status (`HavenObjectDetector.isAvailable`)
- Live trigger log
- Plain-text export via Android share sheet

**No Firebase Analytics, no crash reporting, no cloud telemetry.**

## CI/CD & Deployment

**CI Pipeline:** Not detected. No `.github/`, `.gitlab-ci.yml`, or equivalent.

**Build:** Local Gradle builds only. `./gradlew assembleDebug` / `./gradlew assembleRelease`.

**Distribution:** Not configured. No Play Store or F-Droid setup files present.

## Permissions Summary

All permissions declared in `app/src/main/AndroidManifest.xml`:

| Permission | Purpose |
|---|---|
| `CAMERA` | CameraX image analysis |
| `RECORD_AUDIO` | MicrophoneMonitor (AudioRecord) |
| `FOREGROUND_SERVICE` | MonitorService as foreground service |
| `FOREGROUND_SERVICE_CAMERA` | Camera use in foreground service |
| `FOREGROUND_SERVICE_MICROPHONE` | Mic use in foreground service |
| `POST_NOTIFICATIONS` | Persistent monitoring notification (Android 13+) |
| `WAKE_LOCK` | Keep CPU running while screen is off |
| `RECEIVE_BOOT_COMPLETED` | Auto-start after reboot (Phase 4) |
| `INTERNET` | Reserved for Phase 3/4 networking |
| `ACCESS_NETWORK_STATE` | Reserved for Phase 3/4 networking |

---

*Integration audit: 2026-03-30*
