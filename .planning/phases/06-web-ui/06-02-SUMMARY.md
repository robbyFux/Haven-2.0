---
phase: 06-web-ui
plan: "02"
subsystem: server/webui/accounts
tags: [django, auth, totp, bcrypt, tailwind, htmx]
dependency_graph:
  requires:
    - 06-01 (Django scaffold, HavenUser model, HavenAuthBackend, core/totp.py)
  provides:
    - Full user self-service auth: registration, login, TOTP 2FA, profile, change password, delete account
    - 8 Django views in accounts/views.py
    - 6 form classes in accounts/forms.py
    - 8 URL patterns in accounts/urls.py
    - 7 Tailwind dark-theme templates
    - 22 pytest-django tests covering all flows
  affects:
    - server/webui/tests/conftest.py (added migrate --run-syncdb for session table)
    - server/webui/config/settings.py (added test-mode STORAGES override)
    - server/webui/events/urls.py (stub list URL so auth redirects resolve)
tech_stack:
  added: []
  patterns:
    - Two-step TOTP login via session key pending_2fa_user_id
    - bcrypt verify via passlib CryptContext in views (not backend)
    - update_session_auth_hash() to keep session valid after password change
    - HAVEN_OPEN_REGISTRATION env var gates registration (default true)
    - login_required decorator on all protected views
    - Tailwind dark theme: bg-gray-800 cards, teal-600 buttons, gray-700 inputs
key_files:
  created:
    - server/webui/accounts/forms.py
    - server/webui/accounts/views.py
    - server/webui/accounts/templates/accounts/login.html
    - server/webui/accounts/templates/accounts/register.html
    - server/webui/accounts/templates/accounts/totp_verify.html
    - server/webui/accounts/templates/accounts/totp_setup.html
    - server/webui/accounts/templates/accounts/profile.html
    - server/webui/accounts/templates/accounts/change_password.html
    - server/webui/accounts/templates/accounts/delete_account.html
    - server/webui/tests/test_accounts.py
    - server/webui/events/views.py
  modified:
    - server/webui/accounts/urls.py (stub → 8 real URL patterns)
    - server/webui/tests/conftest.py (added migrate --run-syncdb for django_session)
    - server/webui/config/settings.py (added test-mode STORAGES override)
    - server/webui/events/urls.py (added stub events:list route)
decisions:
  - "Two-step TOTP: login_view stores pending_2fa_user_id in session; totp_verify_view completes auth_login() — keeps 1FA and 2FA paths cleanly separated"
  - "HAVEN_OPEN_REGISTRATION env var controls registration gate (default true) — allows self-hosted deployments to lock down signups"
  - "user_key generated with secrets.token_hex(32) (64 hex chars) vs 16 in create_user factory — stronger entropy for production registrations"
  - "events/views.py stub added to worktree so events:list resolves during auth redirect assertions — full implementation deferred to plan 06-03"
  - "conftest.py updated with call_command('migrate', '--run-syncdb') to create django_session in :memory: SQLite — worktree had lighter conftest than main repo"
  - "config/settings.py gains test-mode STORAGES override (StaticFilesStorage) — worktree was missing this compared to main repo which already had it"
metrics:
  duration_seconds: 480
  completed_date: "2026-04-09"
  tasks_completed: 2
  files_created: 11
  files_modified: 4
  tests_passing: 22
---

# Phase 6 Plan 2: Auth Views, Forms, and Templates Summary

**One-liner:** Complete user self-service auth — registration + bcrypt, login + optional TOTP 2FA, 2FA setup with QR code, change password with session preservation, account deletion with username confirmation — 22 tests, all passing.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Auth forms, views, templates: registration, login, TOTP 2FA, profile, logout | a6ceb76 | forms.py, views.py, urls.py, 5 templates, test_accounts.py |
| 2 | Change password and delete account views | a6ceb76 | change_password.html, delete_account.html (included in same commit per plan) |

## What Was Built

**6 form classes** (`accounts/forms.py`):
- `RegisterForm` — username + password + confirm; validates match and uniqueness
- `LoginForm` — username + password
- `TotpForm` — single 6-digit code field with numeric input mode
- `TotpSetupConfirmForm` — code confirmation during 2FA setup
- `ChangePasswordForm` — current + new + new-confirm; validates new passwords match
- `DeleteAccountForm` — confirm_username field matched against request.user.username in view

**8 views** (`accounts/views.py`):
- `register_view` — creates HavenUser with `_pwd_context.hash(password)` + `haven_u_{secrets.token_hex(32)}` user_key; checks `HAVEN_OPEN_REGISTRATION` env var
- `login_view` — calls `authenticate()` → if TOTP enabled stores `pending_2fa_user_id` in session and redirects to verify; otherwise calls `auth_login()` directly
- `totp_verify_view` — guards on `pending_2fa_user_id`, decrypts secret via `core.totp.decrypt_totp_secret`, verifies with `verify_totp(secret, code)`
- `totp_setup_view` — GET generates secret + QR PNG → base64 inline; POST verifies code, calls `encrypt_totp_secret`, saves `totp_enabled=True`
- `profile_view` — renders username + 2FA status badge + action links
- `logout_view` — POST only, `auth_logout()` + redirect to login
- `change_password_view` — verifies current via `_pwd_context.verify()`, hashes new, calls `update_session_auth_hash()` to preserve session
- `delete_account_view` — confirms username match, `auth_logout()` before `user.delete()` to avoid session corruption

**7 Tailwind dark-theme templates** — all extend `base.html`, use `bg-gray-800` card containers, `bg-gray-700 border border-gray-600` inputs, `bg-teal-600` submit buttons. Delete account template uses red border (`border-red-800`) and red button (`bg-red-700`).

**22 tests** (`tests/test_accounts.py`) — TDD: tests written first (RED), implementation written to pass (GREEN). Covers all plan behaviors: register, duplicate username, password mismatch, login valid/invalid, TOTP required redirect, TOTP verify valid/invalid/no-session, 2FA setup QR + confirm, profile, profile-login-required, logout, change password (success, wrong current, mismatch, login-required), delete account (confirmation page, success, wrong confirmation, login-required).

## Verification Results

```
22 passed, 23 warnings in 4.48s
```

Scaffold tests unaffected:
```
25 passed, 1 warning in 1.84s
```

All done criteria met:
- Registration creates user with bcrypt hash and haven_u_ user_key
- Login authenticates via HavenAuthBackend and creates Django session
- TOTP 2FA: login -> pending state in session -> code verify -> auth_login()
- 2FA setup: QR code generated inline, code confirmed, totp_enabled set in DB
- Profile page shows username and 2FA status badge
- Logout clears session, POST only
- Change password verifies current, hashes new, preserves session
- Delete account requires username confirmation, cascades delete, logs out
- All templates styled with Tailwind dark theme

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] django_session table missing in worktree test DB**
- **Found during:** Task 1 test run in worktree
- **Issue:** The worktree's `conftest.py` had a session-scoped `django_db_setup` override that manually created unmanaged model tables but did not call `migrate`, so `django_session` (needed for session-based auth tests) was never created in the `:memory:` SQLite DB. Tests that created a Django session failed with `OperationalError: no such table: django_session`.
- **Fix:** Added `call_command("migrate", "--run-syncdb", verbosity=0)` before the manual table creation loop in `conftest.py`
- **Files modified:** `server/webui/tests/conftest.py`
- **Commit:** a6ceb76

**2. [Rule 1 - Bug] Missing STORAGES test-mode override in worktree settings**
- **Found during:** Task 1 test run in worktree
- **Issue:** The worktree's `config/settings.py` had a `pytest` test-mode block that switched to SQLite `:memory:` but was missing the `STORAGES` override to replace `CompressedManifestStaticFilesStorage` with `StaticFilesStorage`. Template rendering failed with `ValueError: Missing staticfiles manifest entry for 'css/tailwind.css'`. The main repo already had this fix from plan 06-01 but it wasn't in the worktree's base commit.
- **Fix:** Added `STORAGES` override block inside `if "pytest" in sys.modules:`
- **Files modified:** `server/webui/config/settings.py`
- **Commit:** a6ceb76

**3. [Rule 3 - Blocking] events:list URL missing in worktree**
- **Found during:** Task 1 (login tests asserting redirect to `/events/`)
- **Issue:** `login_view` redirects to `events:list` on success. The worktree's `events/urls.py` was a stub with empty `urlpatterns` — `NoReverseMatch` on `events:list`. The main repo had full events views from a parallel plan that ran ahead.
- **Fix:** Created minimal `events/views.py` stub with `event_list` view and wired `path("", views.event_list, name="list")` in `events/urls.py`. Tests only assert on the redirect Location header, not the page content, so the stub template is not needed.
- **Files modified/created:** `server/webui/events/views.py`, `server/webui/events/urls.py`
- **Commit:** a6ceb76

## Known Stubs

| Stub | File | Reason |
|------|------|--------|
| events/views.py stub event_list | server/webui/events/views.py | Minimal stub for redirect URL resolution; full implementation in plan 06-03 |

## Threat Model Coverage

| Threat | Status | Implementation |
|--------|--------|----------------|
| T-06-01 Spoofing (login brute-force) | Mitigated | bcrypt cost factor via passlib + HavenAuthBackend.dummy_verify() timing guard |
| T-06-02 Tampering (CSRF) | Mitigated | CsrfViewMiddleware + {% csrf_token %} on all POST forms |
| T-06-03 TOTP bypass | Mitigated | Two-step: auth_login() only called after both password AND TOTP code verified |
| T-06-07 Session hijacking | Mitigated | SESSION_COOKIE_HTTPONLY=True; update_session_auth_hash() on password change |

## Self-Check: PASSED

| Item | Status |
|------|--------|
| server/webui/accounts/forms.py | FOUND |
| server/webui/accounts/views.py | FOUND |
| server/webui/accounts/urls.py | FOUND |
| server/webui/accounts/templates/accounts/login.html | FOUND |
| server/webui/accounts/templates/accounts/register.html | FOUND |
| server/webui/accounts/templates/accounts/totp_verify.html | FOUND |
| server/webui/accounts/templates/accounts/totp_setup.html | FOUND |
| server/webui/accounts/templates/accounts/profile.html | FOUND |
| server/webui/accounts/templates/accounts/change_password.html | FOUND |
| server/webui/accounts/templates/accounts/delete_account.html | FOUND |
| server/webui/tests/test_accounts.py | FOUND |
| commit a6ceb76 | FOUND |
| 22 tests passing | VERIFIED |
