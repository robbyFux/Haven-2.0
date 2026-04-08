---
phase: 05-cloud-server
plan: "01"
subsystem: server-scaffold
tags: [python, fastapi, sqlalchemy, alembic, celery, postgresql, docker, pytest]
dependency_graph:
  requires: []
  provides:
    - server/app/config.py (Settings singleton)
    - server/app/database.py (AsyncSession, get_db, DbSession)
    - server/app/main.py (create_app factory, FastAPI app)
    - server/app/models (Base, User, Device, Event, EventTrigger, AnalysisResult)
    - server/app/celery_app.py (Celery instance)
    - server/docker-compose.yml (4-service dev stack)
    - server/alembic/ (async migration env)
    - server/tests/conftest.py (async_client fixture)
  affects: []
tech_stack:
  added:
    - fastapi 0.135.3
    - pydantic 2.12.5
    - pydantic-settings
    - sqlalchemy[asyncio] 2.0.49
    - alembic 1.18.4
    - asyncpg 0.31.0
    - celery[redis] 5.6.3
    - uvicorn[standard] 0.44.0
    - gunicorn 25.3.0
    - python-jose[cryptography] 3.5.0
    - passlib[bcrypt] 1.7.4
    - bcrypt 5.0.0
    - argon2-cffi 25.1.0
    - cryptography
    - pyotp 2.9.0
    - qrcode[pil] 8.2
    - pillow 12.2.0
    - aiosmtplib 5.1.0
    - aiofiles 25.1.0
    - httpx 0.28.1
    - python-multipart
    - ai-edge-litert 2.1.3 (optional ml group)
    - pytest, pytest-asyncio>=1.0, aiosqlite (dev group)
  patterns:
    - FastAPI app factory pattern (create_app + lifespan)
    - SQLAlchemy 2.0 async with mapped_column (DeclarativeBase)
    - pydantic-settings for env-file-backed configuration
    - Alembic async migration via run_sync pattern
    - pytest-asyncio session-scoped fixture with SQLite override
key_files:
  created:
    - server/pyproject.toml
    - server/.env.example
    - server/docker-compose.yml
    - server/Dockerfile
    - server/app/__init__.py
    - server/app/main.py
    - server/app/config.py
    - server/app/database.py
    - server/app/celery_app.py
    - server/app/routers/__init__.py
    - server/app/routers/health.py
    - server/app/schemas/__init__.py
    - server/app/dependencies/__init__.py
    - server/app/services/__init__.py
    - server/app/tasks/__init__.py
    - server/app/ml/__init__.py
    - server/app/models/__init__.py
    - server/app/models/base.py
    - server/app/models/user.py
    - server/app/models/device.py
    - server/app/models/event.py
    - server/alembic.ini
    - server/alembic/env.py
    - server/alembic/versions/.gitkeep
    - server/tests/__init__.py
    - server/tests/conftest.py
  modified: []
decisions:
  - "Used SQLAlchemy 2.0 DeclarativeBase with mapped_column instead of legacy Column syntax for type-safe models"
  - "Alembic env.py uses async_engine_from_config + run_sync pattern for async migration support"
  - "test conftest uses SQLite+aiosqlite for fast in-process tests, avoiding PostgreSQL dependency in CI"
  - "TimestampMixin updated_at uses server_default=func.now() + onupdate=func.now() for DB-level timestamps"
  - "app/main.py includes router stubs as comments so downstream plans can uncomment without restructuring"
metrics:
  duration_minutes: 5
  completed_date: "2026-04-08"
  tasks_completed: 2
  files_created: 26
  files_modified: 0
---

# Phase 05 Plan 01: Server Scaffold Summary

**One-liner:** FastAPI+SQLAlchemy async server scaffold with Docker dev stack, 5 ORM models, Alembic async migrations, and pytest SQLite test harness.

## What Was Built

Complete `server/` project scaffold at the repo root. This is a standalone Python backend project providing the foundation all subsequent Phase 05 plans build upon.

### Task 1: Project Scaffold

- `server/pyproject.toml` with all 20+ locked dependencies (fastapi 0.135.3, sqlalchemy 2.0.49, celery 5.6.3, etc.) and dev/ml optional groups
- `server/.env.example` with all 19 configuration keys documented
- `server/docker-compose.yml` with 4 services: `db` (postgres:16-alpine), `redis` (redis:7-alpine), `app` (uvicorn), `worker` (celery)
- `server/Dockerfile` multi-stage: builder installs native extensions (gcc, libffi, libssl), runtime copies slim venv
- `server/app/config.py` pydantic-settings `Settings` with module-level `settings` singleton
- `server/app/database.py` async engine with `pool_pre_ping=True`, `AsyncSessionLocal`, `get_db` dependency, `DbSession` type alias
- `server/app/celery_app.py` Celery instance with JSON serialization, includes analysis + notifications tasks
- `server/app/main.py` `create_app()` factory with `@asynccontextmanager` lifespan, health router included
- `server/app/routers/health.py` `GET /health` returning `{"status": "ok", "version": "1.0.0"}`
- All empty `__init__.py` package stubs: routers, schemas, dependencies, services, tasks, ml

### Task 2: Models, Alembic, Tests

- `server/app/models/base.py` `DeclarativeBase` + `TimestampMixin` (created_at, updated_at via server `func.now()`)
- `server/app/models/user.py` `User` model: username, bcrypt password_hash, user_key (`haven_u_<32hex>`), TOTP 2FA fields, admin quota fields (storage_quota_mb, max_events), notification contact fields
- `server/app/models/device.py` `Device` model: app_key (`hav_<32hex>`), name, is_active, last_seen_at
- `server/app/models/event.py` `Event`, `EventTrigger`, `AnalysisResult` — 3 models, 5 tables total
- `server/alembic.ini` with DATABASE_URL placeholder (overridden by env.py at runtime)
- `server/alembic/env.py` async migration pattern: `async_engine_from_config` + `connection.run_sync(do_run_migrations)`
- `server/tests/conftest.py` session-scoped `async_client` fixture with SQLite+aiosqlite override and full table lifecycle

## Verification Results

All checks pass:

```
python3 -c "from app.main import app; print(app.title)"          → Haven Cloud
python3 -c "from app.models import Base; print(len(Base.metadata.tables))"  → 5
python3 -c "from app.config import settings; assert settings.AI_BACKEND == 'none'"  → OK
grep asyncio_mode pyproject.toml                                  → asyncio_mode = "auto"
docker-compose.yml services                                       → db, redis, app, worker
```

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

- `server/app/main.py` router includes for auth/devices/events/admin are commented out (placeholder). These will be uncommented by downstream plans (05-02 auth, 05-03 devices, etc.).
- `server/app/tasks/analysis.py` and `server/app/tasks/notifications.py` do not exist yet — Celery `include=` references them but they are created in plans 05-05/05-06. Celery will warn on worker start but not crash.

These stubs are intentional: they define the integration points downstream plans wire up.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 | `0b89109` | feat(05-01): project scaffold — pyproject.toml, config, Docker, database layer |
| Task 2 | `d8b4ae0` | feat(05-01): SQLAlchemy models, Alembic async setup, pytest test infrastructure |

## Self-Check: PASSED

Files verified:
- `server/pyproject.toml` FOUND
- `server/app/main.py` FOUND
- `server/app/models/user.py` FOUND
- `server/app/models/event.py` FOUND
- `server/alembic/env.py` FOUND
- `server/tests/conftest.py` FOUND

Commits verified:
- `0b89109` FOUND
- `d8b4ae0` FOUND
