---
slug: cloud-archive-not-working
status: resolved
trigger: "Archive function ('Archivieren') in Haven 2.0 Cloud Server web UI does not work"
created: 2026-05-28
updated: 2026-05-28
---

## Symptoms

- Archive button/action in Django web UI does not work
- Two prior errors already fixed: (1) missing DB column `is_archived` (Alembic migration 6fb71bb9b5b6 applied), (2) TemplateSyntaxError in list.html line 102 (`&quot;` → `"`)
- Selecting events and clicking "Archive Events" then "Confirm" had no visible effect

## Current Focus

**hypothesis:** RESOLVED — see Resolution
**next_action:** none

## Evidence

- timestamp: 2026-05-28T09:12:00
  file: server/webui/events/templates/events/list.html
  finding: >
    Line 154: `@click="showConfirmModal = false; $refs[confirmAction + 'Form'].submit()"`
    calls the native `HTMLFormElement.submit()` method. This method does NOT dispatch
    a `submit` event — it bypasses all event listeners. HTMX hooks into form submission
    via the `submit` event listener on the element. Since the event is never fired,
    HTMX never intercepts the form, `hx-include` is never evaluated, `event_ids` are
    not collected, and the POST either sends an empty `event_ids` list (no events
    archived) or performs a full-page redirect instead of an HTMX partial swap.

- timestamp: 2026-05-28T09:12:00
  file: server/webui/events/views.py
  finding: >
    `bulk_archive` view at line 270: `if event_ids:` — if `event_ids` is empty
    (which it always was due to the .submit() bug), the `.update()` call is skipped
    entirely and zero events are archived. The view then redirects (non-HTMX path)
    or re-renders the table (HTMX path) with no changes.

## Resolution

**root_cause:** `HTMLFormElement.submit()` called from Alpine.js `@click` handler bypasses HTMX event interception. HTMX listens for the DOM `submit` event to trigger `hx-post`, `hx-include`, and other HTMX attributes. Native `.submit()` does not fire this event, so HTMX never runs, `event_ids` are never serialized into the POST body, and no events are archived.

**fix:** Replaced `$refs[confirmAction + 'Form'].submit()` with a helper method `submitBulkForm()` defined in the Alpine `x-data` block. The helper uses `form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))` which fires the `submit` event that HTMX intercepts, allowing `hx-post`, `hx-include=".event-checkbox:checked"`, and `hx-target="#event-table"` to work correctly. Changed file: `server/webui/events/templates/events/list.html` line 154 (Confirm button `@click` handler) and added `submitBulkForm()` method to Alpine `x-data` at line 108.
