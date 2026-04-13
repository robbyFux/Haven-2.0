<!-- GSD:generated -->
<!-- generated-by: gsd-doc-writer -->

# Testing

Haven 2.0 has two distinct testing layers: a comprehensive Python test suite for the self-hosted
server backend, and a minimal scaffold for the Android app. This document covers both, plus
practical guidance for manual sensor testing on a real device.

---

## Android App Testing

### Current State

The Android app has no unit or instrumentation tests at this time. The `app/src/test/` and
`app/src/androidTest/` directories do not exist. The `build.gradle.kts` configures
`testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`, so the infrastructure
is in place to add tests.

**Why test coverage is minimal:** The core detection logic depends heavily on live hardware —
accelerometer, gyroscope, microphone, and camera. Mocking `SensorManager` callbacks and
`ImageAnalysis` frames to produce meaningful test coverage requires substantial scaffolding with
low signal-to-noise ratio for a project at this stage. Testing priority is on the server API
(which is fully mockable) and on manual verification on real devices.

### Gradle Test Commands

These commands are available once tests exist:

```bash
# Run JVM unit tests (no device required)
./gradlew :app:testDebugUnitTest

# Run instrumentation tests (device or emulator required)
./gradlew :app:connectedDebugAndroidTest

# Compile check only (fast, ~35s with daemon)
./gradlew :app:compileDebugKotlin
```

### What to Test First (When Adding Android Tests)

High-value candidates that do not require hardware:

- `DetectionZone.fromString()` — serialization round-trip and malformed-input handling
- `SensorFusionEngine` — complementary filter output for known accel/gyro input vectors
- `PerceptualHashDetector` — Hamming distance calculation for identical vs. shifted images
- `SettingsRepository` — DataStore read/write for `Sensitivity` enum and `DetectionZone`
- `EventRepository.discardTriggersSince()` — stop-cooldown DB logic

---

## Server (Python) Testing

The server backend at `server/` has a full pytest suite covering authentication, devices, events,
admin operations, notifications, analysis tasks, and cryptography.

### Framework and Dependencies

| Dependency | Role |
|---|---|
| `pytest` | Test runner |
| `pytest-asyncio >= 1.0` | Async test support (`asyncio_mode = "auto"`) |
| `httpx` | Async HTTP client for ASGI integration tests |
| `aiosqlite` | In-memory SQLite backend (replaces PostgreSQL in tests) |
| `pytest-django 4.12.0` | Django test utilities (for the web UI layer) |

All dev dependencies are declared in `server/pyproject.toml` under `[project.optional-dependencies] dev`.

### Test Database

Tests use an **in-memory SQLite database** via `aiosqlite`. The `conftest.py` fixture overrides
FastAPI's `get_db` dependency with a session-scoped SQLite engine. All tables are created before
the first test and dropped after the last. No PostgreSQL instance is required to run the suite.

```
TEST_DATABASE_URL = "sqlite+aiosqlite:///./test.db"
```

Celery tasks run **synchronously in-process** — `CELERY_TASK_ALWAYS_EAGER=true` is set in
`conftest.py`, so no Redis broker is needed.

### Installation

```bash
cd server
pip install -e ".[dev]"
```

### Running Tests

```bash
# Run the full suite from the server/ directory
cd server
pytest

# Verbose output with test names
pytest -v

# Run a single file
pytest tests/test_auth.py

# Run a single test function
pytest tests/test_events.py::test_upload_event_encrypted

# Stop on first failure
pytest -x
```

### Test Coverage by Module

| File | Tests | What is covered |
|---|---|---|
| `test_auth.py` | 9 | Registration, login, wrong password, token refresh, `/me` endpoint, 2FA setup and verify, login with TOTP |
| `test_devices.py` | 6 | Create device (app_key format), list, get by ID, revoke, unauthenticated create (401), user isolation |
| `test_events.py` | 10 | Metadata-only upload, video upload, encrypted upload with round-trip decrypt verify, storage quota (413), event count quota (429), paginated listing, get by ID, delete, user isolation, invalid app_key (401) |
| `test_admin.py` | 7 | List users as admin/non-admin, get user detail, update quota, system stats, deactivate user, cannot self-delete |
| `test_notifications.py` | 8 | GET/PATCH notification settings, Celery task with notifications disabled, email channel, Signal channel, Pushover channel, no channels configured, `format_notification_message` output |
| `test_analysis.py` | 6 | `AI_BACKEND=none` skips gracefully, `media_path=None` returns `no_media`, TFLite backend with mock detector, OpenRouter backend with mock, encrypted media decrypted before inference, missing file returns error |
| `test_crypto.py` | 6 | `derive_key` determinism, cross-user key isolation, key length (32 bytes), encrypt/decrypt round-trip, wrong key raises `InvalidTag`, nonce randomness |

**Total: 52 tests** across 7 files.

### External Services in Tests

All external I/O is mocked. The test suite never makes real network calls:

- SMTP (`send_email`), Signal (`send_signal`), and Pushover (`send_pushover`) senders are patched
  via `unittest.mock.patch` in `test_notifications.py`.
- ML backends (`get_detector`, `analyze_frame_openrouter`) are mocked in `test_analysis.py`.
- `asyncio.run` calls inside Celery tasks are intercepted to inject controlled return values.

---

## CI Integration

No CI pipeline is currently configured. There is no `.github/workflows/` directory. Tests are run
locally by developers.

When CI is added, the recommended test command is:

```bash
cd server && pip install -e ".[dev]" && pytest -v
```

No coverage threshold is configured in `pyproject.toml`.

---

## Manual Testing on Device

The following steps describe how to deliberately trigger each sensor type during development.
All triggers appear in the Timeline screen and the Diagnostics trigger log in real time.

### Prerequisites

- App installed and monitoring active (MonitorState = ACTIVE, past the calibration phase)
- Sensitivity set to HIGH for easier triggering during testing
- Diagnostics screen open in a second window or logged via `adb logcat`

### Motion (Accelerometer / Fused Motion)

1. With the device resting on a stable surface, tap or nudge the surface sharply.
2. `FusedMotionMonitor` triggers when the fused score (70% gyro + 30% accel delta) exceeds
   `noiseFloor * sensitivityMultiplier`.
3. HIGH sensitivity multiplier is 2.5x. A firm table knock should trigger at this level.
4. Expected: `TriggerType.ACCELEROMETER` event at `Severity.MEDIUM` or `HIGH`.

### Light

1. Shine a flashlight directly at the device or switch a room light on/off.
2. The `LightMonitor` uses an EMA baseline (alpha=0.02). A sudden +30 lux change triggers at HIGH.
3. Thresholds: LOW=100 lux delta, MEDIUM=60 lux delta, HIGH=30 lux delta.
4. Expected: `TriggerType.LIGHT` event. A 30-second cooldown follows each trigger.

### Microphone

1. Clap near the device or speak loudly.
2. The `MicrophoneMonitor` uses absolute dB thresholds: LOW=70dB, MEDIUM=60dB, HIGH=50dB.
3. At HIGH sensitivity, a sharp clap (~60–70dB) at 1 metre should trigger.
4. Expected: `TriggerType.SOUND_DECIBEL` event.

### Camera Motion (Luma Diff)

1. Wave a hand in front of the camera or move a large object in frame.
2. Stage 1 (luminance diff) fires when the fraction of changed pixels exceeds
   `sensitivity.cameraMotionThreshold` (HIGH=0.06, i.e., 6% of pixels must change).
3. Stage 2 (pHash) confirms structural change; Hamming distance < 4 is suppressed as
   a lighting flicker rather than motion.
4. Expected: `TriggerType.CAMERA` event (or `CAMERA_PERSON` / `CAMERA_PET` /
   `CAMERA_VEHICLE` if TFLite model is installed and the object matches).

### Camera ML Object Detection (TFLite)

1. Requires `efficientdet_lite0.tflite` in `app/src/main/assets/`.
2. Set Detection Mode to PERSON (or ALL) in Settings.
3. Walk slowly into camera frame. The model runs only after Stage 1 and Stage 2 confirm motion.
4. Expected: `TriggerType.CAMERA_PERSON` with confidence shown in the trigger log.
5. `DiagnosticsScreen` shows `TFLite available: true` and `DetectionMode` when the model is loaded.

### Detection Zone (ROI)

1. Draw a zone in Settings > Detection Zone covering only part of the frame.
2. Motion outside the zone should produce no trigger; motion inside should trigger normally.
3. `CameraAnalyzer.cropLuma()` applies the zone before both luma-diff and pHash stages.

### Stop Cooldown Verification

1. Start monitoring, trigger a motion event, then immediately stop monitoring.
2. The last 30 seconds of events are removed from the database (`EventRepository.discardTriggersSince()`).
3. Open Timeline — the events caused by walking to the device to stop it should not appear.
4. The cooldown value (30 s) is visible in the Diagnostics screen under "Stop Cooldown".
