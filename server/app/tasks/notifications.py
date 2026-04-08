"""
Celery notification dispatch task for Haven Cloud.

send_notification_task loads an event and its associated user, device,
and analysis result from the database, then dispatches notifications to
all configured channels (Email, Signal, Pushover) based on the user's
notification settings.

Returns a dict with:
  {"status": "disabled"}           — notifications_enabled is False
  {"status": "sent", "results": {}} — one or more channels attempted
  {"status": "no_channels"}        — enabled but no channels configured
"""

import asyncio
import json
import logging
from datetime import timezone

from app.celery_app import celery_app
from app.config import settings
from app.database import AsyncSessionLocal
from app.services.notify import format_notification_message, send_email, send_pushover, send_signal

logger = logging.getLogger(__name__)


@celery_app.task(name="app.tasks.notifications.send_notification_task")
def send_notification_task(event_id: int) -> dict:
    """
    Dispatch notifications for a completed event.

    Loads the event, user, device, and analysis result from the database,
    formats a message, and sends to all configured channels.

    @param event_id: database ID of the Event to notify about
    @return: dict with "status" key
    """
    # Load event data via async DB session
    data = asyncio.run(_load_event_data(event_id))
    if data is None:
        logger.error("send_notification_task: event %d not found", event_id)
        return {"status": "error", "detail": "event not found"}

    user = data["user"]
    device_name = data["device_name"]
    event_type = data["event_type"]
    severity = data["severity"]
    timestamp = data["timestamp"]
    analysis_summary = data["analysis_summary"]

    if not user.notifications_enabled:
        return {"status": "disabled"}

    subject, body = format_notification_message(
        event_type=event_type,
        severity=severity,
        timestamp=timestamp,
        device_name=device_name,
        analysis_summary=analysis_summary,
    )

    results: dict[str, bool] = {}

    if user.notification_email and settings.SMTP_HOST:
        results["email"] = asyncio.run(send_email(user.notification_email, subject, body))

    if user.notification_signal_number and settings.SIGNAL_API_URL:
        results["signal"] = send_signal(user.notification_signal_number, body)

    if user.pushover_user_key and settings.PUSHOVER_APP_TOKEN:
        results["pushover"] = send_pushover(user.pushover_user_key, body, subject)

    if not results:
        return {"status": "no_channels"}

    return {"status": "sent", "results": results}


async def _load_event_data(event_id: int) -> dict | None:
    """
    Load event, user, device, and analysis result from DB.

    @param event_id: ID of the Event row to load
    @return: dict with event fields and related objects, or None if not found
    """
    from sqlalchemy import select
    from sqlalchemy.orm import selectinload

    from app.models.event import AnalysisResult, Event
    from app.models.device import Device
    from app.models.user import User

    async with AsyncSessionLocal() as session:
        result = await session.execute(
            select(Event)
            .options(
                selectinload(Event.user),
                selectinload(Event.device),
                selectinload(Event.analysis_result),
            )
            .where(Event.id == event_id)
        )
        event = result.scalar_one_or_none()
        if event is None:
            return None

        # Format timestamp to ISO string
        ts = event.timestamp
        if ts.tzinfo is None:
            ts = ts.replace(tzinfo=timezone.utc)
        timestamp_str = ts.strftime("%Y-%m-%d %H:%M:%S UTC")

        # Extract AI summary from analysis result if available
        analysis_summary: str | None = None
        if event.analysis_result is not None:
            ar = event.analysis_result
            if ar.description:
                analysis_summary = ar.description
            elif ar.labels:
                try:
                    label_list = json.loads(ar.labels)
                    if label_list:
                        analysis_summary = "Detected: " + ", ".join(label_list)
                except (json.JSONDecodeError, TypeError):
                    pass

        return {
            "user": event.user,
            "device_name": event.device.name if event.device else "Unknown Device",
            "event_type": event.event_type,
            "severity": event.severity,
            "timestamp": timestamp_str,
            "analysis_summary": analysis_summary,
        }
