---
phase: 06-web-ui
plan: "04"
subsystem: server/webui/events
tags: [django, htmx, django-filter, tailwind, event-browser, video-streaming]
dependency_graph:
  requires:
    - 06-01 (Django scaffold, unmanaged models, test infra)
  provides:
    - Event list view with 4-way filtering and HTMX partial rendering
    - Event detail view with triggers and AI analysis display
    - Video streaming view (unencrypted) and encrypted video message
    - Event deletion with media file cleanup and quota counter update
  affects:
    - server/webui/events/urls.py (was stub, now fully wired)
tech_stack:
  added:
    - django-filter 25.2 (EventFilter with DateTimeFilter, ChoiceFilter, NumberFilter, CharFilter)
  patterns:
    - HTMX partial rendering via request.htmx flag (django-htmx middleware)
    - StreamingHttpResponse for video file serving
    - get_object_or_404 with user_id= for user isolation on every view
    - Custom templatetag (query_replace) for pagination URL construction
    - TDD: tests written first (RED), then views/templates (GREEN)
key_files:
  created:
    - server/webui/events/filters.py
    - server/webui/events/views.py
    - server/webui/events/templatetags/__init__.py
    - server/webui/events/templatetags/event_tags.py
    - server/webui/events/templates/events/list.html
    - server/webui/events/templates/events/partials/event_table.html
    - server/webui/events/templates/events/partials/event_row.html
    - server/webui/events/templates/events/detail.html
    - server/webui/tests/test_events.py
  modified:
    - server/webui/events/urls.py (stub -> 4 URL patterns)
decisions:
  - "query_replace template tag takes request as argument: allows preserving existing filter params when paginating"
  - "Detail URL links used in isolation tests (not bare IDs): bare integer IDs appear in HTML page structure, causing false positives"
  - "secrets.token_hex(16) for test device app_key: prevents UNIQUE constraint failures across test functions sharing the session-scoped DB"
  - "EVENT_TYPES constant defined in views.py and passed via template context: avoids Django template split filter (not built-in)"
  - "StreamingHttpResponse opens file directly: appropriate for small-to-medium video files; no chunked generator needed"
metrics:
  duration_seconds: 1200
  completed_date: "2026-04-09"
  tasks_completed: 2
  files_created: 9
  files_modified: 1
  tests_passing: 18
---

# Phase 6 Plan 4: Event Browser Summary

**One-liner:** Django event browser with django-filter 4-way filtering, HTMX partial table updates, event detail with trigger list and AI analysis, StreamingHttpResponse video serving, and quota-aware event deletion — 18 tests passing.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Event list with django-filter and HTMX partial rendering | aa2af2c | filters.py, views.py, list.html, event_table.html, event_row.html, event_tags.py, test_events.py |
| 2 | Event detail, video serving, and event deletion | 3f922d9 | detail.html (views.py already committed in Task 1) |

## What Was Built

**EventFilter** (`events/filters.py`): django-filter FilterSet with:
- `timestamp__gte` / `timestamp__lte`: DateTimeFilter for date range
- `event_type`: CharFilter with `iexact` lookup
- `severity`: ChoiceFilter with LOW/MEDIUM/HIGH/CRITICAL options
- `device_id`: NumberFilter for device scoping

**event_list view**: `@login_required`, filters events to `user_id=request.user.id`, applies `EventFilter`, paginates at 25 per page. Returns `event_table.html` partial when `request.htmx` is true (HTMX partial swap), full `list.html` otherwise.

**list.html**: Filter form with `hx-get`, `hx-target="#event-table"`, `hx-trigger="change, submit"`, `hx-push-url="true"`. Severity dropdowns, device dropdown, date inputs, event type select built from `EVENT_TYPES` context variable.

**event_table.html / event_row.html**: Table with severity-colored badges (teal=LOW, amber=MEDIUM, orange=HIGH, red=CRITICAL), event type icons, media indicator (lock icon for encrypted), "View" detail link. Pagination links use HTMX for partial updates.

**event_detail view**: Fetches triggers ordered by `created_at`, fetches `AnalysisResult` via `.first()`, parses JSON labels. Passes `labels` list to template.

**detail.html**: Two-column grid (event info + media left, triggers + AI analysis right). HTML5 `<video>` for unencrypted media with `src` pointing to `serve_video`. Yellow warning box for encrypted video. AI analysis card: backend badge, label chips, confidence progress bar, description blockquote.

**serve_video view**: `StreamingHttpResponse` with `Content-Type: video/mp4` and `Content-Disposition: inline`. Returns 403 with plain-text explanation for encrypted videos. Returns 404 for missing file or no media_path.

**event_delete view**: POST-only, removes media file from `MEDIA_ROOT`, decrements `current_storage_bytes` and `current_event_count` on the user (clamped to 0), deletes the Event row (DB CASCADE handles EventTrigger + AnalysisResult), redirects to event list with success message.

**query_replace template tag** (`events/templatetags/event_tags.py`): Simple tag that copies `request.GET`, applies kwargs overrides, returns `urlencode()` — used for pagination links that preserve active filters.

## Verification Results

```
18 passed in 3.63s (events tests only)
85 passed in 15.02s (full suite)
```

All success criteria met:
- Event list with 4-way filtering: date range, device, event type, severity
- HTMX partial rendering returns table-only HTML (no DOCTYPE/html tags)
- Pagination works at 25 events per page
- Event detail shows all triggers and AI analysis (labels, confidence, description)
- Video streaming with correct Content-Type for unencrypted; 403 for encrypted
- Event deletion removes DB row, media file, and updates user quota counters
- User isolation enforced via `get_object_or_404(user_id=request.user.id)` on all views
- 18 tests pass across list + filter + pagination + detail + video + delete

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed Django template `split` filter not built-in**
- **Found during:** Task 1 first test run
- **Issue:** `list.html` used `"CAMERA,MICROPHONE,..."|split:","` which is not a built-in Django template filter — caused `TemplateSyntaxError`
- **Fix:** Moved event types list to `EVENT_TYPES` constant in `views.py`, passed as `event_types` context variable
- **Files modified:** `events/views.py`, `events/templates/events/list.html`
- **Commit:** aa2af2c

**2. [Rule 1 - Bug] Fixed flaky isolation test assertions using bare integer IDs**
- **Found during:** Task 1 test run (`test_event_list_excludes_other_users`)
- **Issue:** `assert str(event_id) not in content` fails because small integers (e.g. `2`) appear in CSS classes, page numbers, and other HTML — causing false negatives
- **Fix:** Changed all "not in content" assertions to check for detail URL pattern `/events/{id}/` instead of bare ID string
- **Files modified:** `server/webui/tests/test_events.py`
- **Commit:** aa2af2c

**3. [Rule 1 - Bug] Fixed UNIQUE constraint collision on test device app_key**
- **Found during:** Task 1 test run (`test_event_filter_by_device`)
- **Issue:** `make_device` generated a fixed app_key based on name prefix — multiple tests creating devices with same name caused SQLite UNIQUE constraint failure across session-scoped DB
- **Fix:** Changed `make_device` to use `secrets.token_hex(16)` for unique app_key per call
- **Files modified:** `server/webui/tests/test_events.py`
- **Commit:** aa2af2c

## Known Stubs

None — all views are fully implemented. The `event_types` dropdown in `list.html` is a static list (not dynamic from DB), which is correct: event types are a fixed enum defined by the Android app, not user-configurable data.

## Threat Model Coverage

| Threat | Status | Implementation |
|--------|--------|----------------|
| T-06-03 User data isolation | Mitigated | `get_object_or_404(user_id=request.user.id)` on all views |
| T-06-04 Unauthorized video access | Mitigated | `@login_required` + user_id check before streaming |
| T-06-05 Encrypted video exposure | Mitigated | `is_encrypted` check returns 403 before reading file |

## Self-Check: PASSED

| Item | Status |
|------|--------|
| server/webui/events/filters.py | FOUND |
| server/webui/events/views.py | FOUND |
| server/webui/events/urls.py | FOUND (4 URL patterns) |
| server/webui/events/templates/events/list.html | FOUND |
| server/webui/events/templates/events/partials/event_table.html | FOUND |
| server/webui/events/templates/events/partials/event_row.html | FOUND |
| server/webui/events/templates/events/detail.html | FOUND |
| server/webui/events/templatetags/event_tags.py | FOUND |
| server/webui/tests/test_events.py | FOUND |
| commit aa2af2c (Task 1) | FOUND |
| commit 3f922d9 (Task 2) | FOUND |
| 18 tests passing | VERIFIED |
