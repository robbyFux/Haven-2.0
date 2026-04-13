<!-- GSD:generated -->
<!-- generated-by: gsd-doc-writer -->

# Configuration

Haven 2.0 has two separately configured components: the **Android app** (settings stored in
DataStore on-device) and the **self-hosted Python server** (settings loaded from environment
variables or a `.env` file).

---

## Android App — DataStore Settings

All Android settings are persisted with `androidx.datastore.preferences` under the app's private
storage. There is no configuration file to edit — all settings are written by the app UI or by
calling the corresponding `SettingsRepository` setter.

### Monitoring

| Key | Type | Default | Description |
|---|---|---|---|
| `sensitivity` | `Sensitivity` enum | `MEDIUM` | Global sensitivity tier: `OFF`, `LOW`, `MEDIUM`, `HIGH`. Acts as the single source of truth for all sensor thresholds — see the [Sensitivity Thresholds](#sensitivity-thresholds) table below. |
| `countdown_seconds` | `Int` | `60` | Seconds of countdown before the monitoring session becomes active. |
| `calibration_seconds` | `Int` | `10` | Warmup duration in seconds during which sensor noise floors are sampled. The 90th-percentile of samples during this window becomes the noise floor. |
| `detection_mode` | `DetectionMode` enum | `MOTION_ONLY` | Controls the camera analysis pipeline: `MOTION_ONLY`, `PERSON`, `PET`, `VEHICLE`, `ALL`. Modes other than `MOTION_ONLY` require the TFLite model (`efficientdet_lite0.tflite`). |
| `detection_zone` | `String?` | `null` (whole frame) | Active ROI for camera analysis, serialized as `"left,top,right,bottom"` with normalized coordinates (0.0–1.0). `null` means the full frame is analyzed. Set via `ZoneEditorScreen`. |

### Sensor Enable/Disable

| Key | Type | Default | Description |
|---|---|---|---|
| `sensor_motion_enabled` | `Boolean` | `true` | Enables the fused motion monitor (accelerometer + gyroscope). |
| `sensor_light_enabled` | `Boolean` | `true` | Enables the light sensor monitor. |
| `sensor_mic_enabled` | `Boolean` | `true` | Enables the microphone monitor. |
| `sensor_camera_enabled` | `Boolean` | `true` | Enables the camera analysis pipeline. |

### Camera

| Key | Type | Default | Description |
|---|---|---|---|
| `camera_position` | `CameraPosition` enum | `BACK` | Which camera to use: `BACK` or `FRONT`. |
| `clip_duration_seconds` | `Int` | `30` | Duration in seconds of video clips recorded when a sensor triggers an event. |

### Media Encryption

| Key | Type | Default | Description |
|---|---|---|---|
| `media_encryption_enabled` | `Boolean` | `true` | When `true`, newly recorded video clips are encrypted with AES-GCM before storage (`.enc` files). When `false`, clips are stored as plain `.mp4`. Toggling this setting does not re-process existing files. |
| `media_encrypted_v1` | `Boolean` | `false` | Internal flag tracking whether the one-shot SEC-02 migration has been applied. Not user-facing. |

### PIN Lock

| Key | Type | Default | Description |
|---|---|---|---|
| `pin_enabled` | `Boolean` | `false` | Whether PIN-lock is active. |
| `auto_lock_delay_seconds` | `Int` | `0` | Seconds after which the app auto-locks. `0` = immediate, `-1` = never. |
| `pin_hash` | `String?` | `null` | Hashed PIN credential (internal, not user-editable). |
| `pin_salt` | `String?` | `null` | Salt for PIN hash (internal, not user-editable). |

### Cross-Sensor Suppression

| Key | Type | Default | Description |
|---|---|---|---|
| `light_suppress_motion_seconds` | `Int` | `10` | After a light-sensor trigger, motion triggers are suppressed for this many seconds to prevent correlated false positives. |

### Expert Threshold Overrides

Expert overrides allow fine-tuning of the Medium-tier threshold for each individual sensor without changing the global sensitivity level. Only the Medium tier is customizable; LOW and HIGH are derived automatically. A `null` value (key absent) means the sensor uses its `Sensitivity` enum default.

| Key | Type | Default (Medium) | Description |
|---|---|---|---|
| `expert_accel_medium_multiplier` | `Float?` | `2.0×` | Accelerometer noise-floor multiplier at Medium sensitivity. |
| `expert_mic_medium_db` | `Float?` | `55 dB` | Microphone absolute threshold in dB at Medium sensitivity. |
| `expert_light_medium_lux` | `Float?` | `40 lux` | Light sensor delta threshold in lux at Medium sensitivity. |
| `expert_camera_medium_fraction` | `Float?` | `0.08` | Fraction of pixels that must change (luma-diff) at Medium sensitivity. |

### Sensitivity Thresholds

The `Sensitivity` enum is the single source of truth for all sensor thresholds. These are the values built into the enum (before any expert overrides):

| Tier | Accel Multiplier | Mic Threshold | Light Delta | Camera Motion |
|---|---|---|---|---|
| `OFF` | ∞ (disabled) | ∞ (disabled) | ∞ (disabled) | ∞ (disabled) |
| `LOW` | 4.0× | 65 dB | 80 lux | 0.18 (18% pixels) |
| `MEDIUM` | 2.0× | 55 dB | 40 lux | 0.08 (8% pixels) |
| `HIGH` | 1.2× | 45 dB | 20 lux | 0.04 (4% pixels) |

### Notification Routing

| Key | Type | Default | Description |
|---|---|---|---|
| `notification_min_severity` | `Severity` enum | `MEDIUM` | Minimum event severity that triggers a notification: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`. |
| `notification_cooldown_ms` | `Long` | `60000` (60 s) | Minimum milliseconds between consecutive notifications from the same channel. |
| `notification_trigger_types` | `Set<TriggerType>` | `CAMERA, CAMERA_PERSON, CAMERA_PET, CAMERA_VEHICLE, MICROPHONE` | Set of trigger types that are routed to notification channels. |
| `notification_attach_media` | `Boolean` | `true` | When `true`, the media file path is included in notifications that support attachments. |

### Signal Channel

| Key | Type | Default | Description |
|---|---|---|---|
| `signal_enabled` | `Boolean` | `false` | Enables the Signal REST channel. |
| `signal_server_url` | `String` | `""` | Base URL of the signal-cli REST API instance (e.g., `http://192.168.1.10:8080`). <!-- VERIFY: signal-cli REST API default port --> |
| `signal_sender_number` | `String` | `""` | Sender phone number registered with the signal-cli REST API (E.164 format). |
| `signal_recipient_number` | `String` | `""` | Recipient phone number to send alerts to (E.164 format). |
| `signal_bearer_token` | `String` | `""` | Bearer JWT for authenticating with the signal-cli REST API. |
| `heartbeat_signal_minutes` | `Int` | `0` | Heartbeat interval in minutes over Signal. `0` = disabled. |

### Mattermost Channel

| Key | Type | Default | Description |
|---|---|---|---|
| `mattermost_enabled` | `Boolean` | `false` | Enables the Mattermost incoming webhook channel. |
| `mattermost_webhook_url` | `String` | `""` | Full incoming webhook URL provided by your Mattermost server. |
| `heartbeat_mattermost_minutes` | `Int` | `0` | Heartbeat interval in minutes over Mattermost. `0` = disabled. |

### Pushover Channel

| Key | Type | Default | Description |
|---|---|---|---|
| `pushover_enabled` | `Boolean` | `false` | Enables the Pushover channel. |
| `pushover_user_key` | `String` | `""` | Pushover user key from your Pushover account. |
| `pushover_app_token` | `String` | `""` | Pushover application token for the Haven app entry. |
| `heartbeat_pushover_minutes` | `Int` | `0` | Heartbeat interval in minutes over Pushover. `0` = disabled. |

### Cloud Server Channel

| Key | Type | Default | Description |
|---|---|---|---|
| `cloud_enabled` | `Boolean` | `false` | Enables the self-hosted Haven server channel. |
| `cloud_server_url` | `String` | `""` | Base URL of the Haven server API (e.g., `http://192.168.1.10:8000`). |
| `cloud_app_key` | `String` | `""` | API key for authenticating the Android app with the Haven server. |

### Diagnostics

| Key | Type | Default | Description |
|---|---|---|---|
| `log_level` | `String` | `"NORMAL"` | In-app log verbosity. Values: `"NORMAL"` or `"DEBUG"`. |

---

## Server — Environment Variables

The Haven server is configured entirely via environment variables. Copy `server/.env.example` to
`server/.env` and fill in the required values before starting the server.

```bash
cp server/.env.example server/.env
```

### Database and Cache

| Variable | Required | Default | Description |
|---|---|---|---|
| `DATABASE_URL` | Optional | `postgresql+asyncpg://haven:haven@localhost:5432/haven` | SQLAlchemy async connection URL for PostgreSQL. Must point to a running PostgreSQL 16+ instance. |
| `REDIS_URL` | Optional | `redis://localhost:6379/0` | Redis connection URL used by Celery as its broker and result backend. |

The `docker-compose.yml` in `server/` starts PostgreSQL 16 and Redis 7 locally. For production,
replace these with your own managed database and Redis instances.

### Security

| Variable | Required | Default | Description |
|---|---|---|---|
| `SECRET_KEY` | **Required** | `change-me-to-random-64-chars` | Secret key used to sign JWT access and refresh tokens. Must be changed to a random 64-character string in any non-development deployment. The default value is insecure. |
| `ACCESS_TOKEN_EXPIRE_MINUTES` | Optional | `30` | Lifetime of JWT access tokens in minutes. |
| `REFRESH_TOKEN_EXPIRE_DAYS` | Optional | `7` | Lifetime of JWT refresh tokens in days. |

### Media Storage

| Variable | Required | Default | Description |
|---|---|---|---|
| `MEDIA_ROOT` | Optional | `./media` | Directory where uploaded media files (video clips, images) are stored. Must be writable by the server process. The `docker-compose.yml` mounts this as a volume. |

### AI Backend

| Variable | Required | Default | Description |
|---|---|---|---|
| `AI_BACKEND` | Optional | `none` | Which AI backend to use for server-side analysis: `none`, `tflite`, or `openrouter`. |
| `TFLITE_MODEL_PATH` | Optional | `./efficientdet_lite0.tflite` | Path to the EfficientDet Lite 0 TFLite model file. Required when `AI_BACKEND=tflite`. |
| `OPENROUTER_API_KEY` | Optional | `""` | OpenRouter API key. Required when `AI_BACKEND=openrouter`. <!-- VERIFY: OpenRouter key format and required scopes --> |
| `OPENROUTER_MODEL` | Optional | `google/gemini-flash-1.5` | OpenRouter model identifier used for analysis. Only read when `AI_BACKEND=openrouter`. |

### SMTP Email Notifications

All SMTP variables are optional. When `SMTP_HOST` is empty, email notifications are disabled.

| Variable | Required | Default | Description |
|---|---|---|---|
| `SMTP_HOST` | Optional | `""` | SMTP server hostname. Empty = email notifications disabled. |
| `SMTP_PORT` | Optional | `587` | SMTP server port. |
| `SMTP_USER` | Optional | `""` | SMTP authentication username. |
| `SMTP_PASSWORD` | Optional | `""` | SMTP authentication password. |
| `SMTP_FROM` | Optional | `haven@example.com` | From address used in notification emails. |

### Signal Notifications (Server-Side)

| Variable | Required | Default | Description |
|---|---|---|---|
| `SIGNAL_API_URL` | Optional | `""` | Base URL of the signal-cli REST API for server-side Signal alerts. Empty = disabled. |
| `SIGNAL_SENDER` | Optional | `""` | Phone number registered with the signal-cli REST API (E.164 format). |
| `SIGNAL_AUTH_TOKEN` | Optional | `""` | Bearer token for authenticating with the signal-cli REST API. |

### Pushover Notifications (Server-Side)

| Variable | Required | Default | Description |
|---|---|---|---|
| `PUSHOVER_APP_TOKEN` | Optional | `""` | Pushover application token for server-side push notifications. Empty = disabled. The user key is stored per-account in the database, not in this file. |

---

## Per-Environment Setup

### Development

Use the defaults from `server/.env.example` as-is with the Docker Compose stack:

```bash
cd server
docker compose up -d db redis
cp .env.example .env
# Edit SECRET_KEY at minimum
```

The `docker-compose.yml` defines four services:

| Service | Port | Description |
|---|---|---|
| `db` | 5432 | PostgreSQL 16 |
| `redis` | 6379 | Redis 7 |
| `app` | 8000 | FastAPI application (uvicorn with `--reload`) |
| `worker` | — | Celery worker (concurrency 2) |
| `webui` | 8080 | Django web UI |

### Production

At minimum, change these values from their defaults before deploying:

1. **`SECRET_KEY`** — generate with `python -c "import secrets; print(secrets.token_hex(32))"` and set to a 64-character random string.
2. **`DATABASE_URL`** — point to your production PostgreSQL instance with a strong password.
3. **`REDIS_URL`** — point to your production Redis instance.
4. **`MEDIA_ROOT`** — set to a persistent, backed-up directory outside the container filesystem. <!-- VERIFY: recommended production media volume path -->

### Android App — First-Run Defaults

Settings that are not yet written to DataStore use these defaults on first launch:

- Sensitivity: `MEDIUM`
- Countdown: 60 seconds
- Calibration warmup: 10 seconds
- Camera: `BACK`, enabled
- All sensors: enabled
- Detection mode: `MOTION_ONLY`
- Media encryption: enabled
- All notification channels: disabled
- Log level: `NORMAL`
