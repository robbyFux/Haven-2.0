---
phase: 05-cloud-server
reviewed: 2026-04-07T00:00:00Z
depth: standard
files_reviewed: 36
files_reviewed_list:
  - app/src/main/java/org/havenapp/main/MonitorService.kt
  - app/src/main/java/org/havenapp/main/notify/CloudChannel.kt
  - app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt
  - app/src/main/java/org/havenapp/main/ui/settings/SettingsScreen.kt
  - app/src/main/java/org/havenapp/main/ui/settings/SettingsViewModel.kt
  - server/app/config.py
  - server/app/database.py
  - server/app/dependencies/auth.py
  - server/app/dependencies/quota.py
  - server/app/main.py
  - server/app/ml/detector.py
  - server/app/ml/openrouter.py
  - server/app/models/base.py
  - server/app/models/device.py
  - server/app/models/event.py
  - server/app/models/user.py
  - server/app/routers/admin.py
  - server/app/routers/auth.py
  - server/app/routers/devices.py
  - server/app/routers/events.py
  - server/app/routers/notifications.py
  - server/app/schemas/admin.py
  - server/app/schemas/auth.py
  - server/app/schemas/device.py
  - server/app/schemas/event.py
  - server/app/schemas/notification.py
  - server/app/services/crypto.py
  - server/app/services/jwt.py
  - server/app/services/notify.py
  - server/app/services/storage.py
  - server/app/services/totp.py
  - server/app/tasks/analysis.py
  - server/app/tasks/notifications.py
  - server/tests/test_admin.py
  - server/tests/test_analysis.py
  - server/tests/test_auth.py
  - server/tests/test_devices.py
  - server/tests/test_events.py
  - server/tests/test_notifications.py
findings:
  critical: 4
  warning: 7
  info: 5
  total: 16
status: issues_found
---

# Phase 05: Code Review Report

**Reviewed:** 2026-04-07
**Depth:** standard
**Files Reviewed:** 36
**Status:** issues_found

## Summary

This phase introduces the self-hosted Haven Cloud Server (FastAPI/Python) and the Android-side `CloudChannel` client. The architecture is sound: clean separation between routers, services, and tasks; Argon2id + AES-GCM for media encryption; JWT + optional TOTP 2FA for user auth; and X-App-Key device authentication. Test coverage is broad and well-structured.

Four critical issues were found: a path-traversal vulnerability in media storage, weak deterministic salt in the file-encryption key-derivation scheme, JSON injection risk in CloudChannel's manual JSON construction, and the encryption key being passed in plaintext over the Celery broker. Seven warnings cover logic gaps that can cause data-loss or silent failures in production.

---

## Critical Issues

### CR-01: Path traversal in media storage via unvalidated filename

**File:** `server/app/routers/events.py:140-143`
**Issue:** The `filename` used for media storage is derived directly from `video.filename` (the multipart filename field submitted by the client). If an attacker sends `filename=../../etc/passwd` or `../../config.py`, the resulting path after `os.path.join(MEDIA_ROOT, user_id, event_id, filename)` can escape the intended directory on some OS configurations — or at minimum write to unexpected paths.

```python
# Vulnerable:
original_filename = video.filename or "video.mp4"
if is_encrypted:
    filename = original_filename + ".enc"
else:
    filename = original_filename
```

**Fix:** Sanitize the filename before use. Replace with a server-generated name entirely (safest), or at minimum strip path separators:

```python
import os
raw_name = video.filename or "video.mp4"
# Strip all path components; keep only the base name
safe_name = os.path.basename(raw_name).replace("..", "").strip() or "video.mp4"
filename = (safe_name + ".enc") if is_encrypted else safe_name
```

The simplest safe approach is to ignore the client-supplied name entirely and generate one:
```python
import secrets
ext = ".enc" if is_encrypted else ".mp4"
filename = f"{secrets.token_hex(8)}{ext}"
```

---

### CR-02: Deterministic key derivation salt leaks keys across password changes

**File:** `server/app/services/crypto.py:47-58`
**Issue:** The Argon2id salt is derived deterministically from the lowercased username (padded to 16 bytes). If a user changes their password, `derive_key(new_password, username)` produces a new key — but old encrypted files were written with the old key. More critically, an attacker who compromises the database and learns a username can attempt offline dictionary attacks against any user's encrypted file using only the username as salt, because the salt is publicly derivable. Standard practice is to generate a random salt per-file and store it with the ciphertext.

The immediate impact: files encrypted under the old password cannot be decrypted after a password change, which is an undocumented data-loss risk; and the effective salt entropy is bounded by username length (often 3–50 characters), not the Argon2id 16-byte requirement.

**Fix:** Generate a random 16-byte salt per encryption operation and prepend it to the output alongside the nonce:

```python
# encrypt_file: nonce (12) || salt (16) || ciphertext+tag
def encrypt_file(data: bytes, password: str) -> bytes:
    salt = os.urandom(16)
    key = _derive_key_with_salt(password, salt)
    nonce = os.urandom(12)
    ct = AESGCM(key).encrypt(nonce, data, None)
    return salt + nonce + ct

def decrypt_file(data: bytes, password: str) -> bytes:
    salt, nonce, ct = data[:16], data[16:28], data[28:]
    key = _derive_key_with_salt(password, salt)
    return AESGCM(key).decrypt(nonce, ct, None)
```

Note: the call sites in `events.py` (line 135-136) and `tasks/analysis.py` (line 68-69) pass the raw derived key bytes today, so any refactor must update both callers consistently.

---

### CR-03: JSON injection in CloudChannel heartbeat via manual string interpolation

**File:** `app/src/main/java/org/havenapp/main/notify/CloudChannel.kt:90-92`
**Issue:** The heartbeat JSON body is built via string interpolation with manual escaping. The escaping only handles `\` and `"` — it does not handle control characters (newlines, tabs, null bytes). A heartbeat message containing a literal `\n` or `\t` in `_state.value.toString()` or `VERSION_NAME` would produce malformed JSON that could be misinterpreted by the server.

```kotlin
// Current — fragile:
val escaped = message.replace("\\", "\\\\").replace("\"", "\\\"")
val jsonBody = """{"message":"$escaped"}"""
```

**Fix:** Use a proper JSON library. OkHttp ships `okhttp3.internal.http2` but the simplest idiomatic fix uses the `org.json` library (available on Android) or Gson/kotlinx-serialization which is already in scope:

```kotlin
import org.json.JSONObject
val jsonBody = JSONObject().put("message", message).toString()
    .toRequestBody("application/json".toMediaType())
```

---

### CR-04: Encryption key transmitted as plaintext hex string through Celery broker

**File:** `server/app/routers/events.py:208-209` and `server/app/tasks/analysis.py:34,64-72`
**Issue:** When a user uploads a video with an `X-Encryption-Password` header, the server derives an AES key from the password and passes `encryption_key.hex()` as a plain string argument to the Celery task via the Redis broker. Redis stores task arguments in plaintext by default. Any party with read access to Redis (other workers, Redis monitoring, Redis persistence dump) can recover the 32-byte AES key and decrypt all protected media files.

```python
# events.py:208-209 — key material goes to Redis unprotected:
encryption_key_hex = encryption_key.hex() if encryption_key is not None else None
analyze_event_task.delay(event.id, media_path, encryption_key_hex)
```

**Fix (short-term):** Do not pass the key through the broker at all. Store the key encrypted at rest in the database (e.g. Fernet-wrapped with `SECRET_KEY`) and retrieve it inside the task:

```python
# events.py — store wrapped key in DB instead
wrapped_key = Fernet(fernet_key).encrypt(encryption_key)
event.wrapped_encryption_key = wrapped_key  # add column to Event model
analyze_event_task.delay(event.id, media_path)  # no key in task args

# tasks/analysis.py — unwrap inside the worker
wrapped = event.wrapped_encryption_key
key = Fernet(fernet_key).decrypt(wrapped)
```

**Fix (longer-term):** Enable Redis AUTH + TLS, use Celery's `task_serializer = "json"` with message signing, or move to an encrypted backend.

---

## Warnings

### WR-01: Refresh token endpoint does not validate that the user still exists or is active

**File:** `server/app/routers/auth.py:117-141`
**Issue:** The `/refresh` endpoint decodes the refresh token and issues a new token pair without verifying that the user identified by `sub` still exists in the database or has `is_active=True`. A deactivated user (soft-deleted via admin) can continue refreshing tokens indefinitely until the refresh token expires (7 days by default).

**Fix:** Load the user from the database in the refresh endpoint and check `is_active`:

```python
@router.post("/refresh", response_model=TokenResponse)
async def refresh(body: RefreshRequest, db: DbSession) -> TokenResponse:
    ...
    result = await db.execute(select(User).where(User.id == int(subject)))
    user = result.scalar_one_or_none()
    if user is None or not user.is_active:
        raise credentials_exception
    return TokenResponse(
        access_token=create_access_token(subject),
        refresh_token=create_refresh_token(subject),
    )
```

---

### WR-02: Video upload streams entire file into memory before size check

**File:** `server/app/routers/events.py:118-128`
**Issue:** The chunked read loop accumulates all chunks into `video_bytes` (a single `bytes` object in memory) and only raises HTTP 413 after reading beyond the limit. For a 50 MB upload, the server allocates 50 MB+ of RAM per concurrent request before rejecting it. Under concurrent upload load this can cause OOM. The check fires one chunk too late — the server reads `64 KB` past the limit before it raises.

```python
video_bytes += chunk
if len(video_bytes) > MAX_VIDEO_BYTES:   # 50 MB + 64 KB already in memory
    raise HTTPException(...)
```

**Fix:** Check the limit before appending the new chunk, not after:

```python
if len(video_bytes) + len(chunk) > MAX_VIDEO_BYTES:
    raise HTTPException(status_code=413, detail=f"Video exceeds {MAX_VIDEO_BYTES // (1024*1024)} MB")
video_bytes += chunk
```

Additionally, FastAPI supports limiting request body size at the ASGI layer via `max_size` on `UploadFile`, which is more memory-efficient.

---

### WR-03: User quota counters are not protected against concurrent uploads causing over-quota writes

**File:** `server/app/routers/events.py:107-193`
**Issue:** The quota check (`check_event_quota`, `check_storage_quota`) reads `user.current_event_count` and `user.current_storage_bytes` at the start of the request, then increments them at the end. Two concurrent uploads from the same device can both pass the quota check and both commit, resulting in the user exceeding their quota by one event or one upload.

**Fix:** Use a database-level `SELECT ... FOR UPDATE` to lock the user row during the quota check, or use a `CASE`-guarded `UPDATE` with a `WHERE` clause that enforces the quota atomically:

```sql
UPDATE users SET current_event_count = current_event_count + 1
WHERE id = :user_id AND current_event_count < max_events
RETURNING current_event_count;
-- If no row returned, quota exceeded.
```

Alternatively, a Redis-backed per-user lock with a short TTL is appropriate for the self-hosted scale of this project.

---

### WR-04: `asyncio.run()` called from within an already-running event loop in Celery tasks

**File:** `server/app/tasks/analysis.py:181` and `server/app/tasks/notifications.py:40,66`
**Issue:** `asyncio.run()` creates and runs a new event loop. If Celery is configured to use a gevent or eventlet pool, or if the task is ever called from within an async context (e.g., during tests or when using `celery -P solo`), calling `asyncio.run()` from within a running loop raises `RuntimeError: This event loop is already running`. The test suite works around this with a `ThreadPoolExecutor` hack in `test_notifications.py:156-163` — this is a signal that the production code has the same risk.

**Fix:** Use `asyncio.get_event_loop().run_until_complete()` with a guard, or restructure Celery tasks to use `celery[asyncio]` / `asgiref.sync.async_to_sync`, which handles nested loop detection:

```python
from asgiref.sync import async_to_sync

# Replace asyncio.run(_persist()) with:
async_to_sync(_persist)()
```

---

### WR-05: `verify_app_key` commits `last_seen_at` update on every authenticated device request — N+1 DB commit problem

**File:** `server/app/dependencies/auth.py:92-93`
**Issue:** Every call to `verify_app_key` performs a `db.commit()` to update `device.last_seen_at`. For the event upload endpoint (`upload_event`), this means two commits per request: one inside `verify_app_key` and one at the end of the route handler. The `verify_app_key` dependency also receives its own `db` session (via `Depends(get_db)`) while `upload_event` calls `verify_app_key(x_app_key=x_app_key, db=db)` passing the route's `db` directly (line 89) — meaning the `last_seen_at` update and the event creation share the same session but the intermediate commit can leave the session in an inconsistent state if the route subsequently raises an exception before its own `db.commit()`.

**Fix:** Defer `last_seen_at` to a background task or use `db.flush()` instead of `db.commit()` inside `verify_app_key`, letting the route's outer commit handle persistence:

```python
device.last_seen_at = datetime.now(timezone.utc)
await db.flush()   # not commit — let the caller own the transaction boundary
```

---

### WR-06: `CloudConfigDialog` saves server URL and app key as separate ViewModel calls — race condition window

**File:** `app/src/main/java/org/havenapp/main/ui/settings/SettingsScreen.kt:654-658`
**Issue:** When the user saves the Cloud config dialog, `setCloudServerUrl` and `setCloudAppKey` are called as two separate ViewModel functions, each dispatching a separate `dataStore.edit { }` write. Between the two writes there is a brief window where the URL is updated but the app key is still the old value (or vice versa). If `MonitorService` starts and reads settings during this window, it will construct a `CloudChannel` with a mismatched URL and key.

```kotlin
onSave = { url, key ->
    viewModel.setCloudServerUrl(url)   // first DataStore write
    viewModel.setCloudAppKey(key)      // second DataStore write (race window)
}
```

**Fix:** Add a combined setter to `SettingsRepository` and `SettingsViewModel`:

```kotlin
// SettingsRepository
suspend fun setCloudConfig(url: String, appKey: String) {
    dataStore.edit { prefs ->
        prefs[KEY_CLOUD_SERVER_URL] = url
        prefs[KEY_CLOUD_APP_KEY] = appKey
    }
}

// SettingsScreen onSave callback
onSave = { url, key -> viewModel.setCloudConfig(url, key) }
```

This is the same pattern already used for `setSignalConfig`, `setPinCredentials`, and `setPushoverConfig`.

---

### WR-07: TFLite interpreter is not thread-safe but the comment relies on single-worker assumption

**File:** `server/app/ml/detector.py:86-87`
**Issue:** The docstring states "The interpreter is not thread-safe — Celery workers run one task at a time per process, so no locking is required in the default single-threaded worker configuration." This assumption is only valid with the `prefork` concurrency pool at concurrency=1, or the `solo` pool. The default Celery configuration uses `prefork` with `os.cpu_count()` workers — each worker process gets its own `_detector` singleton (safe), but if someone switches to `gevent` or `eventlet` pools, multiple green-threads within the same process will share the singleton and corrupt inference state.

**Fix:** Add a `threading.Lock` around the `detect()` call to make the singleton thread-safe regardless of the concurrency pool:

```python
import threading
_lock = threading.Lock()

class HavenDetector:
    def detect(self, image_bytes: bytes) -> list[dict]:
        with _lock:
            # ... existing inference code ...
```

---

## Info

### IN-01: `event_type` and `trigger_type` fields on server models accept arbitrary strings — no validation

**File:** `server/app/schemas/event.py:22-42`
**Issue:** `EventCreateSchema.event_type` and `TriggerSchema.trigger_type` are plain `str` fields. There is a validator for `severity` but not for `event_type` or `trigger_type`. A device can upload `event_type="DROP TABLE events"` or any other string, which gets stored verbatim. While this is not a SQL injection risk (SQLAlchemy parameterises queries), it does pollute the event log and can cause issues in UI code that maps event type strings to enum values.

**Fix:** Add a `field_validator` for `event_type` that restricts values to the known `TriggerType` names, matching the Android `TriggerType` enum.

---

### IN-02: `DeviceResponse` always returns the full `app_key` — credential exposure in list endpoints

**File:** `server/app/schemas/device.py:24-38` and `server/app/routers/devices.py:55-69`
**Issue:** The `DeviceResponse` schema always includes the full `app_key`. The `GET /devices/` list endpoint and `GET /devices/{id}` endpoint return the full key to any authenticated user. This means an attacker who compromises a user's JWT can enumerate all their device keys. Industry convention is to show the full key only at creation time and return a masked or truncated version (e.g., `hav_...abcd`) on subsequent reads.

**Fix:** Add a separate response schema for list/get that masks the key:
```python
class DeviceListItemResponse(DeviceResponse):
    app_key: str  # override to mask: f"hav_...{raw[-4:]}"
```

---

### IN-03: `SettingsUiState` does not include cloud settings — cloud fields collected separately via individual `StateFlow`s

**File:** `app/src/main/java/org/havenapp/main/ui/settings/SettingsViewModel.kt:29-44` and lines 350-378
**Issue:** The cloud server settings (`cloudEnabled`, `cloudServerUrl`, `cloudAppKey`) and notification settings are exposed as individual `StateFlow`s outside of `SettingsUiState`. This is an inconsistency with the codebase convention (single `uiState: StateFlow<XxxUiState>` data class). The `SettingsScreen` then collects 20+ separate `StateFlow`s, each re-composing the screen on any change. Consolidating into `SettingsUiState` would both align with the project convention and reduce recomposition scope.

**Fix:** Expand `SettingsUiState` to include all notification and cloud settings, following the same `combine()` pattern already used for the primary settings.

---

### IN-04: `_COCO_LABELS` dict in `detector.py` maps only 20 of 91 COCO classes but `label` falls back to `class_N` for unmapped IDs

**File:** `server/app/ml/detector.py:50-68`
**Issue:** EfficientDet Lite 0 outputs 90 classes, but `_COCO_LABELS` maps only 20. The fallback `label = _COCO_LABELS.get(class_id, f"class_{class_id}")` means events with labels like `class_42` will be stored and displayed to users. The label is also stored in the `AnalysisResult.labels` JSON column and surfaced in notifications, where `"class_42"` is meaningless.

**Fix:** Expand `_COCO_LABELS` to cover the full COCO-80 label set, or filter results to only return detections whose `class_id` is in the known security-relevant subset (persons, vehicles, animals).

---

### IN-05: `test_notifications.py` fixture creates multiple SQLite databases (`test.db`, `test_notifications.db`) that are never cleaned up on test failure

**File:** `server/tests/test_notifications.py:29` and `server/tests/test_events.py:36`
**Issue:** Both test modules reference fixed file paths (`./test.db`, `./test_notifications.db`). The `db_session` fixture in `test_notifications.py` drops all tables on teardown, but `test_events.py`'s `_set_user_quota` helper creates a new engine against `./test.db` each time without cleanup. If a test run is interrupted, stale `.db` files are left on disk and can cause state leak between test runs.

**Fix:** Use `tmp_path` (pytest's built-in tmpdir fixture) or `":memory:"` SQLite URLs in test fixtures to ensure isolation and automatic cleanup. The `conftest.py` shared fixture should own the database URL.

---

_Reviewed: 2026-04-07_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
