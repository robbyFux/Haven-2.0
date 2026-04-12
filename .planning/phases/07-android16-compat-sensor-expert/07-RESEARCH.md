# Phase 7: Android 16 Compatibility & Sensor Expert Settings — Research

**Researched:** 2026-04-12
**Domain:** Android NDK/ELF alignment compliance + Compose Slider UI + DataStore settings architecture
**Confidence:** MEDIUM-HIGH

---

## Summary

Phase 7 has two fully independent work streams that share only the Settings navigation layer.

**Stream A** replaces `tensorflow-lite-task-vision:0.4.4` with `com.google.mediapipe:tasks-vision:0.10.29`
to eliminate the Android 16 "app not 16 KB compatible" warning. The TFLite Task Vision library ships
`libtask_vision_jni.so` with 4 KB ELF LOAD segment alignment; Android 16 requires 16 KB. MediaPipe
Tasks Vision ships with 16 KB-aligned `.so` files and its API is structurally similar but not identical
to TFLite Task Vision — there are package and method-name differences that require surgical changes to
`HavenObjectDetector.kt` only. The existing `efficientdet_lite0.tflite` model (with embedded metadata)
is confirmed compatible with MediaPipe Tasks without replacement. [VERIFIED: Google AI Edge official docs]

**Stream B** addresses motion sensor under-sensitivity. The current `Sensitivity.MEDIUM` multiplier (4.5×)
is too conservative for typical indoor use. The fix is two-part: (1) lower the default medium multiplier
to approximately 2.5× (current HIGH), and (2) add an Expert Settings screen where the user can slide
each sensor's Medium threshold independently. Low and High values are derived automatically as fixed
offsets from Medium, preserving the three-tier model. Custom thresholds persist in DataStore using four
new float preference keys; the `Sensitivity` enum remains as a fallback/default source.

**Primary recommendation:** Migrate `HavenObjectDetector.kt` to MediaPipe Tasks API (minimal blast radius),
then add `ExpertSettingsRepository` keys + `ExpertSettingsScreen` composable accessible from the existing
Settings screen via a dedicated Expert card row.

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| COMPAT-01 | App installs and runs on Android 16 without "not 16 KB compatible" warning; TFLite object detection (PERSON/PET/VEHICLE) works after migration | MediaPipe Tasks Vision 0.10.29 ships 16 KB-aligned `.so`; API migration documented in Code Examples section |
| SENSOR-10 | Motion sensor triggers reliably at default Medium sensitivity with normal room movement | Multiplier adjustment from 4.5× to ~2.0–2.5× documented in Common Pitfalls; threshold analysis in Architecture Patterns |
| SENSOR-11 | Settings → Expert contains per-sensor Medium threshold sliders; Low/High derived as offsets; persist via DataStore; Reset to defaults button | DataStore architecture for expert overrides + Slider API documented in Standard Stack and Code Examples |
</phase_requirements>

---

## Project Constraints (from CLAUDE.md)

- **Kotlin only** — no new Java files
- **Hilt DI** — all injected classes use `@Inject constructor`, no manual graphs
- **DataStore** — not SharedPreferences, not Room, for all persistent settings
- **Jetpack Compose + Material Design 3** — no XML layouts; `Icons.Filled.*`; `collectAsStateWithLifecycle()`
- **No Google Cloud services** — Firebase, Analytics forbidden
- **Architecture pattern** — UI calls ViewModel only; business logic in repositories; `StateFlow` for state
- **Naming** — ViewModel setters use `set` prefix; DataStore keys use `KEY_` prefix; private backing flows use `_` prefix
- **Logging** — use `AppLogger`, not raw `Log.*` (except `HavenObjectDetector` which uses both); no `Runtime.exec("logcat")`
- **Single Source of Truth** — `Sensitivity` enum remains the canonical fallback for threshold defaults; expert overrides layer on top

---

## Standard Stack

### Core (Stream A — MediaPipe Migration)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.google.mediapipe:tasks-vision` | 0.10.29 | Object detection, image classification, pose | Google's official replacement for TFLite Task Vision; ships 16 KB-aligned `.so` files; active maintenance |

**Remove:**

| Library | Version | Reason |
|---------|---------|--------|
| `org.tensorflow:tensorflow-lite-task-vision` | 0.4.4 | Deprecated; ships 4 KB-aligned `libtask_vision_jni.so`; no planned 16 KB fix from Google |

[VERIFIED: Google AI Edge docs, MediaPipe sample `build.gradle` at `google-ai-edge/mediapipe-samples`]

### Core (Stream B — No new dependencies)

Stream B requires no new Gradle dependencies. It uses:
- **`androidx.datastore:datastore-preferences`** (already in project at 1.1.1) — new float preference keys
- **`androidx.compose.material3:material3`** (Compose BOM 2024.09.03) — `Slider` composable is included

[VERIFIED: Compose Material3 official docs; existing `gradle/libs.versions.toml`]

### Version Verification

```bash
# Verify current MediaPipe tasks-vision version
# As of 2026-04-12, latest confirmed: 0.10.29
# Source: github.com/google-ai-edge/mediapipe-samples build.gradle (confirmed via WebFetch)
```

[CITED: https://github.com/google-ai-edge/mediapipe-samples/blob/main/examples/object_detection/android/app/build.gradle]

**Installation diff:**

```toml
# gradle/libs.versions.toml — remove tflite version key, add mediapipe key
[versions]
# Remove: tflite = "0.4.4"
mediapipe = "0.10.29"

[libraries]
# Remove: tflite-task-vision = { group = "org.tensorflow", name = "tensorflow-lite-task-vision", version.ref = "tflite" }
mediapipe-tasks-vision = { group = "com.google.mediapipe", name = "tasks-vision", version.ref = "mediapipe" }
```

```kotlin
// app/build.gradle.kts — swap dependency
// Remove: implementation(libs.tflite.task.vision)
// Add:
implementation(libs.mediapipe.tasks.vision)
```

---

## Architecture Patterns

### Stream A: HavenObjectDetector.kt Migration Map

The migration is confined to `detection/HavenObjectDetector.kt`. All callsites (`CameraAnalyzer.kt`) remain unchanged because the public contract (`initialize()`, `detect(bitmap, mode)`, `isAvailable`, `availabilityFlow`, `initError`) is preserved.

#### Package / Import Changes

| TFLite Task Vision (old) | MediaPipe Tasks Vision (new) |
|--------------------------|------------------------------|
| `org.tensorflow.lite.support.image.TensorImage` | `com.google.mediapipe.framework.image.MPImage` |
| `org.tensorflow.lite.support.image.TensorImage` | `com.google.mediapipe.framework.image.BitmapImageBuilder` |
| `org.tensorflow.lite.task.vision.detector.ObjectDetector` | `com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector` |
| `org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions` | `com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector.ObjectDetectorOptions` |
| (no equivalent) | `com.google.mediapipe.tasks.core.BaseOptions` |
| (no equivalent) | `com.google.mediapipe.tasks.vision.core.RunningMode` |

[VERIFIED: Google AI Edge official Android object detection guide + MediaPipe sample OverlayView.kt]

#### Options Builder Differences

```kotlin
// OLD (TFLite Task Vision)
val options = ObjectDetector.ObjectDetectorOptions.builder()
    .setMaxResults(MAX_RESULTS)
    .setScoreThreshold(SCORE_THRESHOLD)
    .build()
detector = ObjectDetector.createFromFileAndOptions(context, MODEL_FILENAME, options)

// NEW (MediaPipe Tasks Vision)
val baseOptions = BaseOptions.builder()
    .setModelAssetPath(MODEL_FILENAME)
    .build()
val options = ObjectDetector.ObjectDetectorOptions.builder()
    .setBaseOptions(baseOptions)
    .setRunningMode(RunningMode.IMAGE)
    .setMaxResults(MAX_RESULTS)
    .setScoreThreshold(SCORE_THRESHOLD)
    .build()
detector = ObjectDetector.createFromOptions(context, options)
```

Key differences:
- Model path moves from `createFromFileAndOptions(context, filename, options)` to `BaseOptions.setModelAssetPath(filename)` inside the options builder
- Factory method changes from `createFromFileAndOptions()` to `createFromOptions()`
- `RunningMode.IMAGE` must be set explicitly (required for synchronous `detect()`)

[CITED: https://ai.google.dev/edge/mediapipe/solutions/vision/object_detector/android]

#### Inference Call Differences

```kotlin
// OLD (TFLite Task Vision)
val tensorImage = TensorImage.fromBitmap(bitmap)
val results: List<Detection> = d.detect(tensorImage)
results.flatMap { detection ->
    detection.categories.mapNotNull { cat ->
        mapLabel(cat.label.lowercase().trim(), mode)
    }
}

// NEW (MediaPipe Tasks Vision)
val mpImage = BitmapImageBuilder(bitmap).build()
val result: ObjectDetectorResult = d.detect(mpImage)
result.detections().flatMap { detection ->
    detection.categories().mapNotNull { cat ->
        mapLabel(cat.categoryName().lowercase().trim(), mode)
    }
}
```

Critical API differences:
- `TensorImage.fromBitmap(bitmap)` → `BitmapImageBuilder(bitmap).build()`
- `d.detect(tensorImage)` returns `List<Detection>` → `d.detect(mpImage)` returns `ObjectDetectorResult`
- `detection.categories` (property) → `detection.categories()` (method call)
- `category.label` (property) → `category.categoryName()` (method call)
- `category.score` (property) → `category.score()` (method call)

[VERIFIED: MediaPipe sample OverlayView.kt accessed via WebFetch from google-ai-edge/mediapipe-samples]

#### Model Compatibility

The existing `assets/efficientdet_lite0.tflite` downloaded from the TF storage URL (with embedded metadata) is compatible with MediaPipe Tasks Vision. MediaPipe Tasks Vision requires `.tflite` models with embedded metadata — exactly what the TF Task Library model format provides. Both libraries share the same metadata standard.

A dedicated MediaPipe-hosted version of the same model is available at:
```
https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite
```

The existing model file is a strong candidate to work without replacement. However, if initialization fails with the existing model, replace it with the MediaPipe-hosted version. The model file name stays `efficientdet_lite0.tflite` in both cases.

[CITED: https://ai.google.dev/edge/mediapipe/solutions/vision/object_detector — model download section]
[ASSUMED: Existing TF Task Library model with metadata is binary-compatible with MediaPipe Tasks Vision; not confirmed in a single authoritative source. The MediaPipe-hosted replacement is the safe fallback.]

### Stream B: Sensitivity Override Architecture

#### Design Principle

The `Sensitivity` enum remains unchanged and is the authoritative source of defaults. Expert overrides are stored in DataStore as four independent `floatPreferencesKey` values for the Medium tier only. Low and High are derived at read time using fixed absolute offsets.

The override system is additive — no existing code path changes until `FusedMotionMonitor`, `LightMonitor`, `MicrophoneMonitor`, and `CameraAnalyzer` are told to use custom thresholds instead of enum values.

#### Approach: ExpertThresholds data class passed at monitor start

`MonitorService` reads settings once at session start via `.first()`. Add one more read: `settingsRepository.expertThresholds.first()`. This returns an `ExpertThresholds` data class. If all fields are null (no customization), fall back to `Sensitivity.MEDIUM` enum values. If fields are set, use them.

Pass `ExpertThresholds` alongside `Sensitivity` into `FusedMotionMonitor.observe()` and `CameraAnalyzer` constructor. Each monitor computes its own effective threshold: `customMedium ?: sensitivity.enumDefault`.

This keeps monitors decoupled from DataStore — they receive computed values, not a repository.

#### DataStore Key Architecture

```kotlin
// New keys in SettingsRepository (or a dedicated ExpertSettingsRepository)
private val KEY_EXPERT_ACCEL_MEDIUM   = floatPreferencesKey("expert_accel_medium_multiplier")
private val KEY_EXPERT_MIC_MEDIUM     = floatPreferencesKey("expert_mic_medium_db")
private val KEY_EXPERT_LIGHT_MEDIUM   = floatPreferencesKey("expert_light_medium_lux")
private val KEY_EXPERT_CAMERA_MEDIUM  = floatPreferencesKey("expert_camera_medium_fraction")
```

Null (key absent) means "use enum default". Writing null is a `prefs.remove(key)` operation (reset).

#### Low/High Derivation — Absolute Offsets

Fixed absolute offsets from Medium are simpler than percentage-based and produce predictable values across the slider range. Derived values are computed at display time (ExpertSettingsScreen) and at runtime (monitor initialization).

Recommended offsets (based on current enum spread):

| Sensor | Low offset from Medium | High offset from Medium |
|--------|----------------------|------------------------|
| Accelerometer multiplier | +2.0× (less sensitive = higher multiplier) | -1.0× (more sensitive = lower multiplier) |
| Microphone dB | +10 dB | -10 dB |
| Light lux delta | +40 lux | -20 lux |
| Camera motion fraction | +0.10 | -0.05 |

These offsets replicate the existing enum spread (LOW–MEDIUM–HIGH). Importantly, derived Low/High values must be clamped to valid ranges (multiplier ≥ 0.5, dB ≥ 20, lux ≥ 5, fraction 0.01–0.99).

[ASSUMED: Offset values above are derived from existing enum spread. Planner should flag for user confirmation during Expert Settings implementation.]

#### Recommended Default Medium Values (SENSOR-10 fix)

The current `MEDIUM` multiplier is 4.5× — identical to TFLite Task Vision behavior but too conservative. Based on the enum spread and the reported under-sensitivity issue, the new default Medium values should move toward what is currently HIGH:

| Sensor | Current MEDIUM | Proposed new MEDIUM default | Rationale |
|--------|---------------|---------------------------|-----------|
| Accelerometer multiplier | 4.5× | 2.0× | Current HIGH is 2.5×; slightly more sensitive |
| Microphone dB | 60 dB | 55 dB | Mid-point between current MEDIUM (60) and HIGH (50) |
| Light lux delta | 60 lux | 40 lux | Mid-point between current MEDIUM (60) and HIGH (30) |
| Camera motion fraction | 0.12 | 0.08 | Mid-point between MEDIUM (0.12) and HIGH (0.06) |

These new defaults should be reflected directly in the `Sensitivity.MEDIUM` enum constructor arguments (the enum is the reset target), and the existing `Sensitivity.LOW` and `Sensitivity.HIGH` values adjusted proportionally.

[ASSUMED: Recommended threshold values above. User should test and confirm; they are starting points, not validated measurements.]

### Recommended Project Structure Changes

```
app/src/main/java/org/havenapp/main/
├── detection/
│   └── HavenObjectDetector.kt     # Surgical rewrite: swap TFLite API → MediaPipe API
├── sensor/
│   ├── Sensitivity.kt             # Update MEDIUM default values
│   └── ExpertThresholds.kt        # NEW: data class holding nullable custom Medium values
├── storage/
│   └── SettingsRepository.kt      # Add 4 floatPreferencesKey entries + Flow<ExpertThresholds>
├── ui/
│   └── settings/
│       ├── SettingsScreen.kt      # Add "Expert Settings" row to monitoring card
│       ├── SettingsViewModel.kt   # Add expertThresholds StateFlow + setters
│       └── ExpertSettingsScreen.kt  # NEW: 4 sliders + reset button
└── HavenNavGraph.kt               # Add EXPERT_SETTINGS route
```

### Anti-Patterns to Avoid

- **Do not change `CameraAnalyzer`'s public constructor or `HavenObjectDetector`'s public contract.** The migration is internal to `HavenObjectDetector`.
- **Do not introduce a separate `ObjectDetectorHelper` wrapper class.** The existing `HavenObjectDetector` pattern is correct; just swap the internals.
- **Do not use `detectAsync()` or `RunningMode.LIVE_STREAM`.** The current synchronous detection pattern on a throttled basis is correct. `RunningMode.IMAGE` with synchronous `detect()` preserves the existing call pattern.
- **Do not store Low/High overrides in DataStore.** They are derived at runtime from Medium + fixed offsets. Storing them creates consistency bugs.
- **Do not add Expert Settings to `SettingsUiState`.** Expert thresholds are a separate concern. Use separate StateFlows in `SettingsViewModel` or a new `ExpertSettingsViewModel`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Image format conversion for ML | Custom YUV→RGB pipeline for MediaPipe | `BitmapImageBuilder(bitmap).build()` | Already done in `CameraAnalyzer.buildBitmap()` — produces ARGB Bitmap; MediaPipe accepts it directly |
| 16 KB alignment of `.so` files | Manual linker flags or NDK recompile | Switch library — `tasks-vision` ships pre-aligned | The alignment problem is in TFLite's prebuilt `.so`, not in app code |
| Offset clamping | Complex validation logic | `coerceIn(min, max)` on derived values | Kotlin stdlib covers this |
| Slider value display formatting | Custom number formatter | `"%.2f".format(value)` | Sufficient for this use case |

---

## Common Pitfalls

### Pitfall 1: `RunningMode` Not Set → `IllegalArgumentException`

**What goes wrong:** If `RunningMode` is not set in `ObjectDetectorOptions`, MediaPipe throws an exception when `detect()` is called.
**Why it happens:** MediaPipe requires explicit mode declaration. TFLite Task Vision did not have this concept.
**How to avoid:** Always set `.setRunningMode(RunningMode.IMAGE)` in the options builder.
**Warning signs:** Crash in `initialize()` with `IllegalArgumentException` or `RuntimeException` about running mode.

[VERIFIED: MediaPipe Android guide — options builder section]

### Pitfall 2: `ObjectDetectorResult.detections()` vs old `List<Detection>`

**What goes wrong:** Code written against TFLite Task Vision calls `detect()` and expects `List<Detection>`. MediaPipe `detect()` returns `ObjectDetectorResult`; the detections are accessed via `.detections()`.
**How to avoid:** The migration map in Architecture Patterns documents every changed call. Follow it exactly.
**Warning signs:** Kotlin type error on the return value of `d.detect(...)` at compile time.

### Pitfall 3: `category.label` vs `category.categoryName()`

**What goes wrong:** TFLite Task Vision `Category` has a `label` property (Kotlin-style). MediaPipe `Category` has a `categoryName()` method. Code compiles but the method-vs-property distinction causes confusion.
**How to avoid:** Use `category.categoryName()` in all MediaPipe code. Never `category.label`.
**Warning signs:** Kotlin `Unresolved reference: label` compile error.

[VERIFIED: MediaPipe sample OverlayView.kt code pattern from google-ai-edge/mediapipe-samples]

### Pitfall 4: Existing Model File May Need Replacement

**What goes wrong:** The model at `assets/efficientdet_lite0.tflite` was downloaded from the TF Task Library storage URL. Although both libraries use the same `.tflite` + metadata format, a small number of model files have metadata quirks that one runtime handles but the other does not.
**How to avoid:** Test `initialize()` with the existing model after migration. If it fails with a metadata parsing error, replace the file with the MediaPipe-hosted version (different URL, same filename, same COCO model).
**Warning signs:** `initError` populated at app start; `HavenObjectDetector.isAvailable == false`; AppLogger shows init failure.

### Pitfall 5: Expert Slider Medium Value Produces Invalid Derived Low/High

**What goes wrong:** User slides Accelerometer Medium to 0.3×. Derived High = 0.3 − 1.0 = −0.7× (invalid negative).
**How to avoid:** Slider `valueRange` must be bounded so that `Medium − highOffset` stays ≥ minimum valid value. Alternatively, clamp all derived values with `coerceAtLeast(minValidValue)`.
**Warning signs:** Negative or zero thresholds; sensors never trigger or trigger on every frame.

### Pitfall 6: `floatPreferencesKey` Absent vs. Set to Zero

**What goes wrong:** `prefs[KEY_EXPERT_ACCEL_MEDIUM]` returns null when absent and returns 0.0f if someone accidentally writes 0. Treating 0.0f as "not set" causes a zero multiplier which triggers on every sample.
**How to avoid:** Use `?: null` when reading — absent key means "use enum default". Never write 0.0f intentionally. Reset = `prefs.remove(key)`, not `prefs[key] = 0f`.

---

## Code Examples

### MediaPipe ObjectDetector Initialize (Migration of HavenObjectDetector.kt)

```kotlin
// Source: https://ai.google.dev/edge/mediapipe/solutions/vision/object_detector/android
// + https://github.com/google-ai-edge/mediapipe-samples OverlayView.kt

import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult

// --- Initialization ---
val baseOptions = BaseOptions.builder()
    .setModelAssetPath(MODEL_FILENAME)
    .build()
val options = ObjectDetector.ObjectDetectorOptions.builder()
    .setBaseOptions(baseOptions)
    .setRunningMode(RunningMode.IMAGE)
    .setMaxResults(MAX_RESULTS)
    .setScoreThreshold(SCORE_THRESHOLD)
    .build()
detector = ObjectDetector.createFromOptions(context, options)

// --- Inference ---
val mpImage = BitmapImageBuilder(bitmap).build()
val result: ObjectDetectorResult = detector.detect(mpImage)

// --- Result parsing ---
result.detections().flatMap { detection ->
    detection.categories().mapNotNull { cat ->
        mapLabel(cat.categoryName().lowercase().trim(), mode)
    }
}
```

### ExpertThresholds Data Class

```kotlin
// NEW: sensor/ExpertThresholds.kt
// Nullable fields: null = use Sensitivity enum default
data class ExpertThresholds(
    val accelMediumMultiplier: Float? = null,
    val micMediumDb: Float? = null,
    val lightMediumLux: Float? = null,
    val cameraMediumFraction: Float? = null,
) {
    companion object {
        val DEFAULT = ExpertThresholds()
    }
}

// Effective threshold resolution (used at monitor start):
fun Sensitivity.effectiveAccelMultiplier(expert: ExpertThresholds): Float =
    expert.accelMediumMultiplier ?: accelerometerMultiplier

fun Sensitivity.effectiveMicDb(expert: ExpertThresholds): Float =
    expert.micMediumDb ?: microphoneThresholdDb
```

### DataStore Keys for Expert Thresholds

```kotlin
// In SettingsRepository.kt — add to companion object
private val KEY_EXPERT_ACCEL_MEDIUM  = floatPreferencesKey("expert_accel_medium_multiplier")
private val KEY_EXPERT_MIC_MEDIUM    = floatPreferencesKey("expert_mic_medium_db")
private val KEY_EXPERT_LIGHT_MEDIUM  = floatPreferencesKey("expert_light_medium_lux")
private val KEY_EXPERT_CAMERA_MEDIUM = floatPreferencesKey("expert_camera_medium_fraction")

// Flow returning null = use defaults
val expertThresholds: Flow<ExpertThresholds> = dataStore.data.map { prefs ->
    ExpertThresholds(
        accelMediumMultiplier = prefs[KEY_EXPERT_ACCEL_MEDIUM],
        micMediumDb           = prefs[KEY_EXPERT_MIC_MEDIUM],
        lightMediumLux        = prefs[KEY_EXPERT_LIGHT_MEDIUM],
        cameraMediumFraction  = prefs[KEY_EXPERT_CAMERA_MEDIUM],
    )
}

// Reset: remove all keys atomically
suspend fun resetExpertThresholds() {
    dataStore.edit { prefs ->
        prefs.remove(KEY_EXPERT_ACCEL_MEDIUM)
        prefs.remove(KEY_EXPERT_MIC_MEDIUM)
        prefs.remove(KEY_EXPERT_LIGHT_MEDIUM)
        prefs.remove(KEY_EXPERT_CAMERA_MEDIUM)
    }
}
```

### Compose Slider Pattern (Expert Settings Screen)

```kotlin
// Source: https://developer.android.com/develop/ui/compose/components/slider
// Material3 Slider — continuous (steps = 0), no discrete ticks needed

var sliderValue by remember { mutableFloatStateOf(currentMedium) }

Slider(
    value = sliderValue,
    onValueChange = { sliderValue = it },
    onValueChangeFinished = {
        viewModel.setExpertAccelMedium(sliderValue)
    },
    valueRange = 0.5f..5.0f,    // accelerometer example range
    // steps = 0 for continuous (default)
    modifier = Modifier.fillMaxWidth()
)
Text(
    text = "Multiplier: ${"%.1f".format(sliderValue)}×",
    style = MaterialTheme.typography.bodyMedium
)
```

### Slider Ranges Per Sensor

| Sensor | Unit | valueRange | Default Medium |
|--------|------|-----------|----------------|
| Accelerometer | multiplier | 0.5f..8.0f | 2.0f (proposed) |
| Microphone | dB threshold | 30f..80f | 55f (proposed) |
| Light | lux delta | 5f..150f | 40f (proposed) |
| Camera motion | fraction 0–1 | 0.01f..0.30f | 0.08f (proposed) |

All sliders are continuous (steps = 0). Live preview showing derived Low/High labels below each slider.

### 16 KB Alignment Verification

After migration, verify the MediaPipe `.so` is 16 KB aligned:

```bash
# Extract APK
unzip app-debug.apk -d /tmp/haven_apk

# Check alignment of MediaPipe JNI library
SDK_ROOT/ndk/VERSION/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-objdump \
  -p /tmp/haven_apk/lib/arm64-v8a/libmediapipe_tasks_vision_jni.so | grep LOAD

# Expected for 16 KB: "align 2**14"
# Old TFLite output:   "align 2**12" (4 KB = bad)
```

Alternatively, use the `zipalign` check:
```bash
SDK_ROOT/build-tools/35.0.0/zipalign -v -c -P 16 4 app-debug.apk
```

[VERIFIED: https://developer.android.com/guide/practices/page-sizes — verification commands section]

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `tensorflow-lite-task-vision` for on-device ML | `com.google.mediapipe:tasks-vision` | 2022–2023 (MediaPipe Tasks released) | API restructure: `createFromOptions()`, `RunningMode`, `BitmapImageBuilder` |
| 4 KB ELF LOAD alignment default | 16 KB alignment required for Android 16 | Android 16 / Nov 2025 Google Play deadline | Apps with 4 KB `.so` files show warning dialog on Android 16+ devices |
| AGP 8.3-8.5 manual 16 KB opt-in | AGP 8.5.1+ automatic 16 KB alignment for uncompressed `.so` | AGP 8.5.1 | Project uses AGP 8.6.0 → uncompressed `.so` files are already 16 KB aligned by packaging; the problem is the prebuilt library content itself |

**Note on AGP 8.6.0 (project's current version):** AGP ≥ 8.5.1 handles ZIP alignment automatically for uncompressed `.so` files. However, AGP's ZIP alignment is irrelevant when the **ELF LOAD segment alignment inside the `.so` file itself** is wrong (as is the case with TFLite Task Vision 0.4.4). The fix is to use a library that ships a correctly aligned `.so`, not to change AGP version.

[VERIFIED: https://developer.android.com/guide/practices/page-sizes]

---

## Environment Availability

Step 2.6: SKIPPED — Phase 7 is code/dependency changes only. No new external services, CLI tools, or databases are required. The MediaPipe library is a Gradle dependency resolved at build time.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 (unit tests); AndroidX Test (instrumented) |
| Config file | None (default Android project layout) |
| Quick run command | `./gradlew :app:compileDebugKotlin` (~35s) |
| Full suite command | `./gradlew :app:assembleDebug` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| COMPAT-01 | MediaPipe `ObjectDetector` initializes from `efficientdet_lite0.tflite` | Instrumented / manual device test | `./gradlew :app:connectedDebugAndroidTest` | No — Wave 0 |
| COMPAT-01 | `HavenObjectDetector.detect()` returns correct `TriggerType` for known labels | Unit test with mocked detector | `./gradlew :app:testDebugUnitTest` | No — Wave 0 |
| SENSOR-10 | `FusedMotionMonitor` threshold computation with updated MEDIUM enum values | Unit test verifying threshold = noiseFloor × multiplier | `./gradlew :app:testDebugUnitTest` | No — Wave 0 |
| SENSOR-11 | `SettingsRepository.expertThresholds` emits `ExpertThresholds` with null fields when no keys set | Unit test with in-memory DataStore | `./gradlew :app:testDebugUnitTest` | No — Wave 0 |
| SENSOR-11 | `SettingsRepository.resetExpertThresholds()` removes all keys → flow emits all-null | Unit test | `./gradlew :app:testDebugUnitTest` | No — Wave 0 |

**Manual verification required for COMPAT-01:** 16 KB alignment can only be confirmed by running on a device/emulator with Android 16 or 16 KB page size mode. The `llvm-objdump` check confirms library alignment statically.

### Wave 0 Gaps

- `tests/HavenObjectDetectorTest.kt` — unit test for MediaPipe detect() result parsing (mocked ObjectDetectorResult)
- `tests/SettingsRepositoryExpertTest.kt` — DataStore expert threshold read/write/reset using `PreferenceDataStoreFactory` in tests
- `tests/ExpertThresholdsOffsetTest.kt` — verify Low/High derivation logic stays within valid ranges across slider range

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Existing `efficientdet_lite0.tflite` (TF Task Library download) is binary-compatible with MediaPipe Tasks Vision without replacement | Architecture Patterns (Model Compatibility) | If wrong: `initialize()` fails silently → `isAvailable = false`; fix = download MediaPipe-hosted version (easy swap) |
| A2 | Proposed new MEDIUM threshold values (2.0×, 55dB, 40lux, 0.08) fix the under-sensitivity complaint | Architecture Patterns (SENSOR-10 fix) | If wrong: still too insensitive or too many false positives; fix = user adjusts via Expert Settings screen |
| A3 | Absolute fixed offsets (+2.0/−1.0 for accel, +10/−10 for mic, +40/−20 for light, +0.10/−0.05 for camera) produce valid Low/High ranges | Architecture Patterns (Low/High Derivation) | If wrong: edge slider positions produce invalid derived values; fix = adjust offsets or tighten slider valueRange |
| A4 | `com.google.mediapipe:tasks-vision:0.10.29` is the most current stable release | Standard Stack | If newer version exists: prefer newer version; MEDIUM confidence — confirmed from mediapipe-samples repo but not live Maven query |

---

## Open Questions

1. **Should the MEDIUM enum defaults change, or only Expert override defaults?**
   - What we know: SENSOR-10 requires better defaults for MEDIUM. Changing the enum affects all users immediately. Adding expert overrides without changing the enum means expert defaults and reset-to-defaults would restore the too-insensitive MEDIUM.
   - What's unclear: Whether the user wants enum defaults changed (affects users who never open Expert Settings) or only expert UI defaults changed.
   - Recommendation: Change the `Sensitivity.MEDIUM` enum values directly. Expert Settings "Reset to defaults" resets to the new enum values. Both paths improve sensitivity.

2. **Single `SettingsRepository` or separate `ExpertSettingsRepository`?**
   - What we know: `SettingsRepository` already has ~70 lines of companion object keys. Adding 4 more float keys is low impact.
   - Recommendation: Add to existing `SettingsRepository` to avoid a new Hilt binding and injection site.

3. **Should Expert Settings live as a separate route or as a card at the bottom of SettingsScreen?**
   - What we know: ROADMAP says "Settings → Expert contains sliders". ZoneEditorScreen is a separate route accessed via SettingsScreen.
   - Recommendation: Separate route (`EXPERT_SETTINGS`) following the `ZoneEditorScreen` pattern — better navigation UX and screen isn't overcrowded.

---

## Sources

### Primary (HIGH confidence)
- `https://ai.google.dev/edge/mediapipe/solutions/vision/object_detector/android` — MediaPipe Tasks Vision ObjectDetector Android API, options builder, model path, result parsing
- `https://developer.android.com/guide/practices/page-sizes` — 16 KB page size requirement, verification commands, AGP behavior, Google Play deadline
- `github.com/google-ai-edge/mediapipe-samples` (build.gradle + OverlayView.kt) — confirmed version 0.10.29, `categoryName()` / `score()` method names, result iteration pattern
- `app/src/main/java/org/havenapp/main/detection/HavenObjectDetector.kt` — existing code: imports, TFLite API usage, public contract
- `app/src/main/java/org/havenapp/main/sensor/Sensitivity.kt` — current threshold values
- `app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt` — DataStore key pattern to follow

### Secondary (MEDIUM confidence)
- `https://developer.android.com/develop/ui/compose/components/slider` — Compose Slider API, valueRange, steps, onValueChangeFinished
- MediaPipe sample `ObjectDetectorHelper.kt` — `ObjectDetector.createFromOptions()` pattern, `BaseOptions.builder()`, delegate configuration
- WebSearch results confirming Google Play November 2025 16 KB requirement

### Tertiary (LOW confidence)
- A3: Offset values for Low/High derivation — inferred from existing enum spread; no authoritative source
- A4: MediaPipe 0.10.29 as latest version — confirmed from a sample file, not a live Maven query

---

## Metadata

**Confidence breakdown:**
- Stream A (MediaPipe migration): HIGH — API documented in official Google sources, verified code patterns from official samples
- Stream B (DataStore architecture): HIGH — follows exact existing patterns in `SettingsRepository.kt`
- Stream B (threshold values): LOW — inferred starting points; require runtime testing
- 16 KB verification approach: HIGH — verified from official Android developer documentation

**Research date:** 2026-04-12
**Valid until:** 2026-07-12 (MediaPipe versioning moves fast; verify version before execution)
