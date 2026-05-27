"""
Device management views: list, create, revoke, and delete.

All views require authentication via @login_required.
Revoke, delete, and list support HTMX partial rendering.
"""

import secrets

from django.contrib import messages
from django.contrib.auth.decorators import login_required
from django.http import HttpResponse, HttpResponseForbidden
from django.shortcuts import get_object_or_404, redirect, render
from django.utils import timezone
from django.views.decorators.http import require_POST

from .forms import DeviceCreateForm
from .models import Device


@login_required
def device_list(request):
    """
    List all devices belonging to the current user.

    HTMX requests receive only the device_table.html partial (for inline refresh).
    Regular requests receive the full list.html with create form.
    """
    devices = Device.objects.filter(user_id=request.user.id).order_by("-created_at")
    if request.htmx:
        return render(request, "devices/partials/device_table.html", {"devices": devices})
    form = DeviceCreateForm()
    return render(request, "devices/list.html", {"devices": devices, "form": form})


@login_required
@require_POST
def device_create(request):
    """
    Create a new device for the current user.

    Generates a hav_<32 hex chars> app_key (68 chars total).
    Shows the generated key once via a flash message — user must copy it immediately.
    Redirects to device_list on success; re-renders list with errors on failure.
    """
    form = DeviceCreateForm(request.POST)
    if not form.is_valid():
        devices = Device.objects.filter(user_id=request.user.id).order_by("-created_at")
        return render(request, "devices/list.html", {"devices": devices, "form": form})

    app_key = f"hav_{secrets.token_hex(32)}"
    Device.objects.create(
        user_id=request.user.id,
        name=form.cleaned_data["name"],
        app_key=app_key,
        is_active=True,
        created_at=timezone.now(),
    )
    messages.success(
        request,
        f"Device created. Your App-Key (copy it now — it will NOT be shown again): {app_key}",
    )
    return redirect("devices:device_list")


@login_required
@require_POST
def device_revoke(request, device_id):
    """
    Revoke a device (set is_active=False).

    Only the owning user can revoke their device — 404 otherwise.
    HTMX requests receive the updated device_row.html partial for inline row replacement.
    Regular requests redirect to device_list with a success message.
    """
    device = get_object_or_404(Device, id=device_id, user_id=request.user.id)
    device.is_active = False
    device.save()

    if request.htmx:
        return render(request, "devices/partials/device_row.html", {"device": device})

    messages.success(request, f"Device '{device.name}' has been revoked.")
    return redirect("devices:device_list")


@login_required
@require_POST
def device_delete(request, device_id):
    """
    Permanently delete a revoked device row.

    Only the owning user can delete their device. Active devices cannot be deleted —
    returns 403 Forbidden to prevent accidental deletion of live devices.
    HTMX: returns empty 200 response — hx-swap="outerHTML" removes the <tr> from the DOM.
    Non-HTMX fallback: redirects to device_list with a success message.
    """
    device = get_object_or_404(Device, id=device_id, user_id=request.user.id)
    if device.is_active:
        return HttpResponseForbidden("Cannot delete an active device.")
    device.delete()

    if request.htmx:
        return HttpResponse("")  # empty body — HTMX outerHTML swap removes the <tr>

    messages.success(request, f"Device '{device.name}' permanently deleted.")
    return redirect("devices:device_list")
