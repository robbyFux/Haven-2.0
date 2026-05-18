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
import re
from datetime import timezone

import psycopg2

from app.celery_app import celery_app
from app.config import settings
from app.services.notify import format_notification_message, send_email, send_pushover, send_signal

logger = logging.getLogger(__name__)


def _sync_dsn() -> str:
    """Strip async driver suffix from DATABASE_URL to get a psycopg2-compatible DSN."""
    return re.sub(r"\+[^:]+://", "://", settings.DATABASE_URL, count=1)


@celery_app.task(name="app.tasks.notifications.send_notification_task")
def send_notification_task(event_id: int) -> dict:
    """
    Dispatch notifications for a completed event.

    Loads the event, user, device, and analysis result from the database,
    formats a message, and sends to all configured channels.

    Uses psycopg2 directly (same pattern as _save_analysis_result) to avoid
    asyncpg event-loop conflicts in Celery prefork workers.

    @param event_id: database ID of the Event to notify about
    @return: dict with "status" key
    """
    # Load event data via synchronous psycopg2 (avoids asyncpg loop conflict)
    data = _load_event_data_sync(event_id)
    if data is None:
        logger.error("send_notification_task: event %d not found", event_id)
        return {"status": "error", "detail": "event not found"}

    user = data["user"]
    device_name = data["device_name"]
    event_type = data["event_type"]
    severity = data["severity"]
    timestamp = data["timestamp"]
    analysis_summary = data["analysis_summary"]

    if not user["notifications_enabled"]:
        return {"status": "disabled"}

    subject, body = format_notification_message(
        event_type=event_type,
        severity=severity,
        timestamp=timestamp,
        device_name=device_name,
        analysis_summary=analysis_summary,
    )

    results: dict[str, bool] = {}

    if user["notification_email"] and settings.SMTP_HOST:
        results["email"] = asyncio.run(send_email(user["notification_email"], subject, body))

    if user["notification_signal_number"] and settings.SIGNAL_API_URL:
        results["signal"] = send_signal(user["notification_signal_number"], body)

    if user["pushover_user_key"] and user["pushover_app_token"]:
        results["pushover"] = send_pushover(
            user["pushover_user_key"], body, subject, app_token=user["pushover_app_token"]
        )
    elif user["pushover_user_key"] or user["pushover_app_token"]:
        logger.warning(
            "send_notification_task: Pushover partially configured for event %d — both user_key and app_token required",
            event_id,
        )

    if not results:
        return {"status": "no_channels"}

    return {"status": "sent", "results": results}


def _load_event_data_sync(event_id: int) -> dict | None:
    """
    Load event, user, device, and analysis result from DB using psycopg2.

    Uses a synchronous psycopg2 connection to avoid asyncpg event-loop
    conflicts in Celery prefork workers (same pattern as _save_analysis_result).

    @param event_id: ID of the Event row to load
    @return: dict with event fields and user sub-dict, or None if not found
    """
    try:
        with psycopg2.connect(_sync_dsn()) as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT
                        e.event_type,
                        e.severity,
                        e.timestamp,
                        d.name AS device_name,
                        u.notifications_enabled,
                        u.notification_email,
                        u.notification_signal_number,
                        u.pushover_user_key,
                        u.pushover_app_token,
                        ar.labels,
                        ar.description
                    FROM events e
                    JOIN users u ON u.id = e.user_id
                    LEFT JOIN devices d ON d.id = e.device_id
                    LEFT JOIN analysis_results ar ON ar.event_id = e.id
                    WHERE e.id = %s
                    """,
                    (event_id,),
                )
                row = cur.fetchone()
    except Exception as exc:  # noqa: BLE001
        logger.exception("_load_event_data_sync: DB query failed for event %d: %s", event_id, exc)
        return None

    if row is None:
        return None

    (
        event_type,
        severity,
        ts,
        device_name,
        notifications_enabled,
        notification_email,
        notification_signal_number,
        pushover_user_key,
        pushover_app_token,
        ar_labels,
        ar_description,
    ) = row

    # Format timestamp
    if ts is not None and ts.tzinfo is None:
        ts = ts.replace(tzinfo=timezone.utc)
    timestamp_str = ts.strftime("%Y-%m-%d %H:%M:%S UTC") if ts else "Unknown"

    # Extract AI summary from analysis result if available
    analysis_summary: str | None = None
    if ar_description:
        analysis_summary = ar_description
    elif ar_labels:
        try:
            label_list = json.loads(ar_labels)
            if label_list:
                analysis_summary = "Detected: " + ", ".join(label_list)
        except (json.JSONDecodeError, TypeError):
            pass

    return {
        "user": {
            "notifications_enabled": notifications_enabled,
            "notification_email": notification_email,
            "notification_signal_number": notification_signal_number,
            "pushover_user_key": pushover_user_key,
            "pushover_app_token": pushover_app_token,
        },
        "device_name": device_name or "Unknown Device",
        "event_type": event_type,
        "severity": severity,
        "timestamp": timestamp_str,
        "analysis_summary": analysis_summary,
    }
