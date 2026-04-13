---
phase: 07-android16-compat-sensor-expert
plan: "01"
subsystem: detection
tags: [mediapipe, android16, 16kb-compat, tflite-migration, object-detection]
dependency_graph:
  requires: []
  provides: [mediapipe-object-detector]
  affects: [HavenObjectDetector, CameraAnalyzer]
tech_stack:
  added: ["com.google.mediapipe:tasks-vision:0.10.29"]
  removed: ["org.tensorflow:tensorflow-lite-task-vision:0.4.4"]
  patterns: [MediaPipe Tasks Vision IMAGE mode, BaseOptions.setModelAssetPath]
key_files:
  created: []
  modified:
    - gradle/libs.versions.toml
    - app/build.gradle.kts
    - app/src/main/java/org/havenapp/main/detection/HavenObjectDetector.kt
decisions:
  - "MediaPipe Tasks Vision 0.10.29 chosen as drop-in replacement for TFLite Task Vision 0.4.4 — ships 16 KB-aligned .so files required by Android 16"
  - "RunningMode.IMAGE used (not LIVE_STREAM) — synchronous per-frame inference matches existing CameraAnalyzer call pattern"
  - "Model filename efficientdet_lite0.tflite unchanged — same COCO-trained model works with MediaPipe Tasks Vision"
  - "Public HavenObjectDetector contract preserved byte-identically — CameraAnalyzer requires zero changes"
metrics:
  duration_minutes: 15
  completed_date: "2026-04-13"
  tasks_completed: 2
  files_modified: 3
---

# Phase 07 Plan 01: MediaPipe Tasks Vision Migration Summary

**One-liner:** Migrated on-device object detection from TFLite Task Vision 0.4.4 to MediaPipe Tasks Vision 0.10.29, achieving Android 16 / 16 KB ELF alignment compliance while preserving the full HavenObjectDetector public contract.

## What Was Built

Replaced the `org.tensorflow:tensorflow-lite-task-vision:0.4.4` dependency with `com.google.mediapipe:tasks-vision:0.10.29` across the Gradle build configuration and rewrote `HavenObjectDetector.kt` to use the MediaPipe Tasks Vision API. The same `efficientdet_lite0.tflite` model file is used — only the inference library changed.

### Changes Made

**gradle/libs.versions.toml:**
- Removed `tflite = "0.4.4"` version entry
- Removed `tflite-task-vision` library entry (org.tensorflow group)
- Added `mediapipe = "0.10.29"` version entry
- Added `mediapipe-tasks-vision` library entry (com.google.mediapipe group)
- Reorganized CameraX entries into a clean `# CameraX` section (previously interleaved with TFLite)

**app/build.gradle.kts:**
- Replaced `implementation(libs.tflite.task.vision)` with `implementation(libs.mediapipe.tasks.vision)`
- Updated comment block from `// TFLite Modell-Setup:` to `// Object detection model setup (MediaPipe Tasks Vision):`
- `noCompress += "tflite"` preserved — MediaPipe still loads `.tflite` model files

**app/src/main/java/org/havenapp/main/detection/HavenObjectDetector.kt:**
- Removed: `import org.tensorflow.lite.support.image.TensorImage`, `import org.tensorflow.lite.task.vision.detector.ObjectDetector`
- Added: `BitmapImageBuilder`, `BaseOptions`, `RunningMode`, `ObjectDetector` (MediaPipe), `ObjectDetectorResult`
- `initialize()`: replaced `ObjectDetector.createFromFileAndOptions(context, MODEL_FILENAME, options)` with `BaseOptions.builder().setModelAssetPath(MODEL_FILENAME)` + `RunningMode.IMAGE` + `ObjectDetector.createFromOptions(context, options)`
- `detect()`: replaced `TensorImage.fromBitmap(bitmap)` + `d.detect(tensorImage)` + `detection.categories` + `cat.label` with `BitmapImageBuilder(bitmap).build()` + `d.detect(mpImage)` + `result.detections()` + `detection.categories()` + `cat.categoryName()`
- KDoc updated: "TFLite Task Library" → "MediaPipe Tasks Vision (Android 16 / 16 KB ELF alignment compliant)"
- All other members unchanged: `mapLabel()`, `close()`, `PET_LABELS`, `VEHICLE_LABELS`, `SCORE_THRESHOLD`, `MAX_RESULTS`, `MODEL_FILENAME`, Hilt annotations, `@ApplicationContext`

## Commits

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Swap Gradle dependency TFLite → MediaPipe | 762f8fa | gradle/libs.versions.toml, app/build.gradle.kts |
| 2 | Rewrite HavenObjectDetector to MediaPipe API | 4bff60b | HavenObjectDetector.kt |

## Verification Results

- `gradle/libs.versions.toml` declares `mediapipe = "0.10.29"` and `mediapipe-tasks-vision` — no TFLite entries remain
- `app/build.gradle.kts` references `libs.mediapipe.tasks.vision` — no `libs.tflite.task.vision`
- `./gradlew :app:dependencies --configuration debugRuntimeClasspath` resolves `com.google.mediapipe:tasks-vision:0.10.29` and does NOT include `org.tensorflow:tensorflow-lite-task-vision`
- `grep -rn "org.tensorflow" app/src/main/java/` returns no matches
- `./gradlew :app:compileDebugKotlin` exits 0 (BUILD SUCCESSFUL)
- Public API unchanged: `fun initialize(): Boolean`, `fun detect(bitmap: Bitmap, mode: DetectionMode): List<TriggerType>`, `val isAvailable: Boolean`, `val availabilityFlow: StateFlow<Boolean>`, `var initError: String?`, `fun close()`
- `CameraAnalyzer.kt` not modified

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None — no new network endpoints, auth paths, file access patterns, or schema changes introduced. The migration is confined to the inference library; the trust boundary (assets-loaded model file, no network calls) is unchanged.

## Self-Check: PASSED

- `gradle/libs.versions.toml` exists and contains `mediapipe = "0.10.29"`: FOUND
- `app/build.gradle.kts` contains `libs.mediapipe.tasks.vision`: FOUND
- `HavenObjectDetector.kt` contains `BitmapImageBuilder` and `createFromOptions`: FOUND
- Commit 762f8fa exists: FOUND
- Commit 4bff60b exists: FOUND
- No `org.tensorflow` imports in source: CONFIRMED
- `compileDebugKotlin` BUILD SUCCESSFUL: CONFIRMED
