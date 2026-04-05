# GSD Debug Knowledge Base

Resolved debug sessions. Used by `gsd-debugger` to surface known-pattern hypotheses at the start of new investigations.

---

## camera-motion-detection-not-triggering — CAMERA trigger never fires during active monitoring
- **Date:** 2026-04-04
- **Error patterns:** CAMERA trigger, lumaDiff, cameraMotionThreshold, no trigger, motion detection, CameraAnalyzer, MOTION_ONLY, ImageAnalysis
- **Root cause:** cameraMotionThreshold values in Sensitivity enum were 6–14x too high (LOW=0.22, MEDIUM=0.12, HIGH=0.06). Real-world front-camera lumaDiff during hand wave at <1m produces max ~0.034. Values were theoretical, never calibrated against actual camera output.
- **Fix:** Lowered thresholds to LOW=0.05, MEDIUM=0.03, HIGH=0.01. Also removed per-trigger WARN log from CameraAnalyzer.analyze() to prevent AppLogger ring buffer flooding.
- **Files changed:** app/src/main/java/org/havenapp/main/sensor/Sensitivity.kt, app/src/main/java/org/havenapp/main/media/CameraAnalyzer.kt, app/src/main/java/org/havenapp/main/MonitorService.kt
---

