# GSD Debug Knowledge Base

Resolved debug sessions. Used by `gsd-debugger` to surface known-pattern hypotheses at the start of new investigations.

---

## cloud-ai-analysis-webui-and-heartbeat-timing — AI settings missing from webui; CloudChannel heartbeat dead code
- **Date:** 2026-04-13
- **Error patterns:** AI_BACKEND, OpenRouter, webui, admin, heartbeat, CloudChannel, sendHeartbeat, SettingsRepository, no endpoint
- **Root cause:** (1) AI analysis pipeline built in Phase 5 with no webui configuration page added in Phase 6 — AI_BACKEND/OPENROUTER_API_KEY/OPENROUTER_MODEL were env-var-only. (2) CloudChannel.sendHeartbeat() implemented but never called: no KEY_HEARTBEAT_CLOUD_MIN in SettingsRepository, no coroutine in MonitorService, no /heartbeat server endpoint.
- **Fix:** (1) Added AISettings Django managed singleton model + AISettingsForm + ai_settings view + /admin/ai-settings/ URL + templates. (2) Added KEY_HEARTBEAT_CLOUD_MIN to SettingsRepository, heartbeat coroutine in MonitorService, POST /api/v1/devices/heartbeat FastAPI endpoint (verify_app_key handles last_seen_at, returns 204).
- **Files changed:** server/webui/admin_panel/models.py, server/webui/admin_panel/forms.py, server/webui/admin_panel/views.py, server/webui/admin_panel/urls.py, server/webui/admin_panel/templates/admin_panel/ai_settings.html, server/webui/admin_panel/templates/admin_panel/partials/ai_settings_form.html, server/webui/admin_panel/templates/admin_panel/dashboard.html, server/webui/tests/test_ai_settings.py, app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt, app/src/main/java/org/havenapp/main/MonitorService.kt, server/app/routers/devices.py, server/tests/test_devices.py
---

## camera-motion-detection-not-triggering — CAMERA trigger never fires during active monitoring
- **Date:** 2026-04-04
- **Error patterns:** CAMERA trigger, lumaDiff, cameraMotionThreshold, no trigger, motion detection, CameraAnalyzer, MOTION_ONLY, ImageAnalysis
- **Root cause:** cameraMotionThreshold values in Sensitivity enum were 6–14x too high (LOW=0.22, MEDIUM=0.12, HIGH=0.06). Real-world front-camera lumaDiff during hand wave at <1m produces max ~0.034. Values were theoretical, never calibrated against actual camera output.
- **Fix:** Lowered thresholds to LOW=0.05, MEDIUM=0.03, HIGH=0.01. Also removed per-trigger WARN log from CameraAnalyzer.analyze() to prevent AppLogger ring buffer flooding.
- **Files changed:** app/src/main/java/org/havenapp/main/sensor/Sensitivity.kt, app/src/main/java/org/havenapp/main/media/CameraAnalyzer.kt, app/src/main/java/org/havenapp/main/MonitorService.kt
---

