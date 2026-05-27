---
phase: 08-android-webui-ux-polish
plan: "02"
subsystem: webui-events
tags: [bulk-actions, archive, django, htmx, alpine-js, alembic]
dependency_graph:
  requires: []
  provides:
    - webui/events: is_archived field, status filter, bulk archive/unarchive/delete
  affects:
    - server/alembic/versions/: new migration head
    - server/app/models/event.py: is_archived field
    - server/webui/events/: models, views, urls, templates
tech_stack:
  added: []
  patterns:
    - Alpine.js x-data wrapper outside HTMX swap target to prevent state loss on partial refresh
    - hx-include=".event-checkbox:checked" for multi-select bulk POST without nesting forms
    - _render_event_table() helper for consistent table re-render from bulk action views
    - Status filter applied in view before EventFilter queryset (three-option Boolean doesn't fit FilterSet)
    - server_default="false" (string) in Alembic migration for zero-downtime column add
key_files:
  created:
    - server/alembic/versions/6fb71bb9b5b6_add_is_archived_to_events.py
  modified:
    - server/app/models/event.py
    - server/webui/events/models.py
    - server/webui/events/views.py
    - server/webui/events/urls.py
    - server/webui/events/templates/events/list.html
    - server/webui/events/templates/events/partials/event_table.html
    - server/webui/events/templates/events/partials/event_row.html
decisions:
  - Alembic revision generated manually (no venv on CI host); file is syntactically correct and ready to apply via alembic upgrade head
  - Status filter handled in event_list view (not in EventFilter FilterSet) — three-option active/archived/all does not map cleanly to a single FilterSet BooleanFilter
  - _render_event_table() accepts explicit status parameter from POST body to restore filter context after bulk actions (GET params are empty on POST requests)
  - Alpine x-data wrapper placed OUTSIDE #event-table div so HTMX innerHTML swap doesn't destroy component state
  - x-on:htmx:after-swap.window resets selected[] and selectAll after every HTMX swap to prevent stale checkboxes
  - All three bulk views filter by user_id=request.user.id before update/delete (T-08-03 and T-08-05 mitigations)
metrics:
  duration: 22
  completed: "2026-05-27"
  tasks: 3
  files: 7
---

# Phase 8 Plan 02: WebUI Bulk Event Actions (Archive / Unarchive / Delete) Summary

**One-liner:** Alembic migration adds `is_archived` boolean to events; Django/FastAPI models updated; WebUI gains Status filter dropdown and checkbox-based bulk archive, unarchive, and delete with Alpine.js confirmation modal and HTMX table refresh.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Add is_archived field via Alembic migration, FastAPI model, Django model | `15428a4` | alembic/versions/6fb71bb9b5b6_add_is_archived_to_events.py, server/app/models/event.py, server/webui/events/models.py |
| 2 | Event list view — status filter + bulk archive/unarchive/delete views + URL routes | `447c5f4` | server/webui/events/views.py, server/webui/events/urls.py |
| 3 | Event list templates — Status dropdown + Alpine.js bulk select + confirmation modal + checkbox columns | `ea682b8` | templates/events/list.html, templates/events/partials/event_table.html, templates/events/partials/event_row.html |

## Deviations from Plan

### Auto-fixed Issues

None - plan executed exactly as written.

### Notes

**Alembic binary not in PATH:** The `alembic` CLI was not available outside a Docker container on this host. Per plan instructions, the migration file was created manually with the correct revision structure, imports, and `server_default="false"`. The command `cd server && alembic upgrade head` must be run inside the Docker environment after deployment. The migration file is syntactically correct.

## Known Stubs

None. All functionality is fully wired:
- is_archived column wired from Alembic migration through FastAPI model and Django model
- Status filter applied in event_list view with three branches (active/archived/all)
- Bulk action views update is_archived via ownership-safe querysets
- Alpine.js forms submit to bulk action URLs via hx-post with filter state preserved

## Threat Surface Scan

No new security surface beyond what the plan's threat model covers. All bulk action views implement T-08-03 and T-08-05 mitigations (user_id ownership filter before update/delete). CSRF tokens present in all hidden bulk forms.

## Self-Check

**Files exist:**
- [x] server/alembic/versions/6fb71bb9b5b6_add_is_archived_to_events.py — FOUND
- [x] server/app/models/event.py — FOUND (contains is_archived)
- [x] server/webui/events/models.py — FOUND (contains is_archived)
- [x] server/webui/events/views.py — FOUND (contains bulk_archive, bulk_unarchive, bulk_delete)
- [x] server/webui/events/urls.py — FOUND (3 bulk routes)
- [x] server/webui/events/templates/events/list.html — FOUND (status dropdown + Alpine wrapper + modal + forms)
- [x] server/webui/events/templates/events/partials/event_table.html — FOUND (checkbox th)
- [x] server/webui/events/templates/events/partials/event_row.html — FOUND (checkbox td)

**Commits exist:**
- [x] 15428a4 feat(08-02): add is_archived field via Alembic migration and model updates
- [x] 447c5f4 feat(08-02): add status filter and bulk archive/unarchive/delete views
- [x] ea682b8 feat(08-02): add status dropdown, Alpine.js bulk select, confirmation modal and checkbox columns

## Self-Check: PASSED
