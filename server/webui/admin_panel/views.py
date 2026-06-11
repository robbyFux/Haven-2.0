"""
Admin panel views for Haven Web UI (plan 06-05, 08-03).

All views are protected by @admin_required (login + is_admin=True).
Supports HTMX partial rendering for user table, user row updates, and
SMTP settings form swap.
"""

import asyncio
import base64
import hashlib
import os

import aiosmtplib
from cryptography.fernet import Fernet, InvalidToken
from django.conf import settings as django_settings
from django.contrib import messages
from django.db.models import Sum
from django.http import HttpResponse
from django.shortcuts import get_object_or_404, redirect, render
from django.views.decorators.http import require_POST
from email.mime.text import MIMEText

from accounts.models import HavenUser
from admin_panel.forms import AISettingsForm, QuotaEditForm, SmtpSettingsForm
from admin_panel.models import AISettings, SMTPSettings
from core.decorators import admin_required
from devices.models import Device
from events.models import Event


def _get_fernet() -> Fernet:
    """
    Derive a stable Fernet key from django_settings.SECRET_KEY.

    SHA-256 of the key → 32 bytes → base64url → valid Fernet key.
    This is deterministic: the same SECRET_KEY always yields the same Fernet key.
    Mirrors the pattern in server/app/services/totp.py.
    """
    key_bytes = hashlib.sha256(django_settings.SECRET_KEY.encode()).digest()
    fernet_key = base64.urlsafe_b64encode(key_bytes)
    return Fernet(fernet_key)


def _format_storage(total_bytes: int) -> str:
    """Format bytes as human-readable MB or GB string."""
    if total_bytes >= 1_073_741_824:  # 1 GB
        return f"{total_bytes / 1_073_741_824:.1f} GB"
    return f"{total_bytes / 1_048_576:.1f} MB"


@admin_required
def dashboard(request):
    """
    Admin dashboard: system stats + paginated user list.

    HTMX requests return the user table partial only.
    Full requests return the complete dashboard template.
    """
    total_users = HavenUser.objects.count()
    total_events = Event.objects.count()
    total_storage_bytes = (
        HavenUser.objects.aggregate(total=Sum("current_storage_bytes"))["total"] or 0
    )
    total_storage = _format_storage(total_storage_bytes)

    users = HavenUser.objects.all().order_by("-created_at")

    context = {
        "total_users": total_users,
        "total_events": total_events,
        "total_storage": total_storage,
        "total_storage_bytes": total_storage_bytes,
        "users": users,
    }

    if request.htmx:
        return render(request, "admin_panel/partials/user_table.html", context)
    return render(request, "admin_panel/dashboard.html", context)


@admin_required
def user_detail(request, user_id: int):
    """
    Admin view of a single user's details and quota settings.

    Shows: user info, event count, device count, storage used.
    Includes QuotaEditForm for inline editing.
    """
    target_user = get_object_or_404(HavenUser, id=user_id)
    event_count = Event.objects.filter(user_id=target_user.id).count()
    device_count = Device.objects.filter(user_id=target_user.id).count()

    form = QuotaEditForm(
        initial={
            "storage_quota_mb": target_user.storage_quota_mb,
            "max_events": target_user.max_events,
        }
    )

    return render(
        request,
        "admin_panel/user_detail.html",
        {
            "target_user": target_user,
            "form": form,
            "event_count": event_count,
            "device_count": device_count,
        },
    )


@admin_required
def quota_edit(request, user_id: int):
    """
    Update a user's storage quota and max event count.

    POST only. HTMX: returns updated user row partial on success, 422 on error.
    Non-HTMX: redirects to user detail on success, re-renders form on error.
    """
    target_user = get_object_or_404(HavenUser, id=user_id)

    if request.method != "POST":
        return redirect("admin_panel:user_detail", user_id=user_id)

    form = QuotaEditForm(request.POST)
    if form.is_valid():
        target_user.storage_quota_mb = form.cleaned_data["storage_quota_mb"]
        target_user.max_events = form.cleaned_data["max_events"]
        target_user.save(update_fields=["storage_quota_mb", "max_events"])
        messages.success(request, f"Quota updated for {target_user.username}")

        if request.htmx:
            return render(
                request,
                "admin_panel/partials/user_row.html",
                {"user": target_user},
            )
        return redirect("admin_panel:user_detail", user_id=user_id)

    # Validation failed
    if request.htmx:
        return HttpResponse(form.errors.as_text(), status=422)

    event_count = Event.objects.filter(user_id=target_user.id).count()
    device_count = Device.objects.filter(user_id=target_user.id).count()
    return render(
        request,
        "admin_panel/user_detail.html",
        {
            "target_user": target_user,
            "form": form,
            "event_count": event_count,
            "device_count": device_count,
        },
    )


@admin_required
def toggle_active(request, user_id: int):
    """
    Flip a user's is_active flag.

    POST only. HTMX: returns updated user row partial.
    Non-HTMX: redirects to user detail.
    """
    target_user = get_object_or_404(HavenUser, id=user_id)

    if request.method != "POST":
        return redirect("admin_panel:user_detail", user_id=user_id)

    target_user.is_active = not target_user.is_active
    target_user.save(update_fields=["is_active"])

    action = "Activated" if target_user.is_active else "Deactivated"
    messages.success(request, f"{action} {target_user.username}")

    if request.htmx:
        return render(
            request,
            "admin_panel/partials/user_row.html",
            {"user": target_user},
        )
    return redirect("admin_panel:user_detail", user_id=user_id)


@admin_required
def ai_settings(request):
    """
    GET/POST view for configuring the AI analysis backend.

    GET: populates form with current AISettings singleton values.
    POST: validates and saves changes. HTMX: returns partial on success/error.
    Non-HTMX: redirects to this view on success, re-renders on error.
    """
    current = AISettings.get()

    if request.method == "POST":
        form = AISettingsForm(request.POST)
        if form.is_valid():
            current.ai_backend = form.cleaned_data["ai_backend"]
            current.openrouter_api_key = form.cleaned_data["openrouter_api_key"] or ""
            current.openrouter_model = form.cleaned_data["openrouter_model"] or ""
            current.save()
            messages.success(request, "AI settings saved.")

            if request.htmx:
                return render(request, "admin_panel/partials/ai_settings_form.html", {"form": form})
            return redirect("admin_panel:ai_settings")

        if request.htmx:
            return HttpResponse(form.errors.as_text(), status=422)
        return render(request, "admin_panel/ai_settings.html", {"form": form})

    form = AISettingsForm(
        initial={
            "ai_backend": current.ai_backend,
            "openrouter_api_key": current.openrouter_api_key,
            "openrouter_model": current.openrouter_model,
        }
    )
    model_missing = (
        current.ai_backend == "tflite"
        and not os.path.isfile(
            os.environ.get("TFLITE_MODEL_PATH", "./efficientdet_lite0.tflite")
        )
    )
    return render(
        request,
        "admin_panel/ai_settings.html",
        {"form": form, "model_missing": model_missing},
    )


def _smtp_form_initial(current: SMTPSettings) -> dict:
    """Build initial data for SmtpSettingsForm from the singleton (excluding password)."""
    return {
        "smtp_host": current.smtp_host,
        "smtp_port": current.smtp_port,
        "smtp_user": current.smtp_user,
        # smtp_password intentionally omitted — write-only field
        "smtp_from": current.smtp_from,
        "tls_mode": current.tls_mode,
    }


@admin_required
def smtp_settings(request):
    """
    GET/POST view for configuring the outgoing mail server (SMTP).

    GET: populates form with current SMTPSettings singleton values.
    POST: validates and saves changes. Password is Fernet-encrypted before storage;
    leaving the password field blank preserves the existing encrypted password.
    HTMX: returns partial on success/error.
    Non-HTMX: redirects to this view on success, re-renders on error.
    """
    current = SMTPSettings.get()

    if request.method == "POST":
        form = SmtpSettingsForm(request.POST)
        if form.is_valid():
            current.smtp_host = form.cleaned_data["smtp_host"] or ""
            current.smtp_port = form.cleaned_data["smtp_port"]
            current.smtp_user = form.cleaned_data["smtp_user"] or ""
            current.smtp_from = form.cleaned_data["smtp_from"] or ""
            current.tls_mode = form.cleaned_data["tls_mode"]

            new_password = form.cleaned_data.get("smtp_password", "")
            if new_password:
                current.smtp_password_encrypted = (
                    _get_fernet().encrypt(new_password.encode()).decode()
                )
            # If smtp_password is blank, leave smtp_password_encrypted unchanged.

            current.save()
            messages.success(request, "SMTP settings saved.")

            if request.htmx:
                return render(
                    request,
                    "admin_panel/partials/smtp_settings_form.html",
                    {"form": SmtpSettingsForm(initial=_smtp_form_initial(current))},
                )
            return redirect("admin_panel:smtp_settings")

        if request.htmx:
            return HttpResponse(form.errors.as_text(), status=422)
        return render(request, "admin_panel/smtp_settings.html", {"form": form})

    form = SmtpSettingsForm(initial=_smtp_form_initial(current))
    return render(request, "admin_panel/smtp_settings.html", {"form": form})


@admin_required
@require_POST
def smtp_test(request):
    """
    Send a test email to the admin's own address using the current SMTP settings.

    Uses aiosmtplib via asyncio.run() — Django is sync (WSGI) so bare await is
    not available. A timeout of 10 seconds is enforced to prevent worker hang.

    tls_mode maps to aiosmtplib parameters:
      - 'ssl'      → use_tls=True  (SMTPS, implicit TLS, port 465)
      - 'starttls' → start_tls=True (STARTTLS upgrade after connect, port 587)
      - 'none'     → no TLS flags

    HTMX note: errors are returned with HTTP 200 so HTMX swaps them into the
    target container. HTTP 4xx responses are not swapped by default.
    """
    current = SMTPSettings.get()

    def _htmx_error(msg: str) -> HttpResponse:
        if request.htmx:
            return HttpResponse(f'<span class="text-red-400">{msg}</span>')
        messages.error(request, msg)
        return redirect("admin_panel:smtp_settings")

    if not current.smtp_host:
        return _htmx_error("SMTP host not configured.")

    # Decrypt password — InvalidToken means corrupted ciphertext (never log plaintext)
    plaintext_password = ""
    if current.smtp_password_encrypted:
        try:
            plaintext_password = (
                _get_fernet()
                .decrypt(current.smtp_password_encrypted.encode())
                .decode()
            )
        except InvalidToken:
            return _htmx_error(
                "Failed to decrypt stored password. "
                "Please re-enter the SMTP password and save settings."
            )

    recipient = request.user.notification_email or ""
    if not recipient:
        return _htmx_error("Admin account has no notification email configured.")

    msg = MIMEText("This is a test email from Haven Cloud.", "plain")
    msg["Subject"] = "Haven Cloud — Test Email"
    msg["From"] = current.smtp_from or current.smtp_user or "haven@localhost"
    msg["To"] = recipient

    tls_kwargs: dict = {}
    if current.tls_mode == SMTPSettings.TLS_SSL:
        tls_kwargs["use_tls"] = True
    elif current.tls_mode == SMTPSettings.TLS_STARTTLS:
        tls_kwargs["start_tls"] = True

    async def _send():
        await aiosmtplib.send(
            msg,
            hostname=current.smtp_host,
            port=current.smtp_port,
            username=current.smtp_user or None,
            password=plaintext_password or None,
            timeout=10,
            **tls_kwargs,
        )

    try:
        asyncio.run(_send())
    except Exception as exc:
        return _htmx_error(f"Send failed: {exc}")

    if request.htmx:
        return HttpResponse(
            f'<span class="text-green-400">Test email sent to {recipient}.</span>'
        )
    messages.success(request, f"Test email sent to {recipient}.")
    return redirect("admin_panel:smtp_settings")
