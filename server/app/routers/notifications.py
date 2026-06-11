"""
Notification settings router for Haven Cloud.

Provides endpoints to read and update per-user notification channel configuration,
and to send a test notification to all configured channels.

Routes (all require JWT Bearer auth):
  GET  /settings  — return current notification settings for authenticated user
  PATCH /settings — update notification channel config fields
  POST /test      — send a test alert to all configured channels
"""

import asyncio

from fastapi import APIRouter, Depends

from app.dependencies.auth import get_current_user
from app.database import DbSession
from app.models.user import User
from app.schemas.notification import NotificationSettingsRequest, NotificationSettingsResponse
from app.services.notify import (
    format_notification_message,
    send_email,
    send_pushover,
    send_signal,
)
from app.services.smtp_settings import get_smtp_settings
from app.config import settings

router = APIRouter(tags=["notifications"])


@router.get("/settings", response_model=NotificationSettingsResponse)
async def get_notification_settings(
    current_user: User = Depends(get_current_user),
) -> NotificationSettingsResponse:
    """Return the current user's notification channel configuration."""
    return NotificationSettingsResponse.model_validate(current_user)


@router.patch("/settings", response_model=NotificationSettingsResponse)
async def update_notification_settings(
    body: NotificationSettingsRequest,
    db: DbSession,
    current_user: User = Depends(get_current_user),
) -> NotificationSettingsResponse:
    """
    Update the current user's notification channel configuration.

    Only fields explicitly set in the request body are updated;
    omitted fields (None in the model) are left unchanged.
    """
    if body.notification_email is not None:
        current_user.notification_email = body.notification_email
    if body.notification_signal_number is not None:
        current_user.notification_signal_number = body.notification_signal_number
    if body.pushover_user_key is not None:
        current_user.pushover_user_key = body.pushover_user_key
    if body.notifications_enabled is not None:
        current_user.notifications_enabled = body.notifications_enabled

    await db.commit()
    await db.refresh(current_user)
    return NotificationSettingsResponse.model_validate(current_user)


@router.post("/test")
async def send_test_notification(
    current_user: User = Depends(get_current_user),
) -> dict:
    """
    Send a test alert to all notification channels configured by the current user.

    Returns a results dict indicating which channels were attempted and whether
    each send succeeded (true) or failed (false). Channels without required
    credentials are not attempted.
    """
    subject, body = format_notification_message(
        event_type="TEST",
        severity="LOW",
        timestamp="now",
        device_name="Haven Test",
        analysis_summary="This is a test notification from Haven Cloud.",
    )

    results: dict[str, bool] = {}

    if current_user.notification_email:
        smtp = await get_smtp_settings()
        if smtp.host:
            results["email"] = await send_email(
                current_user.notification_email, subject, body, smtp=smtp
            )

    if current_user.notification_signal_number and settings.SIGNAL_API_URL:
        results["signal"] = send_signal(current_user.notification_signal_number, body)

    if current_user.pushover_user_key and settings.PUSHOVER_APP_TOKEN:
        results["pushover"] = send_pushover(current_user.pushover_user_key, body, subject)

    return {"results": results}
