"""
Event upload and retrieval endpoints.

Provides:
  POST /devices/{app_key}/events  — upload event metadata + optional encrypted video
  GET  /events                    — paginated event list for the authenticated user
  GET  /events/{event_id}         — single event retrieval
  DELETE /events/{event_id}       — delete event + media + update user quotas

Upload flow:
  1. Authenticate via X-App-Key header (verify_app_key dependency)
  2. Parse metadata JSON from multipart Form field
  3. Check event count quota
  4. If video provided: check storage quota, optionally encrypt, save to MEDIA_ROOT
  5. Create Event + EventTrigger rows, update user counters
  6. Enqueue Celery task: analyze_event_task (AI enabled) or send_notification_task (AI disabled)
  7. Return 201 EventResponse
"""

import json
import logging

from fastapi import APIRouter, Depends, File, Form, Header, HTTPException, Query, UploadFile, status

logger = logging.getLogger(__name__)
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.config import settings
from app.database import DbSession, get_db
from app.dependencies.auth import get_current_user, verify_app_key
from app.dependencies.quota import check_event_quota, check_storage_quota
from app.models.device import Device
from app.models.event import AnalysisResult, Event, EventTrigger
from app.models.user import User
from app.schemas.event import EventCreateSchema, EventListResponse, EventResponse
from app.services import storage as storage_service
from app.services.ai_settings import get_ai_backend
from app.services.crypto import derive_key, encrypt_file
from app.tasks.analysis import analyze_event_task
from app.tasks.notifications import send_notification_task

router = APIRouter(tags=["events"])

# Maximum video upload size: 50 MB
MAX_VIDEO_BYTES = 50 * 1024 * 1024


def _build_event_response(event: Event) -> EventResponse:
    """Build an EventResponse from an ORM Event (must have device relationship loaded)."""
    return EventResponse(
        id=event.id,
        event_type=event.event_type,
        severity=event.severity,
        timestamp=event.timestamp,
        sensor_value=event.sensor_value,
        media_path=event.media_path,
        has_analysis=event.analysis_result is not None,
        device_name=event.device.name if event.device else "",
        created_at=event.created_at,
    )


@router.post(
    "/devices/{app_key}/events",
    status_code=status.HTTP_201_CREATED,
    response_model=EventResponse,
    summary="Upload a security event from a Haven device",
)
async def upload_event(
    app_key: str,
    metadata: str = Form(..., description="JSON-encoded EventCreateSchema"),
    video: UploadFile | None = File(None),
    x_app_key: str = Header(..., alias="X-App-Key"),
    x_encryption_password: str | None = Header(None, alias="X-Encryption-Password"),
    db: AsyncSession = Depends(get_db),
) -> EventResponse:
    """
    Upload an event from a Haven device.

    Authentication via X-App-Key header. The path parameter {app_key} must match
    the X-App-Key header value for security (prevents cross-device uploads).
    """
    # Validate that path param matches header (prevents cross-device spoofing)
    if app_key != x_app_key:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="App-Key mismatch: path parameter does not match X-App-Key header",
        )

    # Authenticate device via X-App-Key header
    device = await verify_app_key(x_app_key=x_app_key, db=db)

    # Load the device owner
    result = await db.execute(select(User).where(User.id == device.user_id))
    user = result.scalar_one_or_none()
    if user is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Device owner not found")

    # Parse metadata
    try:
        metadata_dict = json.loads(metadata)
        event_data = EventCreateSchema(**metadata_dict)
    except (json.JSONDecodeError, ValueError) as exc:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Invalid metadata JSON: {exc}",
        )

    # Quota: event count
    check_event_quota(user)

    # Handle optional video upload
    media_path: str | None = None
    media_size_bytes: int | None = None
    is_encrypted = False
    encryption_key: bytes | None = None

    if video is not None:
        # Read video in chunks (max 50 MB)
        video_bytes = b""
        while True:
            chunk = await video.read(64 * 1024)  # 64 KB chunks
            if not chunk:
                break
            video_bytes += chunk
            if len(video_bytes) > MAX_VIDEO_BYTES:
                raise HTTPException(
                    status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                    detail=f"Video exceeds maximum size of {MAX_VIDEO_BYTES // (1024 * 1024)} MB",
                )

        # Quota: storage
        check_storage_quota(user, len(video_bytes))

        # Optional encryption
        if x_encryption_password is not None:
            encryption_key = derive_key(x_encryption_password, user.username)
            video_bytes = encrypt_file(video_bytes, encryption_key)
            is_encrypted = True

        # Determine filename
        original_filename = video.filename or "video.mp4"
        if is_encrypted:
            filename = original_filename + ".enc"
        else:
            filename = original_filename

        media_size_bytes = len(video_bytes)

        # Create the event row first (need event_id for directory structure)
        event = Event(
            user_id=user.id,
            device_id=device.id,
            event_type=event_data.event_type,
            severity=event_data.severity,
            timestamp=event_data.timestamp,
            sensor_value=event_data.sensor_value,
            is_encrypted=is_encrypted,
        )
        db.add(event)
        await db.flush()  # Get event.id without committing

        # Save media file
        media_path = await storage_service.save_media(user.id, event.id, video_bytes, filename)
        event.media_path = media_path
        event.media_size_bytes = media_size_bytes

    else:
        # No video
        event = Event(
            user_id=user.id,
            device_id=device.id,
            event_type=event_data.event_type,
            severity=event_data.severity,
            timestamp=event_data.timestamp,
            sensor_value=event_data.sensor_value,
            is_encrypted=False,
        )
        db.add(event)
        await db.flush()

    # Create trigger rows
    if event_data.triggers:
        for trigger_schema in event_data.triggers:
            trigger = EventTrigger(
                event_id=event.id,
                trigger_type=trigger_schema.trigger_type,
                sensor_value=trigger_schema.sensor_value,
            )
            db.add(trigger)

    # Update user counters
    if media_size_bytes:
        user.current_storage_bytes += media_size_bytes
    user.current_event_count += 1

    await db.commit()

    # Reload event with relationships for response building
    result = await db.execute(
        select(Event)
        .options(selectinload(Event.device), selectinload(Event.analysis_result))
        .where(Event.id == event.id)
    )
    event = result.scalar_one()

    # Enqueue Celery task — read AI backend from DB (admin_panel_aisettings) so
    # that WebUI config changes take effect without a server restart.
    logger.info(
        "upload_event: received event_id=%d has_video=%s file_size=%s",
        event.id,
        video is not None,
        media_size_bytes,
    )
    ai_backend = await get_ai_backend(session=db)
    logger.info("upload_event: get_ai_backend() returned %r for event_id=%d", ai_backend, event.id)
    if ai_backend != "none":
        encryption_key_hex = encryption_key.hex() if encryption_key is not None else None
        logger.info(
            "upload_event: dispatching analyze_event_task for event_id=%d media_path=%r encrypted=%s",
            event.id,
            media_path,
            encryption_key_hex is not None,
        )
        analyze_event_task.delay(event.id, media_path, encryption_key_hex)
    else:
        logger.info(
            "upload_event: ai_backend=none — skipping analysis, dispatching send_notification_task for event_id=%d",
            event.id,
        )
        send_notification_task.delay(event.id)

    return _build_event_response(event)


@router.get(
    "/events",
    response_model=EventListResponse,
    summary="List events for the authenticated user",
)
async def list_events(
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    device_id: int | None = Query(None),
    severity: str | None = Query(None),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> EventListResponse:
    """Return a paginated list of events belonging to the authenticated user."""
    base_query = select(Event).where(Event.user_id == current_user.id)

    if device_id is not None:
        base_query = base_query.where(Event.device_id == device_id)
    if severity is not None:
        base_query = base_query.where(Event.severity == severity)

    # Count total
    count_query = select(func.count()).select_from(base_query.subquery())
    total_result = await db.execute(count_query)
    total = total_result.scalar_one()

    # Fetch page
    offset = (page - 1) * page_size
    events_query = (
        base_query
        .options(selectinload(Event.device), selectinload(Event.analysis_result))
        .order_by(Event.timestamp.desc())
        .offset(offset)
        .limit(page_size)
    )
    events_result = await db.execute(events_query)
    events = events_result.scalars().all()

    return EventListResponse(
        events=[_build_event_response(e) for e in events],
        total=total,
        page=page,
        page_size=page_size,
    )


@router.get(
    "/events/{event_id}",
    response_model=EventResponse,
    summary="Get a single event by ID",
)
async def get_event(
    event_id: int,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> EventResponse:
    """Return a single event owned by the authenticated user."""
    result = await db.execute(
        select(Event)
        .options(selectinload(Event.device), selectinload(Event.analysis_result))
        .where(Event.id == event_id, Event.user_id == current_user.id)
    )
    event = result.scalar_one_or_none()
    if event is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Event not found")
    return _build_event_response(event)


@router.delete(
    "/events/{event_id}",
    status_code=status.HTTP_200_OK,
    summary="Delete an event and its media",
)
async def delete_event(
    event_id: int,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> dict:
    """
    Delete an event, its associated media file, and update user storage counters.

    EventTrigger and AnalysisResult rows are cascade-deleted by the DB.
    """
    result = await db.execute(
        select(Event).where(Event.id == event_id, Event.user_id == current_user.id)
    )
    event = result.scalar_one_or_none()
    if event is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Event not found")

    # Delete media file if present
    if event.media_path:
        await storage_service.delete_media(event.media_path)

    # Update user counters
    result_user = await db.execute(select(User).where(User.id == current_user.id))
    user = result_user.scalar_one()
    if event.media_size_bytes:
        user.current_storage_bytes = max(0, user.current_storage_bytes - event.media_size_bytes)
    user.current_event_count = max(0, user.current_event_count - 1)

    await db.delete(event)
    await db.commit()

    return {"status": "deleted"}
