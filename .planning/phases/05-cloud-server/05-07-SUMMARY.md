---
phase: 05-cloud-server
plan: "07"
subsystem: notifications
tags: [python, fastapi, celery, smtp, signal-cli, pushover, aiosmtplib, httpx, pytest]
dependency_graph:
  requires:
    - server/app/config.py (SMTP_HOST, SIGNAL_API_URL, PUSHOVER_APP_TOKEN settings)
    - server/app/models/user.py (notification_email, notification_signal_number, pushover_user_key, notifications_enabled)
    - server/app/models/event.py (Event, AnalysisResult — for message formatting)
    - server/app/dependencies/auth.py (get_current_user)
    - server/app/database.py (AsyncSessionLocal — for Celery sync-to-async DB access)
    - server/app/celery_app.py (celery_app instance)
    - server/app/tasks/analysis.py (chains to send_notification_task)
  provides:
    - server/app/services/notify.py (send_email, send_signal, send_pushover, format_notification_message)
    - server/app/schemas/notification.py (NotificationSettingsRequest, NotificationSettingsResponse)
    - server/app/routers/notifications.py (GET/PATCH /settings, POST /test)
    - server/app/tasks/notifications.py (send_notification_task — replaces stub from plan 04)
  affects:
    - server/app/main.py (notifications router wired at /api/v1/notifications)
tech_stack:
  added:
    - aiosmtplib (already in pyproject.toml) — async SMTP send with STARTTLS
    - httpx.Client synchronous — Signal and Pushover HTTP calls from Celery sync context
  patterns:
    - Module-level imports of senders in notifications.py — required for test mockability via patch("app.tasks.notifications.send_signal")
    - asyncio.run() wrapping _load_event_data coroutine — Celery sync task drives async DB load
    - selectinload eager-loading for User/Device/AnalysisResult relations — avoids N+1 in async session
    - Per-channel conditional dispatch: each channel checked independently, results dict accumulates successes
key_files:
  created:
    - server/app/services/notify.py
    - server/app/schemas/notification.py
    - server/app/routers/notifications.py
    - server/tests/test_notifications.py
  modified:
    - server/app/tasks/notifications.py (stub replaced with full implementation)
    - server/app/main.py (notifications router wired)
decisions:
  - "Module-level imports of send_email/send_signal/send_pushover in notifications.py: lazy function-body imports are not patchable at the task module level; module-level import allows patch('app.tasks.notifications.send_signal') in tests"
  - "send_signal and send_pushover are synchronous (httpx.Client): Celery workers are synchronous by default; asyncio.run for HTTP calls would add overhead without benefit"
  - "send_email is async (aiosmtplib): called via asyncio.run() in the Celery task, consistent with how _load_event_data is loaded"
  - "PATCH /settings updates only non-None fields: omitted fields preserved, no accidental erasure of existing config"
  - "_load_event_data uses selectinload for user/device/analysis_result: avoids lazy-loading DetachedInstanceError after session close"
metrics:
  duration_minutes: 12
  completed_date: "2026-04-07"
  tasks_completed: 2
  files_created: 4
  files_modified: 2
---

# Phase 05 Plan 07: Notification Dispatch Summary

**One-liner:** Celery send_notification_task dispatches Email (aiosmtplib STARTTLS), Signal (signal-cli REST), and Pushover (Pushover API) with per-user channel config via PATCH /api/v1/notifications/settings, completing the upload→analysis→notify pipeline.

## What Was Built

### Task 1: Notification senders, schemas, router, main.py wiring

**server/app/services/notify.py** — three notification channel senders:
- `async send_email(to, subject, body) -> bool`: aiosmtplib STARTTLS; MIMEText with From=SMTP_FROM; returns False on any error (logs, never raises)
- `send_signal(recipient, message) -> bool`: synchronous httpx.Client POST to `{SIGNAL_API_URL}/v2/send`; optional Bearer auth via SIGNAL_AUTH_TOKEN; 200/201 = True
- `send_pushover(user_key, message, title) -> bool`: synchronous httpx.Client POST to `api.pushover.net/1/messages.json`; form data with PUSHOVER_APP_TOKEN; 200 = True
- `format_notification_message(event_type, severity, timestamp, device_name, analysis_summary) -> tuple[str, str]`: produces structured (subject, body) pair; includes AI summary when available

**server/app/schemas/notification.py** — Pydantic v2:
- `NotificationSettingsRequest`: all fields Optional (None = "don't update")
- `NotificationSettingsResponse`: from_attributes=True for ORM model validation

**server/app/routers/notifications.py** — `APIRouter(tags=["notifications"])`:
- `GET /settings` → return user's current notification config
- `PATCH /settings` → update only provided fields, commit, return updated config
- `POST /test` → send test alert to all configured channels, return results dict

**server/app/main.py** — notifications router wired at `/api/v1/notifications`

### Task 2: Celery notification task and tests

**server/app/tasks/notifications.py** — replaces no-op stub from plan 04:
- `@celery_app.task(name="app.tasks.notifications.send_notification_task")`
- `send_notification_task(event_id: int) -> dict`
- Returns `{"status": "disabled"}` if `user.notifications_enabled` is False
- Returns `{"status": "no_channels"}` if enabled but no channels configured
- Returns `{"status": "sent", "results": {...}}` with per-channel bool results
- `_load_event_data(event_id)`: async helper, `selectinload` for user/device/analysis_result, formats timestamp to "YYYY-MM-DD HH:MM:SS UTC", extracts AI summary from labels JSON or description field
- Module-level imports of `send_email`, `send_signal`, `send_pushover`, `format_notification_message` for test mockability

**server/tests/test_notifications.py** — 8 tests, all passing:
- `test_notification_settings_get`: GET returns default fields for new user
- `test_notification_settings_update`: PATCH persists email, preserves other fields
- `test_send_notification_disabled`: task returns `{"status": "disabled"}` when flag is False
- `test_send_notification_email`: asyncio.run mock counts calls; verifies email result True
- `test_send_notification_signal`: patches send_signal at task module level; verifies called with correct recipient
- `test_send_notification_pushover`: patches send_pushover; verifies called with correct user_key
- `test_send_notification_no_channels`: no channels configured → `{"status": "no_channels"}`
- `test_format_notification_message`: subject contains severity/event_type/device; body contains AI summary

## Verification Results

```
python3 -m pytest tests/test_notifications.py -v
tests/test_notifications.py::test_notification_settings_get PASSED
tests/test_notifications.py::test_notification_settings_update PASSED
tests/test_notifications.py::test_send_notification_disabled PASSED
tests/test_notifications.py::test_send_notification_email PASSED
tests/test_notifications.py::test_send_notification_signal PASSED
tests/test_notifications.py::test_send_notification_pushover PASSED
tests/test_notifications.py::test_send_notification_no_channels PASSED
tests/test_notifications.py::test_format_notification_message PASSED
========================= 8 passed, 5 warnings in 1.90s =========================

python3 -m pytest tests/ -v
======================== 45 passed, 7 pre-existing failures (test_events.py), 12 warnings =========================
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Lazy function-body imports of notify senders not patchable in tests**
- **Found during:** Task 2 — test_send_notification_signal raised `AttributeError: module 'app.tasks.notifications' does not have the attribute 'send_signal'`
- **Issue:** The plan specified importing senders lazily inside `send_notification_task`; unittest.mock.patch requires module-level names to patch
- **Fix:** Moved `from app.services.notify import format_notification_message, send_email, send_pushover, send_signal` to module level in `notifications.py`
- **Files modified:** `server/app/tasks/notifications.py`
- **Commit:** `5547a1d`
- **Pattern precedent:** Identical fix applied in plan 05-06 for `AsyncSessionLocal` (same root cause)

## Known Stubs

None — `send_notification_task` is fully implemented. The analysis→notify chain is complete.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: credentials-in-config | server/app/services/notify.py | SMTP_PASSWORD, SIGNAL_AUTH_TOKEN, and PUSHOVER_APP_TOKEN flow through settings object. These should be loaded from secrets manager or encrypted env vars in production, not plain .env. No change needed for server MVP but worth noting for hardening. |

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 | `c05fc1e` | feat(05-07): notification senders (Email/Signal/Pushover), schemas, router, main.py wiring |
| Task 2 | `5547a1d` | feat(05-07): Celery send_notification_task and 8 passing tests |

## Self-Check: PASSED
