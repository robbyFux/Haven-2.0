"""
Notification settings views for Haven Web UI.

Provides the notification_settings view for users to configure their email,
Signal, and Pushover notification channels and enable/disable notifications.
Supports HTMX partial responses for inline save without full page reload.
"""

from django.contrib import messages
from django.contrib.auth.decorators import login_required
from django.shortcuts import redirect, render

from .forms import NotificationSettingsForm


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
