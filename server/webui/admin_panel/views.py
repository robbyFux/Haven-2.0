"""
Admin panel views for Haven Web UI (plan 06-05).

All views are protected by @admin_required (login + is_admin=True).
Supports HTMX partial rendering for user table and user row updates.
"""

from django.contrib import messages
from django.db.models import Sum
from django.http import HttpResponse
from django.shortcuts import get_object_or_404, redirect, render

from accounts.models import HavenUser
from admin_panel.forms import QuotaEditForm
from core.decorators import admin_required
from devices.models import Device
from events.models import Event


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
