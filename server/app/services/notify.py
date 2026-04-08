"""
Notification sender functions for Haven Cloud.

Provides three notification channels:
  - Email via SMTP (aiosmtplib, async)
  - Signal via signal-cli REST API (httpx synchronous, for Celery workers)
  - Pushover via Pushover HTTP API (httpx synchronous, for Celery workers)

All send functions return bool (True = sent, False = error). They log errors
but never raise — a failed notification must not crash the Celery worker.
"""

import logging
from email.mime.text import MIMEText

import httpx

from app.config import settings

logger = logging.getLogger(__name__)


async def send_email(to: str, subject: str, body: str) -> bool:
    """
    Send an email notification via SMTP with STARTTLS.

    Uses aiosmtplib for async delivery (called via asyncio.run from the
    Celery task context).

    @param to: recipient email address
    @param subject: email subject line
    @param body: plain-text message body
    @return: True on success, False if the send failed
    """
    try:
        import aiosmtplib

        message = MIMEText(body, "plain", "utf-8")
        message["From"] = settings.SMTP_FROM
        message["To"] = to
        message["Subject"] = subject

        await aiosmtplib.send(
            message,
            hostname=settings.SMTP_HOST,
            port=settings.SMTP_PORT,
            username=settings.SMTP_USER or None,
            password=settings.SMTP_PASSWORD or None,
            start_tls=True,
        )
        return True
    except Exception as exc:  # noqa: BLE001
        logger.error("send_email: failed to send to %s: %s", to, exc)
        return False


def send_signal(recipient: str, message: str) -> bool:
    """
    Send a Signal message via the signal-cli REST API.

    Synchronous (called from Celery task context; no event loop).

    @param recipient: E.164 phone number or Signal group ID of the recipient
    @param message: text body of the Signal message
    @return: True on 200/201 response, False on error
    """
    url = f"{settings.SIGNAL_API_URL.rstrip('/')}/v2/send"
    payload = {
        "message": message,
        "number": settings.SIGNAL_SENDER,
        "recipients": [recipient],
    }
    headers: dict[str, str] = {"Content-Type": "application/json"}
    if settings.SIGNAL_AUTH_TOKEN:
        headers["Authorization"] = f"Bearer {settings.SIGNAL_AUTH_TOKEN}"

    try:
        with httpx.Client(timeout=10.0) as client:
            response = client.post(url, json=payload, headers=headers)
        if response.status_code in (200, 201):
            return True
        logger.error(
            "send_signal: unexpected status %d for recipient %s: %s",
            response.status_code,
            recipient,
            response.text[:200],
        )
        return False
    except Exception as exc:  # noqa: BLE001
        logger.error("send_signal: request failed for recipient %s: %s", recipient, exc)
        return False


def send_pushover(user_key: str, message: str, title: str = "Haven Alert") -> bool:
    """
    Send a Pushover push notification.

    Synchronous (called from Celery task context; no event loop).

    @param user_key: Pushover user/group key
    @param message: notification message body
    @param title: notification title (default: "Haven Alert")
    @return: True on HTTP 200, False on error
    """
    url = "https://api.pushover.net/1/messages.json"
    data = {
        "token": settings.PUSHOVER_APP_TOKEN,
        "user": user_key,
        "message": message,
        "title": title,
    }

    try:
        with httpx.Client(timeout=10.0) as client:
            response = client.post(url, data=data)
        if response.status_code == 200:
            return True
        logger.error(
            "send_pushover: unexpected status %d for user_key %s: %s",
            response.status_code,
            user_key[:8] + "...",
            response.text[:200],
        )
        return False
    except Exception as exc:  # noqa: BLE001
        logger.error("send_pushover: request failed for user_key %s: %s", user_key[:8] + "...", exc)
        return False


def format_notification_message(
    event_type: str,
    severity: str,
    timestamp: str,
    device_name: str,
    analysis_summary: str | None,
) -> tuple[str, str]:
    """
    Build a (subject, body) tuple for a Haven alert notification.

    Used by send_notification_task to produce consistent messages across
    Email, Signal, and Pushover channels.

    @param event_type: TriggerType name (e.g. "CAMERA_PERSON", "MICROPHONE")
    @param severity: Severity level (e.g. "HIGH", "CRITICAL")
    @param timestamp: ISO 8601 timestamp string
    @param device_name: human-readable device name
    @param analysis_summary: AI-generated description, or None
    @return: (subject, body) pair ready to send
    """
    subject = f"Haven Alert: {severity} {event_type} on {device_name}"

    lines = [
        "Haven Security Alert",
        "=" * 40,
        f"Device:    {device_name}",
        f"Event:     {event_type}",
        f"Severity:  {severity}",
        f"Time:      {timestamp}",
    ]

    if analysis_summary:
        lines.append("")
        lines.append("AI Analysis:")
        lines.append(analysis_summary)

    lines.append("")
    lines.append("-- Haven 2.0 Security System")

    body = "\n".join(lines)
    return subject, body
