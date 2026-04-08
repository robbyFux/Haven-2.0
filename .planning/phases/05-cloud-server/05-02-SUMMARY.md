---
phase: 05-cloud-server
plan: "02"
subsystem: auth
tags: [python, fastapi, jwt, totp, bcrypt, pydantic, pytest]
dependency_graph:
  requires:
    - server/app/config.py (Settings: SECRET_KEY, token expiry)
    - server/app/database.py (DbSession, get_db)
    - server/app/models/user.py (User ORM model)
    - server/app/models/device.py (Device ORM model)
  provides:
    - server/app/schemas/auth.py (Pydantic v2 request/response schemas)
    - server/app/services/jwt.py (create_access_token, create_refresh_token, decode_token)
    - server/app/services/totp.py (generate_totp_secret, verify_totp, get_totp_qr_png, encrypt/decrypt_totp_secret)
    - server/app/dependencies/auth.py (get_current_user, require_admin, verify_app_key)
    - server/app/routers/auth.py (POST /register, /login, /refresh, GET /me, POST /2fa/setup, /2fa/verify)
  affects:
    - server/app/main.py (auth router wired at /api/v1/auth)
    - server/pyproject.toml (bcrypt pinned to 4.3.0)
tech_stack:
  added:
    - python-jose[cryptography]==3.5.0 (JWT HS256 encode/decode)
    - passlib[bcrypt]==1.7.4 (password hashing context)
    - bcrypt==4.3.0 (downgraded from 5.0.0 — passlib 1.7.4 incompatible with bcrypt 5.x)
    - pyotp==2.9.0 (TOTP generation and verification)
    - qrcode[pil]==8.2 (QR code PNG generation)
    - cryptography (Fernet for TOTP secret encryption)
  patterns:
    - OAuth2PasswordBearer + JWT Bearer token flow
    - Fernet TOTP secret encryption (SHA-256 derived key from SECRET_KEY)
    - TOTP enrollment: setup (stores encrypted secret) → verify (enables 2FA)
    - X-TOTP-Required header to signal 2FA requirement to clients
key_files:
  created:
    - server/app/schemas/auth.py
    - server/app/services/jwt.py
    - server/app/services/totp.py
    - server/app/dependencies/auth.py
    - server/app/routers/auth.py
    - server/tests/test_auth.py
  modified:
    - server/app/main.py (auth router wired)
    - server/pyproject.toml (bcrypt version pinned)
decisions:
  - "Used X-TOTP-Required response header (not JSON body field) to signal 2FA requirement — HTTP header is more REST-idiomatic and doesn't require clients to parse error bodies to detect 2FA flows"
  - "Fernet key derived via SHA-256(SECRET_KEY) → 32 bytes → base64url — avoids separate TOTP_ENCRYPTION_KEY config, deterministic from existing SECRET_KEY"
  - "bcrypt pinned to 4.3.0: passlib 1.7.4 calls bcrypt.__about__.__version__ which was removed in bcrypt 5.0.0; downgrade fixes ValueError on first hash call"
  - "verify_app_key updates last_seen_at inline on each call (no dedicated endpoint) — keeps device presence tracking transparent to callers"
metrics:
  duration_minutes: 15
  completed_date: "2026-04-08"
  tasks_completed: 2
  files_created: 6
  files_modified: 2
---

# Phase 05 Plan 02: Authentication System Summary

**One-liner:** JWT access/refresh tokens + TOTP 2FA + bcrypt registration with Fernet-encrypted secrets and FastAPI auth dependencies (get_current_user, require_admin, verify_app_key).

## What Was Built

Complete authentication system for Haven Cloud at `/api/v1/auth/*`. All downstream plans can protect endpoints using the three auth dependencies.

### Task 1: Auth building blocks

- `server/app/schemas/auth.py` — 8 Pydantic v2 schemas: `RegisterRequest` (min_length validation), `RegisterResponse`, `LoginRequest`, `TokenResponse`, `RefreshRequest`, `TotpSetupResponse`, `TotpVerifyRequest`, `UserResponse` (from_attributes=True)
- `server/app/services/jwt.py` — `create_access_token`, `create_refresh_token`, `decode_token` using python-jose HS256; tokens carry `type` claim to prevent refresh/access confusion
- `server/app/services/totp.py` — `generate_totp_secret` (pyotp), `get_totp_uri`, `get_totp_qr_png` (qrcode PNG bytes), `verify_totp` (valid_window=1), `encrypt_totp_secret` / `decrypt_totp_secret` (Fernet with SHA-256 derived key)
- `server/app/dependencies/auth.py` — `get_current_user` (OAuth2PasswordBearer, validates access token type, loads User, checks is_active), `require_admin` (403 gate), `verify_app_key` (X-App-Key header lookup + last_seen_at update)

### Task 2: Auth router, main.py wiring, tests

- `server/app/routers/auth.py` — 6 endpoints on `APIRouter(tags=["auth"])`:
  - `POST /register` → 201 with user_key (`haven_u_<32hex>`), 409 on duplicate
  - `POST /login` → JWT pair; 401 with `X-TOTP-Required: true` header if 2FA enabled but code missing
  - `POST /refresh` → rotates both access + refresh tokens
  - `GET /me` → `UserResponse` (requires Bearer token)
  - `POST /2fa/setup` → stores encrypted TOTP secret, returns secret + QR PNG base64
  - `POST /2fa/verify` → confirms code, sets `totp_enabled=True`
- `server/app/main.py` — auth router wired at `/api/v1/auth`
- `server/tests/test_auth.py` — 9 tests, all passing

## Verification Results

```
python3 -m pytest tests/test_auth.py -x -v
tests/test_auth.py::test_register_success PASSED
tests/test_auth.py::test_register_duplicate_username PASSED
tests/test_auth.py::test_login_success PASSED
tests/test_auth.py::test_login_wrong_password PASSED
tests/test_auth.py::test_refresh_token PASSED
tests/test_auth.py::test_me_authenticated PASSED
tests/test_auth.py::test_me_unauthenticated PASSED
tests/test_auth.py::test_2fa_setup_and_verify PASSED
tests/test_auth.py::test_login_with_2fa PASSED
========================= 9 passed, 1 warning in 3.33s =========================
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] bcrypt 5.0.0 incompatible with passlib 1.7.4**
- **Found during:** Task 2 — first test run
- **Issue:** `passlib` 1.7.4 calls `bcrypt.__about__.__version__` to detect the bcrypt version, but `bcrypt` 5.0.0 removed the `__about__` module. This caused `ValueError: password cannot be longer than 72 bytes` on the first `pwd_context.hash()` call (passlib's internal bug detection routine using a 255-byte string).
- **Fix:** Pinned `bcrypt==4.3.0` in `server/pyproject.toml`. Version 4.x still has `__about__` and is fully compatible with passlib 1.7.4.
- **Files modified:** `server/pyproject.toml`
- **Commit:** `e63085c`

## Known Stubs

None — all auth endpoints are fully wired and verified.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: auth-endpoint | server/app/routers/auth.py | New unauthenticated endpoints: /register and /login are open to the internet. No rate limiting applied — brute-force and registration spam are possible. Rate limiting should be added in a hardening plan. |

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 | `e30cc80` | feat(05-02): auth schemas, JWT service, TOTP service, auth dependencies |
| Task 2 | `e63085c` | feat(05-02): auth router endpoints, main.py wiring, 9 passing tests |

## Self-Check: PASSED

Files verified:
- `server/app/schemas/auth.py` FOUND
- `server/app/services/jwt.py` FOUND
- `server/app/services/totp.py` FOUND
- `server/app/dependencies/auth.py` FOUND
- `server/app/routers/auth.py` FOUND
- `server/tests/test_auth.py` FOUND

Commits verified:
- `e30cc80` FOUND
- `e63085c` FOUND
