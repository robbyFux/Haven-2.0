---
phase: 06-web-ui
plan: "01"
subsystem: server/webui
tags: [django, tailwind, htmx, auth, postgresql, docker]
dependency_graph:
  requires: []
  provides:
    - Django project scaffold at server/webui/
    - 5 unmanaged Django models (HavenUser, Device, Event, EventTrigger, AnalysisResult)
    - Custom HavenAuthBackend (bcrypt verification via passlib)
    - TOTP helper compatible with FastAPI's Fernet key derivation
    - Base HTML template with Tailwind CSS + HTMX + Alpine.js
    - pytest-django test infrastructure with SQLite in-memory fixture
  affects:
    - server/docker-compose.yml (webui service added)
    - server/pyproject.toml ([web] and dev deps added)
tech_stack:
  added:
    - Django 5.2.x
    - django-tailwind-cli 4.5.1
    - django-htmx 1.27.0
    - django-filter 25.2
    - whitenoise 6.12.0
    - psycopg2-binary 2.9.11
    - pytest-django 4.12.0
  patterns:
    - AbstractBaseUser with managed=False for shared PostgreSQL table
    - ForeignKey db_constraint=False to avoid DDL conflicts with Alembic-owned tables
    - pytest sys.modules detection to switch settings to SQLite :memory:
    - passlib CryptContext(schemes=["bcrypt"]) for bcrypt verification
    - Session fixture creates unmanaged model tables via schema_editor.create_model()
key_files:
  created:
    - server/Dockerfile.webui
    - server/webui/manage.py
    - server/webui/config/settings.py
    - server/webui/config/urls.py
    - server/webui/config/wsgi.py
    - server/webui/accounts/models.py
    - server/webui/devices/models.py
    - server/webui/events/models.py
    - server/webui/core/auth_backend.py
    - server/webui/core/totp.py
    - server/webui/core/templates/base.html
    - server/webui/core/templates/partials/nav.html
    - server/webui/static/src/input.css
    - server/webui/tailwind.config.js
    - server/webui/pytest.ini
    - server/webui/tests/conftest.py
    - server/webui/tests/test_scaffold.py
    - server/webui/{accounts,devices,events,admin_panel,notifications}/urls.py (stubs)
    - server/webui/{accounts,devices,events,admin_panel,notifications,core}/__init__.py
  modified:
    - server/pyproject.toml (added [web] optional-deps group + pytest-django to dev)
    - server/docker-compose.yml (added webui service on port 8080)
decisions:
  - "AbstractBaseUser with managed=False: avoids Django creating/migrating the users table while still enabling session auth"
  - "ForeignKey db_constraint=False on all cross-table relations: prevents duplicate DDL from Django since Alembic owns all constraints"
  - "pytest sys.modules detection in settings.py: switches to SQLite :memory: without requiring a separate test-settings file"
  - "passlib CryptContext used in both auth backend and create_user factory: consistent bcrypt cost factor"
  - "TOTP helper reads SECRET_KEY from env directly (not Django settings): ensures identical Fernet derivation to FastAPI even before django.setup()"
  - "Stub urls.py in all 5 apps: allows ROOT_URLCONF includes to work now; views implemented in plans 06-02 through 06-06"
metrics:
  duration_seconds: 328
  completed_date: "2026-04-09"
  tasks_completed: 2
  files_created: 32
  files_modified: 2
  tests_passing: 25
---

# Phase 6 Plan 1: Django Project Scaffold and Test Infrastructure Summary

**One-liner:** Django 5.2 project with 5 unmanaged models sharing FastAPI's PostgreSQL tables, bcrypt auth backend, Tailwind+HTMX+Alpine base template, and pytest-django test infra — 25 smoke tests passing.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Django scaffold, unmanaged models, Docker | 0bdda0c | config/settings.py, 5 models, Dockerfile.webui, docker-compose.yml |
| 2 | Auth backend, TOTP helper, templates, tests | 1394080 | core/auth_backend.py, core/totp.py, base.html, tests/ |

## What Was Built

**Django project structure** (`server/webui/`) with six apps: `accounts`, `devices`, `events`, `admin_panel`, `notifications`, `core`. All URL patterns are stubbed and will be filled by subsequent plans (06-02 through 06-06).

**5 unmanaged models** map to the existing Alembic-managed PostgreSQL tables:
- `HavenUser` (db_table="users") — extends AbstractBaseUser; `password_hash` property shim bridges Django's session auth with the bcrypt column
- `Device` (db_table="devices")
- `Event`, `EventTrigger`, `AnalysisResult` (db_table="events", "event_triggers", "analysis_results")
- All ForeignKeys use `db_constraint=False` to prevent Django from generating DDL constraints that would conflict with Alembic-created ones

**Custom auth backend** (`HavenAuthBackend`) verifies passwords via `passlib.context.CryptContext(schemes=["bcrypt"])`. Uses `dummy_verify()` on unknown usernames to prevent timing-based username enumeration (T-06-01).

**TOTP helper** (`core/totp.py`) is a direct port of `server/app/services/totp.py`: SHA-256(SECRET_KEY) → 32 bytes → base64url → Fernet key. Roundtrip verified by test.

**Base template** (`core/templates/base.html`) loads Tailwind CSS (WhiteNoise-served), HTMX 2.0.4 (CDN), Alpine.js 3.14.8 (CDN), injects CSRF token via `hx-headers` on `<body>` (T-06-02 mitigation).

**Test infrastructure**: `pytest.ini` + `conftest.py` with session-scoped fixture that calls `schema_editor.create_model()` for all 5 unmanaged models. Settings auto-detects `"pytest" in sys.modules` and switches to SQLite `:memory:`.

## Verification Results

```
25 passed, 1 warning in 1.79s
System check identified no issues (0 silenced).
```

All done criteria met:
- pyproject.toml has [web] optional deps with Django 5.2, django-tailwind-cli, django-htmx, django-filter, whitenoise, psycopg2-binary
- All 5 unmanaged models defined with correct db_table and managed=False
- Django settings configured with shared DB, custom auth backend, WhiteNoise, Tailwind CLI
- docker-compose.yml has webui service on port 8080
- Django can import and setup without errors (`manage.py check` → 0 issues)
- Custom auth backend verifies bcrypt hashes via passlib
- TOTP helper derives Fernet key identically to FastAPI's totp.py
- pytest-django test suite runs and passes (25/25)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed create_user factory missing created_at**
- **Found during:** Task 2 test run
- **Issue:** `HavenUser.created_at` is NOT NULL in the unmanaged table (matches FastAPI's `server_default=func.now()`), but the Django test factory didn't supply it — causing `IntegrityError: NOT NULL constraint failed: users.created_at`
- **Fix:** Added `kwargs.setdefault("created_at", timezone.now())` to the `create_user` factory in `conftest.py`
- **Files modified:** `server/webui/tests/conftest.py`
- **Commit:** included in 1394080

### Additional Decisions (not in plan)

- Added stub `urls.py` in all 5 app directories: the plan specified adding `__init__.py` files but the `config/urls.py` uses `include("accounts.urls")` etc., which would fail without those files. Added minimal stub urlpatterns to unblock Django's URL resolution.
- Added `accounts/urls.py` stub with `app_name = "accounts"` etc. for all apps.

## Threat Model Coverage

| Threat | Status | Implementation |
|--------|--------|----------------|
| T-06-01 Spoofing (login) | Mitigated | HavenAuthBackend bcrypt verify + dummy_verify timing guard |
| T-06-02 Tampering (CSRF) | Mitigated | CsrfViewMiddleware in MIDDLEWARE + hx-headers CSRF injection in base.html |
| T-06-07 Session hijacking | Mitigated | SESSION_COOKIE_HTTPONLY=True (explicit), SESSION_COOKIE_SECURE configurable via SECURE_COOKIES env |
| T-06-08 XSS | Mitigated | Django template auto-escaping enabled (APP_DIRS=True default) |

## Known Stubs

| Stub | File | Reason |
|------|------|--------|
| Empty urlpatterns | server/webui/{accounts,devices,events,admin_panel,notifications}/urls.py | Views implemented in plans 06-02 through 06-06 |

These stubs are intentional — the plan explicitly defers views to subsequent plans. The scaffold plan's goal (Django boots, models importable, auth backend works, tests pass) is fully achieved.

## Self-Check: PASSED

| Item | Status |
|------|--------|
| server/webui/config/settings.py | FOUND |
| server/webui/accounts/models.py | FOUND |
| server/webui/core/auth_backend.py | FOUND |
| server/webui/core/totp.py | FOUND |
| server/webui/core/templates/base.html | FOUND |
| server/webui/tests/conftest.py | FOUND |
| server/Dockerfile.webui | FOUND |
| commit 0bdda0c (Task 1) | FOUND |
| commit 1394080 (Task 2) | FOUND |
