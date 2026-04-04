---
phase: 03-phase-2-complete
plan: 05
subsystem: sensor/light
tags: [light-monitor, ema, cross-sensor-suppression, settings]
dependency_graph:
  requires: [03-04]
  provides: [RecentTriggerState, LightMonitor-dual-EMA, light-suppression-settings]
  affects: [MonitorService, SettingsRepository, SettingsViewModel, SettingsScreen]
tech_stack:
  added: []
  patterns: [dual-rate-EMA, cross-sensor-priority-gate, ConcurrentHashMap-singleton]
key_files:
  created:
    - app/src/main/java/org/havenapp/main/sensor/RecentTriggerState.kt
  modified:
    - app/src/main/java/org/havenapp/main/sensor/LightMonitor.kt
    - app/src/main/java/org/havenapp/main/MonitorService.kt
    - app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt
    - app/src/main/java/org/havenapp/main/ui/settings/SettingsViewModel.kt
    - app/src/main/java/org/havenapp/main/ui/settings/SettingsScreen.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-de/strings.xml
decisions:
  - "LightMonitor no longer implements SensorMonitor: 3-param observe() cannot satisfy 2-param interface; MonitorService injects concrete type so interface is not needed"
  - "emaFast (alpha=0.1) used for trigger deviation; emaSlow (alpha=0.02) reserved for future diagnostics"
  - "Deviation measured from emaFast BEFORE updating to avoid absorbing the spike into the baseline"
  - "RecentTriggerState is a plain Kotlin object (not Hilt) for zero-overhead process-global access from both MonitorService and LightMonitor"
metrics:
  duration_minutes: 15
  completed_date: "2026-04-04"
  tasks_completed: 1
  files_changed: 8
---

# Phase 03 Plan 05: Dual-rate EMA LightMonitor with Cross-Sensor Suppression Summary

**One-liner:** Dual-rate EMA LightMonitor (emaFast alpha=0.1, emaSlow alpha=0.02) with ConcurrentHashMap-based cross-sensor priority gate suppressing false light triggers when motion/camera fired recently, configurable 0/10/30/60s window in Settings.

## Tasks Completed

| Task | Name | Commit | Status |
|------|------|--------|--------|
| 1 | Create RecentTriggerState, rewrite LightMonitor dual-rate EMA, wire MonitorService, add Settings | 47fa57b | DONE |
| 2 | Verify light sensor behavior on device | — | AWAITING (checkpoint:human-verify) |

## What Was Built

### RecentTriggerState (`sensor/RecentTriggerState.kt`)
New process-global singleton using `ConcurrentHashMap<TriggerType, Long>` for thread-safe timestamp tracking. Provides `record(type)`, `wasRecentlyTriggeredBy(types, windowMs)`, and `reset()`. Follows the `AppLockState` pattern (plain Kotlin object, no Hilt).

### LightMonitor rewrite (`sensor/LightMonitor.kt`)
- Replaced single EMA (alpha=0.02) with dual-rate EMA: `emaFast` (alpha=0.1) and `emaSlow` (alpha=0.02)
- Deviation measured from `emaFast` **before** updating it (spike not absorbed)
- On trigger: `emaFast` snaps to current lux value to prevent cascade triggers
- `emaSlow` always updates normally (slow reference, never snapped)
- `_emaBaseline` exposes `emaFast` value (null until warmup, same pattern as before)
- Added cross-sensor gate: `RecentTriggerState.wasRecentlyTriggeredBy(setOf(ACCELEROMETER, CAMERA), suppressionWindowMs)` — if true, trigger is suppressed without resetting cooldown
- New 3-param `observe(sensitivity, warmupMs, suppressionWindowMs)` signature (no `override`, class no longer implements `SensorMonitor`)

### MonitorService integration (`MonitorService.kt`)
- Reads `lightSuppressMotionSeconds` from DataStore at session start
- Passes `lightSuppressMotionSeconds * 1000L` to `lightMonitor.observe()`
- Calls `RecentTriggerState.record(trigger.type)` as first line inside `sensorFlow.collect` block
- Calls `RecentTriggerState.reset()` in `stopMonitoring()` after `clipRecorder = null`

### SettingsRepository (`storage/SettingsRepository.kt`)
Added `KEY_LIGHT_SUPPRESS_MOTION_SECONDS` DataStore key, `lightSuppressMotionSeconds: Flow<Int>` (default 10), and `setLightSuppressMotionSeconds(seconds: Int)`.

### SettingsViewModel (`ui/settings/SettingsViewModel.kt`)
Added `lightSuppressMotionSeconds: StateFlow<Int>` (stateIn with 5s subscription) and `setLightSuppressMotionSeconds(seconds: Int)`.

### SettingsScreen (`ui/settings/SettingsScreen.kt`)
Added "Suppress light on motion" section with Off/10s/30s/60s radio options between the Active Sensors and Detection Zone sections. Added `LIGHT_SUPPRESS_OPTIONS = listOf(0, 10, 30, 60)` and `lightSuppressLabel()` composable.

### String resources
EN: "Suppress light on motion" / Off / 10 s / 30 s / 60 s
DE: "Licht bei Bewegung unterdrücken" / Aus / 10 s / 30 s / 60 s

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None. All data flows are wired end-to-end.

## Self-Check: PASSED

- `sensor/RecentTriggerState.kt` exists: FOUND
- `sensor/LightMonitor.kt` — no `: SensorMonitor`, contains `emaFastAlpha`, `emaSlowAlpha`, `wasRecentlyTriggeredBy`: FOUND
- `MonitorService.kt` — contains `RecentTriggerState.record`, `RecentTriggerState.reset`, `lightSuppressMotionSeconds`: FOUND
- `SettingsRepository.kt` — contains `KEY_LIGHT_SUPPRESS_MOTION_SECONDS`, `lightSuppressMotionSeconds`: FOUND
- `SettingsViewModel.kt` — contains `lightSuppressMotionSeconds StateFlow`, `setLightSuppressMotionSeconds`: FOUND
- `SettingsScreen.kt` — contains `LIGHT_SUPPRESS_OPTIONS`, `settings_light_suppress_title`: FOUND
- Commit 47fa57b: FOUND
- `./gradlew :app:compileDebugKotlin` result: BUILD SUCCESSFUL
