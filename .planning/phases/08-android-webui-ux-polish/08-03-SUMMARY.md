---
phase: 08-android-webui-ux-polish
plan: "03"
subsystem: webui
tags: [smtp, email, admin-panel, device-management, htmx, fernet, django]
dependency_graph:
  requires: []
  provides:
    - admin_panel:smtp_settings view and URL
    - admin_panel:smtp_test view and URL
    - SMTPSettings singleton model with Fernet-encrypted password storage
    - 0002_smtpsettings migration
    - devices:device_delete view and URL
  affects:
    - server/webui/admin_panel/models.py
    - server/webui/admin_panel/forms.py
    - server/webui/admin_panel/views.py
    - server/webui/admin_panel/urls.py
    - server/webui/devices/views.py
    - server/webui/devices/urls.py
tech_stack:
  added: []
  patterns:
    - "SMTPSettings singleton via get_or_create(pk=1) — mirrors AISettings pattern"
    - "Fernet key derivation SHA-256(SECRET_KEY) — mirrors app/services/totp.py"
    - "asyncio.run(aiosmtplib.send()) in sync Django view — no bare await"
    - "HTMX outerHTML swap with empty HttpResponse for row deletion"
key_files:
  created:
    - server/webui/admin_panel/migrations/0002_smtpsettings.py
    - server/webui/admin_panel/templates/admin_panel/smtp_settings.html
    - server/webui/admin_panel/templates/admin_panel/partials/smtp_settings_form.html
  modified:
    - server/webui/admin_panel/models.py
    - server/webui/admin_panel/forms.py
    - server/webui/admin_panel/views.py
    - server/webui/admin_panel/urls.py
    - server/webui/admin_panel/templates/admin_panel/dashboard.html
    - server/webui/devices/views.py
    - server/webui/devices/urls.py
    - server/webui/devices/templates/devices/partials/device_row.html
decisions:
  - "Use Django SECRET_KEY (not a new env var) for Fernet key derivation — consistent with app/services/totp.py (D-05 resolved)"
  - "smtp_password field is write-only: blank submission preserves existing encrypted ciphertext"
  - "asyncio.run() chosen over async_to_sync for aiosmtplib — Django is WSGI (sync), asyncio.run() creates an isolated event loop without coupling to Django Channels"
  - "hx-confirm browser dialog (not Alpine modal) for device deletion — simpler, sufficient for admin flow (D-08)"
  - "HttpResponseForbidden returned for active device deletion — prevents accidental loss of live devices (T-08-12)"
metrics:
  duration_minutes: 25
  completed_date: "2026-05-27"
  tasks_completed: 3
  files_changed: 8
  files_created: 3
---

# Phase 8 Plan 03: SMTP Configuration and Device Deletion Summary

**One-liner:** Admin SMTP settings page with Fernet-encrypted password storage and revoked device permanent deletion with HTMX inline swap.

## What Was Built

### Task 1 — SMTPSettings Model, Migration, SmtpSettingsForm (commit: 432f91d)

Added `SMTPSettings` singleton model to `admin_panel/models.py` following the exact pattern of the existing `AISettings` class. Fields include `smtp_host`, `smtp_port`, `smtp_user`, `smtp_password_encrypted` (TextField — accommodates Fernet ciphertext length), `smtp_from`, `use_tls`, and `updated_at`. The `get()` classmethod uses `get_or_create(pk=1)`.

Created `0002_smtpsettings.py` migration manually (Docker not available in worktree) using `migrations.CreateModel` with dependency on `("admin_panel", "0001_initial")`.

Added `SmtpSettingsForm` to `forms.py` with Tailwind-styled widgets matching `AISettingsForm`. Password field uses `PasswordInput` with `autocomplete="new-password"` and placeholder "Leave blank to keep current". Includes `clean_smtp_port()` validation.

### Task 2 — Views, URLs, Templates, Dashboard Nav (commit: b3d8fbf)

Added `_get_fernet()` module-level helper in `views.py` using `hashlib.sha256(django_settings.SECRET_KEY.encode()).digest()` — exact port of the pattern from `app/services/totp.py`. Uses `django_settings` alias to avoid shadowing.

Implemented `smtp_settings()` view with `@admin_required`:
- GET: loads `SMTPSettings.get()`, populates form excluding the password field (write-only)
- POST: Fernet-encrypts non-blank password; blank preserves existing ciphertext; HTMX partial swap on success

Implemented `smtp_test()` view with `@admin_required @require_POST`:
- Decrypts stored password with `InvalidToken` guard
- Sends test email via `asyncio.run(aiosmtplib.send(..., timeout=10))`
- Inline HTMX result (green success / red error); plaintext password never logged

Registered `admin_panel:smtp_settings` at `settings/` and `smtp_test` at `settings/test/`.

Created `smtp_settings.html` (full page, extends `base.html`) and `smtp_settings_form.html` (HTMX partial with `hx-target="#smtp-settings-form"`, `id="smtp-test-result"` div).

Added SMTP Settings quick-link to admin dashboard alongside AI Settings link.

### Task 3 — Device Deletion (commit: d1ff146)

Added `device_delete()` view in `devices/views.py` with `@login_required @require_POST`:
- `get_object_or_404(Device, id=device_id, user_id=request.user.id)` — ownership check (T-08-09)
- `if device.is_active: return HttpResponseForbidden(...)` — prevents active device deletion (T-08-12)
- HTMX: `return HttpResponse("")` — empty body triggers `outerHTML` swap to remove `<tr>`
- Non-HTMX: redirects with success message

Registered `devices:device_delete` at `<int:device_id>/delete/`.

Added Delete button in the `{% else %}` (revoked) branch of `device_row.html` with:
- `hx-post="{% url 'devices:device_delete' device.id %}"`
- `hx-target="#device-{{ device.id }}"`, `hx-swap="outerHTML"`
- `hx-confirm="Delete this device permanently? This cannot be undone."`

Active device rows remain unchanged — no Delete button.

## Deviations from Plan

None — plan executed exactly as written. The Django migration was created manually (plan anticipated this fallback since Docker is not available in the worktree execution environment).

## Threat Model Coverage

All STRIDE threats addressed:

| Threat ID | Status |
|-----------|--------|
| T-08-07 | Mitigated — smtp_password_encrypted stores Fernet ciphertext only; decrypt for test send only; never logged |
| T-08-08 | Mitigated — @admin_required on smtp_settings and smtp_test |
| T-08-09 | Mitigated — ownership check in device_delete via user_id=request.user.id |
| T-08-10 | Mitigated — aiosmtplib.send() called with timeout=10 |
| T-08-11 | Mitigated — @require_POST + HTMX global CSRF in base.html |
| T-08-12 | Mitigated — device_delete checks device.is_active, returns 403 |
| T-08-13 | Accepted — SHA-256(SECRET_KEY) consistent with existing totp.py pattern |

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | 432f91d | SMTPSettings model, 0002 migration, SmtpSettingsForm |
| 2 | b3d8fbf | smtp_settings/smtp_test views, URLs, templates, dashboard nav |
| 3 | d1ff146 | device_delete view, URL, Delete button in device_row |

## Self-Check

### Created files exist:
- server/webui/admin_panel/migrations/0002_smtpsettings.py: FOUND
- server/webui/admin_panel/templates/admin_panel/smtp_settings.html: FOUND
- server/webui/admin_panel/templates/admin_panel/partials/smtp_settings_form.html: FOUND

### Commits exist:
- 432f91d: FOUND
- b3d8fbf: FOUND
- d1ff146: FOUND

## Self-Check: PASSED
