"""
Notification settings views for Haven Web UI.

Provides the notification_settings view for users to configure their email,
Signal, and Pushover notification channels and enable/disable notifications.
Supports HTMX partial responses for inline save without full page reload.
"""

import asyncio
import base64
import hashlib
from email.mime.text import MIMEText

import aiosmtplib
from django.conf import settings as django_settings
from django.contrib import messages
from django.contrib.auth.decorators import login_required
from django.http import HttpResponse
from django.shortcuts import redirect, render
from django.views.decorators.http import require_POST

from admin_panel.models import SMTPSettings
from cryptography.fernet import Fernet, InvalidToken

from .forms import NotificationSettingsForm


def _get_fernet() -> Fernet:
    key_bytes = hashlib.sha256(django_settings.SECRET_KEY.encode()).digest()
    return Fernet(base64.urlsafe_b64encode(key_bytes))


@login_required
def notification_settings(request):
    """
    GET/POST view for managing user notification settings.

    GET: populates form with current notification field values from request.user.
    POST: validates and saves changes; if HTMX request returns a partial
          (partials/notification_form.html) with inline success feedback;
          otherwise redirects to notifications:settings.
    """
    user = request.user

    if request.method == "POST":
        form = NotificationSettingsForm(request.POST)
        if form.is_valid():
            user.notifications_enabled = form.cleaned_data["notifications_enabled"]
            user.notification_email = form.cleaned_data["notification_email"] or None
            user.notification_signal_number = (
                form.cleaned_data["notification_signal_number"] or None
            )
            user.pushover_user_key = form.cleaned_data["pushover_user_key"] or None
            user.pushover_app_token = form.cleaned_data["pushover_app_token"] or None
            user.save(
                update_fields=[
                    "notifications_enabled",
                    "notification_email",
                    "notification_signal_number",
                    "pushover_user_key",
                    "pushover_app_token",
                ]
            )
            messages.success(request, "Notification settings saved.")
            if request.headers.get("HX-Request"):
                return render(
                    request,
                    "notifications/partials/notification_form.html",
                    {"form": form},
                )
            return redirect("notifications:settings")
        else:
            # Form invalid
            if request.headers.get("HX-Request"):
                return render(
                    request,
                    "notifications/partials/notification_form.html",
                    {"form": form},
                )
            return render(request, "notifications/settings.html", {"form": form})
    else:
        # GET — populate form with current values
        form = NotificationSettingsForm(
            initial={
                "notifications_enabled": user.notifications_enabled,
                "notification_email": user.notification_email or "",
                "notification_signal_number": user.notification_signal_number or "",
                "pushover_user_key": user.pushover_user_key or "",
                "pushover_app_token": user.pushover_app_token or "",
            }
        )

    return render(request, "notifications/settings.html", {"form": form})


@login_required
@require_POST
def email_test(request):
    """
    Send a test email to the logged-in user's notification_email address.

    Uses the admin-configured SMTPSettings (same server the Celery worker uses).
    Returns an HTMX-swappable span with success or error feedback.
    """
    def _htmx_error(msg: str) -> HttpResponse:
        return HttpResponse(f'<span class="text-red-400 text-sm">{msg}</span>')

    recipient = request.user.notification_email or ""
    if not recipient:
        return _htmx_error("No email address saved. Save your settings first.")

    smtp = SMTPSettings.get()
    if not smtp.smtp_host:
        return _htmx_error("SMTP not configured. Ask your admin to set up the mail server.")

    plaintext_password = ""
    if smtp.smtp_password_encrypted:
        try:
            plaintext_password = (
                _get_fernet().decrypt(smtp.smtp_password_encrypted.encode()).decode()
            )
        except InvalidToken:
            return _htmx_error("SMTP password could not be decrypted. Contact your admin.")

    msg = MIMEText("This is a test email from Haven Cloud.", "plain")
    msg["Subject"] = "Haven Cloud — Test Email"
    msg["From"] = smtp.smtp_from or smtp.smtp_user or "haven@localhost"
    msg["To"] = recipient

    tls_kwargs: dict = {}
    if smtp.tls_mode == SMTPSettings.TLS_SSL:
        tls_kwargs["use_tls"] = True
    elif smtp.tls_mode == SMTPSettings.TLS_STARTTLS:
        tls_kwargs["start_tls"] = True

    async def _send():
        await aiosmtplib.send(
            msg,
            hostname=smtp.smtp_host,
            port=smtp.smtp_port,
            username=smtp.smtp_user or None,
            password=plaintext_password or None,
            timeout=10,
            **tls_kwargs,
        )

    try:
        asyncio.run(_send())
    except Exception as exc:
        return _htmx_error(f"Send failed: {exc}")

    return HttpResponse(
        f'<span class="text-green-400 text-sm">&#10003; Test email sent to {recipient}.</span>'
    )
