# Phase 3: Phase-2-Complete — Context

**Gathered:** 2026-04-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 3 originally delivered: TFLite/zone/event fixes (plans 01–03, complete) and app PIN lock
(plan 04, pending). This context captures three **additional sensor calibration improvements**
added to Phase 3 based on real-world testing:

1. **LightMonitor false-alarm reduction** — adaptive dual-rate EMA + cross-sensor priority gate
2. **FusedMotionMonitor latency reduction** — faster sensor sampling rate
3. **EventDetailScreen sensor filter** — filter trigger list by sensor type

These result in plans 05 and 06 (sensor backend) + plan 07 (EventDetailScreen UI).
Plan 04 (App PIN) is unaffected — execute it first in wave 4, new plans in wave 5.

**Evidence from test session (2026-04-04, ~1.5 h):**
- Settings: LOW sensitivity, light delta 100 lux, MOTION_ONLY, SENSOR_DELAY_NORMAL
- Trigger log (last 100): ~85 LIGHT events, ~15 MICROPHONE events, 0 MOTION events
- LIGHT HIGH events (200–6000+ lux) every ~1–2 minutes → periodic sunlight through window
- Root cause: EMA baseline (α=0.02) cannot track rapid outdoor light oscillations

</domain>

<decisions>
## Implementation Decisions

### LightMonitor — Dual-Rate EMA (D-01)

- **D-01:** Replace single EMA with **dual-rate EMA**:
  - `emaFast` (α = 0.1) — tracks the "learned" baseline; adapts quickly to gradual daylight changes
  - `emaSlow` (α = 0.02) — tracks a slower reference, reserved for future use / diagnostics
  - **Trigger condition:** `|lux - emaFast| > threshold` (deviation measured from fast baseline)
  - **On trigger:** snap `emaFast = lux` immediately (prevents cascade triggers, existing pattern)
  - **Rationale:** sunrise/sunset drift is gradual → `emaFast` follows it → deviation stays small.
    A lamp turning on is instantaneous → `emaFast` hasn't caught up → large deviation → trigger.

- **D-02:** Keep the 30s cooldown, snap-on-trigger, and existing threshold values (`Sensitivity.lightDeltaLux`). No changes to `Sensitivity` enum.

- **D-03:** Expose `emaFast` as `StateFlow<Float?>` named `emaBaseline` (replaces current `emaBaseline`) so DiagnosticsScreen can still display it. Same null-until-warmup pattern.

### LightMonitor — Cross-Sensor Priority Gate (D-04)

- **D-04:** LightMonitor suppresses its triggers if ACCELEROMETER or CAMERA fired recently.
  - Suppression is checked via a **shared singleton `RecentTriggerState`** (plain Kotlin object,
    not Hilt — must be process-global):
    ```kotlin
    object RecentTriggerState {
        fun record(type: TriggerType)           // called by MonitorService on each trigger
        fun wasRecentlyTriggeredBy(types: Set<TriggerType>, windowMs: Long): Boolean
    }
    ```
  - `MonitorService` calls `RecentTriggerState.record(event.type)` for every trigger it receives.
  - `LightMonitor.onSensorChanged` calls `RecentTriggerState.wasRecentlyTriggeredBy(
      setOf(TriggerType.ACCELEROMETER, TriggerType.CAMERA), suppressionWindowMs)` before emitting.
  - If the check returns true → skip the trigger (no emit, no cooldown reset).

- **D-05:** Suppression window is **user-configurable in Settings**:
  - DataStore key `light_suppress_motion_seconds`, default = 10
  - UI: SettingsScreen row "Licht bei Bewegung unterdrücken" / "Suppress light on motion",
    with a dropdown/radio: Off | 10 s | 30 s | 60 s
  - `LightMonitor.observe()` receives suppression window as a parameter (passed by MonitorService
    reading the setting at session start, same pattern as other settings).

### FusedMotionMonitor — Latency Reduction (D-06)

- **D-06:** Change sensor registration from `SENSOR_DELAY_NORMAL` to `SENSOR_DELAY_GAME` for
  both accelerometer and gyroscope in `FusedMotionMonitor.kt`:
  ```kotlin
  // Before:
  sensorManager.registerListener(accelListener, accelSensor, SensorManager.SENSOR_DELAY_NORMAL)
  sensorManager.registerListener(gyroListener, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL)
  // After:
  sensorManager.registerListener(accelListener, accelSensor, SensorManager.SENSOR_DELAY_GAME)
  sensorManager.registerListener(gyroListener, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
  ```
  SENSOR_DELAY_GAME ≈ 20 ms vs. SENSOR_DELAY_NORMAL ≈ 200 ms. Moderate battery impact,
  acceptable for a security monitoring app.

- **D-07:** No other changes to `FusedMotionMonitor`. Warmup, noise floor (90th percentile),
  multipliers, and cooldown logic are unchanged.

### EventDetailScreen — Sensor Type Filter (D-08)

- **D-08:** Add a **horizontal scrollable filter chip row** above the trigger `LazyColumn` in
  `EventDetailScreen`:
  - First chip: "Alle" / "All" — always present, selected by default
  - Additional chips: one per `TriggerType` that actually exists in the event's trigger list
    (dynamic — derived from the loaded triggers, no hardcoded list)
  - Use `FilterChip` (Material3) — toggled state, single-select (selecting one deselects "All"
    and vice versa)
  - Filter state held in local Compose `remember { mutableStateOf<TriggerType?>(null) }`
    (null = All, non-null = specific type)
  - Filtered list passed to `LazyColumn` via a `derivedStateOf` computation

- **D-09:** Chips use existing severity color tokens where possible; chip text is the
  `TriggerType` name (localized if string resources exist for each type, otherwise enum name).

- **D-10:** Filter state is NOT persisted — resets to "Alle" each time the screen is opened.
  Local UI state only, no ViewModel changes needed.

### Claude's Discretion

- `RecentTriggerState` internal data structure (e.g., `Map<TriggerType, Long>` for last-seen timestamps)
- Exact chip layout padding and scroll behavior in EventDetailScreen
- Whether to add `emaFast`/`emaSlow` both or only `emaFast` to DiagnosticsScreen

</decisions>

<specifics>
## Specific Ideas

**From test log analysis:**
- LIGHT HIGH events at 1100–6240 lux happening every 1–2 minutes from 11:28 to 12:51 →
  classic cloud-cover / direct sun oscillation through a window. The dual-rate EMA with α=0.1
  tracks this on a ~10-sample (2s) timescale, so the deviation measured from `emaFast` should
  stay below threshold during these oscillations.
- LIGHT LOW events at 60–90 lux (below the 100 lux threshold) — possibly from a prior session
  with MEDIUM sensitivity (60 lux threshold). Not a current bug.

**User phrasing (DE):**
- "Der Lichtsensor sollte Lernen" → dual-rate EMA is the implementation
- "Bewegungssensor vor dem Lichtsensor Vorrang" → `RecentTriggerState` cross-sensor gate
- "nur bei starker Schwankung reagieren z.B. Raum dunkel → Licht an" → existing threshold
  values are correct; the EMA learning is what prevents the false alarms
- "Bewegungssensor reagiert leicht zu spät" → `SENSOR_DELAY_GAME` fix

</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Sensor monitors (files being modified)
- `app/src/main/java/org/havenapp/main/sensor/LightMonitor.kt` — current EMA implementation to replace
- `app/src/main/java/org/havenapp/main/sensor/FusedMotionMonitor.kt` — sensor rate to change (line 128–131)
- `app/src/main/java/org/havenapp/main/sensor/Sensitivity.kt` — threshold values (do NOT change)

### Cross-sensor state (new file)
- `app/src/main/java/org/havenapp/main/sensor/RecentTriggerState.kt` — to be created (new singleton)
- `app/src/main/java/org/havenapp/main/MonitorService.kt` — must call `RecentTriggerState.record()` on each trigger

### Settings (existing, to be extended)
- `app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt` — add `lightSuppressMotionSeconds: Flow<Int>`
- `app/src/main/java/org/havenapp/main/ui/settings/SettingsScreen.kt` — add suppress-on-motion UI row
- `app/src/main/java/org/havenapp/main/ui/settings/SettingsViewModel.kt` — expose `lightSuppressMotionSeconds`

### UI (EventDetailScreen)
- `app/src/main/java/org/havenapp/main/ui/timeline/EventDetailScreen.kt` — add FilterChip row above LazyColumn
- `app/src/main/res/values/strings.xml` — add string resources for new Settings labels
- `app/src/main/res/values-de/strings.xml` — add German string resources

### Project conventions
- `CLAUDE.md` — sensor monitor patterns, DataStore conventions, Compose UI rules

### Existing plans (context for wave ordering)
- `.planning/phases/03-phase-2-complete/03-04-PLAN.md` — App PIN plan (wave 4, execute first)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `LightMonitor.kt` has the complete EMA + cooldown + warmup pattern — replace only the `ema` variable with `emaFast`/`emaSlow` pair, keep all surrounding logic
- `MonitorService.kt` already iterates over trigger events — add `RecentTriggerState.record(event.type)` at the recording call site
- `EventDetailScreen.kt` already has a `LazyColumn` with `triggers` list — filter chips slot in above it with a local `selectedType` state
- `SuggestionChip` is already imported in `EventDetailScreen.kt` — switch to `FilterChip` (same import source)

### Established Patterns
- Singletons without Hilt: `AppLockState` (plan 04) is the established pattern for process-global state objects — follow the same structure for `RecentTriggerState`
- `StateFlow<Float?>` null-until-warmup: `LightMonitor.emaBaseline` and `FusedMotionMonitor.noiseFloor` — keep this for `emaFast` exposure
- DataStore settings: `KEY_X = typePreferencesKey("x")` + `Flow<T>` + `suspend fun setX()` in `SettingsRepository`
- `MonitorService` reads settings via `.first()` snapshot at session start — pass `suppressionWindowMs` to `LightMonitor.observe()` the same way sensitivity is passed

### Integration Points
- `MonitorService.kt`: add `RecentTriggerState.record(event.type)` in the trigger processing block (after receiving from merged sensor flow, before `eventRepository.recordTrigger()`)
- `LightMonitor.observe()`: add `suppressionWindowMs: Long` parameter (or read from a shared Flow — simpler: pass as parameter at session start)
- `SettingsRepository`: one new `Int` key + flow + setter
- `SettingsViewModel`: one new `StateFlow<Int>` + one new `fun setSuppressMotionSeconds()`

</code_context>

<deferred>
## Deferred Ideas

- Timeline-level filter by sensor type (filtering events, not triggers within an event) — Phase 4/UX
- Microphone as a cross-sensor suppressor — user chose ACCELEROMETER + CAMERA only
- Automatic sensitivity auto-tuning based on trigger frequency — Phase 5/Hardening
- Per-sensor cooldown configurability (currently hardcoded in each monitor) — Phase 5

</deferred>

---

*Phase: 03-phase-2-complete*
*Context gathered: 2026-04-04*
