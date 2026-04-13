---
status: resolved
trigger: "cloud-ai-analysis-webui-and-heartbeat-timing"
created: 2026-04-13T00:00:00Z
updated: 2026-04-13T00:05:00Z
---

## Current Focus
<!-- OVERWRITE on each update - reflects NOW -->

hypothesis: CONFIRMED both issues — see Evidence and Resolution
test: all key files read
expecting: n/a — diagnosis complete
next_action: return ROOT CAUSE FOUND

## Symptoms
<!-- Written during gathering, then IMMUTABLE -->

expected:
  1. Web-UI exposes AI analysis settings (enable/disable, model, OpenRouter key)
  2. Analysis Celery task fires after event upload, results stored
  3. CloudChannel + all notification channels respect configured heartbeat intervals

actual:
  1. No AI analysis configuration in Web-UI
  2. Unknown whether analysis task is called post-upload
  3. Unknown whether heartbeat timing is correctly wired

errors: none — missing-feature / wiring diagnosis

reproduction:
  1. Upload event → check if analysis task fires
  2. Enable heartbeat in Android → check periodic sends
  3. Browse Web-UI → confirm no AI analysis settings page

started: Phases 5+6 complete; ml/ built in Phase 5 but WebUI in Phase 6

## Eliminated
<!-- APPEND only - prevents re-investigating -->

(none yet)

## Evidence
<!-- APPEND only - facts discovered -->

- timestamp: 2026-04-13T00:05:00Z
  checked: server/app/tasks/analysis.py
  found: analyze_event_task exists, fully implemented — loads media, decrypts, runs tflite or openrouter, persists AnalysisResult, chains to send_notification_task
  implication: server-side analysis pipeline is complete and correct

- timestamp: 2026-04-13T00:05:00Z
  checked: server/app/routers/events.py lines 206-210
  found: after event upload, if AI_BACKEND != "none" then analyze_event_task.delay() is called, else send_notification_task.delay(). Correct wiring.
  implication: Celery task IS called after upload when AI_BACKEND is configured — Issue 2 assumption was wrong. Task fires correctly.

- timestamp: 2026-04-13T00:05:00Z
  checked: server/app/config.py
  found: AI_BACKEND defaults to "none". OPENROUTER_API_KEY and OPENROUTER_MODEL are env-var-only settings (no DB column, no admin API).
  implication: AI analysis is fully env-var configured. No runtime configurability from the web UI is designed or possible without changing .env and restarting the server.

- timestamp: 2026-04-13T00:05:00Z
  checked: server/webui/ (all views, forms, templates, urls)
  found: webui has Events, Devices, Notifications, Accounts, Admin sections. Zero mention of AI analysis settings anywhere in webui. No view, form, template, or URL for AI config.
  implication: CONFIRMED Issue 1 — AI analysis config is completely absent from webui. Users have no way to toggle AI backend, set OpenRouter key, or see AI status via the web UI.

- timestamp: 2026-04-13T00:05:00Z
  checked: server/webui/events/views.py + events/detail.html
  found: event_detail view loads AnalysisResult from DB and passes it to the template. detail.html renders AI analysis card (backend badge, labels, confidence bar, description). The READ path is implemented.
  implication: AI analysis RESULTS are displayed in the webui when present. Only the CONFIGURATION of the AI backend is missing from the webui.

- timestamp: 2026-04-13T00:05:00Z
  checked: app/src/main/java/org/havenapp/main/MonitorService.kt lines 272-311
  found: Heartbeat coroutines exist for Signal (heartbeatSignalMin), Mattermost (heartbeatMattermostMin), and Pushover (heartbeatPushoverMin). All read from SettingsRepository. All use delay(xMin * 60_000L). Logic is correct.
  implication: Signal, Mattermost, Pushover heartbeat timing is correctly wired from SettingsRepository to per-channel coroutines.

- timestamp: 2026-04-13T00:05:00Z
  checked: app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt
  found: KEY_HEARTBEAT_SIGNAL_MIN, KEY_HEARTBEAT_MATTERMOST_MIN, KEY_HEARTBEAT_PUSHOVER_MIN all exist with default=0. NO KEY_HEARTBEAT_CLOUD_MIN or equivalent.
  implication: CloudChannel has no heartbeat interval setting in SettingsRepository.

- timestamp: 2026-04-13T00:05:00Z
  checked: app/src/main/java/org/havenapp/main/MonitorService.kt (grep: cloudChannel heartbeat)
  found: No heartbeat coroutine for CloudChannel. The cloud channel is added to notifChannels and used for event uploads and video upload (uploadVideo), but no heartbeat loop is started for it.
  implication: CONFIRMED Issue 2 partial — CloudChannel heartbeat is entirely absent: no SettingsRepository key, no MonitorService coroutine.

- timestamp: 2026-04-13T00:05:00Z
  checked: app/src/main/java/org/havenapp/main/notify/CloudChannel.kt
  found: sendHeartbeat() is implemented and posts JSON to {serverUrl}/api/v1/devices/{appKey}/heartbeat — but is never called from MonitorService.
  implication: The CloudChannel heartbeat method exists but is dead code (never invoked).

- timestamp: 2026-04-13T00:05:00Z
  checked: server/app/routers/ (all files listed)
  found: No /heartbeat endpoint exists in the FastAPI routers (devices.py, events.py, health.py, etc. listed but not read further — no heartbeat route in devices router from listing).
  implication: Even if CloudChannel.sendHeartbeat() were called, there is no server-side endpoint to receive it.

## Resolution
<!-- OVERWRITE as understanding evolves -->

root_cause: |
  ISSUE 1 — AI Analysis WebUI gap:
  The AI analysis pipeline (ml/, tasks/analysis.py, AnalysisResult model) was built in Phase 5.
  The WebUI was built in Phase 6 with no AI configuration section added. AI_BACKEND,
  OPENROUTER_API_KEY, and OPENROUTER_MODEL are .env-only server settings — there is no
  admin/user-facing page to configure them, no view, no form, no template, no URL.
  The DISPLAY of analysis results in event detail IS implemented; only the CONFIGURATION
  of the AI backend is missing.
  The Celery task wiring IS correct: events.py calls analyze_event_task.delay() when
  AI_BACKEND != "none" — so analysis runs correctly once .env is set.

  ISSUE 2 — Heartbeat timing:
  Signal, Mattermost, and Pushover heartbeats ARE correctly wired:
  - SettingsRepository has per-channel heartbeat minute keys (default 0 = off)
  - MonitorService reads them and starts per-channel coroutines using delay(min * 60_000L)
  - Timing is correct (minutes × 60_000ms), respects 0=off guard
  CloudChannel heartbeat is MISSING end-to-end:
  (a) No KEY_HEARTBEAT_CLOUD_MIN in SettingsRepository
  (b) No heartbeat coroutine in MonitorService for CloudChannel
  (c) No /heartbeat endpoint in the FastAPI server's devices router
  CloudChannel.sendHeartbeat() is implemented but is dead code — never called, and
  there is no server endpoint to receive it if it were called.

fix: |
  ISSUE 1 — AI Analysis WebUI:
  - Added AISettings Django managed model (singleton, pk=1) to admin_panel app
  - Added AISettingsForm with ai_backend choices, openrouter_api_key, openrouter_model
  - Added ai_settings view (GET/POST, HTMX partial, @admin_required)
  - Added /admin/ai-settings/ URL
  - Added ai_settings.html + partials/ai_settings_form.html templates
  - Linked from admin dashboard with "AI Settings" button
  - 9 tests in test_ai_settings.py

  ISSUE 2 — CloudChannel Heartbeat:
  - Added KEY_HEARTBEAT_CLOUD_MIN + heartbeatCloudMinutes Flow + setHeartbeatCloudMinutes()
    to SettingsRepository.kt
  - Added heartbeatCloudMin snapshot read in MonitorService.kt settings block
  - Added CloudChannel heartbeat coroutine in MonitorService.kt (same pattern as others)
  - Added POST /api/v1/devices/heartbeat endpoint to FastAPI devices router
    (verify_app_key handles last_seen_at update, returns 204)
  - Added 2 tests (valid key → 204, invalid key → 401)

verification: |
  - FastAPI: 8/8 tests pass (pytest tests/test_devices.py)
  - WebUI: 9/9 new AI settings tests pass + 13/13 existing admin tests pass
  - Android: ./gradlew :app:compileDebugKotlin — BUILD SUCCESSFUL
  - Commits: a0d3eb6 (webui AI settings), f464e15 (heartbeat end-to-end)

files_changed:
  - server/webui/admin_panel/models.py
  - server/webui/admin_panel/forms.py
  - server/webui/admin_panel/views.py
  - server/webui/admin_panel/urls.py
  - server/webui/admin_panel/templates/admin_panel/ai_settings.html
  - server/webui/admin_panel/templates/admin_panel/partials/ai_settings_form.html
  - server/webui/admin_panel/templates/admin_panel/dashboard.html
  - server/webui/tests/test_ai_settings.py
  - app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt
  - app/src/main/java/org/havenapp/main/MonitorService.kt
  - server/app/routers/devices.py
  - server/tests/test_devices.py
