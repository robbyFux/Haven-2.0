---
phase: 08-android-webui-ux-polish
reviewed: 2026-05-27T00:00:00Z
depth: standard
files_reviewed: 26
files_reviewed_list:
  - app/src/main/java/org/havenapp/main/detection/HavenObjectDetector.kt
  - app/src/main/java/org/havenapp/main/detection/SensorFusionEngine.kt
  - app/src/main/java/org/havenapp/main/media/CameraAnalyzer.kt
  - app/src/main/java/org/havenapp/main/sensor/FusedMotionMonitor.kt
  - app/src/main/java/org/havenapp/main/MonitorService.kt
  - app/src/main/java/org/havenapp/main/ui/settings/ZoneEditorScreen.kt
  - app/src/main/java/org/havenapp/main/ui/settings/SettingsScreen.kt
  - server/alembic/versions/6fb71bb9b5b6_add_is_archived_to_events.py
  - server/app/models/event.py
  - server/webui/events/models.py
  - server/webui/events/views.py
  - server/webui/events/urls.py
  - server/webui/events/templates/events/list.html
  - server/webui/events/templates/events/partials/event_table.html
  - server/webui/events/templates/events/partials/event_row.html
  - server/webui/admin_panel/migrations/0002_smtpsettings.py
  - server/webui/admin_panel/templates/admin_panel/smtp_settings.html
  - server/webui/admin_panel/templates/admin_panel/partials/smtp_settings_form.html
  - server/webui/admin_panel/models.py
  - server/webui/admin_panel/forms.py
  - server/webui/admin_panel/views.py
  - server/webui/admin_panel/urls.py
  - server/webui/admin_panel/templates/admin_panel/dashboard.html
  - server/webui/devices/views.py
  - server/webui/devices/urls.py
  - server/webui/devices/templates/devices/partials/device_row.html
findings:
  critical: 5
  warning: 8
  info: 4
  total: 17
status: issues_found
---

# Phase 08: Code Review Report

**Reviewed:** 2026-05-27
**Depth:** standard
**Files Reviewed:** 26
**Status:** issues_found

## Summary

This phase covers Android UX polish (Zone Editor, Settings Screen, Camera/Sensor pipeline), a new server-side `is_archived` feature for events, SMTP settings admin UI, and device management UX. The implementation is broadly solid but contains five blockers — two security issues and three logic/correctness bugs — that must be fixed before shipping.

The most severe issues are: (1) a path-traversal vulnerability in the video streaming view, (2) SMTP exception details surfaced verbatim to the browser (information disclosure), (3) a race condition between quota decrement and the bulk-delete path, (4) a throttle-bypass bug in `CameraAnalyzer` that silently drops events, and (5) the `_render_event_table` helper used by bulk actions ignores the `page` GET parameter, causing incorrect pagination rendering after bulk actions.

---

## Critical Issues

### CR-01: Path-Traversal Vulnerability in `serve_video`

**File:** `server/webui/events/views.py:176`
**Issue:** `os.path.join(settings.MEDIA_ROOT, event.media_path)` does not sanitise `event.media_path`. If an attacker can write an `event` row whose `media_path` starts with `/` or contains `../` sequences (e.g. via a compromised Android client or direct API call), `os.path.join` will silently discard `MEDIA_ROOT` and follow the attacker-supplied absolute path. The same pattern is repeated at lines 237 and 319 (`event_delete`, `bulk_delete`). Any server-readable file could be streamed to a logged-in user who owns the event row.

**Fix:**
```python
import pathlib

def _safe_media_path(media_path: str) -> pathlib.Path:
    """Resolve media_path relative to MEDIA_ROOT; raise Http404 on traversal."""
    root = pathlib.Path(settings.MEDIA_ROOT).resolve()
    full = (root / media_path).resolve()
    if not str(full).startswith(str(root) + "/") and full != root:
        raise Http404("Invalid media path.")
    return full
```
Replace all three `os.path.join(settings.MEDIA_ROOT, event.media_path)` calls with `_safe_media_path(event.media_path)`.

---

### CR-02: SMTP Exception Details Leaked to Browser

**File:** `server/webui/admin_panel/views.py:370`
**Issue:** The `smtp_test` view returns the raw Python exception object directly in an HTMX HTML response:
```python
return HttpResponse(
    f'<span class="text-red-400">Send failed: {exc}</span>',
    status=422,
)
```
`aiosmtplib` exceptions typically include SMTP server banners, relay hostnames, authentication error details, and internal IP addresses. These are surfaced verbatim to the browser. In a multi-admin deployment this leaks internal network topology; in a single-admin deployment it still leaks data in browser history and logs.

**Fix:**
```python
# Log the full exception; surface only a safe summary.
import logging
logger = logging.getLogger(__name__)
logger.exception("SMTP test send failed")
return HttpResponse(
    '<span class="text-red-400">Send failed. Check server logs for details.</span>',
    status=422,
)
```

---

### CR-03: Bulk-Delete Quota Counters Not Updated

**File:** `server/webui/events/views.py:299-327`
**Issue:** `bulk_delete` deletes event rows and their media files but never decrements `user.current_storage_bytes` or `user.current_event_count`. The single-event `event_delete` view (lines 241-248) does update these counters. The omission means bulk-deleting N events leaves the user's quota counters permanently inflated. Users will hit their quota ceiling earlier than they should and admins will see inflated storage figures in the dashboard.

**Fix:** Add the same quota decrement logic before `events_qs.delete()`:
```python
if event_ids:
    events_qs = Event.objects.filter(user_id=request.user.id, id__in=event_ids)

    total_bytes = 0
    total_count = 0
    for event in events_qs:
        if event.media_path:
            full_path = _safe_media_path(event.media_path)
            if full_path.exists():
                full_path.unlink()
        total_bytes += event.media_size_bytes or 0
        total_count += 1

    if total_bytes > 0 or total_count > 0:
        user = request.user
        user.current_storage_bytes = max(0, user.current_storage_bytes - total_bytes)
        user.current_event_count = max(0, user.current_event_count - total_count)
        user.save(update_fields=["current_storage_bytes", "current_event_count"])

    events_qs.delete()
```

---

### CR-04: `CameraAnalyzer` Throttle Returns Without Emitting Fallback Event

**File:** `app/src/main/java/org/havenapp/main/media/CameraAnalyzer.kt:155`
**Issue:** When Stage 1+2 confirm motion but the TFLite throttle has not expired, the code does:
```kotlin
if (now - lastTfliteMs < TFLITE_MIN_INTERVAL_MS) return  // throttle: not enough time elapsed
```
This `return` exits `analyze()` entirely — it does not emit the fallback `TriggerType.CAMERA` event that would be emitted on line 175 when ML is unavailable. The net result is that any motion event that occurs within 1.5 s of a previous TFLite call is silently swallowed, even though Stage 1+2 confirmed real motion. This is an incorrect silencing of confirmed triggers and can cause alert gaps up to the throttle interval.

The throttle should only gate whether TFLite is *called*, not whether an event is *emitted*. The fallback generic `CAMERA` event should always be emitted when motion is confirmed.

**Fix:**
```kotlin
if (detectionMode.requiresML && objectDetector?.isAvailable == true) {
    val now = System.currentTimeMillis()
    if (now - lastTfliteMs >= TFLITE_MIN_INTERVAL_MS) {
        lastTfliteMs = now
        val bitmap = buildBitmap(image, luma, width, height)
        if (bitmap != null) {
            val detected = objectDetector.detect(bitmap, detectionMode)
            if (detected.isNotEmpty()) {
                val severity = deriveSeverity(lumaDiff)
                detected.forEach { type ->
                    _events.trySend(TriggerEvent(type = type, sensorValue = lumaDiff, severity = severity))
                }
                return
            }
            // ML ran but no relevant objects — fall through to emit generic CAMERA event
        }
    }
    // Throttle active or bitmap build failed — fall through to emit generic CAMERA event
}

// Fallback: generic camera motion event
_events.trySend(
    TriggerEvent(type = TriggerType.CAMERA, sensorValue = lumaDiff, severity = deriveSeverity(lumaDiff))
)
```

---

### CR-05: `_render_event_table` Loses Page Number After Bulk Actions

**File:** `server/webui/events/views.py:36-74`
**Issue:** `_render_event_table` reads `page` from `request.GET.get("page", 1)` at line 63. When called from a POST handler (`bulk_archive`, `bulk_unarchive`, `bulk_delete`), `request.GET` is empty — there are no query parameters on a POST request. So after any bulk action the partial always re-renders page 1, regardless of which page the user was on. This is a data integrity issue for users managing large event lists: they lose their scroll position and might re-select the wrong page of events.

**Fix:** Pass the page number as an explicit parameter to `_render_event_table`:
```python
def _render_event_table(request, status=None, page_number=1):
    ...
    page = paginator.get_page(page_number)
    ...
```
And in the POST views, read the page from the POST body:
```python
page_number = request.POST.get("page", 1)
return _render_event_table(request, status=status, page_number=page_number)
```
The hidden forms in `list.html` should include `<input type="hidden" name="page" :value="...">`.

---

## Warnings

### WR-01: `HavenObjectDetector.initialize()` Is Not Thread-Safe

**File:** `app/src/main/java/org/havenapp/main/detection/HavenObjectDetector.kt:80-119`
**Issue:** `initialize()` is a `@Singleton` called from `CameraAnalyzer`'s `init{}` block (which runs on the camera executor thread), but `initAttempted` and `detector` are plain `var` fields with no synchronization. If `initialize()` were ever called concurrently from two threads the check-then-act pattern at line 81 (`if (initAttempted) return detector != null`) contains a read-check-write race. While the current call site serializes on the camera executor, the lack of `@GuardedBy` annotations or `@Volatile` makes this fragile and undocumented.

**Fix:** Mark `initAttempted` as `@Volatile`, or synchronize the guard block:
```kotlin
@Volatile private var initAttempted = false
```
Or document that `initialize()` must only be called from a single thread and add an assertion.

---

### WR-02: `SensorFusionEngine.latestGyroMagnitude` Is Not Thread-Safe

**File:** `app/src/main/java/org/havenapp/main/detection/SensorFusionEngine.kt:23`
**Issue:** `latestGyroMagnitude` is a plain `var Float`. In `FusedMotionMonitor`, the gyro listener writes it from the sensor callback thread while the accel listener reads it via `fuse()` from the same callback thread — both are registered on the default sensor handler. However, `SensorManager` does not guarantee that both listeners share the same `HandlerThread`; on some devices sensor callbacks may arrive on different threads. The field is `private set` but not `@Volatile`, creating a potential stale-read.

**Fix:**
```kotlin
@Volatile var latestGyroMagnitude = 0f
    private set
```

---

### WR-03: `serve_video` File Handle Leak on Full Response

**File:** `server/webui/events/views.py:212`
**Issue:** The non-range code path opens a file with `open(full_path, "rb")` and passes the file object directly to `StreamingHttpResponse`. Django's `StreamingHttpResponse` does not guarantee it closes the file iterator when streaming is complete. If the response is cancelled mid-stream (client disconnect) the file handle will leak until GC.

**Fix:**
```python
def _full_file_iter(path, chunk=64 * 1024):
    with open(path, "rb") as f:
        while True:
            data = f.read(chunk)
            if not data:
                break
            yield data

response = StreamingHttpResponse(_full_file_iter(full_path), content_type="video/mp4")
```

---

### WR-04: `event_delete` Does Not Guard Against Non-POST Requests Properly

**File:** `server/webui/events/views.py:224`
**Issue:** `event_delete` checks `request.method != "POST"` and raises `Http404("Method not allowed.")`. Returning 404 for a method error is semantically wrong and will confuse monitoring tools. The URL for event_delete is not decorated with `@require_POST`, so GET requests currently hit the database `get_object_or_404` before the method check — doing unnecessary work and leaking object existence via timing. More importantly, Http404 (status 404) is the wrong code; it should be 405.

**Fix:** Decorate with `@require_POST` as done for all other mutating views in this file:
```python
@login_required
@require_POST
def event_delete(request, event_id):
    ...  # remove the manual method check
```

---

### WR-05: `bulk_delete` Iterates QuerySet Twice

**File:** `server/webui/events/views.py:314-323`
**Issue:** `events_qs` is a lazy QuerySet. Iterating over it in the `for event in events_qs:` loop (line 317) executes one SQL query. Then `events_qs.delete()` (line 323) executes a second query on the same object. Between these two calls another request could insert or modify rows matching the filter, leading to a TOCTOU inconsistency where files are deleted for a different set of rows than are deleted from the DB. Additionally, if `events_qs.delete()` raises an exception after some files are deleted, media is orphaned.

**Fix:** Materialize and lock the queryset in a transaction:
```python
from django.db import transaction

with transaction.atomic():
    events_qs = Event.objects.select_for_update().filter(
        user_id=request.user.id, id__in=event_ids
    )
    media_to_delete = [
        (os.path.join(settings.MEDIA_ROOT, e.media_path), e.media_size_bytes)
        for e in events_qs
        if e.media_path
    ]
    events_qs.delete()

# Delete files outside the transaction to avoid holding DB lock
for path, _ in media_to_delete:
    if os.path.exists(path):
        os.remove(path)
```

---

### WR-06: `FusedMotionMonitor` — Warmup Always Collects Gyro-Biased Samples

**File:** `app/src/main/java/org/havenapp/main/sensor/FusedMotionMonitor.kt:87-106`
**Issue:** During warmup, `warmupSamples.add(fused)` stores the *fused* score (line 87), which includes the 70 % gyro term (`alpha * latestGyroMagnitude`). However, at sensor registration time `latestGyroMagnitude` is 0 (default). The first gyro callback sets it to a real value, but there is a brief window where accel callbacks arrive before any gyro callback has fired — those early fused samples will read `0.7 * 0 + 0.3 * accelDelta` instead of the true fused value. This deflates the 90th-percentile noise floor estimate, potentially making it too low and causing false positives immediately after calibration.

**Fix:** Either skip fused samples until `latestGyroMagnitude > 0` (indicating at least one gyro reading has arrived), or record only the raw `accelDelta` during warmup and only use gyro weighting during active monitoring.

---

### WR-07: `AISettings` OpenRouter API Key Stored Plaintext in Database

**File:** `server/webui/admin_panel/models.py:49`
**Issue:** `openrouter_api_key = models.CharField(max_length=255, blank=True, default="")` stores the API key in plaintext in the database. The SMTP password received the Fernet-encryption treatment in this same phase (see `SMTPSettings`), but the OpenRouter API key was left unprotected. Anyone with read access to the database (backup, replica, leaked dump) can extract the API key.

**Fix:** Apply the same `_get_fernet().encrypt(...)` pattern used for the SMTP password to the OpenRouter API key. Store it as `openrouter_api_key_encrypted` (TextField), and decrypt on read in the Celery worker helper.

---

### WR-08: `MonitorService.startMonitoring()` — `clipRecorderDeferred` Reset Too Late

**File:** `app/src/main/java/org/havenapp/main/MonitorService.kt:241`
**Issue:** `clipRecorderDeferred = CompletableDeferred()` is reset at line 241, inside the `monitoringJob` coroutine, *after* the countdown and calibration phases complete. The comment says "Reset deferred so a fresh await is available for this session." But `startCamera()` is called at line 244, which may complete (and call `clipRecorderDeferred.complete(...)`) before the reset at line 241 runs — there is no ordering guarantee between the camera provider callback (which fires on `mainExecutor`) and the continuation of the monitoring coroutine. If the camera future resolves before line 241, `clipRecorderDeferred.complete()` would complete the *old* deferred from a previous session (which was cancelled at line 429 in `stopMonitoring()`), and the new deferred created at line 241 would never be completed, causing `sensorFlow.collect` to hang for 3 s on every trigger.

**Fix:** Move the deferred reset to *before* `startCamera()` is invoked, and ensure it happens before `startCamera()` is called:
```kotlin
clipRecorderDeferred = CompletableDeferred()  // reset BEFORE startCamera
val analyzer: CameraAnalyzer? = if (cameraEnabled) {
    CameraAnalyzer(sensitivity, expert, detectionMode, objectDetector, detectionZone)
        .also { cameraAnalyzer = it; startCamera(it, cameraPosition) }
} else { null }
```
(The current code already does this — it is on line 241 which is before line 244 — but the comment "Reset deferred so a fresh await is available" is misleading. The actual risk is that the previous session's cancelled deferred could still have a listener attached. Verify `cancel()` in `stopMonitoring` prevents completion of the cancelled deferred before the new one is constructed.)

Note: On closer reading, `CompletableDeferred` docs state that a cancelled deferred cannot be completed. So the reset at line 241 *before* `startCamera()` on line 244 is correct. The issue is moot if line 241 always precedes the camera provider callback — but this ordering is NOT guaranteed because the camera provider callback fires on `mainExecutor` and the monitoring coroutine may yield between line 241 and line 244. The window is tiny but non-zero. This is a WARNING-level race rather than a guaranteed blocker, but it should be documented or guarded.

---

## Info

### IN-01: Magic Number `0.05f` in `buildZone` Is Not Named

**File:** `app/src/main/java/org/havenapp/main/ui/settings/ZoneEditorScreen.kt:201`
**Issue:** `if (abs(maxX - minX) / canvasW <= 0.05f || abs(maxY - minY) / canvasH <= 0.05f) return null` uses an unnamed constant. 5 % minimum zone size is not obvious from reading.

**Fix:** Extract to a named constant:
```kotlin
private const val MIN_ZONE_FRACTION = 0.05f
```

---

### IN-02: Duplicate Heartbeat Coroutine Block Copy-Pasted Four Times

**File:** `app/src/main/java/org/havenapp/main/MonitorService.kt:276-325`
**Issue:** The Signal, Mattermost, Pushover, and Cloud heartbeat coroutines are structurally identical. The 50-line block is repeated four times with minor variable substitutions. Any future change (e.g., heartbeat message format, error handling) must be made in four places.

**Fix:** Extract a shared helper:
```kotlin
private fun CoroutineScope.launchHeartbeat(
    intervalMin: Int,
    channelName: String,
    sendFn: suspend (String) -> Unit,
) {
    if (intervalMin <= 0) return
    launch {
        while (_state.value != MonitorState.ACTIVE) delay(500)
        while (true) {
            delay(intervalMin * 60_000L)
            val msg = "Haven alive - v${BuildConfig.VERSION_NAME} - ${_state.value} - ..."
            runCatching { sendFn(msg) }
                .onFailure { appLogger.e(TAG, "$channelName heartbeat failed: ${it.message}") }
        }
    }
}
```

---

### IN-03: `SettingsScreen` Uses Hardcoded Hex Color `Color(0xFFFFB300)`

**File:** `app/src/main/java/org/havenapp/main/ui/settings/SettingsScreen.kt:168` and `374`
**Issue:** The amber warning color `Color(0xFFFFB300)` is hardcoded twice. If the design token changes, it must be updated in both locations.

**Fix:** Extract to a theme token or a `val WarnAmber = Color(0xFFFFB300)` constant in a shared theme file.

---

### IN-04: `_render_event_table` Duplicates `event_list` View Logic

**File:** `server/webui/events/views.py:36-74` vs `78-117`
**Issue:** `_render_event_table` and `event_list` contain nearly identical queryset construction, filtering, pagination, and context-building code (lines 44-74 and 87-117). This is roughly 30 lines of duplication. A bug fix in one location is unlikely to be mirrored in the other.

**Fix:** Have `event_list` call `_render_event_table` for the partial case and share the common queryset logic, or extract the shared logic into a private helper that both call.

---

_Reviewed: 2026-05-27_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
