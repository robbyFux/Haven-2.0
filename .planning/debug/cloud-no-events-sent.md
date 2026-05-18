---
status: diagnosed
trigger: "Die Cloud versendet keine Events — der Server empfängt Events vom Android-Client, aber es werden keine Benachrichtigungen/Weiterleitungen versendet."
created: 2026-04-09T00:00:00Z
updated: 2026-04-09T00:00:00Z
---

## Current Focus
<!-- OVERWRITE on each update - reflects NOW -->

hypothesis: CONFIRMED — two independent bugs prevent notifications from being sent
test: Full code trace from Android client through server task dispatch
expecting: n/a — root cause confirmed
next_action: Report diagnosis

## Symptoms
<!-- Written during gathering, then IMMUTABLE -->

expected: POST /api/v1/devices/{token}/events triggers notification dispatch via configured channels
actual: Events arrive (201 Created), no notifications sent
errors: No known error messages
reproduction: Android client sends events, server responds 201, no notifications arrive
started: Unknown — unclear if it ever worked

## Eliminated
<!-- APPEND only - prevents re-investigating -->

- hypothesis: Celery worker not running or not connected
  evidence: docker-compose.yml shows a properly configured worker service with Redis dependency; task dispatch code exists and is reachable
  timestamp: 2026-04-09

- hypothesis: Tasks not dispatched at all from events router
  evidence: events.py lines 206-210 show clear dispatch: analyze_event_task.delay() when AI_BACKEND != "none", send_notification_task.delay() when AI_BACKEND == "none"
  timestamp: 2026-04-09

- hypothesis: send_notification_task has a silent exception swallowing the real error
  evidence: Task code is well-formed; channels are gated on config values (SMTP_HOST, SIGNAL_API_URL, PUSHOVER_APP_TOKEN) — no crash path, just "no_channels" fallback
  timestamp: 2026-04-09

## Evidence
<!-- APPEND only - facts discovered -->

- timestamp: 2026-04-09
  checked: server/app/routers/events.py lines 205-210
  found: When AI_BACKEND != "none" (tflite or openrouter), only analyze_event_task.delay() is called. send_notification_task is NOT called from the router in this path.
  implication: analyze_event_task must chain to send_notification_task — and it does (analysis.py line 128). BUT: analyze_event_task returns early without chaining when (a) media_path is None (no video attached) or (b) TFLite/OpenRouter inference fails. In these cases send_notification_task.delay() is never called.

- timestamp: 2026-04-09
  checked: server/app/tasks/analysis.py lines 48-130
  found: Three early-return paths that skip the notification chain: (1) settings.AI_BACKEND == "none" → {"status":"skipped"} — but this path uses send_notification_task directly from router, so OK. (2) media_path is None → {"status":"no_media"} — NO notification dispatch. (3) Any inference error → {"status":"error"} — NO notification dispatch.
  implication: Events without video (sensor-only triggers like ACCELEROMETER, MICROPHONE, LIGHT) never trigger notifications when AI_BACKEND is "tflite" or "openrouter", because analyze_event_task exits at line 51 ("no_media") before reaching the chain call.

- timestamp: 2026-04-09
  checked: app/src/main/java/org/havenapp/main/notify/CloudChannel.kt line 41, NotificationRouter.kt lines 67-75
  found: CloudChannel.deferresToVideo = true. NotificationRouter.route() (lines 67-68) filters to immediateChannels = channels.filter { !it.deferresToVideo } and returns early if immediateChannels.isEmpty(). Since CloudChannel is the only cloud channel and it always defers, route() returns without sending anything.
  implication: Cloud notifications are ONLY sent via NotificationRouter.uploadVideo() — which requires a video clip. Sensor-only events (motion, mic, light) that don't produce a clip never trigger any cloud upload. This is the Android-side bug that explains why even properly working server-side notifications would not be triggered by sensor events.

- timestamp: 2026-04-09
  checked: server/app/config.py
  found: AI_BACKEND defaults to "none". SMTP_HOST, SIGNAL_API_URL, PUSHOVER_APP_TOKEN all default to empty string.
  implication: In a default/unconfigured deployment, send_notification_task returns {"status":"no_channels"} because none of the three channel conditions are met. This is a configuration issue but not the code bug.

## Resolution
<!-- OVERWRITE as understanding evolves -->

root_cause: Two independent bugs, one on each side of the system:

  BUG 1 — Server: analyze_event_task does not chain to send_notification_task when there is no media.
  File: server/app/tasks/analysis.py, line 51-52.
  When AI_BACKEND is "tflite" or "openrouter" and the event has no video attachment, analyze_event_task
  returns {"status": "no_media"} immediately. The send_notification_task.delay(event_id) call at line 128
  is never reached. Sensor-only events (ACCELEROMETER, MICROPHONE, LIGHT) submitted without video are
  silently dropped — no notification is ever dispatched.

  BUG 2 — Android: CloudChannel.deferresToVideo = true, so NotificationRouter.route() always skips it.
  File: app/src/main/java/org/havenapp/main/notify/CloudChannel.kt, line 41.
  NotificationRouter.route() (NotificationRouter.kt lines 67-75) filters out deferred channels and returns
  early when only deferred channels remain. Since CloudChannel is the only channel that uploads to the
  self-hosted server, and it always defers, sensor-triggered events without a video clip NEVER reach the
  server POST endpoint. Only events that successfully complete a video clip recording trigger uploadVideo(),
  which is the only code path that calls CloudChannel.send(). This means: even if BUG 1 is fixed, the
  server would only receive events that have a video — all sensor-only triggers are silently dropped on
  the Android side before any HTTP request is made.

fix: (not applied — diagnose-only mode)
  BUG 1 fix direction: In analyze_event_task, move the send_notification_task.delay(event_id) call to
  execute after DB persistence regardless of whether media was present. The "no_media" early return should
  be restructured so notification dispatch happens unconditionally for all events, and only the analysis
  portion is skipped when there is no video.

  BUG 2 fix direction: Either (a) set deferresToVideo = false on CloudChannel so sensor events are
  immediately uploaded (with a JPEG frame attachment if available), or (b) explicitly call
  notificationRouter.route() for CloudChannel separately on sensor events. Option (a) aligns with how
  Signal/Mattermost/Pushover channels work — they send immediately with an optional JPEG, and video is not
  a prerequisite for alerting.

verification:
files_changed: []
