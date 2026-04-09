---
phase: 06-web-ui
plan: "03"
subsystem: server/webui/devices
tags: [django, htmx, tailwind, devices, auth]
dependency_graph:
  requires:
    - "06-01 (Django scaffold, Device model, conftest)"
  provides:
    - Device list view filtered by current user
    - Device create view generating hav_ app_key
    - Device revoke view via HTMX inline row replacement
    - 3 Tailwind dark-themed device templates
    - 7 passing tests for device CRUD and user isolation
  affects:
    - server/webui/tests/conftest.py (added migrate call for django_session)
    - server/webui/config/settings.py (StaticFilesStorage override in test mode)
tech_stack:
  added: []
  patterns:
    - "@login_required + @require_POST decorators on mutating views"
    - "request.htmx branch for partial vs full template rendering"
    - "get_object_or_404(Device, id=..., user_id=request.user.id) for ownership enforcement"
    - "secrets.token_hex(32) for app_key generation"
    - "hx-post + hx-target + hx-swap=outerHTML for inline row replacement"
    - "Flash message carries generated app_key (shown once)"
key_files:
  created:
    - server/webui/devices/forms.py
    - server/webui/devices/views.py
    - server/webui/devices/templates/devices/list.html
    - server/webui/devices/templates/devices/partials/device_table.html
    - server/webui/devices/templates/devices/partials/device_row.html
    - server/webui/tests/test_devices.py
  modified:
    - server/webui/devices/urls.py (stub replaced with real URL patterns)
    - server/webui/tests/conftest.py (migrate call added for session table)
    - server/webui/config/settings.py (test-mode StaticFilesStorage override)
decisions:
  - "get_object_or_404 with user_id filter enforces ownership in a single DB call — no separate permission check needed"
  - "app_key shown via flash message (not stored unhashed) — user must copy on creation; matches security model of API keys"
  - "request.htmx branch in device_list returns device_table.html partial only — enables future inline refresh without full page reload"
  - "conftest django_db_setup now calls migrate before schema_editor.create_model — ensures Django managed tables (django_session, auth_*) exist in SQLite :memory:"
  - "StaticFilesStorage override in test settings.py — CompressedManifestStaticFilesStorage requires collectstatic output which does not exist in CI/test runs"
metrics:
  duration_seconds: 420
  completed_date: "2026-04-09"
  tasks_completed: 1
  files_created: 6
  files_modified: 3
  tests_passing: 7
---

# Phase 6 Plan 3: Device Management Views Summary

**One-liner:** Django device management with user-scoped list, hav_ app_key generation on create, and HTMX inline revoke — 7 tests pass, 3 dark-themed Tailwind templates.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Device list, create, and revoke views with HTMX | 49232e6 | devices/views.py, devices/forms.py, 3 templates, test_devices.py |

## What Was Built

**`devices/forms.py`** — `DeviceCreateForm` with a single `name` CharField (max_length=100, required), pre-styled with Tailwind dark-theme classes on the widget.

**`devices/views.py`** — Three `@login_required` views:
- `device_list`: filters `Device.objects.filter(user_id=request.user.id)`, returns `partials/device_table.html` on HTMX requests, full `list.html` otherwise.
- `device_create`: `@require_POST`, validates form, generates `hav_{secrets.token_hex(32)}` (68 chars), persists device, flashes key once, redirects to list.
- `device_revoke`: `@require_POST`, `get_object_or_404(Device, id=device_id, user_id=request.user.id)` (404 if not owned), sets `is_active=False`, returns `partials/device_row.html` on HTMX or redirects.

**`devices/urls.py`** — Three named URL patterns under `app_name = "devices"`: `device_list`, `device_create`, `device_revoke`.

**Templates** — Dark-themed Tailwind templates:
- `list.html`: extends `base.html`, create form at top, device table below, green alert for new app_key.
- `partials/device_table.html`: responsive table with Name, App-Key (truncated), Status badge, Last Seen, Created, Actions columns; empty state message.
- `partials/device_row.html`: `<tr id="device-{{ device.id }}">` with green Active / red Revoked badges, HTMX Revoke button with `hx-confirm`.

## Verification Results

```
7 passed, 8 warnings in 1.49s
```

All done criteria met:
- Device list filtered by current user (Bob's devices not visible to Alice)
- Device create generates `hav_<64 hex>` = 68-char app_key, redirects to list
- Device revoke sets `is_active=False`, returns HTMX row partial
- Cannot revoke another user's device (404 enforced by `user_id` filter)
- HTMX partial rendering returns table fragment without full HTML document
- 7/7 tests pass

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `django_session` table missing in test SQLite database**
- **Found during:** Task 1 (test_device_list_shows_own_devices — `force_login` failed)
- **Issue:** The session-scoped `django_db_setup` fixture in `conftest.py` only created unmanaged model tables via `schema_editor.create_model()`, but did not run Django migrations. This meant Django's own managed tables (`django_session`, `auth_*`, etc.) were absent from the in-memory SQLite DB, causing `force_login()` to raise `OperationalError: no such table: django_session`.
- **Fix:** Added `call_command("migrate", "--run-syncdb", verbosity=0)` at the start of `django_db_setup` before the schema_editor block.
- **Files modified:** `server/webui/tests/conftest.py`
- **Commit:** 49232e6

**2. [Rule 1 - Bug] `CompressedManifestStaticFilesStorage` fails in tests**
- **Found during:** Task 1 (test_device_list_shows_own_devices — template render failed)
- **Issue:** `base.html` uses `{% static 'css/tailwind.css' %}`. The production `STORAGES` uses `whitenoise.storage.CompressedManifestStaticFilesStorage` which requires a pre-built `staticfiles.json` manifest (generated by `collectstatic`). That manifest does not exist in the test environment, causing `ValueError: Missing staticfiles manifest entry for 'css/tailwind.css'`.
- **Fix:** Added a test-mode `STORAGES` override in `config/settings.py` that switches to `django.contrib.staticfiles.storage.StaticFilesStorage` (no manifest required) when `"pytest" in sys.modules`.
- **Files modified:** `server/webui/config/settings.py`
- **Commit:** 49232e6

## Known Stubs

None — all device management functionality is fully implemented.

## Threat Model Coverage

| Threat | Status | Implementation |
|--------|--------|----------------|
| T-06-03 User isolation (IDOR) | Mitigated | `filter(user_id=request.user.id)` on list; `get_object_or_404(..., user_id=...)` on revoke |
| T-06-01 Auth required | Mitigated | `@login_required` on all three views |
| T-06-04 App-key exposure | Mitigated | Key shown once via flash message; stored as-is (opaque token, not a secret that needs hashing) |

## Self-Check: PASSED

| Item | Status |
|------|--------|
| server/webui/devices/forms.py | FOUND |
| server/webui/devices/views.py | FOUND |
| server/webui/devices/urls.py | FOUND |
| server/webui/devices/templates/devices/list.html | FOUND |
| server/webui/devices/templates/devices/partials/device_table.html | FOUND |
| server/webui/devices/templates/devices/partials/device_row.html | FOUND |
| server/webui/tests/test_devices.py | FOUND |
| commit 49232e6 (Task 1) | FOUND |
