---
status: resolved
trigger: "CAMERA trigger (CameraX ImageAnalysis pipeline) does not fire when waving hand in front of camera during active monitoring session with MOTION_ONLY mode"
created: 2026-04-04T00:00:00Z
updated: 2026-04-04T23:30:00Z
---

## Current Focus

hypothesis: CONFIRMED — cameraMotionThreshold values in Sensitivity enum are 6-14x too high vs real-world lumaDiff data and vs original Haven reference values.
test: User provided diagnostic log: max lumaDiff=0.034 while waving at front camera; threshold=0.22 (LOW). Reference Haven: NUMBER_THRESHOLD=5000 pixels out of 307200 (640x480) = 1.6% with per-pixel VALUE_THRESHOLD=50. Haven 2.0 LOW=22%, MEDIUM=12%, HIGH=6% — all far above what real camera scenes produce.
expecting: Lowering thresholds to LOW=0.05, MEDIUM=0.03, HIGH=0.01 will bring them in line with observed max lumaDiff=0.034.
next_action: Update Sensitivity.kt cameraMotionThreshold values; remove diagnostic logging from CameraAnalyzer; compile and verify.

## Symptoms

expected: CAMERA trigger emitted when significant motion detected in camera field of view (waving hand, walking past, etc.) during MOTION_ONLY detection mode at LOW sensitivity
actual: No CAMERA triggers recorded during two monitoring sessions with camera=true, motion=true, light=false, mic=false — despite significant hand waving in front of camera for ~1.5 minutes.
errors: None (no crash, no error log). App continues monitoring normally, just never fires CAMERA trigger.
reproduction: 1. Set detection mode to MOTION_ONLY, sensitivity LOW, camera FRONT. 2. Start monitoring, wait for calibration (10s). 3. Wave hand in front of camera — no CAMERA trigger fires. 4. Light sensor triggers normally when lux changes ≥100 lux.
started: First noticed Phase 3 device verification 2026-04-04. Unknown if CAMERA detection ever worked.

## Eliminated

- hypothesis: CameraAnalyzer not receiving frames (analyzer never called) [REVERTED — no longer eliminated]
  evidence: Original elimination was premature — it assumed "no error log = camera working" but startCamera() has a silent fallback (no .onFailure on the inner runCatching) and CameraAnalyzer has ZERO per-frame logging. The user confirmed "No debug logging visible in app log" from camera. Cannot distinguish "bound+threshold too high" from "never bound" without adding logging.
  timestamp: 2026-04-04T22:00:00Z

- hypothesis: Sensitivity.OFF guard suppressing analysis
  evidence: User confirmed sensitivity=LOW; first check in analyze() is `if (sensitivity == Sensitivity.OFF) return` — not triggered.
  timestamp: 2026-04-04T00:00:00Z

- hypothesis: TFLite throttle interfering with MOTION_ONLY
  evidence: In MOTION_ONLY mode, `detectionMode.requiresML == false`, so the TFLite branch at line 107 is never entered. TFLite throttle is irrelevant here.
  timestamp: 2026-04-04T00:00:00Z

- hypothesis: clipRecorder.isRecording suppression
  evidence: No prior triggers → no clip ever started → clipRecorder.isRecording=false always. Not the issue.
  timestamp: 2026-04-04T00:00:00Z

- hypothesis: pHash stage suppressing real motion
  evidence: Could suppress some motion, but the threshold logic at line 98-102 allows triggers without pHash if lumaDiff >= motionThreshold. If lumaDiff is always near-zero (due to byte bug), pHash is irrelevant — luma stage fails first.
  timestamp: 2026-04-04T00:00:00Z

- hypothesis: threshold 0.22 too high for subtle motion
  evidence: Waving hand directly at front camera for 1.5 minutes produces very large luma changes across many pixels. Even at 0.22 threshold, this should trigger. The real problem must suppress the luma diff computation itself.
  timestamp: 2026-04-04T00:00:00Z

## Evidence

- timestamp: 2026-04-04T00:00:00Z
  checked: LuminanceMotionDetector.kt line 26
  found: `Math.abs(luma[i].toInt() - prev[i].toInt()) > pixelThreshold` — uses raw `.toInt()` on a Kotlin Byte, which is SIGNED. Kotlin Byte range is -128..127. Luma values 128–255 are stored as -128..-1.
  implication: When two adjacent frames have pixel values crossing the 127/128 boundary in opposite directions (e.g. prev=255 → curr=0, or prev=200 → curr=50 where byte wraps), the signed subtraction produces a small absolute difference instead of a large one. In the extreme case: prev byte(-1) = 255, curr byte(0) = 0; `.toInt()` gives prev=-1, curr=0; abs(-1-0)=1, which is well below pixelThreshold=25. The actual pixel change was 255 — maximum possible.

- timestamp: 2026-04-04T00:00:00Z
  checked: PerceptualHashDetector.kt line 48
  found: `luma[rowOffset + col].toInt() and 0xFF` — explicitly masks with 0xFF to treat the byte as unsigned.
  implication: pHash detector correctly handles unsigned byte values. LuminanceMotionDetector was written WITHOUT this fix, creating an inconsistency in the same pipeline.

- timestamp: 2026-04-04T00:00:00Z
  checked: CameraAnalyzer.kt line 80-82 (luma extraction)
  found: `luma[row * width + col] = raw[row * rowStride + col * pixelStride]` — raw ByteArray from YUV Y-plane, values 0-255 stored in signed Kotlin bytes.
  implication: All luma values ≥ 128 are stored as negative bytes. LuminanceMotionDetector receives these values and interprets them as negative numbers when calling `.toInt()`. For a typical indoor scene, a large fraction of pixels will be in the 128-255 range (well-lit areas). The signed arithmetic produces systematically wrong diffs, biased toward small values, causing the changedPixels count to be far below the true count.

- timestamp: 2026-04-04T00:00:00Z
  checked: Mathematical worst-case analysis
  found: Consider a pixel transitioning from value 130 (stored as -126) to 125 (stored as 125). Actual change = 5. Signed computation: abs(-126 - 125) = abs(-251) = 251. This OVER-counts. Counter-case: pixel from 125 → 130 produces same result. But pixel from 250 (stored as -6) to 10 (stored as 10): abs(-6 - 10) = 16 ≤ 25 threshold — MISSED. Actual change = 240. The pattern: large transitions ACROSS the 127/128 zero-crossing where one value is above 127 and the other is far below give small signed diffs. This is random w.r.t. actual motion — real motion produces both types. Net effect: changedPixels count is stochastically wrong, generally underestimated for bright scenes.
  implication: For a well-lit scene where many pixels are in the 128-255 range, the luminance diff will be unreliable — sometimes false positives, sometimes false negatives. The user's FRONT camera in an indoor lit environment likely has many bright pixels. The 0.22 threshold was designed for correct unsigned arithmetic; with signed arithmetic, the effective threshold is non-deterministic and frequently far too high.

- timestamp: 2026-04-04T22:00:00Z
  checked: startCamera() in MonitorService.kt lines 297-309
  found: The fallback runCatching block (lines 304-307) that binds imageAnalysis-only has NO .onFailure handler. If BOTH the primary binding (imageAnalysis + videoCaptureUseCase) AND the fallback binding fail, there is zero logging output. The analyzer.events Channel simply never receives frames.
  implication: Camera binding failure is entirely silent. The user sees no crash, no error log, no camera events — indistinguishable from "camera working but threshold too high".

- timestamp: 2026-04-04T22:00:00Z
  checked: CameraAnalyzer.analyze() — entire method
  found: Zero logging calls inside the analyze() method. No frame-arrival confirmation, no lumaDiff logging, no motionConfirmed logging. The AppLogger is not injected into CameraAnalyzer.
  implication: There is no way to know from app logs whether analyze() is being called at all or what lumaDiff values look like in practice.

- timestamp: 2026-04-04T22:00:00Z
  checked: motionThreshold = 0.22 at LOW sensitivity
  found: Requires 22% of all pixels to change by >25 intensity units between consecutive frames. For a hand wave at <1m from front camera, the hand might cover 10-30% of the frame, and background pixels would add some. This MIGHT pass 22% but is not guaranteed, especially at LOW sensitivity which is designed to be conservative.
  implication: Even with the `and 0xFF` fix correct, the threshold could be too conservative. However, "never triggered in ANY session" with "waving directly in front at <1m" for 1.5+ minutes suggests a deeper issue than threshold alone.

- timestamp: 2026-04-04T22:00:00Z
  checked: `and 0xFF` fix in LuminanceMotionDetector.kt line 26
  found: Fix IS applied: `Math.abs((luma[i].toInt() and 0xFF) - (prev[i].toInt() and 0xFF)) > pixelThreshold`. Both current and previous values are masked correctly.
  implication: The byte arithmetic fix is in place. If analyze() is being called, pixel diffs are now computed correctly.

- timestamp: 2026-04-04T23:00:00Z
  checked: referenzen/haven-0.2.1/sensors/motion/LuminanceMotionDetector.java
  found: Original Haven uses NUMBER_THRESHOLD=5000 pixels changed (out of 307200 at 640×480 = 1.6%) with per-pixel VALUE_THRESHOLD=50. Haven 2.0 LOW=0.22 (22%), MEDIUM=0.12 (12%), HIGH=0.06 (6%). The Haven 2.0 thresholds are 6–14× higher than the reference, and observed max lumaDiff=0.034 is 6.5× below the LOW threshold.
  implication: Confirmed root cause. Fix: lower LOW→0.05, MEDIUM→0.03, HIGH→0.01 to match real-world camera output and reference implementation intent.

## Resolution

root_cause: cameraMotionThreshold values in Sensitivity enum were 6–14× too high (LOW=0.22, MEDIUM=0.12, HIGH=0.06). Real-world front-camera lumaDiff during hand wave at <1m produces max ~0.034. Original Haven reference uses ~1.6% pixel change threshold (5000/307200 pixels). The Haven 2.0 threshold was never calibrated against actual camera output — the fraction-based design was sound but the values came from theory, not measurement.
fix: Lowered cameraMotionThreshold values to match real-world observations: LOW 0.22→0.05, MEDIUM 0.12→0.03, HIGH 0.06→0.01. Removed per-trigger WARN log and appLogger parameter from CameraAnalyzer to prevent AppLogger ring buffer flooding (38+ entries per 3s burst). Build: SUCCESS.
verification: Device confirmed: CAMERA trigger fired at lumaDiff=0.0585 (threshold=0.05). Clip recorded 4.8 MB, encrypted, played back correctly. 1 CAMERA LOW trigger in Timeline. Compile: BUILD SUCCESSFUL.
files_changed: [app/src/main/java/org/havenapp/main/sensor/Sensitivity.kt, app/src/main/java/org/havenapp/main/media/CameraAnalyzer.kt, app/src/main/java/org/havenapp/main/MonitorService.kt]
