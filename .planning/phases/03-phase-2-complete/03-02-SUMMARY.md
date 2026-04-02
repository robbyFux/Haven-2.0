---
phase: 03-phase-2-complete
plan: 02
subsystem: media/recording
tags: [camera, video, clip-recording, settings, camerax, room]
dependency_graph:
  requires: [03-01]
  provides: [REC-01, REC-02, REC-03]
  affects: [ClipRecorder, MonitorService, EventRepository, EventTriggerDao, SettingsRepository, SettingsViewModel, SettingsScreen]
tech_stack:
  added: [androidx.camera:camera-video:1.4.0]
  patterns: [CameraX VideoCapture<Recorder>, AtomicReference single-clip guard, Handler.postDelayed auto-stop, Room @Query UPDATE]
key_files:
  created:
    - app/src/main/java/org/havenapp/main/media/ClipRecorder.kt
  modified:
    - gradle/libs.versions.toml
    - app/build.gradle.kts
    - app/src/main/java/org/havenapp/main/MonitorService.kt
    - app/src/main/java/org/havenapp/main/storage/EventRepository.kt
    - app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt
    - app/src/main/java/org/havenapp/main/storage/dao/EventTriggerDao.kt
    - app/src/main/java/org/havenapp/main/ui/settings/SettingsScreen.kt
    - app/src/main/java/org/havenapp/main/ui/settings/SettingsViewModel.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-de/strings.xml
decisions:
  - "ClipRecorder is not a Hilt singleton — owned by MonitorService, tied to camera lifecycle, not application lifecycle"
  - "VideoCapture binding uses runCatching with fallback to ImageAnalysis-only for LEGACY hardware graceful degradation"
  - "EventRepository.recordTrigger now returns Long (trigger row ID) enabling async mediaPath linkage post-clip"
  - "SettingsViewModel secondary combine extended to 4 params (cal, lang, zone, clipDur) using List<Any?> to stay within 4-param combine limit"
metrics:
  duration_minutes: 5
  completed_date: "2026-04-02"
  tasks_completed: 2
  files_modified: 10
---

# Phase 3 Plan 02: Video Clip Recording Summary

**One-liner:** CameraX VideoCapture<Recorder> clip recording on sensor trigger with single-clip AtomicReference guard, configurable 10/30/60s duration, and async Room mediaPath linkage.

## What Was Built

### ClipRecorder (new)

`app/src/main/java/org/havenapp/main/media/ClipRecorder.kt`

Wraps `VideoCapture<Recorder>` to record timed MP4 video+audio clips when a sensor trigger fires.

Key design points:
- `AtomicReference<Recording?>` enforces single-clip guard (REC-03) — `startClip` returns null if already recording
- `isAvailable`/`setUnavailable()` for graceful LEGACY hardware degradation
- Auto-stop via `Handler.postDelayed` after `durationSeconds * 1000`
- `onClipReady` callback fires on the recording executor thread when finalization succeeds

### MonitorService changes

- Added `camerax-video` imports (`VideoCapture`, `Recorder`, `QualitySelector`, etc.)
- `startCamera()` now attempts `cameraProvider.bindToLifecycle(... imageAnalysis, videoCaptureUseCase)` with `runCatching`:
  - **Success:** creates `ClipRecorder` and calls `attach(videoCaptureUseCase)`
  - **Failure:** logs warning, rebinds `imageAnalysis` only, creates unavailable `ClipRecorder`
- `sensorFlow.collect` triggers `clipRecorder?.startClip(...)` after `recordTrigger()`; the returned `triggerId` is used in the `onClipReady` lambda to call `updateTriggerMediaPath`
- `stopMonitoring()` calls `clipRecorder?.stopIfRecording()` before `cameraAnalyzer?.reset()`

### Storage layer changes

- `EventRepository.recordTrigger()`: return type changed from `Unit` to `Long` (the inserted row ID)
- `EventRepository.updateTriggerMediaPath(triggerId, mediaPath)`: new function
- `EventTriggerDao.updateMediaPath(triggerId, mediaPath)`: new `@Query("UPDATE ...")` suspend function

### Settings: clip duration

- `SettingsRepository`: `clipDurationSeconds: Flow<Int>` (default 30) + `setClipDurationSeconds()`
- `SettingsUiState`: added `clipDurationSeconds: Int = 30`
- `SettingsViewModel`: combine extended to include `clipDurationSeconds`, `setClipDurationSeconds()` added
- `SettingsScreen`: new "Clip-Dauer / Clip Duration" radio group with 10s / 30s / 60s options
- String resources added to both `values/strings.xml` (EN) and `values-de/strings.xml` (DE)

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 | ce57e26 | feat(03-02): add camera-video dependency and ClipRecorder class |
| Task 2 | e7bcb07 | feat(03-02): wire ClipRecorder into MonitorService; add clip duration setting |

## Verification

- `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL after each task
- ClipRecorder exists with `AtomicReference<Recording?>` single-clip guard
- MonitorService triggers clip in `sensorFlow.collect` on sensor event
- Clip duration setting visible in SettingsScreen (radio group 10/30/60s)
- `mediaPath` stored via `eventRepository.updateTriggerMediaPath(triggerId, clipPath)` (REC-02)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] SettingsViewModel secondary combine uses List<Any?> to handle 4 parameters**
- **Found during:** Task 2
- **Issue:** Kotlin `combine` for Flows supports max 4 transform parameters in the lambda when combining 4 flows. The existing secondary combine used `Triple` for 3 flows; adding `clipDurationSeconds` as a 4th required a different approach since `Quadruple` doesn't exist.
- **Fix:** Changed secondary combine to return `List<Any?>` with the 4 values indexed; cast in the final lambda with `@Suppress("UNCHECKED_CAST")`. This keeps the nested combine structure intact without introducing a new data class.
- **Files modified:** `SettingsViewModel.kt`
- **Commit:** e7bcb07

## Known Stubs

None — all clip recording paths are fully wired. The `startClip` function operates on real CameraX `VideoCapture` and the `onClipReady` callback links to the real `EventTriggerEntity.mediaPath` in Room.

## Self-Check: PASSED
