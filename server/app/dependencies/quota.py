"""
Quota enforcement dependencies for event upload.

Provides:
  - get_user_for_device: loads the User that owns the authenticated Device
  - check_storage_quota: raises HTTP 413 if upload would exceed storage quota
  - check_event_quota: raises HTTP 429 if user has reached max event count
"""

from fastapi import Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import DbSession, get_db
from app.dependencies.auth import verify_app_key
from app.models.device import Device
from app.models.user import User


async def get_user_for_device(
    device: Device = Depends(verify_app_key),
    db: AsyncSession = Depends(get_db),
) -> User:
    """
    Load the User that owns the authenticated Device.

    @param device: Device returned by verify_app_key dependency
    @param db: async database session
    @return: User ORM instance
    @raises HTTPException 401: if the owning user is not found (data integrity issue)
    """
    result = await db.execute(select(User).where(User.id == device.user_id))
    user = result.scalar_one_or_none()
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Device owner not found",
        )
    return user


def check_storage_quota(user: User, additional_bytes: int) -> None:
    """
    Raise HTTP 413 if adding additional_bytes would exceed the user's storage quota.

    @param user: User ORM instance with quota fields populated
    @param additional_bytes: size of the file about to be stored
    @raises HTTPException 413: if storage quota would be exceeded
    """
    quota_bytes = user.storage_quota_mb * 1024 * 1024
    if user.current_storage_bytes + additional_bytes > quota_bytes:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail="Storage quota exceeded",
        )


def check_event_quota(user: User) -> None:
    """
    Raise HTTP 429 if the user has reached their maximum event count.

    @param user: User ORM instance with quota fields populated
    @raises HTTPException 429: if event count quota is reached
    """
    if user.current_event_count >= user.max_events:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="Event count quota exceeded",
        )
