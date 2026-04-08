---
phase: 05-cloud-server
plan: "05"
subsystem: admin-api
tags: [python, fastapi, sqlalchemy, pydantic, pytest, admin, quota]
dependency_graph:
  requires:
    - server/app/models/user.py (User model with quota fields — from plan 01)
    - server/app/models/device.py (Device model — from plan 01)
    - server/app/models/event.py (Event model — from plan 01)
    - server/app/database.py (DbSession — from plan 01)
    - server/app/dependencies/auth.py (require_admin dependency — from plan 02)
  provides:
    - server/app/schemas/admin.py (UserAdminResponse, UserListResponse, QuotaUpdateRequest, SystemStatsResponse)
    - server/app/routers/admin.py (GET/PATCH/DELETE /api/v1/admin/users/*, GET /api/v1/admin/stats)
  affects:
    - server/app/main.py (admin router wired at /api/v1/admin)
tech_stack:
  added: []
  patterns:
    - require_admin dependency gate on all admin endpoints
    - Soft-delete pattern for user deactivation (is_active=False)
    - Per-request device count subquery (func.count + filter on user_id)
    - func.coalesce for nullable SUM aggregation in stats endpoint
key_files:
  created:
    - server/app/schemas/admin.py
    - server/app/routers/admin.py
    - server/tests/test_admin.py
  modified:
    - server/app/main.py (admin router import + include_router)
decisions:
  - "device_count computed per-request via subquery rather than relationship lazy-load — avoids N+1 and keeps UserAdminResponse independent of ORM session state"
  - "Soft-delete for user deactivation: is_active=False is already respected by get_current_user dependency, so deactivated users are locked out with no extra code"
  - "toggle_admin endpoint accepts raw dict body for is_admin flag to keep the interface minimal; Pydantic validation done inline"
  - "Self-deletion guard compares user_id == admin.id at the router level — prevents privilege escalation via race conditions"
metrics:
  duration_minutes: 8
  completed_date: "2026-04-08"
  tasks_completed: 1
  files_created: 3
  files_modified: 1
---

# Phase 05 Plan 05: Admin API Summary

**One-liner:** Admin endpoints for user listing, quota management, admin-flag toggling, soft-delete deactivation, and system stats — all protected by require_admin, 7 tests passing.

## What Was Built

### Task 1: Admin schemas, router, and tests

**server/app/schemas/admin.py** — Four Pydantic v2 schemas:
- `UserAdminResponse`: Full user detail including id, username, is_admin, is_active, totp_enabled, quota fields (storage_quota_mb, max_events, current_storage_bytes, current_event_count), device_count (int), and created_at. `from_attributes=True` for ORM mapping.
- `UserListResponse`: Wraps `list[UserAdminResponse]` with a `total` count.
- `QuotaUpdateRequest`: Optional `storage_quota_mb` and `max_events` fields (both `int | None = None`) with non-negative validation.
- `SystemStatsResponse`: total_users, total_events, total_devices, total_storage_bytes aggregate integers.

**server/app/routers/admin.py** — Six endpoints on `APIRouter(tags=["admin"])`:
- `GET /users` — lists all users ordered by id; each entry includes a per-request device count via subquery
- `GET /users/{user_id}` — single user detail; 404 if not found
- `PATCH /users/{user_id}/quota` — partial update (only non-None fields written); commits and refreshes
- `PATCH /users/{user_id}/admin` — sets `is_admin` bool; validates body type inline
- `DELETE /users/{user_id}` — soft-deletes (is_active=False); 403 on self-deletion
- `GET /stats` — aggregate counts via `func.count` and `func.coalesce(func.sum(...), 0)`

All endpoints use `Depends(require_admin)` from plan 02.

**server/app/main.py** — Added `admin` import and `include_router(admin.router, prefix="/api/v1/admin", tags=["admin"])`.

**server/tests/test_admin.py** — 7 tests:
- `test_list_users_as_admin`: admin receives 200 with user list including device_count field
- `test_list_users_as_non_admin`: non-admin receives 403
- `test_get_user_detail`: admin retrieves own user record with all quota fields
- `test_update_quota`: storage_quota_mb=500 and max_events=5000 persisted correctly
- `test_get_system_stats`: stats endpoint returns all four count fields
- `test_deactivate_user`: user is_active set to False; deactivated user's token is rejected by /me
- `test_cannot_self_delete`: admin receives 403 when deleting own user_id

Test infrastructure note: the `admin_token_and_id` fixture bootstraps admin rights by connecting to the same `test.db` SQLite file used by conftest.py and running a direct `UPDATE users SET is_admin=True`, matching the pattern used in other test files for state that can't be reached via API.

## Verification Results

```
python3 -m pytest tests/test_admin.py -x -v
tests/test_admin.py::test_list_users_as_admin PASSED
tests/test_admin.py::test_list_users_as_non_admin PASSED
tests/test_admin.py::test_get_user_detail PASSED
tests/test_admin.py::test_update_quota PASSED
tests/test_admin.py::test_get_system_stats PASSED
tests/test_admin.py::test_deactivate_user PASSED
tests/test_admin.py::test_cannot_self_delete PASSED
========================= 7 passed, 1 warning in 3.76s =========================

python3 -m pytest tests/ -x -v
======================== 28 passed, 1 warning in 10.11s ========================
```

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — all admin endpoints are fully wired and verified.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: privilege-escalation | server/app/routers/admin.py | PATCH /users/{id}/admin allows any admin to promote any user (including themselves to ensure they're admin). No second-admin confirmation required. Acceptable for self-hosted single-admin deployments; multi-admin environments should add an approval flow. |

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 | `d9077e5` | feat(05-05): admin API — user management, quota control, system stats |

## Self-Check: PASSED

Files verified:
- `server/app/schemas/admin.py` FOUND
- `server/app/routers/admin.py` FOUND
- `server/tests/test_admin.py` FOUND
- `server/app/main.py` modified (include_router admin) FOUND

Commits verified:
- `d9077e5` FOUND
