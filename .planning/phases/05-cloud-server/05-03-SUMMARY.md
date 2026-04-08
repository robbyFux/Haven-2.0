---
phase: 05-cloud-server
plan: "03"
subsystem: devices-crypto
tags: [python, fastapi, argon2id, aes-gcm, pydantic, sqlalchemy, pytest]
dependency_graph:
  requires:
    - server/app/models/device.py (Device ORM model — from plan 01)
    - server/app/database.py (DbSession — from plan 01)
    - server/app/dependencies/auth.py (get_current_user — from plan 02)
  provides:
    - server/app/services/crypto.py (derive_key, encrypt_file, decrypt_file)
    - server/app/schemas/device.py (DeviceCreateRequest, DeviceResponse, DeviceListResponse)
    - server/app/routers/devices.py (POST/GET/GET/{id}/DELETE at /api/v1/devices/)
  affects:
    - server/app/main.py (devices router wired at /api/v1/devices)
tech_stack:
  added:
    - argon2-cffi (Argon2id key derivation via argon2.low_level.hash_secret_raw)
    - cryptography (AES-256-GCM via cryptography.hazmat.primitives.ciphers.aead.AESGCM)
  patterns:
    - Deterministic salt from username (lower-cased, zero-padded to 16 bytes) — no salt storage
    - Nonce-prepended ciphertext format (12-byte nonce || ciphertext+tag)
    - Soft-delete pattern for device revocation (is_active=False, record retained)
    - hav_ prefixed App-Key (hav_ + 64 hex chars = 68 chars total)
key_files:
  created:
    - server/app/services/crypto.py
    - server/app/schemas/device.py
    - server/app/routers/devices.py
    - server/tests/test_crypto.py
    - server/tests/test_devices.py
  modified:
    - server/app/main.py (devices router import + include_router)
decisions:
  - "Deterministic Argon2id salt from username avoids salt storage; same user+password always yields same encryption key — suitable for client-side key derivation without extra round-trip"
  - "Soft-delete (is_active=False) for device revocation: audit trail preserved, verify_app_key dependency already checks is_active so no code changes needed there"
  - "App-Key format hav_<64hex>=68 chars matches Device.app_key column width (String(68)) from plan 01"
  - "test_devices.py uses unique per-test usernames (secrets.token_hex suffix) to avoid session-scoped fixture state conflicts between tests"
metrics:
  duration_minutes: 3
  completed_date: "2026-04-08"
  tasks_completed: 2
  files_created: 5
  files_modified: 1
---

# Phase 05 Plan 03: Device Management + Crypto Service Summary

**One-liner:** Argon2id key derivation + AES-256-GCM encryption service and device CRUD endpoints with hav_-prefixed App-Key generation, all 12 tests passing.

## What Was Built

### Task 1: Crypto service, device schemas, and router

**server/app/services/crypto.py** — Three public functions:
- `derive_key(password, username) -> bytes`: Argon2id (time_cost=2, memory_cost=64MB, parallelism=2) with a 16-byte deterministic salt derived from `username.lower()`. Returns exactly 32 bytes (256-bit AES key).
- `encrypt_file(data, key) -> bytes`: AES-256-GCM with `os.urandom(12)` nonce. Returns `nonce(12) || ciphertext+tag`.
- `decrypt_file(data, key) -> bytes`: Splits nonce from first 12 bytes, calls `AESGCM.decrypt`. Raises `cryptography.exceptions.InvalidTag` on wrong key or tampered data.

**server/app/schemas/device.py** — Three Pydantic v2 schemas:
- `DeviceCreateRequest`: `name` field with `min_length=1, max_length=100`
- `DeviceResponse`: id, name, app_key, is_active, created_at, last_seen_at; `from_attributes=True` for ORM mapping
- `DeviceListResponse`: wraps `list[DeviceResponse]`

**server/app/routers/devices.py** — Four endpoints on `APIRouter(tags=["devices"])`:
- `POST /` — creates device with `app_key = f"hav_{secrets.token_hex(32)}"`, returns 201 with full DeviceResponse
- `GET /` — lists user's devices ordered by `created_at DESC`
- `GET /{device_id}` — returns 404 if not found or owned by different user
- `DELETE /{device_id}` — sets `is_active=False`, returns `{"status": "revoked"}`

**server/app/main.py** — Added `devices` import and `include_router(devices.router, prefix="/api/v1/devices")`.

### Task 2: Crypto and device tests

**server/tests/test_crypto.py** — 6 tests:
- `test_derive_key_deterministic`: same inputs → same key
- `test_derive_key_different_users`: different usernames → different keys
- `test_derive_key_length`: key is exactly 32 bytes
- `test_encrypt_decrypt_roundtrip`: encrypt → decrypt returns original
- `test_decrypt_wrong_key_fails`: raises `InvalidTag`
- `test_encrypt_produces_different_nonces`: two encryptions → different ciphertext, both decrypt correctly

**server/tests/test_devices.py** — 6 tests:
- `test_create_device`: 201, `app_key` starts with `hav_`, length 68
- `test_list_devices`: 2 devices created → list returns count 2
- `test_get_device`: GET by ID returns correct name
- `test_revoke_device`: DELETE → 200 `{"status":"revoked"}` → GET shows `is_active=False`
- `test_create_device_unauthenticated`: POST without token → 401
- `test_device_isolation`: user A's device not in user B's list, GET by B returns 404

## Verification Results

```
python3 -m pytest tests/test_crypto.py tests/test_devices.py -x -v
tests/test_crypto.py::test_derive_key_deterministic PASSED
tests/test_crypto.py::test_derive_key_different_users PASSED
tests/test_crypto.py::test_derive_key_length PASSED
tests/test_crypto.py::test_encrypt_decrypt_roundtrip PASSED
tests/test_crypto.py::test_decrypt_wrong_key_fails PASSED
tests/test_crypto.py::test_encrypt_produces_different_nonces PASSED
tests/test_devices.py::test_create_device PASSED
tests/test_devices.py::test_list_devices PASSED
tests/test_devices.py::test_get_device PASSED
tests/test_devices.py::test_revoke_device PASSED
tests/test_devices.py::test_create_device_unauthenticated PASSED
tests/test_devices.py::test_device_isolation PASSED
========================= 12 passed, 1 warning in 3.01s =========================
```

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — all device CRUD endpoints are fully wired and verified.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: key-derivation | server/app/services/crypto.py | derive_key uses a deterministic username-based salt. If an attacker gains the Argon2id hash, they can brute-force the password knowing the salt formula. This is acceptable for the upload key derivation use case (key is derived client-side, never stored on server), but should not be used as a password storage mechanism. |

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 | `24ba0ea` | feat(05-03): crypto service (Argon2id+AES-GCM) + device CRUD router + main.py wiring |
| Task 2 | `e377bb3` | feat(05-03): device and crypto tests — 12 passing |

## Self-Check: PASSED

Files verified:
- `server/app/services/crypto.py` FOUND
- `server/app/schemas/device.py` FOUND
- `server/app/routers/devices.py` FOUND
- `server/tests/test_crypto.py` FOUND
- `server/tests/test_devices.py` FOUND

Commits verified:
- `24ba0ea` FOUND
- `e377bb3` FOUND
