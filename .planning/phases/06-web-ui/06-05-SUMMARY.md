---
phase: 06-web-ui
plan: "05"
subsystem: server/webui/admin_panel
tags: [django, htmx, tailwind, admin, access-control, quota-management]
dependency_graph:
  requires:
    - 06-01 (Django scaffold, HavenUser model, test infra)
  provides:
    - admin_required decorator at server/webui/core/decorators.py
    - Admin dashboard with system stats and user list
    - User detail page with quota edit form
    - HTMX inline quota editing and toggle active
  affects:
    - server/webui/admin_panel/urls.py (routes added)
    - server/webui/admin_panel/views.py (new file)
    - server/webui/admin_panel/forms.py (new file)
    - server/webui/core/decorators.py (new file)
tech_stack:
  added: []
  patterns:
    - admin_required decorator wraps @login_required + is_admin check
    - HTMX partial rendering: HX-Request header detection via request.htmx
    - Django aggregate queries: Sum("current_storage_bytes") for total storage
    - QuotaEditForm with min_value=0 for validation
    - update_fields=["storage_quota_mb", "max_events"] for atomic saves
key_files:
  created:
    - server/webui/core/decorators.py
    - server/webui/admin_panel/views.py
    - server/webui/admin_panel/forms.py
    - server/webui/admin_panel/templates/admin_panel/dashboard.html
    - server/webui/admin_panel/templates/admin_panel/partials/user_table.html
    - server/webui/admin_panel/templates/admin_panel/partials/user_row.html
    - server/webui/admin_panel/templates/admin_panel/user_detail.html
    - server/webui/tests/test_admin.py
  modified:
    - server/webui/admin_panel/urls.py (stub → 4 URL patterns)
decisions:
  - "admin_required uses functools.wraps + @login_required nesting: preserves function metadata and reuses Django's redirect-to-login logic"
  - "_format_storage helper in views.py: formats bytes as MB/GB — avoids Django template filter for this specific computation"
  - "quota_edit returns 422 for HTMX validation errors: standard HTMX error handling convention"
  - "toggle_active uses update_fields=['is_active']: atomic save without touching other fields"
metrics:
  duration_seconds: 420
  completed_date: "2026-04-09"
  tasks_completed: 2
  files_created: 8
  files_modified: 1
  tests_passing: 13
---

# Phase 6 Plan 5: Admin Dashboard Summary

**One-liner:** Django admin dashboard with `admin_required` decorator, aggregate system stats, HTMX user list, inline quota editing, and toggle-active — 13 tests covering all access control and CRUD paths.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Admin decorator, dashboard stats, user list | 0f3765e | core/decorators.py, admin_panel/views.py, dashboard.html, user_table.html, user_row.html, tests/test_admin.py |
| 2 | User detail and quota edit with HTMX | 0f3765e | admin_panel/forms.py, user_detail.html, quota_edit + toggle_active views |

## What Was Built

**`core/decorators.py`** — `admin_required` decorator: chains `@login_required` (handles unauthenticated redirect) then checks `request.user.is_admin`. Returns `HttpResponseForbidden("Admin access required.")` for non-admin users. Uses `functools.wraps` to preserve view metadata.

**`admin_panel/views.py`** — Four views:
- `dashboard`: Aggregates `total_users`, `total_events`, `total_storage` via `HavenUser.objects.aggregate(Sum(...))`. Returns partial `user_table.html` for HTMX requests, full `dashboard.html` otherwise.
- `user_detail`: Shows user info, event count, device count, and `QuotaEditForm` pre-populated with current values.
- `quota_edit`: POST-only; validates `QuotaEditForm`, saves with `update_fields`. Returns `user_row.html` partial for HTMX on success, 422 on validation error.
- `toggle_active`: POST-only; flips `is_active`, saves with `update_fields`. Returns `user_row.html` partial for HTMX.

**`admin_panel/forms.py`** — `QuotaEditForm` with `storage_quota_mb` and `max_events` as `IntegerField(min_value=0)`. Tailwind-styled widgets included.

**Templates** — Dark Tailwind theme consistent with `base.html`:
- `dashboard.html`: 3-card stats grid (teal `text-3xl` numbers) + `#user-table` div with HTMX auto-reload.
- `partials/user_table.html`: Table with 8 columns including quota usage.
- `partials/user_row.html`: `<tr id="user-{{ user.id }}">` with admin/2FA/active badges, monospace quota columns, Edit Quota link.
- `user_detail.html`: Info card + quota form card + actions card (toggle active button).

## Verification Results

```
13 passed, 14 warnings in 3.90s
```

All done criteria met:
- `admin_required` blocks non-admin with 403, redirects anonymous to login
- Dashboard shows correct aggregate stats (total_users, total_events, total_storage)
- User list renders all users with quota info
- HTMX partial rendering for user table and user row
- Quota edit updates `storage_quota_mb` and `max_events`, rejects negatives (422)
- Toggle active flips `is_active` flag
- Non-admin blocked from user_detail, quota_edit, toggle_active
- 13 tests cover all behaviors

## Deviations from Plan

None — plan executed exactly as written.

TDD flow: tests written first (RED: 13 failures with 404s), then implementation (GREEN: 13 passed). No refactor phase needed.

## Known Stubs

None. All views are fully implemented with real database queries.

## Threat Model Coverage

| Threat | Status | Implementation |
|--------|--------|----------------|
| T-06-03 Admin access control | Mitigated | admin_required decorator checks is_admin, returns 403 |
| T-06-04 Unauthorized quota modification | Mitigated | all quota/toggle views protected by admin_required |

## Self-Check: PASSED

| Item | Status |
|------|--------|
| server/webui/core/decorators.py | FOUND (in commit 0f3765e) |
| server/webui/admin_panel/views.py | FOUND (in commit 0f3765e) |
| server/webui/admin_panel/forms.py | FOUND (in commit 0f3765e) |
| server/webui/admin_panel/urls.py | FOUND (in commit 0f3765e) |
| server/webui/admin_panel/templates/admin_panel/dashboard.html | FOUND |
| server/webui/admin_panel/templates/admin_panel/partials/user_table.html | FOUND |
| server/webui/admin_panel/templates/admin_panel/partials/user_row.html | FOUND |
| server/webui/admin_panel/templates/admin_panel/user_detail.html | FOUND |
| server/webui/tests/test_admin.py | FOUND (13 tests, all passing) |
| commit 0f3765e | FOUND |
