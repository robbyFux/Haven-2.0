---
status: investigating
trigger: "pushover-still-not-sending"
created: 2026-04-09T00:00:00Z
updated: 2026-04-09T01:00:00Z
---

## Current Focus

hypothesis: Multiple independent root causes identified across the server-side pipeline
test: Full pipeline trace completed — all findings are observational (no runtime needed)
expecting: ROOT CAUSE FOUND
next_action: Report findings

## Symptoms

expected: Bei jedem Cloud-Event soll eine Pushover-Benachrichtigung an den konfigurierten User gesendet werden.
actual: Keine Pushover-Nachrichten kommen an, obwohl Pushover konfiguriert ist.
errors: Keine bekannten Fehlermeldungen.
reproduction: Android-Client sendet Event → Server speichert Event (201 Created) → keine Pushover-Nachricht.
started: War noch nie funktionsfähig.

## Eliminated

- hypothesis: Event.user relationship missing or not loaded
  evidence: Event.user is defined in event.py (line 55) and loaded via selectinload(Event.user) in _load_event_data()
  timestamp: 2026-04-09T01:00:00Z

- hypothesis: send_notification_task never dispatched
  evidence: events.py lines 206-210 clearly call send_notification_task.delay(event.id) when AI_BACKEND == "none"
  timestamp: 2026-04-09T01:00:00Z

- hypothesis: FastAPI User model missing pushover_app_token
  evidence: app/models/user.py line 62 has pushover_app_token: Mapped[str | None]
  timestamp: 2026-04-09T01:00:00Z

- hypothesis: Django webui model missing pushover_app_token
  evidence: webui/accounts/models.py line 90 has pushover_app_token = models.CharField(...)
  timestamp: 2026-04-09T01:00:00Z

- hypothesis: notification_settings view not saving pushover_app_token
  evidence: webui/notifications/views.py lines 37, 44 correctly save pushover_app_token
  timestamp: 2026-04-09T01:00:00Z

- hypothesis: docker-compose worker missing env vars
  evidence: worker service uses same env_file: .env as the app service (line 53)
  timestamp: 2026-04-09T01:00:00Z

## Evidence

- timestamp: 2026-04-09T01:00:00Z
  checked: server/alembic/versions/6874b4390a34_initial_schema.py (the base migration)
  found: Initial schema creates 'users' table WITHOUT pushover_app_token column. Column list ends at pushover_user_key, notifications_enabled.
  implication: CRITICAL — The base schema never included pushover_app_token. The a3f2e1d4c5b6 migration must be applied to add it.

- timestamp: 2026-04-09T01:00:00Z
  checked: server/alembic/versions/a3f2e1d4c5b6_add_pushover_app_token.py
  found: Migration correctly adds pushover_app_token String(50) nullable to users table. down_revision = "61bdd1bdc836" (chaining correctly).
  implication: Migration exists and is correct. But if it has not been applied (alembic upgrade head not run), the column does not exist in the DB → asyncpg will throw an error on INSERT or SELECT referencing this column.

- timestamp: 2026-04-09T01:00:00Z
  checked: server/app/tasks/notifications.py _load_event_data() return dict, line 129
  found: Returns "user": event.user — this is the live ORM User object. SQLAlchemy will lazily load or eagerly load all columns including pushover_app_token when the session is open.
  implication: If pushover_app_token column does not exist in DB yet, accessing event.user.pushover_app_token in send_notification_task will cause an asyncpg error that is silently swallowed by the Celery worker (no log surfaced to user), returning {"status": "error", "detail": "event not found"}.

- timestamp: 2026-04-09T01:00:00Z
  checked: server/app/tasks/notifications.py line 71
  found: Condition is: if user.pushover_user_key and user.pushover_app_token — BOTH must be non-None/non-empty
  implication: If either key is missing, Pushover is silently skipped with no log message. The only log is at the event-not-found branch (line 42).

- timestamp: 2026-04-09T01:00:00Z
  checked: server/app/tasks/notifications.py send_notification_task — error handling
  found: No logging when pushover_user_key OR pushover_app_token is None/empty. The function just skips to "no_channels" without logging why.
  implication: Silent failure — if config is wrong, operator cannot diagnose from logs.

- timestamp: 2026-04-09T01:00:00Z
  checked: server/app/tasks/notifications.py _load_event_data() + asyncpg error path
  found: The data = asyncio.run(_load_event_data(event_id)) call. If the DB column is missing, asyncpg raises an UndefinedColumnError. asyncio.run() propagates this as an exception from send_notification_task. Celery catches unhandled task exceptions and marks the task FAILED — but the log shows up only in Celery worker output (not in the FastAPI app log). Since user says "no error messages seen", Celery worker logs are likely not being monitored.
  implication: The Celery FAILED task is the most probable symptom currently — it crashes before reaching any notification logic.

- timestamp: 2026-04-09T01:00:00Z
  checked: server/app/services/notify.py send_pushover() line 108
  found: token = app_token or settings.PUSHOVER_APP_TOKEN — falls back to global env var if app_token is None/empty
  implication: If pushover_app_token is NULL in DB (e.g. migration not applied, or user never set it), AND settings.PUSHOVER_APP_TOKEN is also empty (likely in self-hosted setup), token will be empty string "" → Pushover API returns HTTP 400, which is logged as error but not re-raised.

- timestamp: 2026-04-09T01:00:00Z
  checked: server/app/routers/events.py lines 206-210
  found: Task dispatch logic:
    if settings.AI_BACKEND != "none":
        analyze_event_task.delay(...)
    else:
        send_notification_task.delay(event.id)
  implication: When AI_BACKEND is "tflite" or "openrouter", send_notification_task is only called from analyze_event_task AFTER analysis completes. If AI analysis fails or the media file is absent (no_media path at line 53-54 in analysis.py), send_notification_task IS called. But if AI_BACKEND == "none", it IS called directly. So the routing is correct per configured mode.

- timestamp: 2026-04-09T01:00:00Z
  checked: server/app/tasks/notifications.py — no logging for partial config
  found: Only ONE log.error call exists: when event is not found (line 42). The conditional pushover block (line 71) has no logging for the case where user.pushover_user_key is set but user.pushover_app_token is None/empty. The "no_channels" path (line 75) has no logging.
  implication: Operator gets zero indication from logs that Pushover was skipped due to missing config.

## Resolution

root_cause: THREE compounding root causes. See detailed findings above.

  ROOT CAUSE 1 (CRITICAL — blocks everything):
    Alembic migration a3f2e1d4c5b6 adds the pushover_app_token column but has likely
    NOT been applied to the running database. The base migration 6874b4390a34 creates
    the users table without pushover_app_token. If alembic upgrade head was never run
    after this migration was created, the DB column does not exist.
    When send_notification_task loads the event user, SQLAlchemy tries to map the
    pushover_app_token attribute (it IS in the ORM model) against a column that does
    not exist in the DB. With asyncpg this raises an UndefinedColumnError that propagates
    out of asyncio.run(_load_event_data(event_id)) — the task crashes and Celery marks
    it FAILED. No Pushover notification is ever sent.
    Evidence: 6874b4390a34 initial schema (line 37-39) lacks pushover_app_token.
    The a3f2e1d4c5b6 migration (down_revision = "61bdd1bdc836") exists but must be applied.

  ROOT CAUSE 2 (SILENT FAILURE — if migration IS applied):
    notifications.py line 71 checks BOTH user.pushover_user_key AND user.pushover_app_token.
    If either is NULL/empty, Pushover is silently skipped — no log entry, no error.
    This is correct logic but produces zero diagnostic signal when misconfigured.
    Additionally, send_pushover() falls back to settings.PUSHOVER_APP_TOKEN (the global
    env var) if app_token is None/empty. In a typical self-hosted setup where
    PUSHOVER_APP_TOKEN is not set in .env, this produces an empty token → Pushover API
    returns 400, which IS logged by send_pushover() but never surfaces to the user.

  ROOT CAUSE 3 (ARCHITECTURE SPLIT — confusion about where to configure Pushover):
    There are TWO separate Pushover paths:
    a) Android PushoverChannel (notify/PushoverChannel.kt) — the Android device sends
       Pushover notifications DIRECTLY to the Pushover API, bypassing the cloud server.
       Config: pushover_app_token and pushover_user_key in Android DataStore settings.
    b) Server-side send_pushover() — the Celery worker sends Pushover notifications
       AFTER receiving a cloud event, using per-user config from the DB (set via webui).
    If the user has configured Pushover in the Android app settings (path a) but has NOT
    configured it in the webui notification settings (path b), the server-side Pushover
    will silently skip because user.pushover_user_key / user.pushover_app_token are NULL
    in the DB. The Android PushoverChannel also only fires for non-deferred channels
    (PushoverChannel.deferresToVideo = false by default), while CloudChannel.deferresToVideo
    = true, so events go to CloudChannel (deferred) → uploaded to server → server triggers
    Celery → server-side Pushover fires. These are completely separate notification paths.

fix: (diagnosis-only mode — no fix applied)
verification:
files_changed: []
