# Phase 8: Android & WebUI UX Polish - Pattern Map

**Mapped:** 2026-05-27
**Files analyzed:** 20 new/modified files
**Analogs found:** 20 / 20

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `app/src/main/java/org/havenapp/main/ui/settings/SettingsScreen.kt` | component | request-response | self (edit) | exact — HorizontalDivider insertion |
| `app/src/main/java/org/havenapp/main/detection/SensorFusionEngine.kt` | utility | transform | `sensor/LightMonitor.kt` | role-match — already has KDoc; needs WHY expansion |
| `app/src/main/java/org/havenapp/main/sensor/LightMonitor.kt` | utility | event-driven | `sensor/FusedMotionMonitor.kt` | exact — callbackFlow + KDoc pattern |
| `app/src/main/java/org/havenapp/main/sensor/FusedMotionMonitor.kt` | utility | event-driven | `sensor/LightMonitor.kt` | exact — callbackFlow + KDoc pattern; has German comment |
| `app/src/main/java/org/havenapp/main/media/CameraAnalyzer.kt` | utility | event-driven | `detection/HavenObjectDetector.kt` | role-match — has German class-level KDoc and inline German comments |
| `app/src/main/java/org/havenapp/main/detection/PerceptualHashDetector.kt` | utility | transform | `detection/SensorFusionEngine.kt` | exact — pure algorithm, already has KDoc; needs WHY comments |
| `app/src/main/java/org/havenapp/main/detection/HavenObjectDetector.kt` | utility | event-driven | `sensor/LightMonitor.kt` | role-match — German class-level KDoc + German inline comments |
| `app/src/main/java/org/havenapp/main/MonitorService.kt` | service | event-driven | self (edit) | exact — 3 German inline comments found at lines 217, 263, 328 |
| `app/src/main/java/org/havenapp/main/ui/settings/ZoneEditorScreen.kt` | component | request-response | self (edit) | exact — 2 German inline comments at lines 121, 157 |
| `server/alembic/versions/XXXX_add_is_archived_to_events.py` | migration | CRUD | `alembic/versions/a3f2e1d4c5b6_add_pushover_app_token.py` | exact |
| `server/app/models/event.py` | model | CRUD | self (edit) | exact — add `is_archived` column mirroring `is_encrypted` |
| `server/webui/events/models.py` | model | CRUD | self (edit) | exact — unmanaged model, add `is_archived = models.BooleanField(default=False)` |
| `server/webui/events/filters.py` | utility | request-response | self (edit) | exact — extend EventFilter with status ChoiceFilter |
| `server/webui/events/views.py` | controller | CRUD | `devices/views.py` | exact — login_required + require_POST + HTMX partial pattern |
| `server/webui/events/urls.py` | config | request-response | `devices/urls.py` | exact — path() urlpatterns pattern |
| `server/webui/events/templates/events/list.html` | component | request-response | self (edit) — `devices/templates/devices/list.html` | exact — filter form with hx-get + hx-target |
| `server/webui/events/templates/events/partials/event_row.html` | component | request-response | `devices/partials/device_row.html` | exact — tr with checkbox + conditional rendering |
| `server/webui/events/templates/events/partials/event_table.html` | component | request-response | self (edit) | exact — table thead with checkbox header column |
| `server/webui/devices/views.py` | controller | CRUD | self (edit) — `device_revoke()` | exact — @login_required + @require_POST + HTMX outerHTML swap |
| `server/webui/devices/urls.py` | config | request-response | self (edit) | exact — add delete route matching revoke route pattern |
| `server/webui/devices/templates/devices/partials/device_row.html` | component | request-response | self (edit) — existing revoke button | exact — hx-confirm + hx-post + hx-target + hx-swap="outerHTML" |
| `server/webui/admin_panel/models.py` | model | CRUD | self (edit) — `AISettings` class | exact — singleton get_or_create(pk=1) pattern |
| `server/webui/admin_panel/forms.py` | utility | request-response | self (edit) — `AISettingsForm` | exact — Django Form with Tailwind-styled widget attrs |
| `server/webui/admin_panel/views.py` | controller | CRUD | self (edit) — `ai_settings()` view | exact — GET/POST singleton + HTMX partial swap |
| `server/webui/admin_panel/urls.py` | config | request-response | self (edit) | exact — add settings/ and settings/test/ routes |
| `server/webui/admin_panel/migrations/0002_smtpsettings.py` | migration | CRUD | `0001_initial.py` (in same dir) | exact — Django managed model migration |
| `server/webui/admin_panel/templates/admin_panel/smtp_settings.html` | component | request-response | `admin_panel/ai_settings.html` | exact — extends base.html, form card with HTMX partial |
| `server/webui/admin_panel/templates/admin_panel/dashboard.html` | component | request-response | self (edit) | exact — add SMTP Settings link matching AI Settings link |
| `server/webui/admin_panel/templates/admin_panel/partials/smtp_settings_form.html` | component | request-response | `admin_panel/partials/ai_settings_form.html` | exact |

---

## Pattern Assignments

### `SettingsScreen.kt` — HorizontalDivider insertion (component, request-response)

**Analog:** `SettingsScreen.kt` itself (lines 121–250 for Card 1, lines 696–718 for CategoryCard definition, lines 1035–1044 for SettingsSection definition)

**Current CategoryCard + SettingsSection structure** (lines 696–718, 1035–1044):
```kotlin
// CategoryCard wraps content in a Column with 16.dp padding (line 708)
private fun CategoryCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), ...) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, ...)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// SettingsSection renders a title + content lambda — no Column wrapper of its own
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(text = title, style = MaterialTheme.typography.titleSmall, ...)
    content()
}
```

**HorizontalDivider insertion pattern** (insert between sequential SettingsSections, not after the last one):
```kotlin
// Import to add at top of file (line 1, after existing material3 imports):
import androidx.compose.material3.HorizontalDivider

// Placement — Card 1 Detection (lines 121–250), between each adjacent pair:
CategoryCard(title = stringResource(R.string.settings_cat_detection)) {
    SettingsSection(title = stringResource(R.string.settings_sensitivity_title)) { ... }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))  // ADD
    SettingsSection(title = stringResource(R.string.settings_camera_title)) { ... }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))  // ADD
    SettingsSection(title = stringResource(R.string.settings_detection_title)) { ... }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))  // ADD
    SettingsSection(title = stringResource(R.string.settings_zone_title)) { ... }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))  // ADD
    SettingsSection(title = stringResource(R.string.expert_entry_label)) { ... }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))  // ADD
    SettingsSection(title = stringResource(R.string.settings_sensors_title)) { ... }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))  // ADD
    SettingsSection(title = stringResource(R.string.settings_light_suppress_title)) { ... }
    // NO divider after last SettingsSection (D-07)
}
// Apply same pattern to Cards 2, 3, 4, 5 — divider between each adjacent pair, none after last
```

**Key constraints:** `HorizontalDivider` is NOT currently imported (confirmed: not in lines 1–58 of SettingsScreen.kt). Import must be added explicitly.

---

### Android Algorithmic Files — Comment + KDoc pattern (utility, transform/event-driven)

**Primary analog:** `SensorFusionEngine.kt` (lines 1–53) — already has English comments + KDoc; use as the gold standard for what the updated files should look like.

**Secondary analog:** `LightMonitor.kt` (lines 1–141) — already has English WHY comments + KDoc `@param`; most complete example.

**KDoc pattern from `LightMonitor.kt`** (lines 47–58):
```kotlin
/**
 * Observes the light sensor and emits [TriggerEvent]s when deviation exceeds threshold.
 *
 * @param suppressionWindowMs Duration in ms during which a recent ACCELEROMETER or CAMERA
 *   trigger suppresses light triggers. 0 = suppression disabled. Default: 10000 (10s).
 */
fun observe(
    sensitivity: Sensitivity,
    warmupMs: Long,
    expert: ExpertThresholds = ExpertThresholds.DEFAULT,
    suppressionWindowMs: Long = 10_000L,
): Flow<TriggerEvent> {
```

**KDoc rule:** Add `@param`/`@return` only where parameter name is non-obvious or return value is non-trivial. `SensorFusionEngine.fuse()` (line 46–47) is a good example:
```kotlin
/** Fused score: alpha × gyroMag + (1-alpha) × accelDelta. */
fun fuse(accelDelta: Float): Float =
    alpha * latestGyroMagnitude + (1f - alpha) * accelDelta
```

**WHY comment style** (from `SensorFusionEngine.kt` class KDoc, lines 6–14):
```kotlin
/**
 * Complementary filter combining accelerometer delta and gyroscope magnitude.
 *
 * alpha=0.7 → 70% gyroscope contribution, 30% accelerometer contribution.
 *
 * Key insight: table vibrations produce a high accel delta but very low gyro magnitude
 * (the table doesn't rotate). The fused score is therefore pulled down by the gyro term,
 * reducing false positives. Intentional movement produces both a large accel delta AND
 * a large gyro magnitude, so the fused score remains high and triggers correctly.
 */
```

**WHY comment style for inline** (from `LightMonitor.kt` lines 90–95):
```kotlin
// Measure deviation from emaFast BEFORE updating (spike must not be absorbed)
val deviation = abs(lux - emaFast)

// Update both EMAs
emaFast = emaFastAlpha * lux + (1f - emaFastAlpha) * emaFast
```

---

### `SensorFusionEngine.kt` — Comments + KDoc (already English, needs WHY expansion)

**Analog:** self — already well-commented (lines 1–53). This file only needs KDoc `@param`/`@return` tags on `processAccelerometer` and `processGyroscope` where currently absent. The class-level KDoc is already complete.

**Functions needing KDoc** (lines 27–47):
```kotlin
// processAccelerometer (line 27) — needs @param (x/y/z axis values) + @return (delta magnitude)
// processGyroscope (line 41) — needs @param (x/y/z angular velocity)
// fuse (line 46) — already has sufficient inline doc
```

---

### `HavenObjectDetector.kt` — German to English translation (utility, event-driven)

**Analog:** `LightMonitor.kt` (fully English, KDoc @param, WHY comments) — use as translation target style.

**German comments to translate** (from lines 19–35, 56–57, 60, 63, 66–67, 84, 108–110, 161):
```kotlin
// BEFORE (line 19-35 — class-level KDoc, all German):
/**
 * Wrapper um die MediaPipe Tasks Vision ObjectDetector API.
 * Modell: EfficientDet Lite 0 (COCO, 80 Klassen, ~4 MB).
 * ...
 */

// AFTER (translate + add WHY):
/**
 * Wrapper around the MediaPipe Tasks Vision ObjectDetector API.
 *
 * Model: EfficientDet Lite 0 (COCO, 80 classes, ~4 MB).
 * The model is loaded lazily on the first [initialize] call.
 * If the file is absent, the detector degrades gracefully: [detect] returns an empty
 * list and [initError] holds a human-readable cause — callers must not crash on absence.
 * ...
 */
```

**German inline comments** (lines 56–57, 60, 63, 66–67, 84, 108, 161):
```kotlin
// line 56: "/** StateFlow, der sich ändert wenn das Modell geladen/entladen wird. */"
//  → "/** StateFlow that changes when the model is loaded or unloaded. */"

// line 67: "/** Lädt das MediaPipe ObjectDetector-Modell. Gibt true zurück wenn erfolgreich. */"
//  → "/** Loads the MediaPipe ObjectDetector model. Returns true on success. */"

// line 71: "// Schritt 1: Datei in assets prüfen"
//  → "// Step 1: verify the model file exists in assets"

// line 84: "// Schritt 2: MediaPipe ObjectDetector laden (RunningMode.IMAGE für synchrone Inferenz)"
//  → "// Step 2: create the ObjectDetector (RunningMode.IMAGE for synchronous per-frame inference)"

// line 108: "// @return Liste der erkannten [TriggerType]s passend zum [DetectionMode]."
//  → translate inline KDoc on detect() to English
```

---

### `CameraAnalyzer.kt` — German to English + WHY comments (utility, event-driven)

**Analog:** `SensorFusionEngine.kt` (algorithmic WHY comments) + `LightMonitor.kt` (inline comment style).

**German class-level KDoc** (lines 26–39 — translate + expand WHY):
```kotlin
// BEFORE (German, lines 26-39):
/**
 * Drei-Stufen Kamera-Bewegungserkennung:
 *   Stufe 1 – Luminanz-Diff (schnell): ...
 *   Stufe 2 – Perceptual Hash (strukturell): ...
 *   Stufe 3 – TFLite ObjectDetection (nur wenn Stufe 1+2 triggern und ML aktiviert): ...
 */

// AFTER (English + WHY):
/**
 * Three-stage camera motion detection pipeline.
 *
 * Stage 1 — Luminance diff (fast gate): fraction of changed pixels. Cheap to compute;
 *   catches obvious motion without allocating a bitmap.
 * Stage 2 — Perceptual hash (structural): Hamming distance of 8×8 average hash.
 *   Filters uniform brightness changes (light flicker, clouds) that fool the pixel diff.
 *   A uniform brightness shift leaves the spatial structure unchanged → near-zero hash distance.
 * Stage 3 — TFLite object detection (semantic, only on confirmed motion + ML mode):
 *   EfficientDet Lite 0 classifies person / pet / vehicle. Runs at most every 1.5 s
 *   to avoid OOM on continuous inference.
 *
 * Running TFLite only after Stage 1+2 confirm motion saves ~80% of inference calls.
 */
```

**German inline comments to translate** (lines 57, 85–86, 103, 111, 144, 147, 162, 165, 184–186, 203–205):
```kotlin
// line 57: "/** TFLite-Drosselung: max 1 Inferenz pro [TFLITE_MIN_INTERVAL_MS] ms, um OOM zu verhindern. */"
//  → "/** TFLite throttle: at most one inference per [TFLITE_MIN_INTERVAL_MS] ms to prevent OOM. */"

// line 85-86: "// Luma-Ebene einmalig extrahieren"
//  → "// Extract the luma (Y) plane once; both Stage 1 and 2 consume the same array"

// line 103: "// Zone-Ausschnitt: Luma einmalig zuschneiden..."
//  → "// Crop luma to the detection zone once; both detectors then work on the region only"

// line 111: "// Stufe 1 + 2: Bewegungsbestätigung"
//  → "// Stage 1 + 2: motion confirmation gate"

// line 144: "// Stufe 3: TFLite..."
//  → "// Stage 3: TFLite object detection (runs only if mode requires ML and throttle allows)"

// line 147: "// Drosselung: still warten"
//  → "// Throttle guard: not enough time has passed since last inference — skip"

// line 162: "// ML verfügbar aber nichts Relevantes erkannt → kein Event"
//  → "// ML available but no relevant object detected — suppress the generic camera event"

// line 165: "// Fallback: generisches Kamera-Bewegungsevent"
//  → "// Fallback: generic camera motion event when ML is off or unavailable"

// line 184-186 (cropLuma KDoc): translate
// line 203-205 (buildBitmap KDoc): translate
```

---

### `FusedMotionMonitor.kt` — German comment (line 41) + KDoc (utility, event-driven)

**Analog:** `LightMonitor.kt` (same pattern file — callbackFlow + KDoc @param).

**German comment** (line 41):
```kotlin
// BEFORE:
/** Wird auf den berechneten Noise-Floor gesetzt, sobald der Warmup abgeschlossen ist. */
private val _noiseFloor = MutableStateFlow<Float?>(null)

// AFTER:
/** Set to the computed noise floor once warmup is complete; null during warmup. */
private val _noiseFloor = MutableStateFlow<Float?>(null)
```

**KDoc for `observe()`** — follows the same `@param` pattern as `LightMonitor.observe()` (lines 47–58):
```kotlin
/**
 * Fuses accelerometer and gyroscope data and emits [TriggerEvent]s on motion above threshold.
 *
 * @param sensitivity Sensitivity level controlling the threshold multiplier.
 * @param warmupMs Warmup duration in ms; motion events are suppressed until calibration completes.
 * @param expert Optional expert thresholds overriding the default sensitivity multipliers.
 */
override fun observe(sensitivity: Sensitivity, warmupMs: Long, expert: ExpertThresholds): Flow<TriggerEvent>
```

---

### `MonitorService.kt` — German inline comment translation only (service, event-driven)

**3 German comments** (confirmed at lines 217, 263, 328):
```kotlin
// line 217: "// Countdown während Kalibrierung"
//  → "// Countdown while calibrating"

// line 263: "// Nach Kalibrierungszeit DB-Event öffnen und Zustand wechseln"
//  → "// After calibration period: open DB event and transition state to ACTIVE"

// line 328: "// Sensor-Flow läuft ab Kalibrierungsstart; currentEventId ist null"
//  → "// Sensor flow runs from calibration start; currentEventId is null until state transitions to ACTIVE"
```

No KDoc changes needed for MonitorService per CONTEXT.md scope (translate-only file).

---

### `ZoneEditorScreen.kt` — German inline comment translation only (component, request-response)

**2 German comments** (confirmed at lines 121, 157):
```kotlin
// line 121: "// Querformat: Kamera links, Steuerung rechts"
//  → "// Landscape: camera on the left, controls on the right"

// line 157: "// Hochformat: Kamera oben, Steuerung unten"
//  → "// Portrait: camera on top, controls below"
```

No KDoc changes needed per CONTEXT.md scope.

---

### `server/alembic/versions/XXXX_add_is_archived_to_events.py` (migration, CRUD)

**Analog:** `server/alembic/versions/a3f2e1d4c5b6_add_pushover_app_token.py` (lines 1–29)

**Full analog** (copy this structure exactly):
```python
"""add is_archived to events

Revision ID: <generate with alembic revision>
Revises: a3f2e1d4c5b6
Create Date: 2026-05-27
"""
from typing import Sequence, Union
import sqlalchemy as sa
from alembic import op

revision: str = "<generate>"
down_revision: Union[str, None] = "a3f2e1d4c5b6"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

def upgrade() -> None:
    op.add_column(
        "events",
        sa.Column("is_archived", sa.Boolean(), nullable=False, server_default="false"),
    )

def downgrade() -> None:
    op.drop_column("events", "is_archived")
```

**Key:** `server_default="false"` (not Python `False`) ensures existing rows get the default without a table rewrite.

---

### `server/app/models/event.py` — Add `is_archived` column (model, CRUD)

**Analog:** Existing `is_encrypted` field (line 52) — same Boolean pattern:
```python
# BEFORE (line 52):
is_encrypted: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)

# ADD after is_encrypted (line 53):
is_archived: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
```

---

### `server/webui/events/models.py` — Add `is_archived` field (model, CRUD)

**Note:** `managed = False` (line 50 of events/models.py) — Django does NOT generate a migration for this field. Alembic creates the column; Django just reads it.

**Analog:** Existing `is_encrypted` field (line 43 of events/models.py):
```python
# BEFORE (line 43):
is_encrypted = models.BooleanField(default=False)

# ADD after is_encrypted:
is_archived = models.BooleanField(default=False)
```

---

### `server/webui/events/filters.py` — Add status filter (utility, request-response)

**Analog:** Existing `severity` ChoiceFilter in `filters.py` (lines 30–38):
```python
# Existing pattern (lines 30-38):
severity = django_filters.ChoiceFilter(
    choices=[("LOW", "Low"), ("MEDIUM", "Medium"), ("HIGH", "High"), ("CRITICAL", "Critical")],
    label="Severity",
)
```

**Status filter approach** — handle `status` in the view (not as a FilterSet field) because it maps three UI options to a Boolean column, which does not fit cleanly into FilterSet's field-per-column model. The EventFilter gets no new field; the view reads `request.GET.get("status", "active")` and applies the queryset filter before passing to EventFilter.

If a FilterSet-based approach is preferred, use a MethodFilter:
```python
status = django_filters.ChoiceFilter(
    choices=[("active", "Active"), ("archived", "Archived"), ("all", "All")],
    label="Status",
    method="filter_status",
    empty_label=None,
)

def filter_status(self, queryset, name, value):
    if value == "active":
        return queryset.filter(is_archived=False)
    if value == "archived":
        return queryset.filter(is_archived=True)
    return queryset  # "all" — no filter
```

---

### `server/webui/events/views.py` — Add bulk_archive, bulk_delete, bulk_unarchive, status filter (controller, CRUD)

**Analog:** `devices/views.py` `device_revoke()` (lines 65–83) — `@login_required` + `@require_POST` + HTMX partial swap pattern; and `event_delete()` in `events/views.py` (lines 167–198) for queryset ownership check.

**Status filter extension of `event_list`** (extend lines 40–61):
```python
@login_required
def event_list(request):
    status = request.GET.get("status", "active")
    base_qs = (
        Event.objects.filter(user_id=request.user.id)
        .select_related("device")
        .order_by("-timestamp")
    )
    if status == "active":
        base_qs = base_qs.filter(is_archived=False)
    elif status == "archived":
        base_qs = base_qs.filter(is_archived=True)
    # status == "all": no filter

    f = EventFilter(request.GET, queryset=base_qs)
    # ... rest unchanged
```

**Bulk archive view** (new function — copy security pattern from `event_delete`):
```python
@login_required
@require_POST
def bulk_archive(request):
    """Archive selected events (set is_archived=True). HTMX: returns refreshed event table."""
    event_ids = request.POST.getlist("event_ids")
    if event_ids:
        Event.objects.filter(user_id=request.user.id, id__in=event_ids).update(is_archived=True)
    # Re-render the table partial (same pattern as event_list HTMX branch)
    return _render_event_table(request)

@login_required
@require_POST
def bulk_unarchive(request):
    """Unarchive selected events (set is_archived=False). HTMX: returns refreshed event table."""
    event_ids = request.POST.getlist("event_ids")
    if event_ids:
        Event.objects.filter(user_id=request.user.id, id__in=event_ids).update(is_archived=False)
    return _render_event_table(request)

@login_required
@require_POST
def bulk_delete(request):
    """Permanently delete selected events and their media files."""
    event_ids = request.POST.getlist("event_ids")
    events = Event.objects.filter(user_id=request.user.id, id__in=event_ids)
    for event in events:
        if event.media_path:
            full_path = os.path.join(settings.MEDIA_ROOT, event.media_path)
            if os.path.exists(full_path):
                os.remove(full_path)
    events.delete()
    return _render_event_table(request)
```

---

### `server/webui/events/urls.py` — Add bulk routes (config, request-response)

**Analog:** `devices/urls.py` (lines 1–13) — `path()` with app_name:
```python
# Add to existing urlpatterns:
path("bulk-archive/", views.bulk_archive, name="bulk_archive"),
path("bulk-unarchive/", views.bulk_unarchive, name="bulk_unarchive"),
path("bulk-delete/", views.bulk_delete, name="bulk_delete"),
```

---

### `server/webui/events/templates/events/list.html` — Status dropdown + bulk action bar (component)

**Analog:** `events/list.html` itself (lines 1–91) — filter form with `hx-get` + `hx-target="#event-table"`.

**Status dropdown** (add inside filter form after existing Severity dropdown, lines 60–70):
```html
<div class="flex flex-col gap-1">
  <label class="text-xs text-gray-400">Status</label>
  <select name="status"
          class="bg-gray-700 text-gray-100 rounded px-2 py-1 text-sm border border-gray-600 focus:outline-none focus:border-teal-500">
    <option value="active" {% if request.GET.status == "active" or not request.GET.status %}selected{% endif %}>Active</option>
    <option value="archived" {% if request.GET.status == "archived" %}selected{% endif %}>Archived</option>
    <option value="all" {% if request.GET.status == "all" %}selected{% endif %}>All</option>
  </select>
</div>
```

**Alpine.js bulk action wrapper + confirmation modal** (wraps the event-table div, lines 85–88):
```html
<div x-data="{
  selected: [],
  selectAll: false,
  showConfirmModal: false,
  confirmAction: '',
  confirmMessage: '',
  toggleAll(checked) {
    this.selectAll = checked;
    this.selected = checked
      ? Array.from(document.querySelectorAll('.event-checkbox')).map(cb => cb.value)
      : [];
  }
}"
x-on:htmx:after-swap.window="selected = []; selectAll = false;">

  <!-- Bulk action bar (visible when events are selected) -->
  <div x-show="selected.length > 0"
       class="flex items-center gap-3 bg-gray-800 rounded-lg p-3 mb-3 border border-gray-700">
    <span class="text-sm text-gray-300" x-text="selected.length + ' event(s) selected'"></span>
    <!-- Archive / Unarchive / Delete buttons trigger modal -->
    <button @click="confirmAction='archive'; confirmMessage='Archive ' + selected.length + ' event(s)?'; showConfirmModal=true"
            class="text-xs px-3 py-1 rounded bg-teal-800/60 text-teal-300 hover:bg-teal-700/60 transition-colors">
      Archive
    </button>
    <button @click="confirmAction='delete'; confirmMessage='Permanently delete ' + selected.length + ' event(s)?'; showConfirmModal=true"
            class="text-xs px-3 py-1 rounded bg-red-800/60 text-red-300 hover:bg-red-700/60 transition-colors">
      Delete
    </button>
  </div>

  <!-- Confirmation modal -->
  <div x-show="showConfirmModal"
       class="fixed inset-0 bg-black/70 z-50 flex items-center justify-center"
       x-cloak>
    <div class="bg-gray-800 rounded-lg border border-gray-700 p-6 max-w-sm w-full mx-4">
      <p class="text-gray-100 mb-6" x-text="confirmMessage"></p>
      <div class="flex gap-3 justify-end">
        <button @click="showConfirmModal=false"
                class="text-sm px-4 py-2 rounded border border-gray-600 text-gray-300 hover:bg-gray-700">
          Cancel
        </button>
        <!-- Submit the actual HTMX form on confirm -->
        <button @click="showConfirmModal=false; $refs[confirmAction + 'Form'].submit()"
                class="text-sm px-4 py-2 rounded bg-red-700 text-white hover:bg-red-600">
          Confirm
        </button>
      </div>
    </div>
  </div>

  <!-- Hidden forms for bulk actions (HTMX POST) -->
  <form x-ref="archiveForm"
        hx-post="{% url 'events:bulk_archive' %}"
        hx-target="#event-table"
        hx-swap="innerHTML"
        hx-include=".event-checkbox:checked">
    {% csrf_token %}
  </form>
  <form x-ref="deleteForm"
        hx-post="{% url 'events:bulk_delete' %}"
        hx-target="#event-table"
        hx-swap="innerHTML"
        hx-include=".event-checkbox:checked">
    {% csrf_token %}
  </form>

  {# Event table #}
  <div id="event-table">
    {% include "events/partials/event_table.html" %}
  </div>
</div>
```

---

### `server/webui/events/templates/events/partials/event_table.html` — Checkbox column header (component)

**Analog:** `event_table.html` itself (lines 12–28) — thead structure.

**Add checkbox column as first `<th>`** (before "Time" column):
```html
<th class="px-4 py-3 text-left w-10">
  <input type="checkbox"
         class="rounded border-gray-600 bg-gray-700 text-teal-500 focus:ring-teal-500"
         @change="toggleAll($event.target.checked)"
         :checked="selectAll">
</th>
```

---

### `server/webui/events/templates/events/partials/event_row.html` — Checkbox column (component)

**Analog:** `device_row.html` (lines 1–34) — `<tr id="...">` with conditional rendering.

**Add checkbox `<td>` as first column of the `<tr>`**:
```html
<tr class="hover:bg-gray-750 transition-colors">
  <td class="px-4 py-3 w-10">
    <input type="checkbox"
           class="event-checkbox rounded border-gray-600 bg-gray-700 text-teal-500 focus:ring-teal-500"
           name="event_ids"
           value="{{ event.id }}"
           x-model="selected">
  </td>
  <!-- existing columns follow -->
```

---

### `server/webui/devices/views.py` — Add `device_delete()` (controller, CRUD)

**Analog:** `device_revoke()` in `devices/views.py` (lines 65–83) — exact same decorator + guard + HTMX pattern:

```python
@login_required
@require_POST
def device_delete(request, device_id):
    """
    Permanently delete a revoked device row.

    Only the owning user can delete their device. Active devices cannot be deleted.
    HTMX: returns empty 200 response — outerHTML swap removes the <tr> from the DOM.
    Non-HTMX: redirects to device_list with a success message.
    """
    device = get_object_or_404(Device, id=device_id, user_id=request.user.id)
    if device.is_active:
        return HttpResponseForbidden("Cannot delete an active device.")
    device.delete()

    if request.htmx:
        return HttpResponse("")  # empty body — HTMX outerHTML swap removes the <tr>

    messages.success(request, f"Device '{device.name}' permanently deleted.")
    return redirect("devices:device_list")
```

**Additional import needed:** `from django.http import HttpResponseForbidden` (not yet in `devices/views.py`).

---

### `server/webui/devices/urls.py` — Add device_delete route (config, request-response)

**Analog:** existing revoke route (line 12 of `devices/urls.py`):
```python
# Add after existing revoke route:
path("<int:device_id>/delete/", views.device_delete, name="device_delete"),
```

---

### `server/webui/devices/templates/devices/partials/device_row.html` — Delete button (component)

**Analog:** existing Revoke button (lines 21–29 of `device_row.html`) — `hx-confirm` + `hx-post` + `hx-target` + `hx-swap="outerHTML"`:

```html
{% else %}
  <!-- EXISTING revoked state: currently just shows "—" (line 32) -->
  <!-- REPLACE the "—" span with: -->
  <button
    hx-post="{% url 'devices:device_delete' device.id %}"
    hx-target="#device-{{ device.id }}"
    hx-swap="outerHTML"
    hx-confirm="Delete this device permanently? This cannot be undone."
    class="text-xs px-3 py-1 rounded bg-gray-700/60 text-gray-400 hover:bg-red-800/60 hover:text-red-300 transition-colors"
  >
    Delete
  </button>
{% endif %}
```

**Note:** The `<tr>` id and `hx-target="#device-{{ device.id }}"` are already in place (lines 1, 23–24). The swap mechanism is confirmed working from the revoke action.

---

### `server/webui/admin_panel/models.py` — Add `SMTPSettings` singleton (model, CRUD)

**Analog:** `AISettings` class in `admin_panel/models.py` (lines 23–62) — copy singleton pattern exactly:

```python
class SMTPSettings(models.Model):
    """
    Singleton model for SMTP email configuration.

    Only one row exists (id=1). Use SMTPSettings.get() to retrieve or
    initialise it with defaults. smtp_password_encrypted stores a Fernet-
    encrypted ciphertext — never the plaintext password.
    Writable only by admin users via the admin panel SMTP settings view.
    """
    smtp_host = models.CharField(max_length=255, blank=True, default="")
    smtp_port = models.IntegerField(default=587)
    smtp_user = models.CharField(max_length=255, blank=True, default="")
    smtp_password_encrypted = models.TextField(blank=True, default="")
    smtp_from = models.CharField(max_length=255, blank=True, default="")
    use_tls = models.BooleanField(default=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = "SMTP Settings"
        verbose_name_plural = "SMTP Settings"

    @classmethod
    def get(cls) -> "SMTPSettings":
        """Return the singleton row, creating it with defaults if absent."""
        obj, _ = cls.objects.get_or_create(pk=1)
        return obj

    def __str__(self) -> str:
        return f"SMTPSettings(host={self.smtp_host}, port={self.smtp_port})"
```

---

### `server/webui/admin_panel/forms.py` — Add `SmtpSettingsForm` (utility, request-response)

**Analog:** `AISettingsForm` in `admin_panel/forms.py` (lines 15–61) — Django Form with Tailwind widget attrs:

```python
# Reuse these widget attrs (copy from AISettingsForm):
_TEXT_WIDGET_ATTRS = {
    "class": "w-full bg-gray-700 border border-gray-600 rounded px-3 py-2 text-gray-100 focus:outline-none focus:border-teal-500",
}

class SmtpSettingsForm(forms.Form):
    """Form for SMTP email configuration. Password is encrypted by the view before save."""

    smtp_host = forms.CharField(max_length=255, required=False, label="SMTP Host",
        widget=forms.TextInput(attrs={**_TEXT_WIDGET_ATTRS, "placeholder": "smtp.example.com"}))
    smtp_port = forms.IntegerField(min_value=1, max_value=65535, initial=587, label="SMTP Port",
        widget=forms.NumberInput(attrs={**_TEXT_WIDGET_ATTRS, "min": "1", "max": "65535"}))
    smtp_user = forms.CharField(max_length=255, required=False, label="Username",
        widget=forms.TextInput(attrs={**_TEXT_WIDGET_ATTRS, "autocomplete": "off"}))
    smtp_password = forms.CharField(required=False, label="Password",
        widget=forms.PasswordInput(attrs={**_TEXT_WIDGET_ATTRS, "autocomplete": "new-password",
                                          "placeholder": "Leave blank to keep current"}))
    smtp_from = forms.CharField(max_length=255, required=False, label="From Address",
        widget=forms.TextInput(attrs={**_TEXT_WIDGET_ATTRS, "placeholder": "haven@example.com"}))
    use_tls = forms.BooleanField(required=False, initial=True, label="Use TLS")

    def clean_smtp_port(self):
        port = self.cleaned_data.get("smtp_port")
        if port is not None and not (1 <= port <= 65535):
            raise forms.ValidationError("Port must be between 1 and 65535.")
        return port
```

---

### `server/webui/admin_panel/views.py` — Add `smtp_settings()` + `smtp_test()` (controller, CRUD)

**Analog:** `ai_settings()` in `admin_panel/views.py` (lines 165–210) — GET/POST singleton + HTMX partial swap.

**Fernet pattern** (from `server/app/services/totp.py` lines 23–32 — translate to Django side):
```python
import base64
import hashlib
from cryptography.fernet import Fernet
from django.conf import settings

def _get_fernet() -> Fernet:
    # SHA-256 of SECRET_KEY → 32 bytes → base64url → valid Fernet key
    # Same derivation used in app/services/totp.py — consistent across stack
    key_bytes = hashlib.sha256(settings.SECRET_KEY.encode()).digest()
    return Fernet(base64.urlsafe_b64encode(key_bytes))
```

**smtp_settings view** (copy ai_settings structure):
```python
@admin_required
def smtp_settings(request):
    """
    GET/POST view for configuring SMTP email settings.

    GET: populates form with current SMTPSettings singleton values.
    POST: validates, encrypts password, saves. HTMX: returns partial on success/error.
    Password field left blank on GET (never expose ciphertext in form).
    """
    current = SMTPSettings.get()

    if request.method == "POST":
        form = SmtpSettingsForm(request.POST)
        if form.is_valid():
            current.smtp_host = form.cleaned_data["smtp_host"]
            current.smtp_port = form.cleaned_data["smtp_port"]
            current.smtp_user = form.cleaned_data["smtp_user"]
            current.smtp_from = form.cleaned_data["smtp_from"]
            current.use_tls = form.cleaned_data["use_tls"]
            # Only update password if a new one was provided
            new_password = form.cleaned_data.get("smtp_password")
            if new_password:
                current.smtp_password_encrypted = _get_fernet().encrypt(
                    new_password.encode()
                ).decode()
            current.save()
            messages.success(request, "SMTP settings saved.")

            if request.htmx:
                return render(request, "admin_panel/partials/smtp_settings_form.html", {"form": SmtpSettingsForm(initial={...})})
            return redirect("admin_panel:smtp_settings")

        if request.htmx:
            return HttpResponse(form.errors.as_text(), status=422)
        return render(request, "admin_panel/smtp_settings.html", {"form": form})

    form = SmtpSettingsForm(initial={
        "smtp_host": current.smtp_host,
        "smtp_port": current.smtp_port,
        "smtp_user": current.smtp_user,
        "smtp_from": current.smtp_from,
        "use_tls": current.use_tls,
        # smtp_password intentionally omitted — never expose ciphertext
    })
    return render(request, "admin_panel/smtp_settings.html", {"form": form})
```

**smtp_test view** (uses aiosmtplib — confirmed pattern from `server/app/notify.py`):
```python
import asyncio
import aiosmtplib
from email.mime.text import MIMEText

@admin_required
@require_POST
def smtp_test(request):
    """Send a test email using current SMTP settings. HTMX: returns inline success/error message."""
    current = SMTPSettings.get()
    if not current.smtp_host:
        if request.htmx:
            return HttpResponse('<p class="text-red-400 text-sm">SMTP host not configured.</p>', status=422)
        messages.error(request, "SMTP host not configured.")
        return redirect("admin_panel:smtp_settings")

    try:
        plaintext = _get_fernet().decrypt(current.smtp_password_encrypted.encode()).decode() if current.smtp_password_encrypted else ""
    except Exception:
        plaintext = ""

    async def _send():
        msg = MIMEText("This is a test email from Haven.")
        msg["From"] = current.smtp_from or current.smtp_user
        msg["To"] = request.user.email
        msg["Subject"] = "Haven SMTP Test"
        await aiosmtplib.send(
            msg,
            hostname=current.smtp_host,
            port=current.smtp_port,
            username=current.smtp_user or None,
            password=plaintext or None,
            use_tls=current.use_tls,
        )

    try:
        asyncio.run(_send())
        if request.htmx:
            return HttpResponse('<p class="text-green-400 text-sm">Test email sent successfully.</p>')
        messages.success(request, "Test email sent.")
    except Exception as exc:
        if request.htmx:
            return HttpResponse(f'<p class="text-red-400 text-sm">Send failed: {exc}</p>', status=422)
        messages.error(request, f"Send failed: {exc}")
    return redirect("admin_panel:smtp_settings")
```

---

### `server/webui/admin_panel/urls.py` — Add settings routes (config, request-response)

**Analog:** existing `admin_panel/urls.py` (lines 1–19) — `path()` pattern:

```python
# Add after existing ai_settings route:
path("settings/", views.smtp_settings, name="smtp_settings"),
path("settings/test/", views.smtp_test, name="smtp_test"),
```

---

### `server/webui/admin_panel/templates/admin_panel/smtp_settings.html` (NEW, component)

**Analog:** `admin_panel/ai_settings.html` (lines 1–53) — copy structure exactly:

```html
{% extends "base.html" %}
{% block title %}SMTP Settings{% endblock %}
{% block content %}
<div class="space-y-6 max-w-2xl">
  <div>
    <a href="{% url 'admin_panel:dashboard' %}" class="text-teal-400 hover:text-teal-300 text-sm">
      &larr; Back to Admin Dashboard
    </a>
  </div>
  <div>
    <h1 class="text-2xl font-bold text-gray-100">SMTP Email Settings</h1>
    <p class="text-gray-400 mt-1">
      Configure the outgoing email server. Changes take effect immediately — no restart required.
    </p>
  </div>
  <div class="bg-gray-800 rounded-lg border border-gray-700 p-6">
    <div id="smtp-settings-form">
      {% include "admin_panel/partials/smtp_settings_form.html" %}
    </div>
  </div>
</div>
{% endblock %}
```

---

### `server/webui/admin_panel/templates/admin_panel/partials/smtp_settings_form.html` (NEW, component)

**Analog:** `admin_panel/partials/ai_settings_form.html` (lines 1–75) — HTMX form partial with messages block:

```html
<!-- messages block (copy lines 1-12 from ai_settings_form.html verbatim) -->
{% if messages %}...{% endif %}

<form method="post"
      action="{% url 'admin_panel:smtp_settings' %}"
      hx-post="{% url 'admin_panel:smtp_settings' %}"
      hx-target="#smtp-settings-form"
      hx-swap="innerHTML">
  {% csrf_token %}

  <!-- Fields: smtp_host, smtp_port, smtp_user, smtp_password, smtp_from, use_tls -->
  <!-- Each field uses same label/input/error pattern as ai_settings_form.html lines 24-62 -->
  <!-- Widget class: "w-full bg-gray-700 border border-gray-600 rounded px-3 py-2 text-gray-100..." -->

  <div class="flex gap-3 mt-6">
    <button type="submit"
            class="bg-teal-600 hover:bg-teal-500 text-white font-medium py-2 px-6 rounded-lg transition-colors">
      Save SMTP Settings
    </button>
    <button type="button"
            hx-post="{% url 'admin_panel:smtp_test' %}"
            hx-target="#smtp-test-result"
            hx-swap="innerHTML"
            class="bg-gray-700 hover:bg-gray-600 text-gray-200 font-medium py-2 px-6 rounded-lg transition-colors">
      Send Test Email
    </button>
  </div>
  <div id="smtp-test-result" class="mt-3"></div>
</form>
```

---

### `server/webui/admin_panel/templates/admin_panel/dashboard.html` — Add SMTP Settings nav link (component)

**Analog:** Existing "AI Settings" link in `dashboard.html` — find it and add an identical sibling link:

```html
<!-- Match the exact element style of the AI Settings link (inspect dashboard.html for exact classes) -->
<a href="{% url 'admin_panel:smtp_settings' %}"
   class="[copy exact classes from existing AI Settings link]">
  SMTP Settings
</a>
```

---

## Shared Patterns

### Authentication / Authorization
**Source:** `server/webui/admin_panel/views.py` lines 30, 60, 91, 137, 165
**Apply to:** All new admin_panel views (`smtp_settings`, `smtp_test`)
```python
from core.decorators import admin_required

@admin_required
def smtp_settings(request): ...
```

**Source:** `server/webui/devices/views.py` lines 20, 35, 65
**Apply to:** New `device_delete()`, all `bulk_*` event views
```python
from django.contrib.auth.decorators import login_required
from django.views.decorators.http import require_POST

@login_required
@require_POST
def device_delete(request, device_id): ...
```

### HTMX Partial Swap
**Source:** `devices/views.py` lines 79–80 (HTMX branch returning partial template)
**Apply to:** All new views that modify data and return DOM updates
```python
if request.htmx:
    return render(request, "template/partial.html", context)
return redirect("namespace:view_name")
```

### Ownership Guard
**Source:** `devices/views.py` line 75 + `events/views.py` line 177
**Apply to:** `device_delete()`, `bulk_archive()`, `bulk_delete()`, `bulk_unarchive()`
```python
# Single object:
device = get_object_or_404(Device, id=device_id, user_id=request.user.id)
# Queryset — filter by user_id before update/delete:
Event.objects.filter(user_id=request.user.id, id__in=event_ids).update(...)
```

### Fernet Encryption
**Source:** `server/app/services/totp.py` lines 23–32
**Apply to:** `admin_panel/views.py` `smtp_settings()` and `smtp_test()` — copy `_get_fernet()` pattern:
```python
def _get_fernet() -> Fernet:
    key_bytes = hashlib.sha256(settings.SECRET_KEY.encode()).digest()
    return Fernet(base64.urlsafe_b64encode(key_bytes))
```

### Singleton Model
**Source:** `admin_panel/models.py` lines 55–58
**Apply to:** New `SMTPSettings` model
```python
@classmethod
def get(cls) -> "AISettings":
    obj, _ = cls.objects.get_or_create(pk=1)
    return obj
```

### Django Form Widget Styling
**Source:** `admin_panel/forms.py` lines 28–29 (Tailwind widget attrs)
**Apply to:** `SmtpSettingsForm`
```python
"class": "w-full bg-gray-700 border border-gray-600 rounded px-3 py-2 text-gray-100 focus:outline-none focus:border-teal-500"
```

### HTMX Validation Error (422)
**Source:** `admin_panel/views.py` lines 120–121, 190
**Apply to:** All POST views that return form errors to HTMX
```python
if request.htmx:
    return HttpResponse(form.errors.as_text(), status=422)
```

### Kotlin KDoc Style
**Source:** `sensor/LightMonitor.kt` lines 47–58, `detection/SensorFusionEngine.kt` lines 6–17
**Apply to:** All 6 Android hot-spot files
```kotlin
/**
 * [Brief English summary of what the function/class does.]
 *
 * [WHY explanation: explain the non-obvious algorithmic reason for this design.]
 *
 * @param paramName [Non-obvious description only — skip if param name is self-explanatory.]
 * @return [Description of return value if non-trivial.]
 */
```

---

## No Analog Found

All files in this phase have close analogs. No new patterns need to be invented.

---

## Metadata

**Analog search scope:** `app/src/main/java/org/havenapp/main/`, `server/webui/`, `server/app/`, `server/alembic/`
**Files scanned:** ~30 source files read directly
**Pattern extraction date:** 2026-05-27
