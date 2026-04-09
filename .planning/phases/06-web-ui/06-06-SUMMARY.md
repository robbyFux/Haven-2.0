---
phase: 06-web-ui
plan: "06"
subsystem: server/webui/notifications
tags: [django, htmx, tailwind, notifications, email, signal, pushover]
dependency_graph:
  requires:
    - 06-01 (Django scaffold, HavenUser model with notification fields, base template)
  provides:
    - NotificationSettingsForm with email/Signal/Pushover/enabled fields + validation
    - notification_settings view (GET/POST, HTMX partial support)
    - notifications/urls.py with path '' -> settings
    - settings.html and partials/notification_form.html templates
    - 10 pytest-django tests covering all flows
  affects: []
tech_stack:
  added: []
  patterns:
    - login_required decorator for auth guard
    - HTMX partial rendering via request.headers.get("HX-Request")
    - Empty string -> None conversion for optional notification fields
    - Django validate_email validator in clean_notification_email
    - E.164 "starts with +" check in clean_notification_signal_number
    - user.save(update_fields=[...]) for targeted DB writes
key_files:
  created:
    - server/webui/notifications/forms.py
    - server/webui/notifications/views.py
    - server/webui/notifications/templates/notifications/settings.html
    - server/webui/notifications/templates/notifications/partials/notification_form.html
    - server/webui/tests/test_notifications.py
  modified:
    - server/webui/notifications/urls.py (stub -> real URL pattern)
decisions:
  - "Empty string saved as None (not ''): consistent with FastAPI backend's nullable columns; prevents empty strings from appearing as 'configured'"
  - "HTMX detection via request.headers.get('HX-Request'): django-htmx middleware not required for simple partial detection"
  - "validate_email from django.core.validators: reuses Django's built-in RFC 5322 validator instead of a regex"
  - "Signal number validation: only checks starts-with-'+' — full E.164 regex would require libphonenumber; lightweight check sufficient for MVP"
  - "notifications_enabled uses BooleanField(required=False): unchecked checkboxes submit nothing, required=False means missing == False"
metrics:
  duration_seconds: 420
  completed_date: "2026-04-09"
  tasks_completed: 1
  files_created: 5
  files_modified: 1
  tests_passing: 10
---

# Phase 6 Plan 6: Notification Settings Page Summary

**One-liner:** Notification settings page with HTMX inline save — email, Signal (E.164), and Pushover channels plus global enable/disable toggle, form validation, and 10 passing tests.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Notification settings form, view, templates, tests | 3f01e5a | forms.py, views.py, urls.py, settings.html, partials/notification_form.html, test_notifications.py |

## What Was Built

**NotificationSettingsForm** (`notifications/forms.py`): Four fields — `notifications_enabled` (BooleanField, required=False), `notification_email` (EmailInput), `notification_signal_number` (TextInput), `pushover_user_key` (TextInput). Two clean methods: `clean_notification_email` uses Django's `validate_email` validator; `clean_notification_signal_number` raises ValidationError if non-empty number doesn't start with `+`.

**notification_settings view** (`notifications/views.py`): `@login_required`. GET populates form with current user values. POST validates, saves to user via `user.save(update_fields=[...])`, converts empty strings to None for nullable fields. Detects HTMX via `request.headers.get("HX-Request")`: returns `partials/notification_form.html` partial (with success message) for HTMX requests; redirects to `notifications:settings` otherwise. Invalid forms re-render the settings template (or partial for HTMX).

**URLs** (`notifications/urls.py`): `app_name = "notifications"`, single `path("", views.notification_settings, name="settings")`.

**settings.html**: Extends `base.html`. Title "Notification Settings", description text, `<div id="notification-form">` wrapping the included partial.

**partials/notification_form.html**: Django messages banner at top (enables HTMX inline feedback). Form with `hx-post`, `hx-target="#notification-form"`, `hx-swap="innerHTML"`. Four `bg-gray-800` section cards: Global toggle (checkbox, `accent-teal-600`), Email, Signal (with E.164 hint), Pushover. Green check icon shown when field is already configured. Save button in teal.

**10 tests** (`tests/test_notifications.py`) — TDD: tests written first (RED, 404 failures), implementation written to pass (GREEN, 10/10). Covers: auth guard redirect, GET shows current values, POST saves email/Signal/Pushover, unchecked toggle saves False, HTMX POST returns partial without `<html>`, empty email clears to None, invalid email format rejected, Signal number without `+` rejected.

## Verification Results

```
10 passed, 11 warnings in 1.72s
```

Full suite unaffected:
```
95 passed, 71 warnings in 16.74s
```

All done criteria met:
- Notification settings page shows current values
- All 3 channels (email, Signal, Pushover) saveable
- Global enable/disable toggle works (unchecked = False)
- HTMX partial rendering returns only form content (no `<html>` tag)
- Validation catches invalid email format and Signal number without `+`
- Empty fields save as None (not empty string)
- 10 tests pass (>= 8 required)

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — all notification fields are wired to `request.user` and saved to the DB.

## Threat Model Coverage

| Threat | Status | Implementation |
|--------|--------|----------------|
| T-06-02 Tampering (CSRF) | Mitigated | `{% csrf_token %}` in form + `hx-headers` CSRF on base.html body |
| T-06-07 Session auth guard | Mitigated | `@login_required` redirects unauthenticated users to login |

## Self-Check: PASSED

| Item | Status |
|------|--------|
| server/webui/notifications/forms.py | FOUND |
| server/webui/notifications/views.py | FOUND |
| server/webui/notifications/urls.py | FOUND |
| server/webui/notifications/templates/notifications/settings.html | FOUND |
| server/webui/notifications/templates/notifications/partials/notification_form.html | FOUND |
| server/webui/tests/test_notifications.py | FOUND |
| commit 3f01e5a (Task 1) | FOUND |
| 10 tests passing | VERIFIED |
