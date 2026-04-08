"""
FastAPI authentication dependencies.

Provides reusable Depends()-compatible callables for:
  - get_current_user: validates Bearer JWT access token → User
  - require_admin: gate for admin-only endpoints
  - verify_app_key: validates X-App-Key header → Device
"""

from datetime import datetime, timezone

from fastapi import Depends, Header, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from jose import JWTError
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import DbSession, get_db
from app.models.device import Device
from app.models.user import User
from app.services.jwt import decode_token

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/v1/auth/login")


async def get_current_user(
    token: str = Depends(oauth2_scheme),
    db: AsyncSession = Depends(get_db),
) -> User:
    """
    Validate Bearer access token and return the authenticated User.

    Raises HTTP 401 if:
      - Token is missing, malformed, or expired
      - Token type is not "access"
      - User does not exist or is inactive
    """
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    try:
        payload = decode_token(token)
        if payload.get("type") != "access":
            raise credentials_exception
        user_id: str | None = payload.get("sub")
        if user_id is None:
            raise credentials_exception
    except JWTError:
        raise credentials_exception

    result = await db.execute(select(User).where(User.id == int(user_id)))
    user = result.scalar_one_or_none()
    if user is None or not user.is_active:
        raise credentials_exception
    return user


async def require_admin(user: User = Depends(get_current_user)) -> User:
    """
    Require the current user to have admin privileges.

    Raises HTTP 403 if the authenticated user is not an admin.
    """
    if not user.is_admin:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Admin access required",
        )
    return user


async def verify_app_key(
    x_app_key: str = Header(..., alias="X-App-Key"),
    db: AsyncSession = Depends(get_db),
) -> Device:
    """
    Validate the X-App-Key header and return the corresponding Device.

    Updates last_seen_at on each successful call.

    Raises HTTP 401 if the App-Key is missing, unknown, or inactive.
    """
    result = await db.execute(select(Device).where(Device.app_key == x_app_key))
    device = result.scalar_one_or_none()
    if device is None or not device.is_active:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or inactive App-Key",
        )
    device.last_seen_at = datetime.now(timezone.utc)
    await db.commit()
    return device
