# Phase 8: Android & WebUI UX Polish - Context

**Gathered:** 2026-05-27
**Status:** Ready for planning

<domain>
## Phase Boundary

Quality-of-life improvements across both the Android app and the Cloud Server WebUI. No new sensors, no architectural changes, no new backend services. Delivers:
- English-only inline comments + expanded algorithmic WHY comments in 6 hot-spot files (Android)
- KDoc `@param`/`@return` tags on public functions in the 6 hot-spot files (Android)
- `HorizontalDivider` between SettingsSections inside multi-section CategoryCards (Android)
- Checkbox-based bulk select (+ "Select all") with Bulk Archive and Bulk Delete on the event list (WebUI)
- Archived events accessible via "Status" filter dropdown; `is_archived` field on the event model (WebUI)
- Admin SMTP configuration UI at a new admin_panel/settings/ page (WebUI)
- Delete button for revoked devices with `hx-confirm` before permanent DB removal (WebUI)

</domain>

<decisions>
## Implementation Decisions

### Archived Events (WebUI)

- **D-01:** Archive filter is presented as a "Status" dropdown in the existing filter bar — options: Active (default), Archived, All. Consistent with current filter UX; no new tab component needed.
- **D-02:** Archive is soft-hide and reversible. Sets `is_archived=True` on the event model. Archived events are excluded from the default Active view; unarchive is supported.
- **D-03:** Bulk action confirmation dialog uses an Alpine.js modal (dark overlay, shows count of affected events before confirming). Consistent with the existing WebUI dark theme; Alpine.js is already in the stack.

### SMTP Configuration (WebUI Admin)

- **D-04:** SMTP settings live on a new dedicated page at `admin_panel/settings/` (not in the notifications app). SMTP is a system-level concern, not a per-user preference — keeping it in admin_panel/ matches that separation.
- **D-05:** SMTP password is encrypted using Fernet symmetric encryption (Python `cryptography` library). Key is sourced from a `HAVEN_SECRET_KEY` environment variable. Does not depend on per-user passwords.

### SettingsScreen Dividers (Android)

- **D-06:** `HorizontalDivider` is placed between `SettingsSection` composables (labeled groups) within a `CategoryCard`. It does NOT go between individual rows (RadioRow, SensorToggleRow, etc.) within a section.
- **D-07:** Dividers are added only to CategoryCards that contain 2 or more SettingsSections. Single-section cards are skipped (nothing to separate).

### Revoked Device Deletion (WebUI)

- **D-08:** The Delete button on revoked devices uses `hx-confirm` (browser confirm dialog: "Delete this device permanently?") before the HTMX POST fires. Consistent with the existing Revoke button pattern. No separate modal needed.
- **D-09:** Deletion permanently removes the DB row (no soft-delete). After HTMX POST, the `<tr>` is swapped out inline (same HTMX swap pattern as the existing revoke action).

### Claude's Discretion

- Comment translation scope: all Kotlin files with German inline comments get English translations. Priority: the 6 algorithmic hot spots (SensorFusionEngine, LightMonitor, FusedMotionMonitor, CameraAnalyzer, PerceptualHashDetector, HavenObjectDetector) plus any other files with German inline comments found during the pass (MonitorService.kt and ZoneEditorScreen.kt have German inline comments).
- KDoc completeness strategy: add `@param`/`@return` only where genuinely useful (non-trivial parameters / non-obvious return values) — do not add boilerplate tags just to have them.
- Bulk select HTMX wiring: implementation detail for the planner (checkboxes + form or Alpine state).
- SMTP model field layout: admin can set host, port, username, password, and TLS toggle — exact form design is Claude's discretion.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Android — Settings UI Pattern
- `app/src/main/java/org/havenapp/main/ui/settings/SettingsScreen.kt` — existing CategoryCard, SettingsSection, RadioRow, SensorToggleRow composables; where dividers must be added
- `CLAUDE.md` §5 — coding conventions (comment style, KDoc rules, Compose patterns)

### Android — Algorithmic Hot Spots (comment + KDoc targets)
- `app/src/main/java/org/havenapp/main/detection/SensorFusionEngine.kt` — Complementary Filter algorithm
- `app/src/main/java/org/havenapp/main/sensor/LightMonitor.kt` — dual-rate EMA baseline, cross-sensor suppression
- `app/src/main/java/org/havenapp/main/sensor/FusedMotionMonitor.kt` — fused accel+gyro monitor
- `app/src/main/java/org/havenapp/main/media/CameraAnalyzer.kt` — 3-stage pipeline (Luma → pHash → TFLite); has German inline comments to translate
- `app/src/main/java/org/havenapp/main/detection/PerceptualHashDetector.kt` — perceptual hashing algorithm
- `app/src/main/java/org/havenapp/main/detection/HavenObjectDetector.kt` — TFLite/EfficientDet wrapper

### Android — Additional files with German comments (translate, no WHY expansion needed)
- `app/src/main/java/org/havenapp/main/MonitorService.kt` — service orchestrator (German inline comments found)
- `app/src/main/java/org/havenapp/main/ui/settings/ZoneEditorScreen.kt` — zone editor (German inline comments found)

### WebUI — Event List (bulk select + archive filter)
- `server/webui/events/templates/events/list.html` — event list template to modify
- `server/webui/events/templates/events/partials/event_row.html` — event row partial (add checkbox)
- `server/webui/events/templates/events/partials/event_table.html` — event table partial
- `server/webui/events/views.py` — event list view (add Status filter, bulk archive/delete endpoints)
- `server/webui/events/models.py` — event model (add `is_archived` boolean field + migration)

### WebUI — Device List (revoked device deletion)
- `server/webui/devices/templates/devices/partials/device_row.html` — add Delete button for revoked devices
- `server/webui/devices/views.py` — add delete view/endpoint

### WebUI — Admin SMTP settings
- `server/webui/admin_panel/` — admin panel app structure (new settings/ page goes here)
- `server/webui/notifications/models.py` — reference for how existing notification config is structured (do not duplicate pattern here; SMTP is system-level)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `CategoryCard` + `SettingsSection` composables in `SettingsScreen.kt`: divider goes between SettingsSections inside CategoryCard.
- `HorizontalDivider` from `androidx.compose.material3` — already available, just needs to be inserted.
- `hx-confirm` attribute on existing Revoke button in `device_row.html` — exact same pattern for the Delete button.
- Alpine.js `x-data`/`x-show` for modal — already used elsewhere in the WebUI (Phase 6).

### Established Patterns
- HTMX inline swap (`hx-target="#device-{id}"`, `hx-swap="outerHTML"`) — established for revoke; use same for delete.
- Filter form with HTMX (`hx-get`, `hx-target="#event-table"`, `hx-trigger="change"`) — add Status dropdown to same form.
- `SettingsSection` takes a title + content lambda — the divider sits between sequential SettingsSections in the outer `CategoryCard` Column.
- Fernet encryption: `cryptography` is likely already in `pyproject.toml`; verify before adding dep.

### Integration Points
- Event model: add `is_archived = models.BooleanField(default=False)` + Alembic migration (FastAPI DB) AND Django migration (WebUI models.py since it uses unmanaged models synced with FastAPI schema — check whether Django models are managed or unmanaged).
- Admin SMTP settings: new URL route in `admin_panel/urls.py`, new view + template, new model or singleton DB table for SMTP config.

</code_context>

<specifics>
## Specific Ideas

- Bulk select: "Select all" checkbox in the table header that toggles all row checkboxes. Alpine.js is natural here to keep checkbox state without a full form submit.
- Status dropdown default: show "Active" events by default (no change to existing behavior when Status is not specified in query params).
- Divider styling: use `HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))` to give breathing room — consistent with existing Spacer spacing in the screen.
- SMTP form fields: Host, Port (int), Username, Password (masked input), Use TLS (checkbox), and a "Test connection" button that sends a test email to the admin's own address.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 8-android-webui-ux-polish*
*Context gathered: 2026-05-27*
