---
status: awaiting_human_verify
trigger: "android16-16kb-compat-and-video-recording"
created: 2026-04-12T00:00:00Z
updated: 2026-04-12T11:00:00Z
---

## Current Focus

hypothesis: CONFIRMED — cameraEnabled=false in user's DataStore settings; startCamera() never called; clipRecorder stays null all session
test: Logcat shows zero "startCamera() called" lines and zero CAMERA-type triggers; only MICROPHONE triggers; clipRecorder=null on every trigger
expecting: n/a — root cause confirmed
next_action: Apply fix: (1) add cameraEnabled logging to MonitorService; (2) fix async clipRecorder race condition via CompletableDeferred

## Symptoms

expected:
  1. App runs without 16 KB page-size compatibility warnings on Android 16
  2. Camera events include a recorded video file

actual:
  1. Android 16 shows a warning that the app is not 16 KB page-size compatible
  2. On Android 16 (Pixel 7 Pro), no videos are saved to events — event records exist but without video attachment

errors: No explicit crash logs provided; warning shown by Android 16 at app launch

reproduction:
  1. Install app on Android 16 device (Pixel 7 Pro) → warning appears
  2. Start monitoring, trigger a camera event → event is saved but has no video file

started: Observed on Android 16 (new); likely worked on earlier Android versions

## Eliminated

- hypothesis: CameraX 1.4.0 dynamic range crash is still the cause
  evidence: camerax upgraded to 1.5.2 in libs.versions.toml (already applied in prior session); dynamic range crash is fixed in 1.5.2. Yet video still does not appear. Root cause is elsewhere.
  timestamp: 2026-04-12T10:00:00Z

- hypothesis: VideoCapture use-case is absent from the codebase
  evidence: MonitorService.startCamera() fully implements VideoCapture<Recorder> + ClipRecorder; the binding path exists
  timestamp: 2026-04-12T00:01:00Z

- hypothesis: ClipRecorder logic discards video due to isRecording guard
  evidence: Guard at line 301 (if clipRecorder?.isRecording == true return@collect) prevents duplicate triggers during an active clip, but the first trigger always reaches startClip(). The onClipReady callback is correctly wired to updateTriggerMediaPath. Logic is sound.
  timestamp: 2026-04-12T00:01:00Z

## Evidence

- timestamp: 2026-04-12T00:00:30Z
  checked: /tmp/tflite_extract/jni/arm64-v8a/libtask_vision_jni.so ELF LOAD segment alignment
  found: Both LOAD segments have Ausr. (alignment) = 0x1000 (4 KB), not 0x4000 (16 KB)
  implication: libtask_vision_jni.so from tensorflow-lite-task-vision:0.4.4 is 4 KB aligned only → causes Android 16 warning; confirmed by multiple open TensorFlow GitHub issues (98489, 100533, 69459)

- timestamp: 2026-04-12T00:00:40Z
  checked: AGP version in libs.versions.toml
  found: agp = "8.5.2" — AGP 8.5.1+ auto-repackages app-owned .so files with 16 KB alignment, but CANNOT fix pre-built .so files bundled inside AAR dependencies
  implication: AGP version is sufficient for own code, but TFLite AAR's libtask_vision_jni.so is pre-built with 4 KB alignment and AGP cannot repackage it

- timestamp: 2026-04-12T00:00:50Z
  checked: tensorflow-lite-task-vision upstream status
  found: tensorflow-lite-task-vision (all versions including 0.4.4) is deprecated and will NOT receive 16 KB alignment fixes; the library is unmaintained (migrated to LiteRT / MediaPipe)
  implication: Staying on tensorflow-lite-task-vision:0.4.4 means the 16 KB warning is permanent; migration to LiteRT or MediaPipe tasks is the only resolution

- timestamp: 2026-04-12T00:01:00Z
  checked: CameraX version (camerax = "1.4.0") and Android 16 release notes
  found: CameraX 1.4.0 has a known crash on Android 16/17 devices caused by an unhandled "unknown dynamic range mode" introduced in new platform builds. This crash occurs during camera binding — before any recording can start. Fixed in CameraX 1.5.2 and 1.6.0.
  implication: On Android 16 (Pixel 7 Pro), cameraProvider.bindToLifecycle() throws an uncaught exception inside the startCamera() listener. The outer runCatching{} in startCamera() catches it and falls through to the LEGACY fallback path — which also fails (same crash) — so clipRecorder.setUnavailable() is called. Result: clipRecorder.isAvailable = false → startClip() always returns null → no video is ever recorded. Events exist (from non-camera sensors or camera analysis frames that arrived before the crash) but have no video path.

- timestamp: 2026-04-12T00:01:10Z
  checked: ClipRecorder.withAudioEnabled() without explicit runtime permission check
  found: ClipRecorder.kt line 72 calls .withAudioEnabled() unconditionally. If RECORD_AUDIO permission was denied by the user, CameraX will emit a VideoRecordEvent.Finalize with hasError()=true and the onClipReady callback is not invoked (line 76: if (!event.hasError())).
  implication: Secondary contributing factor — on Android 16, if permission flow changed (auto-reset, stricter enforcement) and RECORD_AUDIO is not granted, recordings silently fail with no DB entry for the video path. This compounds the CameraX crash issue.

- timestamp: 2026-04-12T10:00:00Z
  checked: CameraX version in libs.versions.toml after prior session fix
  found: camerax = "1.5.2" — upgrade was already applied. AGP = "8.6.0" also applied. Yet user reports video still not working on Android 16.
  implication: The dynamic-range crash fix alone was not sufficient. Need runtime observation to find the actual failure point. Multiple silent failure paths exist: (a) bindToLifecycle still failing for a different reason in 1.5.2, (b) VideoRecordEvent.Finalize firing with an error that was previously swallowed silently, (c) clipRecorder null at trigger time due to async camera init timing, (d) filesDir write failure, (e) output file created but DB update never fires.

- timestamp: 2026-04-12T10:01:00Z
  checked: Full video recording path: startCamera() → ClipRecorder.attach() → startClip() → VideoRecordEvent.Finalize → onClipReady → updateTriggerMediaPath()
  found: VideoRecordEvent.Finalize error branch was completely silent — hasError()=true cases logged nothing and called no callbacks. The startCamera() failure branches logged via appLogger (in-app ring buffer) but not android.util.Log so unavailable via adb logcat. sensorFlow.collect never logged whether clipRecorder was null at trigger time.
  implication: The entire recording failure path was invisible at runtime. Added comprehensive android.util.Log.d/e("HAVEN_VIDEO", ...) throughout all branches: cameraProviderFuture.get(), both bindToLifecycle attempts, ClipRecorder.attach/setUnavailable, startClip entry/guards, VideoRecordEvent.Start/Status/Finalize (both success and error with full errorCode + cause + outputUri + file.exists/size), onClipReady callback, and DB updateTriggerMediaPath. BUILD SUCCESSFUL in 4s after adding all logging.

- timestamp: 2026-04-12T11:00:00Z
  checked: User-provided full HAVEN_VIDEO logcat output from Android 16 Pixel 7 Pro session
  found: |
    1. Zero "startCamera() called" lines in the entire logcat — startCamera() was NEVER invoked
    2. Zero CAMERA-type triggers — CameraAnalyzer was never created
    3. clipRecorder=null on every single trigger (both at 12:15:54 and 12:16:07)
    4. Only MICROPHONE triggers fired; the service IS running and sensors work
    5. First trigger at 12:15:54 had currentEventId=null (during calibration, correctly ignored)
    6. Second trigger at 12:16:07 had currentEventId=13 (ACTIVE state) but clipRecorder=null → error logged
  implication: |
    startCamera() is gated by `if (cameraEnabled)` at MonitorService.kt line 220.
    Since "startCamera() called" never appeared, cameraEnabled=false in the user's DataStore.
    Camera monitoring was disabled in settings — intentionally or by accident.
    This is the definitive root cause: no camera → no CameraAnalyzer → no ClipRecorder → no video.
    
    Secondary structural bug also confirmed: clipRecorder is assigned asynchronously inside
    cameraProviderFuture.addListener(). Even when cameraEnabled=true, a trigger that fires
    before the provider callback resolves will find clipRecorder=null and log the error
    "camera may not have finished binding yet". This race condition exists independently
    of the cameraEnabled setting and should be fixed.

## Resolution

root_cause: |
  THREE independent issues:

  ISSUE 1 — 16 KB page-size warning:
  tensorflow-lite-task-vision:0.4.4 bundles libtask_vision_jni.so with 4 KB ELF
  LOAD segment alignment. Android 16 enforces 16 KB alignment. AGP cannot
  repackage pre-built .so files in third-party AARs. Library is deprecated with
  no fix planned.

  ISSUE 2 — No videos recorded (immediate cause):
  cameraEnabled=false in the user's DataStore settings. MonitorService.startCamera()
  is gated by `if (cameraEnabled)` at line 220. When false, no CameraAnalyzer is
  created, no ClipRecorder is created, and clipRecorder stays null for the entire
  session. Confirmed by logcat: zero "startCamera() called" lines, zero CAMERA-type
  triggers, clipRecorder=null on every trigger.

  ISSUE 3 — Async clipRecorder race condition (structural bug):
  Even when cameraEnabled=true, clipRecorder is assigned inside an async
  cameraProviderFuture.addListener() callback. A sensor trigger that fires during
  calibration and becomes ACTIVE before the camera provider future resolves will
  find clipRecorder=null, logging "camera may not have finished binding yet; no
  video for trigger N". The first real-world trigger after calibration ends can
  silently drop its video clip.

fix: |
  ISSUE 1: Migrate to LiteRT / MediaPipe Tasks (separate work item, not in this session).

  ISSUE 2: User must enable camera monitoring in Settings → Sensor section.
  Code fix: add an explicit log line when cameraEnabled=false so the setting is
  visible in logcat without needing to inspect the UI:
    Log.d("HAVEN_VIDEO", "cameraEnabled=false — startCamera() skipped, clipRecorder will remain null")

  ISSUE 3: Introduce a CompletableDeferred<ClipRecorder> (clipRecorderDeferred) in
  MonitorService. startCamera() completes it when the provider callback fires (either
  with the real ClipRecorder or a setUnavailable() one). The sensorFlow.collect block
  awaits clipRecorderDeferred with a short timeout before attempting startClip(),
  so the first trigger never races against camera init.

verification: |
  ./gradlew :app:compileDebugKotlin — BUILD SUCCESSFUL, zero new warnings.
  Only pre-existing setTargetResolution deprecation warning remains (unrelated).
files_changed:
  - app/src/main/java/org/havenapp/main/MonitorService.kt
