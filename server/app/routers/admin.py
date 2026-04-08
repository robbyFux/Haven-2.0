"""
Admin API router — user management and system statistics.

All endpoints require the requesting user to hold admin privileges
(enforced via the require_admin dependency from auth.py).

Endpoints:
  GET  /users                  — list all users with device counts
  GET  /users/{user_id}        — single user detail
  PATCH /users/{user_id}/quota — update storage_quota_mb / max_events
  PATCH /users/{user_id}/admin — toggle is_admin flag
  DELETE /users/{user_id}      — deactivate user (soft-delete)
  GET  /stats                  — server-wide statistics
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import func, select

from app.database import DbSession
from app.dependencies.auth import require_admin
from app.models.device import Device
from app.models.event import Event
from app.models.user import User
from app.schemas.admin import (
    QuotaUpdateRequest,
    SystemStatsResponse,
    UserAdminResponse,
    UserListResponse,
)

router = APIRouter(tags=["admin"])


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


async def _build_user_response(db: DbSession, user: User) -> UserAdminResponse:
    """Build a UserAdminResponse by counting the user's devices inline."""
    count_result = await db.execute(
        select(func.count()).select_from(Device).where(Device.user_id == user.id)
    )
    device_count: int = count_result.scalar_one()
    return UserAdminResponse(
        id=user.id,
        username=user.username,
        is_admin=user.is_admin,
        is_active=user.is_active,
        totp_enabled=user.totp_enabled,
        storage_quota_mb=user.storage_quota_mb,
        max_events=user.max_events,
        current_storage_bytes=user.current_storage_bytes,
        current_event_count=user.current_event_count,
        device_count=device_count,
        created_at=user.created_at,
    )


async def _get_user_or_404(db: DbSession, user_id: int) -> User:
    """Fetch a user by ID or raise HTTP 404."""
    result = await db.execute(select(User).where(User.id == user_id))
    user = result.scalar_one_or_none()
    if user is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")
    return user


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------


@router.get("/users", response_model=UserListResponse, summary="List all users")
async def list_users(
    db: DbSession,
    _admin: User = Depends(require_admin),
) -> UserListResponse:
    """Return all registered users with quota and device counts."""
    result = await db.execute(select(User).order_by(User.id))
    users = result.scalars().all()
    user_responses = [await _build_user_response(db, u) for u in users]
    return UserListResponse(users=user_responses, total=len(user_responses))


@router.get("/users/{user_id}", response_model=UserAdminResponse, summary="Get user detail")
async def get_user(
    user_id: int,
    db: DbSession,
    _admin: User = Depends(require_admin),
) -> UserAdminResponse:
    """Return full detail for a single user including quota and device count."""
    user = await _get_user_or_404(db, user_id)
    return await _build_user_response(db, user)


@router.patch(
    "/users/{user_id}/quota",
    response_model=UserAdminResponse,
    summary="Update user quota",
)
async def update_quota(
    user_id: int,
    body: QuotaUpdateRequest,
    db: DbSession,
    _admin: User = Depends(require_admin),
) -> UserAdminResponse:
    """
    Update a user's storage and/or event quota.

    Only provided (non-None) fields are updated.
    """
    user = await _get_user_or_404(db, user_id)
    if body.storage_quota_mb is not None:
        user.storage_quota_mb = body.storage_quota_mb
    if body.max_events is not None:
        user.max_events = body.max_events
    await db.commit()
    await db.refresh(user)
    return await _build_user_response(db, user)


@router.patch(
    "/users/{user_id}/admin",
    response_model=UserAdminResponse,
    summary="Toggle user admin status",
)
async def toggle_admin(
    user_id: int,
    body: dict,
    db: DbSession,
    _admin: User = Depends(require_admin),
) -> UserAdminResponse:
    """
    Set or clear the is_admin flag for a user.

    Body: ``{"is_admin": true|false}``
    """
    user = await _get_user_or_404(db, user_id)
    is_admin = body.get("is_admin")
    if not isinstance(is_admin, bool):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="is_admin must be a boolean",
        )
    user.is_admin = is_admin
    await db.commit()
    await db.refresh(user)
    return await _build_user_response(db, user)


@router.delete("/users/{user_id}", summary="Deactivate user")
async def deactivate_user(
    user_id: int,
    db: DbSession,
    admin: User = Depends(require_admin),
) -> dict:
    """
    Deactivate a user (soft-delete: sets is_active=False).

    Prevents an admin from deactivating their own account.
    """
    if user_id == admin.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Cannot deactivate your own account",
        )
    user = await _get_user_or_404(db, user_id)
    user.is_active = False
    await db.commit()
    return {"status": "deactivated"}


@router.get("/stats", response_model=SystemStatsResponse, summary="System statistics")
async def get_stats(
    db: DbSession,
    _admin: User = Depends(require_admin),
) -> SystemStatsResponse:
    """Return server-wide aggregate statistics."""
    total_users = (await db.execute(select(func.count()).select_from(User))).scalar_one()
    total_events = (await db.execute(select(func.count()).select_from(Event))).scalar_one()
    total_devices = (await db.execute(select(func.count()).select_from(Device))).scalar_one()
    total_storage_bytes = (
        await db.execute(select(func.coalesce(func.sum(User.current_storage_bytes), 0)))
    ).scalar_one()

    return SystemStatsResponse(
        total_users=total_users,
        total_events=total_events,
        total_devices=total_devices,
        total_storage_bytes=total_storage_bytes,
    )
