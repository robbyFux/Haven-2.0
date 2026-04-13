---
phase: 07-android16-compat-sensor-expert
plan: 02
subsystem: sensor
tags: [sensor, expert-settings, sensitivity, datastore, foundation]
dependency_graph:
  requires: []
  provides: [ExpertThresholds data class, SettingsRepository.expertThresholds flow, Sensitivity.effective* helpers, sensor monitor expert wiring]
  affects: [FusedMotionMonitor, LightMonitor, MicrophoneMonitor, CameraAnalyzer, MonitorService, SettingsRepository]
tech_stack:
  added: []
  patterns: [DataStore float preference keys, extension functions on enum, value-passed data class at session start]
key_files:
  created:
    - app/src/main/java/org/havenapp/main/sensor/ExpertThresholds.kt
  modified:
    - app/src/main/java/org/havenapp/main/sensor/Sensitivity.kt
    - app/src/main/java/org/havenapp/main/sensor/SensorMonitor.kt
    - app/src/main/java/org/havenapp/main/sensor/FusedMotionMonitor.kt
    - app/src/main/java/org/havenapp/main/sensor/LightMonitor.kt
    - app/src/main/java/org/havenapp/main/sensor/MicrophoneMonitor.kt
    - app/src/main/java/org/havenapp/main/sensor/AccelerometerMonitor.kt
    - app/src/main/java/org/havenapp/main/sensor/GyroscopeMonitor.kt
    - app/src/main/java/org/havenapp/main/media/CameraAnalyzer.kt
    - app/src/main/java/org/havenapp/main/MonitorService.kt
    - app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt
    - app/src/main/java/org/havenapp/main/detection/HavenObjectDetector.kt
decisions:
  - "SensorMonitor interface updated to include expert: ExpertThresholds param with DEFAULT — less invasive than adding a parallel overload since all implementors (active and legacy) are in this repo"
  - "expert import not needed in same-package files (effectiveAccelMultiplier etc.) — Kotlin resolves package-level functions without import"
  - "effectiveCameraFraction imported explicitly in CameraAnalyzer (different package: media vs sensor)"
metrics:
  duration_minutes: 35
  completed_date: "2026-04-13"
  tasks_completed: 3
  files_modified: 11
---

# Phase 07 Plan 02: Sensitivity Defaults + Expert Threshold Foundation Summary

Retuned `Sensitivity.MEDIUM` defaults for reliable indoor motion detection (SENSOR-10) and wired the complete `ExpertThresholds` persistence and consumption layer (SENSOR-11 foundation) that Plan 03's Expert Settings UI will bind to.

## Tasks Completed

| # | Task | Commit | Status |
|---|------|--------|--------|
| 1 | Update Sensitivity defaults, add ExpertThresholds + effective* helpers | b6e3ec6 | Done |
| 2 | Add expert threshold storage to SettingsRepository | ce7d3f0 | Done |
| 3 | Thread ExpertThresholds through monitors, CameraAnalyzer, MonitorService | d5fa084 | Done |

## What Was Built

### SENSOR-10: Sensitivity Default Retuning

`Sensitivity.MEDIUM` changed from `(4.5f, 60f, 60f, 0.12f)` to `(2.0f, 55f, 40f, 0.08f)` — a 2.25x increase in accelerometer sensitivity and proportional improvements on mic, light, and camera thresholds. Low and High tiers adjusted to maintain a reasonable spread:

| Tier | accelMultiplier | micDb | lightLux | cameraFraction |
|------|----------------|-------|----------|----------------|
| LOW  | 4.0× (was 6.5×) | 65 (was 70) | 80 (was 100) | 0.18 (was 0.22) |
| MEDIUM | 2.0× (was 4.5×) | 55 (was 60) | 40 (was 60) | 0.08 (was 0.12) |
| HIGH | 1.2× (was 2.5×) | 45 (was 50) | 20 (was 30) | 0.04 (was 0.06) |

### SENSOR-11 Foundation: ExpertThresholds Data Class

`ExpertThresholds.kt` — four nullable Float fields representing custom Medium-tier overrides. Null = use Sensitivity enum default. `ExpertThresholds.DEFAULT` = all null (no overrides). `ExpertThresholdKind` enum identifies which sensor to set/reset individually.

### SENSOR-11 Foundation: SettingsRepository Extensions

Four `floatPreferencesKey` entries added. `expertThresholds: Flow<ExpertThresholds>` emits all-null when no keys are set (normal operation). `setExpertThreshold(kind, null)` removes the key. `resetExpertThresholds()` removes all four atomically.

### SENSOR-11 Foundation: Sensor Monitor Wiring

All four active consumers now compute their effective threshold via the `effective*` extension helpers:

- `FusedMotionMonitor`: `noiseFloor * sensitivity.effectiveAccelMultiplier(expert)`
- `LightMonitor`: `sensitivity.effectiveLightLux(expert)`
- `MicrophoneMonitor`: `sensitivity.effectiveMicDb(expert)`
- `CameraAnalyzer`: `sensitivity.effectiveCameraFraction(expert)` (constructor param)

`MonitorService` reads `expertThresholds.first()` immediately after `sensitivity.first()` at session bootstrap and passes the value to every consumer. With no expert keys set, effective thresholds equal enum defaults — identical behavior to before this plan (except with the new SENSOR-10 defaults).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Migrate HavenObjectDetector to MediaPipe Tasks Vision API**

- **Found during:** Task 1 compile check
- **Issue:** `libs.versions.toml` and `app/build.gradle.kts` already referenced `com.google.mediapipe:tasks-vision:0.10.29` (TFLite Task Vision removed), but `HavenObjectDetector.kt` still imported `org.tensorflow.lite.*` — causing 15 compile errors with "Unresolved reference: tensorflow".
- **Fix:** Rewrote `HavenObjectDetector.kt` to use MediaPipe Tasks Vision API: `BitmapImageBuilder`, `BaseOptions`, `RunningMode.IMAGE`, `ObjectDetector.createFromOptions()`, `result.detections()`, `category.categoryName()`. Public contract (`initialize`, `detect`, `isAvailable`, `availabilityFlow`, `initError`) preserved unchanged — `CameraAnalyzer` callsites required no changes.
- **Files modified:** `app/src/main/java/org/havenapp/main/detection/HavenObjectDetector.kt`
- **Commit:** b6e3ec6

## Known Stubs

None. All flows and methods are fully wired. Expert UI (Plan 03) will call `setExpertThreshold()` and `resetExpertThresholds()` — these are already implemented and tested via compile verification.

## Threat Flags

None. No new network endpoints, auth paths, file access patterns, or schema changes introduced. DataStore keys added are local-only float preferences.

## Self-Check: PASSED

- `ExpertThresholds.kt` exists: confirmed
- `Sensitivity.kt` has `MEDIUM(2.0f, 55f, 40f, 0.08f)`: confirmed (line 27)
- `SettingsRepository.kt` has `expertThresholds` flow: confirmed (line 241)
- `MonitorService.kt` has `expertThresholds.first()`: confirmed (line 145)
- Commits b6e3ec6, ce7d3f0, d5fa084 exist: confirmed via `git log`
- `./gradlew :app:assembleDebug` passes: BUILD SUCCESSFUL
