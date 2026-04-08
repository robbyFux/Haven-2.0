# Codebase Concerns

**Analysis Date:** 2026-03-30

---

## Technical Debt

**Dead code: AccelerometerMonitor and GyroscopeMonitor are unused but retained**
- Files: `app/src/main/java/org/havenapp/main/sensor/AccelerometerMonitor.kt`, `app/src/main/java/org/havenapp/main/sensor/GyroscopeMonitor.kt`
- Impact: Code duplication — `AccelerometerMonitor` contains a near-identical noise-floor algorithm to `FusedMotionMonitor`. Any future change to the algorithm must be done in two places. Both classes are `@Singleton` Hilt-injected but never injected into `MonitorService`.
- Fix approach: Delete both files. They are confirmed as superseded by `FusedMotionMonitor`. If a standalone fallback path is ever needed, extract the noise-floor logic into a shared utility.

**`fallbackToDestructiveMigration()` — all user data silently deleted on schema change**
- File: `app/src/main/java/org/havenapp/main/di/DatabaseModule.kt`, line 22
- Impact: Any future database schema change (adding a column, a new entity) destroys all stored events and triggers without warning. This is a surveillance/security app — losing historical evidence data is a serious UX and trust issue.
- Fix approach: Replace with proper Room migrations as soon as the schema is considered stable. At minimum add a user-facing migration warning before upgrading.

**`exportSchema = false` on Room database — migration tracking disabled**
- File: `app/src/main/java/org/havenapp/main/storage/HavenDatabase.kt`, line 12
- Impact: Room cannot auto-generate migration tests. Schema evolution is effectively blind, compounding the `fallbackToDestructiveMigration` risk above.
- Fix approach: Set `exportSchema = true` and commit the `schemas/` directory. Required if proper migrations are ever added.

**`EventConfig` designed but not implemented — forensic metadata missing**
- Design: CLAUDE.md specifies `EventConfig(sensitivity, detectionMode, aiModel, isZoneActive)` should be saved with every event.
- Files: `app/src/main/java/org/havenapp/main/storage/entity/EventTriggerEntity.kt`, `app/src/main/java/org/havenapp/main/storage/entity/EventEntity.kt`
- Impact: Neither entity has an `EventConfig` column. When reviewing timeline events there is no way to know what sensitivity, model, or zone was active when the event was recorded — exactly the forensic traceability the design calls for.
- Fix approach: Add `EventConfig` fields to `EventTriggerEntity` (or a separate entity) and persist them in `EventRepository.recordTrigger()`.

**Hard-coded WakeLock timeout of 12 hours**
- File: `app/src/main/java/org/havenapp/main/MonitorService.kt`, line 234
- Impact: After 12 hours the WakeLock is silently released and the CPU may sleep, stopping monitoring without notifying the user. For a security/surveillance context this is a hidden reliability failure.
- Fix approach: Use `PARTIAL_WAKE_LOCK` without a timeout (pass 0), or extend/renew the lock periodically, and add a dead-man mechanism that alerts the user if the service stops unexpectedly.

**`TriggerType` enum stores `id` as a separate Int — fragile serialization**
- File: `app/src/main/java/org/havenapp/main/events/TriggerType.kt`
- `EventTriggerEntity.type` stores `TriggerType.id` (an Int). `TriggerType.fromId()` does a linear `entries.find {}` scan on every DB read.
- Impact: If an enum entry is reordered, renamed, or its `id` changed, all historical DB records become corrupted silently. The label display in `EventDetailScreen` falls back to `"Unbekannt (${trigger.type})"` for any unknown ID.
- Fix approach: Use a `@TypeConverter` or store the enum name string (like `SettingsRepository` does for `Sensitivity`). Add a DB migration when changing the type column.

**Notification text is German-only**
- File: `app/src/main/java/org/havenapp/main/MonitorService.kt`, lines 256–258
- The `buildNotification()` function hard-codes German strings (`"Startet in ${_countdownSeconds.value}s"`, `"Kalibrierung läuft…"`, `"Überwachung aktiv"`) instead of using string resources.
- Impact: The app supports DE+EN locale switching; the persistent notification always shows German.
- Fix approach: Replace with `getString(R.string.*)` calls using the appropriate resource IDs.

**`drawOutsideDimOverlay` is a private extension function on `DrawScope` inside a file — discoverable only in `ZoneEditorScreen.kt`**
- File: `app/src/main/java/org/havenapp/main/ui/settings/ZoneEditorScreen.kt`, line 340
- Impact: Low — no reuse risk today, but if dim overlays are needed elsewhere (future multi-zone editor) this logic will be duplicated.

---

## Known Issues

**Race condition: sensor flow can emit events before `currentEventId` is set**
- File: `app/src/main/java/org/havenapp/main/MonitorService.kt`, lines 164–177
- The `sensorFlow` collection starts immediately after calling `observe()`, but `currentEventId` is set only after `delay(calibrationMs)` inside a separate `launch {}`. During the short window between `CALIBRATING` and `ACTIVE`, a late-arriving sensor event (from a fast sensor with a short calibration phase) could be received but silently dropped (`currentEventId?.let { … }` is null). This is the intended design for calibration-phase events, but if calibration ends and `currentEventId` is set fractionally later than the state change, real events can be lost.
- Impact: Low probability, but undetectable in production.

**Camera binding in `MonitorService` uses `cameraProvider.unbindAll()` — conflicts with `ZoneEditorScreen`**
- File: `app/src/main/java/org/havenapp/main/MonitorService.kt`, line 225; `app/src/main/java/org/havenapp/main/ui/settings/ZoneEditorScreen.kt`, line 101
- Both `MonitorService.startCamera()` and `ZoneEditorScreen`'s `DisposableEffect` call `cameraProvider.unbindAll()`. If monitoring is active while the user navigates to the ZoneEditor, the service's camera binding is silently unbound.
- Impact: Starting the zone editor while monitoring is active would break camera-based monitoring without any error. The UI does not prevent this (the ZoneEditor is accessible from MonitorScreen even when `state != IDLE`).
- Fix approach: Guard the ZoneEditorScreen entry with a check that monitoring is IDLE, or use named `UseCaseGroup` bindings instead of `unbindAll()`.

**`MicrophoneMonitor` ignores `warmupMs` parameter entirely**
- File: `app/src/main/java/org/havenapp/main/sensor/MicrophoneMonitor.kt`, line 30
- The `SensorMonitor` interface passes `warmupMs` so monitors can ignore events during calibration. `LightMonitor` and `FusedMotionMonitor` both respect it; `MicrophoneMonitor` starts emitting triggers immediately. This means microphone events can be recorded to the DB during what the user perceives as a calibration-only phase.
- Impact: False triggers in the timeline, particularly at startup when the user is still in the room.

**`SimpleDateFormat` instances created on every recomposition in `TimelineScreen` and `EventDetailScreen`**
- Files: `app/src/main/java/org/havenapp/main/ui/timeline/TimelineScreen.kt`, line 78–79; `app/src/main/java/org/havenapp/main/ui/timeline/EventDetailScreen.kt`, line 99
- Each composable call creates a new `SimpleDateFormat`. With many items in a `LazyColumn` during scroll, this creates GC pressure.
- Fix approach: Hoist formatters to `remember {}` blocks or declare them as top-level constants.

**`PerceptualHashDetector` uses `java.lang.Long.bitCount()` instead of Kotlin stdlib**
- File: `app/src/main/java/org/havenapp/main/detection/PerceptualHashDetector.kt`, line 71
- Minor style inconsistency; use `hash.xor(previousHash).countOneBits()` (Kotlin stdlib, API 31+) or keep `Integer.bitCount()` via Kotlin extension.

**`LuminanceMotionDetector.analyze()` has a signed-byte subtraction bug potential**
- File: `app/src/main/java/org/havenapp/main/media/LuminanceMotionDetector.kt`, line 26
- `luma[i].toInt()` on a Kotlin `Byte` sign-extends; values > 127 become negative. The `and 0xFF` mask is applied correctly in `PerceptualHashDetector` but NOT in `LuminanceMotionDetector`. The expression `luma[i].toInt() - prev[i].toInt()` can produce values in -255..255 but `Math.abs()` handles the range correctly — the bug is that the raw pixel interpretation (0–255 vs -128..127) is inconsistent with the intent. This has not caused visible failures because `Math.abs()` corrects the final comparison, but any future code relying on the raw luma values will interpret them as signed.

---

## Security Concerns

**No media encryption — plaintext files on disk**
- Design intent: CLAUDE.md Phase 5 specifies "Android Keystore Verschlüsselung aller Medien — keine unverschlüsselten Medien persistieren".
- Current reality: `TriggerEvent.mediaPath` is a field, but no media capture code exists yet. When Phase 3 adds audio/photo capture, files will be written to storage. There is no encryption infrastructure in place.
- Files affected when implemented: `app/src/main/java/org/havenapp/main/storage/EventRepository.kt`, any future media-writing code.
- Risk: Camera snapshots and audio clips stored without encryption are accessible to any app with storage permissions or via ADB on unencrypted/rooted devices.
- Fix approach: Use Android Keystore + AES-GCM before writing any media file. Implement encryption helper in `app/src/main/java/org/havenapp/main/storage/` before media capture goes live.

**DataStore settings stored in plaintext**
- File: `app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt`
- Phase 3 will add sensitive settings: backend JWT token, Signal recipient numbers, Mattermost webhook URLs. These are currently planned to go into the same `DataStore<Preferences>` instance, which is plaintext on disk.
- Risk: On a rooted device or via ADB backup, token and phone number data is readable.
- Fix approach: Use `EncryptedFile` + `EncryptedSharedPreferences` (Jetpack Security) or a separate `DataStore` backed by encrypted storage for sensitive fields before Phase 3 settings are added.

**`RECEIVE_BOOT_COMPLETED` permission declared without a BroadcastReceiver**
- File: `app/src/main/AndroidManifest.xml`, line 20
- The permission is declared but no `<receiver>` for boot autostart exists.
- Risk: Low right now. If a BroadcastReceiver is added later without security review, it could be exploited. The declared permission without a receiver also looks suspicious in security audits.
- Fix approach: Remove the permission until the Phase 4 autostart feature is implemented; add it back with the receiver at that point.

**`DiagnosticsViewModel` runs `Runtime.getRuntime().exec(arrayOf("logcat", ...))` — shell injection surface**
- File: `app/src/main/java/org/havenapp/main/ui/diagnostics/DiagnosticsViewModel.kt`, lines 92–95
- The `logcat` command is built from a constant string array with `myPid()`, so there is no user-controlled injection vector currently. However, the pattern of spawning shell processes is fragile: it relies on `logcat` being available and on the process being allowed to read its own logs (which is restricted on API 26+ by default for non-system apps).
- Risk: Logs may silently return empty on production builds without `READ_LOGS` permission. The `runCatching` fallback to `"(logcat unavailable)"` means this fails silently.
- Fix approach: Replace with Timber or a custom in-memory ring buffer that the DiagnosticsScreen reads directly, without spawning a process.

**Notification uses system icon `android.R.drawable.ic_menu_camera`**
- File: `app/src/main/java/org/havenapp/main/MonitorService.kt`, lines 273–274
- This is a stock Android drawable, not a custom app icon. On some API levels this leads to a blank/missing notification icon, and it does not communicate the Haven identity.
- Risk: Low security risk; UX concern — persistent notification is the primary user signal that monitoring is active.

---

## Performance Concerns

**`buildBitmap()` allocates three large buffers per TFLite inference: NV21, JPEG ByteArrayOutputStream, decoded Bitmap**
- File: `app/src/main/java/org/havenapp/main/media/CameraAnalyzer.kt`, lines 170–194
- Each TFLite-triggered frame creates: a `ByteArray(width * height * 3 / 2)` (NV21), a JPEG-encoded `ByteArrayOutputStream` (variable, ~50–150 KB for 640×480 at q=75), and a `Bitmap` decoded from that JPEG.
- The NV21→JPEG→Bitmap pipeline is lossy and unnecessary — TFLite accepts `TensorImage.fromBitmap()` but the conversion can be done directly via `ImageProcessor` in the TFLite Task Library without JPEG encoding.
- Impact: GC pressure during active TFLite inference (throttled to 1500ms, so impact is moderate). The double allocation (JPEG then Bitmap) is avoidable.
- Fix approach: Use `TensorImage.fromBitmap(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))` filled via `Canvas` from the YUV planes, or use `ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888` directly.

**`LuminanceMotionDetector` copies the full luma array on every frame**
- File: `app/src/main/java/org/havenapp/main/media/LuminanceMotionDetector.kt`, line 20
- `previousLuma = luma.copyOf()` allocates a new `ByteArray` (307,200 bytes for 640×480) on every camera frame (typically 15–30 fps).
- Impact: ~5–10 MB/s of short-lived ByteArray allocation. This creates GC pauses on lower-end devices.
- Fix approach: Pre-allocate two alternating buffers and swap references instead of calling `copyOf()` each frame.

**`PerceptualHashDetector` allocates `IntArray(64)` and calls `.average()` on every frame**
- File: `app/src/main/java/org/havenapp/main/detection/PerceptualHashDetector.kt`, lines 39, 57
- A fresh `IntArray(64)` is allocated on every call. `.average()` returns a `Double` and boxes through the stdlib.
- Impact: Minor; 64-element array is tiny, but it runs at camera frame rate.
- Fix approach: Pre-allocate the `blockAvg` array as a class field; compute mean with a direct integer sum.

**`FusedMotionMonitor` accumulates warmup samples into an unbounded `mutableListOf<Float>()`**
- File: `app/src/main/java/org/havenapp/main/sensor/FusedMotionMonitor.kt`, line 61
- At `SENSOR_DELAY_NORMAL` (~5 Hz), a 30-second calibration period collects ~150 samples — trivial. But at `SENSOR_DELAY_GAME` or if someone sets a very long calibration time, the list grows proportionally.
- Impact: Low for current settings (max 30s calibration = ~150 samples). No immediate risk.

**`MonitorService.startCamera()` runs on `mainExecutor` but camera analyzer runs on `cameraExecutor`**
- File: `app/src/main/java/org/havenapp/main/MonitorService.kt`, lines 215–228
- `cameraProviderFuture.addListener({...}, mainExecutor)` means the `cameraProvider.bindToLifecycle()` call happens on the main thread. This is correct per CameraX requirements, but `runCatching { cameraProvider.unbindAll(); cameraProvider.bindToLifecycle(...) }` silently swallows any binding exception — if camera binding fails (e.g., permission revoked after startup) there is no user feedback and no retry.

---

## Missing Features / Gaps

**No media capture — `TriggerEvent.mediaPath` is always null**
- File: `app/src/main/java/org/havenapp/main/events/TriggerEvent.kt`, line 19
- The `mediaPath` field exists in both `TriggerEvent` and `EventTriggerEntity`, but nothing writes to it. Phase 3 notification attachments require captured photos/audio clips. The `EventDetailScreen` does not render media either (no image/audio player).

**No NotificationEngine — no alerts leave the device**
- Current state: Zero notification channels implemented. `LocalPushChannel`, `SignalRestChannel`, `MattermostChannel`, `NotificationRouter`, `NotificationRule` — all defined in CLAUDE.md design but absent from the source tree.
- Impact: The entire alert/notification value proposition of Haven 2.0 is unimplemented. The app detects events but cannot notify anyone.
- Phase: Phase 3.

**No media playback in EventDetail**
- File: `app/src/main/java/org/havenapp/main/ui/timeline/EventDetailScreen.kt`
- The screen shows trigger metadata but there is no clip player, image viewer, or audio playback. Even if media were captured, the UI has no way to show it.

**No event/data deletion UI**
- `EventDao.deleteAll()` exists but is never called from any UI. Users cannot delete individual events, clear the timeline, or export data. Phase 5 plans export/delete functions but there is no intermediate solution.

**No autostart after reboot**
- `RECEIVE_BOOT_COMPLETED` permission declared in `AndroidManifest.xml` but no `BroadcastReceiver` is registered. Planned for Phase 4.

**`CAMERA_LINGER` (12) and `CAMERA_ABSENT` (13) trigger types defined but have no detection logic**
- File: `app/src/main/java/org/havenapp/main/events/TriggerType.kt`, lines 25–26
- These are present as enum values and have labels in `EventDetailScreen`, but `CameraAnalyzer` never emits them. If they appear in the DB (e.g., via import from a future version), they are displayable but currently dead.

**`PRESSURE` (3), `POWER` (5), `BUMP` (6), `CAMERA_VIDEO` (7), `HEART` (8) trigger types defined but have no monitors**
- These TriggerTypes are preserved from Haven 0.2.1 for DB compatibility, but no monitors emit them in the current codebase.

**Barometric pressure sensor (PRESSURE trigger type) never monitored**
- The original Haven 0.2.1 monitored barometric pressure as a tamper signal. Haven 2.0 defines `TriggerType.PRESSURE` but has no `PressureMonitor`.

**No Schedule/Zeitplan system**
- Phase 3 feature. The `SettingsRepository` has no schedule-related keys. There is no cron-like or time-range-based arming logic.

**`supportsRtl = false` — RTL languages not supported**
- File: `app/src/main/AndroidManifest.xml`, line 41
- Explicitly disabled. The app currently supports DE+EN only, so this is acceptable short-term, but is a hard blocker for any Arabic, Hebrew, or Farsi locale addition.

---

## Dependency Risks

**`tflite-task-vision` version 0.4.4 — significantly outdated**
- File: `gradle/libs.versions.toml`, line 10
- The TFLite Task Vision library version 0.4.4 was released in 2022. The current stable is 0.4.4 (task library) but the recommended migration path is to TFLite's new `Interpreter` API or Google AI Edge / MediaPipe Tasks (which supersedes the Task Library as of 2024).
- Impact: The Task Library API (`ObjectDetector.createFromFileAndOptions()`) is in maintenance mode. Future EfficientDet model variants may require the newer API.
- Risk: Low short-term; medium long-term if the Task Library is deprecated officially.

**Compose BOM 2024.09.03 — ~6 months behind at analysis date**
- File: `gradle/libs.versions.toml`, line 5
- Current Compose BOM is 2025.x. Some Material3 APIs used in the app are marked `@OptIn(ExperimentalMaterial3Api::class)` and may have changed signatures.
- Risk: Low until a BOM update is attempted; at update time, `@ExperimentalMaterial3Api` usages in `ZoneEditorScreen.kt`, `SettingsScreen.kt`, `TimelineScreen.kt`, `EventDetailScreen.kt`, and `DiagnosticsScreen.kt` may require API changes.

**AGP 8.5.2 and Kotlin 2.0.20 — stable versions but not latest at analysis date**
- File: `gradle/libs.versions.toml`, lines 1–2
- Not a concern for current functionality, but any ProGuard/R8 rules in `proguard-rules.pro` should be verified when upgrading AGP past 8.6.

**`material-icons-extended` pulled as full dependency**
- File: `gradle/libs.versions.toml` and `app/build.gradle.kts`, line 84
- This artifact contains all Material icons (~8 MB AAR). Only a handful of icons are used (`Shield`, `Check`, `CropFree`, `ArrowBack`). With `isMinifyEnabled = true` in release builds, R8 removes unused resources, so the release APK is unaffected. Debug builds are bloated.

---

## Architectural Smells

**`MonitorService.Companion` holds mutable global state (`MutableStateFlow`s)**
- File: `app/src/main/java/org/havenapp/main/MonitorService.kt`, lines 58–68
- `_state`, `_countdownSeconds`, `_calibrationSecondsRemaining`, `_calibrationResults` are `private val` companion object members — effectively process-level singletons. Any class anywhere can read `MonitorService.state` without going through DI.
- Impact: Makes the service state untestable (cannot inject a fake). `MonitorViewModel`, `DiagnosticsViewModel`, and `MonitorScreen` all read from `MonitorService.calibrationResults` directly, bypassing the ViewModel layer. If the service is ever replaced or refactored, all consumers must be updated manually.
- Fix approach: Extract service state into a `MonitorStateRepository` singleton injected via Hilt, so ViewModels receive state through normal DI rather than through a static companion.

**`CameraAnalyzer` is constructed manually (`CameraAnalyzer(...)`) inside `MonitorService`, bypassing Hilt**
- File: `app/src/main/java/org/havenapp/main/MonitorService.kt`, line 152
- `CameraAnalyzer` takes dependencies (`HavenObjectDetector`, `DetectionZone?`) but is instantiated with `new`-style construction. This means `CameraAnalyzer` cannot itself be injected or mocked in tests.
- Fix approach: Convert `CameraAnalyzer` to a factory (`@AssistedInject` with `@AssistedFactory`) so it participates in the DI graph while still accepting runtime parameters.

**Notification channel text in German hardcoded in service — i18n violation**
- File: `app/src/main/java/org/havenapp/main/MonitorService.kt`, lines 244, 256–258
- Three user-visible strings bypass the string resource system: `"Aktive Überwachungssession"`, `"Startet in ${...}s"`, `"Kalibrierung läuft…"`, `"Überwachung aktiv"`.

**`EventDetailScreen` and `TimelineScreen` contain hardcoded German UI strings**
- Files: `app/src/main/java/org/havenapp/main/ui/timeline/EventDetailScreen.kt`, lines 141–164
- `TriggerType.label()` and `Severity.label()` return German strings directly, not via string resources. This is inconsistent with all other UI strings which use `stringResource(R.string.*)`.
- Impact: These labels do not respond to locale switching.

**`TriggerType.label()` defined in UI layer, not in domain layer**
- File: `app/src/main/java/org/havenapp/main/ui/timeline/EventDetailScreen.kt`, line 140
- The mapping from `TriggerType` to a display label is defined as a private extension function inside a screen file. If any other screen ever needs to display a trigger type label (e.g., a notification, a share export), the logic must be duplicated or the function made internal/public.
- Fix approach: Move label mapping to `TriggerType` itself or to a dedicated resource mapper, using `@StringRes` IDs.

**`SettingsScreen` contains both `countdownLabel()` and `calibrationLabel()` composable functions that duplicate the RadioButton pattern**
- File: `app/src/main/java/org/havenapp/main/ui/settings/SettingsScreen.kt`, lines 234–245
- `calibrationLabel()` has a `5, 10, 20, 30` when-expression that must be kept in sync with `CALIBRATION_OPTIONS = listOf(5, 10, 20, 30)`. Adding a new option requires updating both. The else-branch falls back to a generic "X seconds" string that doesn't match the specific R.string resources.

**Zero test coverage**
- There are no test files anywhere in the project (`src/test/` and `src/androidTest/` directories do not exist).
- Impact: Every component — `SensorFusionEngine`, `PerceptualHashDetector`, `LuminanceMotionDetector`, `DetectionZone.toPixelBounds()`, `DetectionZone.fromString()`, `TriggerType.fromId()`, `EventRepository` — is unverified. Regressions in detection logic will not be caught until runtime.
- The signed-byte issue in `LuminanceMotionDetector` and the `TriggerType.fromId()` linear scan are examples of bugs that would be trivially caught by unit tests.
- Fix approach: Start with pure unit tests for `SensorFusionEngine`, `PerceptualHashDetector`, `LuminanceMotionDetector`, and `DetectionZone`. These have no Android dependencies and run on JVM.

---

*Concerns audit: 2026-03-30*
