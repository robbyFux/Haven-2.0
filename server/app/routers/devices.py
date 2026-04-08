"""
Device management router for Haven Cloud.

Endpoints:
  POST   /               — Register a new device, returns App-Key (shown once)
  GET    /               — List all devices owned by the current user
  GET    /{device_id}    — Get a single device by ID
  DELETE /{device_id}    — Revoke a device (soft-delete: is_active=False)

All endpoints require a valid Bearer JWT (get_current_user dependency).
App-Key format: hav_<64 hex chars> (68 chars total).
"""

import secrets

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import DbSession, get_db
from app.dependencies.auth import get_current_user
from app.models.device import Device
from app.models.user import User
from app.schemas.device import DeviceCreateRequest, DeviceListResponse, DeviceResponse

router = APIRouter(tags=["devices"])


@router.post("/", response_model=DeviceResponse, status_code=status.HTTP_201_CREATED)
async def create_device(
    body: DeviceCreateRequest,
    db: DbSession,
    current_user: User = Depends(get_current_user),
) -> DeviceResponse:
    """
    Register a new Haven Android app as a device for the current user.

    Returns a DeviceResponse that includes the app_key. The app_key is the
    device's authentication credential for upload and event endpoints.
    """
    app_key = f"hav_{secrets.token_hex(32)}"

    device = Device(
        user_id=current_user.id,
        name=body.name,
        app_key=app_key,
    )
    db.add(device)
    await db.commit()
    await db.refresh(device)

    return DeviceResponse.model_validate(device)


@router.get("/", response_model=DeviceListResponse)
async def list_devices(
    db: DbSession,
    current_user: User = Depends(get_current_user),
) -> DeviceListResponse:
    """
    List all devices registered to the current user, newest first.
    """
    result = await db.execute(
        select(Device)
        .where(Device.user_id == current_user.id)
        .order_by(Device.created_at.desc())
    )
    devices = result.scalars().all()
    return DeviceListResponse(devices=[DeviceResponse.model_validate(d) for d in devices])


@router.get("/{device_id}", response_model=DeviceResponse)
async def get_device(
    device_id: int,
    db: DbSession,
    current_user: User = Depends(get_current_user),
) -> DeviceResponse:
    """
    Retrieve a single device by ID.

    Returns 404 if the device does not exist or belongs to another user.
    """
    result = await db.execute(
        select(Device).where(Device.id == device_id, Device.user_id == current_user.id)
    )
    device = result.scalar_one_or_none()
    if device is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Device not found")
    return DeviceResponse.model_validate(device)


@router.delete("/{device_id}")
async def revoke_device(
    device_id: int,
    db: DbSession,
    current_user: User = Depends(get_current_user),
) -> dict:
    """
    Revoke a device by setting is_active=False (soft delete).

    The App-Key becomes invalid immediately. The device record is retained
    for audit purposes. Returns {"status": "revoked"} on success.
    """
    result = await db.execute(
        select(Device).where(Device.id == device_id, Device.user_id == current_user.id)
    )
    device = result.scalar_one_or_none()
    if device is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Device not found")

    device.is_active = False
    await db.commit()

    return {"status": "revoked"}
