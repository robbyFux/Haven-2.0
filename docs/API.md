<!-- GSD:generated -->
<!-- generated-by: gsd-doc-writer -->

# Haven Cloud — API Reference

Haven Cloud exposes two separate server-side interfaces:

- **FastAPI REST API** (`/api/v1/`) — used by the Haven Android app and programmatic clients
- **Django Web Interface** (`/`) — browser-facing HTML pages for account management and event review

The FastAPI server runs on port 8000 (default). Interactive docs are available at `/docs` (Swagger UI) and `/redoc` (ReDoc).

---

## Authentication Overview

Two authentication mechanisms are used depending on the caller:

| Mechanism | Header | Used by |
|---|---|---|
| JWT Bearer | `Authorization: Bearer <access_token>` | Human/user-facing endpoints |
| App-Key | `X-App-Key: hav_<64hex>` | Device upload endpoint |

**JWT tokens** are obtained via `POST /api/v1/auth/login`. The access token expires after `ACCESS_TOKEN_EXPIRE_MINUTES` (default: 30 minutes). Use `POST /api/v1/auth/refresh` to rotate both tokens before expiry.

**App-Keys** are generated when registering a device (`POST /api/v1/devices/`). Format: `hav_` followed by 64 hex characters. The key is shown in full on creation only — it must be stored by the client immediately.

**Admin endpoints** require a JWT Bearer token from a user with `is_admin = true`. A `403 Forbidden` is returned otherwise.

---

## Standard Error Response

All error responses follow FastAPI's default shape:

```json
{
  "detail": "Human-readable error message"
}
```

Common status codes:

| Code | Meaning |
|---|---|
| `400` | Bad request (e.g. 2FA not set up, invalid input) |
| `401` | Missing, invalid, or expired credentials |
| `403` | Authenticated but insufficient privilege (admin required) |
| `404` | Resource not found or belongs to another user |
| `409` | Conflict (e.g. username already taken) |
| `413` | Payload too large — video exceeds 50 MB or storage quota exceeded |
| `422` | Unprocessable entity — request body validation failed |
| `429` | Too many requests — event count quota exceeded |

---

## Auth

Base path: `/api/v1/auth`

### `POST /api/v1/auth/register`

Register a new user account.

**Auth required:** No

**Request body:**

```json
{
  "username": "alice",
  "password": "secretpassword"
}
```

| Field | Type | Constraints |
|---|---|---|
| `username` | `string` | 3–50 characters |
| `password` | `string` | Minimum 8 characters |

**Response `201`:**

```json
{
  "id": 1,
  "username": "alice",
  "user_key": "haven_u_<64hex>"
}
```

`user_key` is the user's long-lived API key (format: `haven_u_<64 hex chars>`). Store it — it is used as the base credential when registering Haven devices.

**Errors:** `409` username already taken.

---

### `POST /api/v1/auth/login`

Authenticate and receive a JWT token pair.

**Auth required:** No

**Request body:**

```json
{
  "username": "alice",
  "password": "secretpassword",
  "totp_code": "123456"
}
```

| Field | Type | Required |
|---|---|---|
| `username` | `string` | Yes |
| `password` | `string` | Yes |
| `totp_code` | `string` | Only if 2FA is enabled |

**Response `200`:**

```json
{
  "access_token": "<jwt>",
  "refresh_token": "<jwt>",
  "token_type": "bearer"
}
```

**Errors:** `401` invalid credentials, `401` TOTP required (header `X-TOTP-Required: true`), `401` invalid TOTP code.

---

### `POST /api/v1/auth/refresh`

Rotate both tokens using a valid refresh token.

**Auth required:** No (refresh token in body)

**Request body:**

```json
{
  "refresh_token": "<jwt>"
}
```

**Response `200`:** Same shape as `/login` — new access + refresh token pair.

**Errors:** `401` invalid or expired refresh token.

---

### `GET /api/v1/auth/me`

Return the current user's profile.

**Auth required:** JWT Bearer

**Response `200`:**

```json
{
  "id": 1,
  "username": "alice",
  "is_admin": false,
  "totp_enabled": false
}
```

---

### `POST /api/v1/auth/2fa/setup`

Generate a TOTP secret and QR code for 2FA enrollment. 2FA is **not yet active** after this call — the user must confirm with `/2fa/verify`.

**Auth required:** JWT Bearer

**Response `200`:**

```json
{
  "secret": "BASE32SECRET",
  "qr_code_base64": "<base64-encoded PNG>"
}
```

---

### `POST /api/v1/auth/2fa/verify`

Confirm a TOTP code and enable 2FA for the authenticated user.

**Auth required:** JWT Bearer

**Request body:**

```json
{
  "code": "123456"
}
```

| Field | Type | Constraints |
|---|---|---|
| `code` | `string` | Exactly 6 characters |

**Response `200`:**

```json
{
  "status": "2fa_enabled"
}
```

**Errors:** `400` 2FA setup not initiated, `400` invalid TOTP code.

---

## Devices

Base path: `/api/v1/devices`

All device endpoints require **JWT Bearer** authentication.

### `POST /api/v1/devices/`

Register a new Haven Android app as a device.

**Auth required:** JWT Bearer

**Request body:**

```json
{
  "name": "Bedroom Camera"
}
```

| Field | Type | Constraints |
|---|---|---|
| `name` | `string` | 1–100 characters |

**Response `201`:**

```json
{
  "id": 7,
  "name": "Bedroom Camera",
  "app_key": "hav_<64hex>",
  "is_active": true,
  "created_at": "2026-04-13T10:00:00Z",
  "last_seen_at": null
}
```

The `app_key` is shown in full here. Store it in the Android app's settings immediately — this is the credential used for event uploads.

---

### `GET /api/v1/devices/`

List all devices registered to the current user, newest first.

**Auth required:** JWT Bearer

**Response `200`:**

```json
{
  "devices": [
    {
      "id": 7,
      "name": "Bedroom Camera",
      "app_key": "hav_<64hex>",
      "is_active": true,
      "created_at": "2026-04-13T10:00:00Z",
      "last_seen_at": "2026-04-13T12:30:00Z"
    }
  ]
}
```

---

### `GET /api/v1/devices/{device_id}`

Retrieve a single device by ID.

**Auth required:** JWT Bearer

**Path parameters:** `device_id` — integer

**Response `200`:** Single `DeviceResponse` object (same shape as above).

**Errors:** `404` device not found or belongs to another user.

---

### `DELETE /api/v1/devices/{device_id}`

Revoke a device (soft delete — sets `is_active = false`). The App-Key is invalidated immediately.

**Auth required:** JWT Bearer

**Path parameters:** `device_id` — integer

**Response `200`:**

```json
{
  "status": "revoked"
}
```

**Errors:** `404` device not found.

---

## Events

Events are security incidents captured by a Haven device. Upload uses App-Key auth; retrieval uses JWT Bearer.

### `POST /api/v1/devices/{app_key}/events`

Upload a security event from a Haven device. Accepts `multipart/form-data`.

**Auth required:** `X-App-Key` header (App-Key auth). The `{app_key}` path parameter must equal the `X-App-Key` header value.

**Headers:**

| Header | Required | Description |
|---|---|---|
| `X-App-Key` | Yes | Device App-Key (`hav_<64hex>`) |
| `X-Encryption-Password` | No | If provided, the video is encrypted server-side with AES-256-GCM using this password + username as key material. |

**Form fields:**

| Field | Type | Required | Description |
|---|---|---|---|
| `metadata` | `string` (JSON) | Yes | JSON-encoded event metadata — see schema below |
| `video` | `file` | No | Video clip, maximum 50 MB |

**Metadata JSON schema (`EventCreateSchema`):**

```json
{
  "event_type": "CAMERA_PERSON",
  "severity": "HIGH",
  "timestamp": "2026-04-13T10:05:00Z",
  "sensor_value": 0.87,
  "triggers": [
    { "trigger_type": "CAMERA", "sensor_value": 0.15 },
    { "trigger_type": "ACCELEROMETER", "sensor_value": 2.3 }
  ]
}
```

| Field | Type | Required | Values |
|---|---|---|---|
| `event_type` | `string` | Yes | Android `TriggerType` name (e.g. `CAMERA`, `CAMERA_PERSON`, `MICROPHONE`, `ACCELEROMETER`, `LIGHT`, `GYROSCOPE`) |
| `severity` | `string` | Yes | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| `timestamp` | `datetime` | Yes | ISO 8601 with timezone |
| `sensor_value` | `float` | No | Primary sensor reading at trigger time |
| `triggers` | `array` | No | Individual sensor triggers that contributed to this event |

**Upload flow:** After persisting the event, a Celery background task is enqueued. If `AI_BACKEND` is set to `tflite` or `openrouter`, AI analysis runs first and chains to the notification task. If `AI_BACKEND` is `none`, the notification task is enqueued directly.

**Response `201`:**

```json
{
  "id": 42,
  "event_type": "CAMERA_PERSON",
  "severity": "HIGH",
  "timestamp": "2026-04-13T10:05:00Z",
  "sensor_value": 0.87,
  "media_path": "users/1/events/42/clip.mp4",
  "has_analysis": false,
  "device_name": "Bedroom Camera",
  "created_at": "2026-04-13T10:05:01Z"
}
```

**Errors:** `401` App-Key mismatch or invalid, `413` video too large or storage quota exceeded, `422` invalid metadata JSON, `429` event count quota exceeded.

---

### `GET /api/v1/events`

Paginated list of events for the authenticated user, sorted by timestamp descending.

**Auth required:** JWT Bearer

**Query parameters:**

| Parameter | Type | Default | Description |
|---|---|---|---|
| `page` | `int` | `1` | Page number (≥ 1) |
| `page_size` | `int` | `20` | Results per page (1–100) |
| `device_id` | `int` | — | Filter by device ID |
| `severity` | `string` | — | Filter by severity (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`) |

**Response `200`:**

```json
{
  "events": [ /* array of EventResponse */ ],
  "total": 157,
  "page": 1,
  "page_size": 20
}
```

---

### `GET /api/v1/events/{event_id}`

Retrieve a single event by ID.

**Auth required:** JWT Bearer

**Path parameters:** `event_id` — integer

**Response `200`:** Single `EventResponse` object.

**Errors:** `404` event not found or belongs to another user.

---

### `DELETE /api/v1/events/{event_id}`

Delete an event, its media file, and update the user's storage counters. `EventTrigger` and `AnalysisResult` rows are cascade-deleted.

**Auth required:** JWT Bearer

**Path parameters:** `event_id` — integer

**Response `200`:**

```json
{
  "status": "deleted"
}
```

**Errors:** `404` event not found.

---

## Notifications

Base path: `/api/v1/notifications`

All notification endpoints require **JWT Bearer** authentication.

Supported notification channels: **Email** (SMTP), **Signal** (signal-cli REST API), **Pushover**.

### `GET /api/v1/notifications/settings`

Return the current user's notification channel configuration.

**Auth required:** JWT Bearer

**Response `200`:**

```json
{
  "notification_email": "alice@example.com",
  "notification_signal_number": "+491701234567",
  "pushover_user_key": "uXXXXXXXXXXXXXXXXXXXXXX",
  "notifications_enabled": true
}
```

Fields are `null` when not configured.

---

### `PATCH /api/v1/notifications/settings`

Update the current user's notification channel configuration. Only fields present in the request body are updated — omitted fields are left unchanged.

**Auth required:** JWT Bearer

**Request body (all fields optional):**

```json
{
  "notification_email": "alice@example.com",
  "notification_signal_number": "+491701234567",
  "pushover_user_key": "uXXXXXXXXXXXXXXXXXXXXXX",
  "notifications_enabled": true
}
```

**Response `200`:** Updated `NotificationSettingsResponse` (same shape as GET).

---

### `POST /api/v1/notifications/test`

Send a test alert to all notification channels configured by the current user. Channels without required server-side credentials (SMTP host, Signal API URL, Pushover app token) are silently skipped.

**Auth required:** JWT Bearer

**Response `200`:**

```json
{
  "results": {
    "email": true,
    "signal": false,
    "pushover": true
  }
}
```

Each key is `true` if the send succeeded, `false` if it failed. Missing keys indicate the channel was not attempted.

---

## Admin

Base path: `/api/v1/admin`

All admin endpoints require **JWT Bearer** from a user with `is_admin = true`. Non-admin users receive `403 Forbidden`.

### `GET /api/v1/admin/users`

List all registered users with quota details and device counts.

**Auth required:** JWT Bearer + admin

**Response `200`:**

```json
{
  "users": [
    {
      "id": 1,
      "username": "alice",
      "is_admin": true,
      "is_active": true,
      "totp_enabled": false,
      "storage_quota_mb": 1024,
      "max_events": 10000,
      "current_storage_bytes": 204800,
      "current_event_count": 12,
      "device_count": 2,
      "created_at": "2026-01-01T00:00:00Z"
    }
  ],
  "total": 1
}
```

---

### `GET /api/v1/admin/users/{user_id}`

Full detail for a single user.

**Auth required:** JWT Bearer + admin

**Path parameters:** `user_id` — integer

**Response `200`:** Single `UserAdminResponse` object (same shape as above).

**Errors:** `404` user not found.

---

### `PATCH /api/v1/admin/users/{user_id}/quota`

Update a user's storage and/or event quota. Only provided fields are changed.

**Auth required:** JWT Bearer + admin

**Request body:**

```json
{
  "storage_quota_mb": 2048,
  "max_events": 50000
}
```

Both fields are optional and must be ≥ 0 if provided.

**Response `200`:** Updated `UserAdminResponse`.

**Errors:** `404` user not found, `422` negative value.

---

### `PATCH /api/v1/admin/users/{user_id}/admin`

Set or clear the `is_admin` flag for a user.

**Auth required:** JWT Bearer + admin

**Request body:**

```json
{
  "is_admin": true
}
```

**Response `200`:** Updated `UserAdminResponse`.

**Errors:** `404` user not found, `422` `is_admin` not a boolean.

---

### `DELETE /api/v1/admin/users/{user_id}`

Deactivate a user (soft delete — sets `is_active = false`). An admin cannot deactivate their own account.

**Auth required:** JWT Bearer + admin

**Path parameters:** `user_id` — integer

**Response `200`:**

```json
{
  "status": "deactivated"
}
```

**Errors:** `403` cannot deactivate own account, `404` user not found.

---

### `GET /api/v1/admin/stats`

Server-wide aggregate statistics.

**Auth required:** JWT Bearer + admin

**Response `200`:**

```json
{
  "total_users": 42,
  "total_events": 1830,
  "total_devices": 87,
  "total_storage_bytes": 5368709120
}
```

---

## Health

### `GET /health`

Server health check. Not under `/api/v1/` — mounted at root level. Used by Docker health checks and load balancers.

**Auth required:** No

**Response `200`:**

```json
{
  "status": "ok",
  "version": "1.0.0"
}
```

---

## Web Interface Routes

The Django Web UI is a browser-facing application. All routes return HTML. It runs as a separate service (see `server/Dockerfile.webui`) and communicates with the FastAPI backend via internal HTTP.

Authentication is session-based (Django `LoginView`). Unauthenticated requests to protected routes redirect to `/accounts/login/`. Admin routes additionally require `is_admin = true`.

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET/POST` | `/accounts/login/` | Public | Login form |
| `GET/POST` | `/accounts/register/` | Public | New account registration |
| `GET` | `/accounts/logout/` | Session | Log out and clear session |
| `GET/POST` | `/accounts/totp-verify/` | Partial (post-login) | TOTP second-factor verification |
| `GET/POST` | `/accounts/2fa-setup/` | Session | Enroll TOTP authenticator |
| `GET/POST` | `/accounts/profile/` | Session | View and edit user profile |
| `GET/POST` | `/accounts/change-password/` | Session | Change account password |
| `GET/POST` | `/accounts/delete-account/` | Session | Delete own account |
| `GET` | `/events/` | Session | Paginated event list with filter controls |
| `GET` | `/events/{event_id}/` | Session | Event detail with triggers, media player, and AI analysis |
| `GET` | `/events/{event_id}/video/` | Session | Stream or download event video file |
| `POST` | `/events/{event_id}/delete/` | Session | Delete an event |
| `GET` | `/devices/` | Session | List registered devices |
| `GET/POST` | `/devices/create/` | Session | Register a new device |
| `POST` | `/devices/{device_id}/revoke/` | Session | Revoke a device |
| `GET/POST` | `/notifications/` | Session | View and update notification channel settings |
| `GET` | `/admin/` | Session + admin | Admin dashboard with server statistics and user list |
| `GET` | `/admin/users/{user_id}/` | Session + admin | User detail |
| `GET/POST` | `/admin/users/{user_id}/quota/` | Session + admin | Edit user quota |
| `POST` | `/admin/users/{user_id}/toggle-active/` | Session + admin | Toggle user active/inactive |
| `GET` | `/` | — | Redirects to `/events/` |

---

## Background Tasks (Celery)

The following tasks are enqueued automatically by the API — they are not callable via HTTP.

| Task | Trigger | Description |
|---|---|---|
| `analyze_event_task` | Event upload (when `AI_BACKEND != "none"`) | Decrypts video if needed, runs TFLite or OpenRouter AI inference, persists `AnalysisResult`, then chains to `send_notification_task` |
| `send_notification_task` | Event upload (AI disabled) or after analysis | Sends alerts via Email, Signal, and/or Pushover based on user notification settings |

AI backends: `none` (disabled), `tflite` (local EfficientDet Lite 0), `openrouter` (OpenRouter vision API). Configured via the `AI_BACKEND` environment variable. See `docs/CONFIGURATION.md` for full environment variable reference.
