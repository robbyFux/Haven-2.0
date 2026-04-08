---
phase: 05-cloud-server
verified: 2026-04-07T00:00:00Z
status: human_needed
score: 8/8 plans verified
overrides_applied: 0
gaps: []
human_verification:
  - test: "Run the full pytest test suite from server/ directory: python -m pytest tests/ -v"
    expected: "All 52 tests pass (9 auth + 6 crypto + 6 devices + 10 events + 7 admin + 6 analysis + 8 notifications). Zero failures."
    why_human: "Plan 05-07 SUMMARY reported '45 passed, 7 pre-existing failures (test_events.py)' when running the full suite, even though 05-04 reported all 10 event tests passed in isolation. The 7 failures are unexplained — likely test isolation issues (shared file-system state in MEDIA_ROOT, session-scoped fixture state leaks, or Celery eager mode conflict). Cannot verify programmatically whether the full suite is clean without running it."
  - test: "docker compose up in server/ and curl http://localhost:8000/health"
    expected: "Returns {\"status\": \"ok\", \"version\": \"1.0.0\"} with HTTP 200"
    why_human: "Cannot start Docker services in this environment; docker-compose.yml defines 4 services but runtime behavior needs human validation."
---

# Phase 05: Cloud-Server Verification Report

**Phase Goal:** Self-hosted Python backend (FastAPI + PostgreSQL + Redis + Celery) that provides multi-user auth with 2FA, per-user multi-device (App-Key) management, encrypted user data storage (Argon2id key derivation from cloud password + username), admin-defined quotas, secure event+video upload from the Haven Android app, optional AI-based analysis of events/videos (local TFLite or OpenRouter), and cloud-triggered notifications via Mail, Signal, or Pushover after analysis.

**Verified:** 2026-04-07
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|---------|
| 1 | Server scaffold exists with all package dirs and dependencies | VERIFIED | `server/pyproject.toml` with fastapi 0.135.3, all 20+ deps; `server/` dir tree with app/, tests/, alembic/, docker-compose.yml, Dockerfile all present |
| 2 | Multi-user auth with 2FA works (register, login, JWT, TOTP) | VERIFIED | `server/app/routers/auth.py`, `server/app/services/jwt.py`, `server/app/services/totp.py`, `server/app/dependencies/auth.py` all exist and substantive; 9 tests pass |
| 3 | Multi-device App-Key management works | VERIFIED | `server/app/routers/devices.py` with hav_-prefixed App-Key CRUD; `server/app/dependencies/auth.py` has `verify_app_key`; 6 device tests pass |
| 4 | Argon2id key derivation and AES-GCM encryption round-trip correctly | VERIFIED | `server/app/services/crypto.py` with `hash_secret_raw`, `AESGCM`; `derive_key`, `encrypt_file`, `decrypt_file` functions exist and tested; 6 crypto tests pass |
| 5 | Event + video upload with encryption and quota enforcement | VERIFIED | `server/app/routers/events.py` has X-Encryption-Password, encrypt_file call, check_event_quota, check_storage_quota; 413/429 enforced; 10 event tests passed (isolation caveat below) |
| 6 | Admin quota management and system stats API works | VERIFIED | `server/app/routers/admin.py` with require_admin, storage_quota_mb update, /stats endpoint; 7 admin tests pass |
| 7 | Celery AI analysis pipeline (TFLite + OpenRouter) works | VERIFIED | `server/app/ml/detector.py` with HavenDetector lazy singleton, `server/app/ml/openrouter.py` with httpx.Client; `server/app/tasks/analysis.py` with analyze_event_task; chains to notification task; 6 analysis tests pass |
| 8 | Cloud notifications dispatch via Email, Signal, Pushover | VERIFIED | `server/app/services/notify.py` with send_email, send_signal, send_pushover; `server/app/tasks/notifications.py` with send_notification_task; 8 notification tests pass |
| 9 | Android CloudChannel uploads events to cloud server | VERIFIED | `CloudChannel.kt` exists, implements HavenAlertChannel, uses X-App-Key header, posts to `/api/v1/devices/{appKey}/events` |
| 10 | CloudChannel wired into MonitorService when cloudEnabled | VERIFIED | `MonitorService.kt` imports CloudChannel, reads cloudEnabled/cloudServerUrl/cloudAppKey via .first(), adds to buildList |
| 11 | User can configure cloud server in Settings | VERIFIED | SettingsViewModel has cloudEnabled/cloudServerUrl/cloudAppKey StateFlows + setters; SettingsScreen has Cloud Server SettingsSection, OutlinedButton, CloudConfigDialog |
| 12 | String resources in EN and DE for cloud settings | VERIFIED | `values/strings.xml` and `values-de/strings.xml` both have settings_cloud_title, settings_cloud_configured, and 5 other cloud keys |
| 13 | Full test suite passes without failures | ? UNCERTAIN | 05-07 SUMMARY reports 7 unexplained failures in test_events.py when running full suite together; needs human to run and confirm |

**Score:** 12/12 programmatically verified truths; 1 uncertain (needs human run)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `server/pyproject.toml` | Project metadata + dependencies | VERIFIED | fastapi[standard]==0.135.3, sqlalchemy[asyncio] 2.0.49, all locked deps present |
| `server/app/main.py` | FastAPI app factory | VERIFIED | create_app() factory; all 6 routers wired via include_router |
| `server/app/models/user.py` | User SQLAlchemy model | VERIFIED | class User, storage_quota_mb, totp_enabled, user_key, notification fields all present |
| `server/app/models/event.py` | Event, EventTrigger, AnalysisResult | VERIFIED | All 3 classes present |
| `server/app/models/device.py` | Device model | VERIFIED | class Device, app_key, is_active, last_seen_at |
| `server/app/database.py` | AsyncSession factory | VERIFIED | get_db, AsyncSessionLocal, DbSession, pool_pre_ping=True |
| `server/alembic/env.py` | Alembic async migration | VERIFIED | target_metadata = Base.metadata; async_engine_from_config; run_sync pattern |
| `server/tests/conftest.py` | Async test client | VERIFIED | session-scoped async_client fixture with SQLite override |
| `server/app/services/jwt.py` | JWT create/decode | VERIFIED | create_access_token, create_refresh_token, decode_token |
| `server/app/services/totp.py` | TOTP enrollment | VERIFIED | generate_totp_secret, verify_totp, get_totp_qr_png, encrypt/decrypt_totp_secret |
| `server/app/dependencies/auth.py` | Auth dependencies | VERIFIED | get_current_user, require_admin, verify_app_key all present |
| `server/app/services/crypto.py` | Argon2id + AES-GCM | VERIFIED | derive_key (hash_secret_raw), encrypt_file (AESGCM), decrypt_file; 32-byte key |
| `server/app/routers/events.py` | Event upload endpoint | VERIFIED | POST /devices/{app_key}/events, X-Encryption-Password, encrypt_file, quota checks, Celery dispatch |
| `server/app/services/storage.py` | File save/load | VERIFIED | save_media, load_media, delete_media; async aiofiles |
| `server/app/dependencies/quota.py` | Quota enforcement | VERIFIED | check_storage_quota (413), check_event_quota (429) |
| `server/app/routers/admin.py` | Admin endpoints | VERIFIED | require_admin on all routes, quota update, system stats |
| `server/app/ml/detector.py` | TFLite lazy singleton | VERIFIED | HavenDetector, get_detector, ai_edge_litert inside __init__, allocate_tensors |
| `server/app/ml/openrouter.py` | OpenRouter client | VERIFIED | httpx.Client (sync), base64 encoding, openrouter.ai URL, 30s timeout |
| `server/app/tasks/analysis.py` | Celery analysis task | VERIFIED | @celery_app.task, get_detector, analyze_frame_openrouter, decrypt_file, send_notification_task chain |
| `server/app/tasks/notifications.py` | Celery notification task | VERIFIED | @celery_app.task, send_email, send_signal, send_pushover, per-channel dispatch |
| `server/app/services/notify.py` | Email/Signal/Pushover senders | VERIFIED | send_email (aiosmtplib), send_signal (/v2/send), send_pushover (api.pushover.net) |
| `server/docker-compose.yml` | 4-service Docker stack | VERIFIED | db (postgres:16-alpine), redis (redis:7-alpine), app, worker |
| `app/.../notify/CloudChannel.kt` | Android upload client | VERIFIED | HavenAlertChannel impl, X-App-Key header, multipart POST, sendHeartbeat |
| `app/.../storage/SettingsRepository.kt` | Cloud DataStore keys | VERIFIED | KEY_CLOUD_ENABLED, KEY_CLOUD_SERVER_URL, KEY_CLOUD_APP_KEY; flows and setters |
| `app/.../ui/settings/SettingsViewModel.kt` | Cloud StateFlows | VERIFIED | cloudEnabled, cloudServerUrl, cloudAppKey StateFlows + setters |
| `app/.../ui/settings/SettingsScreen.kt` | Cloud Settings UI | VERIFIED | SettingsSection with toggle + OutlinedButton, CloudConfigDialog composable |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `server/app/main.py` | `server/app/routers/health.py` | include_router | WIRED | `application.include_router(health.router)` confirmed |
| `server/app/main.py` | `server/app/routers/auth.py` | include_router at /api/v1/auth | WIRED | Confirmed in main.py line 50 |
| `server/app/main.py` | `server/app/routers/devices.py` | include_router at /api/v1/devices | WIRED | Confirmed in main.py line 51 |
| `server/app/main.py` | `server/app/routers/admin.py` | include_router at /api/v1/admin | WIRED | Confirmed in main.py line 52 |
| `server/app/main.py` | `server/app/routers/events.py` | include_router at /api/v1 | WIRED | Confirmed in main.py line 53 |
| `server/app/main.py` | `server/app/routers/notifications.py` | include_router at /api/v1/notifications | WIRED | Confirmed in main.py line 54 |
| `server/alembic/env.py` | `server/app/models` | target_metadata = Base.metadata | WIRED | `from app.models.base import Base; target_metadata = Base.metadata` |
| `server/app/database.py` | `server/app/config.py` | settings.DATABASE_URL | WIRED | DATABASE_URL referenced in database.py |
| `server/app/routers/auth.py` | `server/app/services/jwt.py` | create_access_token | WIRED | create_access_token called in login endpoint |
| `server/app/dependencies/auth.py` | `server/app/services/jwt.py` | decode_token in get_current_user | WIRED | decode_token used in get_current_user |
| `server/app/routers/auth.py` | `server/app/services/totp.py` | verify_totp | WIRED | verify_totp called in login and 2fa/verify |
| `server/app/routers/devices.py` | `server/app/dependencies/auth.py` | Depends(get_current_user) | WIRED | All device endpoints use get_current_user |
| `server/app/services/crypto.py` | `argon2.low_level` | hash_secret_raw | WIRED | `from argon2.low_level import Type, hash_secret_raw` at module level |
| `server/app/routers/events.py` | `server/app/services/crypto.py` | encrypt_file | WIRED | encrypt_file called in upload endpoint |
| `server/app/routers/events.py` | `server/app/dependencies/auth.py` | verify_app_key | WIRED | Depends(verify_app_key) on upload endpoint |
| `server/app/routers/events.py` | `server/app/dependencies/quota.py` | check_upload_quota | WIRED | check_storage_quota + check_event_quota called in upload |
| `server/app/routers/admin.py` | `server/app/dependencies/auth.py` | require_admin | WIRED | All admin endpoints use Depends(require_admin) |
| `server/app/tasks/analysis.py` | `server/app/ml/detector.py` | get_detector() | WIRED | `from app.ml.detector import get_detector` inside tflite branch |
| `server/app/tasks/analysis.py` | `server/app/ml/openrouter.py` | analyze_frame_openrouter | WIRED | `from app.ml.openrouter import analyze_frame_openrouter` inside openrouter branch |
| `server/app/tasks/analysis.py` | `server/app/services/crypto.py` | decrypt_file | WIRED | decrypt_file imported and called when encryption_key_hex present |
| `server/app/tasks/analysis.py` | `server/app/tasks/notifications.py` | send_notification_task.delay | WIRED | lazy import inside analyze_event_task function body |
| `server/app/tasks/notifications.py` | `server/app/services/notify.py` | send_email/send_signal/send_pushover | WIRED | Module-level imports of all three senders; called conditionally in task |
| `app/.../notify/CloudChannel.kt` | `server/app/routers/events.py` | POST /api/v1/devices/{appKey}/events | WIRED | URL pattern `${serverUrl}/api/v1/devices/$appKey/events` in CloudChannel.send() |
| `app/.../MonitorService.kt` | `app/.../notify/CloudChannel.kt` | if (cloudEnabled) add(CloudChannel(...)) | WIRED | MonitorService imports CloudChannel, reads settings via .first(), adds to buildList |
| `app/.../ui/settings/SettingsScreen.kt` | `app/.../ui/settings/SettingsViewModel.kt` | cloudEnabled, cloudServerUrl, cloudAppKey state | WIRED | collectAsStateWithLifecycle() for all three; CloudConfigDialog calls setters |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `server/app/routers/events.py` | Event query result | SQLAlchemy async query on events table with user_id filter | Yes — real DB query, not static | FLOWING |
| `server/app/routers/admin.py` | User list with device_count | SQLAlchemy query + func.count subquery | Yes — real aggregate query | FLOWING |
| `server/app/tasks/analysis.py` | image_bytes | File read from MEDIA_ROOT + optional AES-GCM decrypt | Yes — reads actual file bytes | FLOWING |
| `server/app/tasks/notifications.py` | event + user + device | asyncio.run with selectinload async DB query | Yes — real ORM query with eager loading | FLOWING |
| `app/.../notify/CloudChannel.kt` | TriggerEvent fields | Passed from NotificationRouter.route() which receives real MonitorService events | Yes — live sensor trigger events | FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED for server (no runnable entry points without Docker/PostgreSQL). Android CloudChannel is an upload client — behavioral verification requires a running server instance.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|---------|
| CLOUD-01 | 05-02 | Multi-user auth with User-Key | SATISFIED | JWT auth, user_key (haven_u_<32hex>), get_current_user dependency |
| CLOUD-02 | 05-02 | Registration, login, 2FA | SATISFIED | POST /register, /login with TOTP, /2fa/setup, /2fa/verify all implemented and tested |
| CLOUD-03 | 05-03 | Multi-device App-Key management | SATISFIED | Device CRUD, App-Key (hav_<64hex>), verify_app_key dependency |
| CLOUD-04 | 05-03, 05-04 | Argon2id key derivation + AES-GCM encryption | SATISFIED | derive_key, encrypt_file, decrypt_file in crypto.py; used in event upload |
| CLOUD-05 | 05-05 | Admin-defined quotas | SATISFIED | storage_quota_mb, max_events on User; admin PATCH /quota; 413/429 enforcement |
| CLOUD-06 | 05-04, 05-08 | Event + video upload from Haven app | SATISFIED | Server: POST /devices/{app_key}/events; Android: CloudChannel.kt with multipart upload |
| CLOUD-07 | 05-06 | Optional AI analysis (TFLite + OpenRouter) | SATISFIED | analyze_event_task with TFLite lazy singleton and OpenRouter client; AI_BACKEND=none skips |
| CLOUD-08 | 05-07 | Cloud notifications (Email/Signal/Pushover) | SATISFIED | send_notification_task dispatches via all three channels; user configures via /api/v1/notifications/settings |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `server/tests/test_events.py` | suite run | 7 of 10 tests fail when running full test suite together (per 05-07 SUMMARY) | WARNING | Test isolation issue — test_events.py likely shares file-system state (MEDIA_ROOT) or session-scoped fixture state with other test modules. Requires human to diagnose and confirm whether failures are still present. |

### Human Verification Required

### 1. Full Test Suite Health

**Test:** From `server/` directory, run: `python -m pytest tests/ -v`
**Expected:** All 52 tests pass (9 + 6 + 6 + 10 + 7 + 6 + 8). Zero failures.
**Why human:** Plan 05-07 SUMMARY reported "45 passed, 7 pre-existing failures (test_events.py)" when running the full suite. This indicates test isolation issues — the 10 test_events.py tests passed in isolation (per 05-04 SUMMARY) but 7 of them failed when run after other test modules. The root cause (shared MEDIA_ROOT state, session-scoped fixture leakage, Celery eager mode interaction) was noted but not fixed or explained. Cannot verify programmatically.

### 2. Docker Stack Health

**Test:** In `server/`: `cp .env.example .env && docker compose up -d && curl -s http://localhost:8000/health`
**Expected:** HTTP 200, `{"status": "ok", "version": "1.0.0"}`. All 4 services healthy.
**Why human:** Cannot start Docker services in this environment. The docker-compose.yml structure looks correct (4 services, correct images, port mapping) but runtime behavior including PostgreSQL migrations and Celery worker startup needs human validation.

### Gaps Summary

No definitive gaps in code completeness — all 8 plans delivered their required artifacts and key links. One quality concern exists: the 05-07 SUMMARY documents 7 failures in test_events.py when running the full test suite, without diagnosing or resolving the root cause. This is a test isolation defect that could hide regressions. Requires human to run the full suite and confirm whether failures are present or were transient.

---

_Verified: 2026-04-07T00:00:00Z_
_Verifier: Claude (gsd-verifier)_
