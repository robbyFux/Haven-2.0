"""
Event browser views for Haven Web UI (plan 06-04 / 08-02).

Views:
  event_list       — paginated list with django-filter + HTMX partial support
  event_detail     — event info, triggers, AI analysis, video player
  serve_video      — stream unencrypted video; 403 for encrypted
  event_delete     — delete event + media file + update user quota counters
  bulk_archive     — archive selected events (is_archived=True)
  bulk_unarchive   — restore selected archived events (is_archived=False)
  bulk_delete      — permanently delete selected events and their media
"""

import os

from django.conf import settings
from django.contrib import messages
from django.contrib.auth.decorators import login_required
from django.core.paginator import Paginator
from django.http import Http404, HttpResponse, StreamingHttpResponse
from django.shortcuts import get_object_or_404, redirect, render
from django.views.decorators.http import require_POST

from devices.models import Device

from .filters import EventFilter
from .models import AnalysisResult, Event, EventTrigger

EVENT_TYPES = [
    "ACCELEROMETER", "CAMERA", "MICROPHONE", "PRESSURE", "LIGHT",
    "POWER", "BUMP", "CAMERA_VIDEO", "HEART", "CAMERA_PERSON",
    "CAMERA_PET", "CAMERA_VEHICLE", "SOUND_DECIBEL", "GYROSCOPE",
]


def _render_event_table(request, status=None):
    """
    Re-render the event table partial with the current filter context.

    Used by bulk action views to return an updated table after a POST.
    The status parameter overrides GET params so POST requests restore the
    correct filter after a bulk action (GET params are empty on POST).
    """
    if status is None:
        status = request.GET.get("status", "active")

    base_qs = (
        Event.objects.filter(user_id=request.user.id)
        .select_related("device")
        .order_by("-timestamp")
    )

    if status == "active":
        base_qs = base_qs.filter(is_archived=False)
    elif status == "archived":
        base_qs = base_qs.filter(is_archived=True)
    # status == "all": no additional filter

    f = EventFilter(request.GET, queryset=base_qs)

    paginator = Paginator(f.qs, 25)
    page = paginator.get_page(request.GET.get("page", 1))

    devices = Device.objects.filter(user_id=request.user.id, is_active=True)

    context = {
        "page": page,
        "filter": f,
        "devices": devices,
        "event_types": EVENT_TYPES,
        "current_status": status,
    }

    return render(request, "events/partials/event_table.html", context)


@login_required
def event_list(request):
    """
    Paginated event list for the logged-in user.

    Filters: date range, device, event type, severity (via EventFilter).
    Status filter (active/archived/all) applied before EventFilter.
    HTMX: if HX-Request header is present, render only the partial table template.
    """
    status = request.GET.get("status", "active")

    base_qs = (
        Event.objects.filter(user_id=request.user.id)
        .select_related("device")
        .order_by("-timestamp")
    )

    if status == "active":
        base_qs = base_qs.filter(is_archived=False)
    elif status == "archived":
        base_qs = base_qs.filter(is_archived=True)
    # status == "all": no additional filter

    f = EventFilter(request.GET, queryset=base_qs)

    paginator = Paginator(f.qs, 25)
    page = paginator.get_page(request.GET.get("page", 1))

    devices = Device.objects.filter(user_id=request.user.id, is_active=True)

    context = {
        "page": page,
        "filter": f,
        "devices": devices,
        "event_types": EVENT_TYPES,
        "current_status": status,
    }

    if request.htmx:
        return render(request, "events/partials/event_table.html", context)
    return render(request, "events/list.html", context)


@login_required
def event_detail(request, event_id):
    """
    Event detail page: event info, trigger list, AI analysis.
    """
    event = get_object_or_404(Event, id=event_id, user_id=request.user.id)
    triggers = EventTrigger.objects.filter(event_id=event.id).order_by("created_at")
    analysis = AnalysisResult.objects.filter(event_id=event.id).first()

    # Parse labels JSON for display
    labels = []
    if analysis and analysis.labels:
        import json

        try:
            labels = json.loads(analysis.labels)
        except (json.JSONDecodeError, ValueError):
            labels = [analysis.labels]

    return render(
        request,
        "events/detail.html",
        {
            "event": event,
            "triggers": triggers,
            "analysis": analysis,
            "labels": labels,
        },
    )


@login_required
def serve_video(request, event_id):
    """
    Stream an unencrypted event video file with HTTP range request support.

    Browsers send Range: bytes=0- for HTML5 <video> — without 206 Partial Content
    many browsers refuse playback and show a codec error. This view handles both
    full (200) and partial (206) responses so seeking works correctly.

    Returns 404 if no media_path or file missing.
    Returns 403 with explanation if the video is client-encrypted.
    """
    event = get_object_or_404(Event, id=event_id, user_id=request.user.id)

    if not event.media_path:
        raise Http404("No media attached to this event.")

    if event.is_encrypted:
        return HttpResponse(
            "This video was client-encrypted and cannot be played in the browser. "
            "Please download to decrypt locally.",
            status=403,
            content_type="text/plain",
        )

    full_path = os.path.join(settings.MEDIA_ROOT, event.media_path)
    if not os.path.exists(full_path):
        raise Http404("Media file not found on server.")

    file_size = os.path.getsize(full_path)
    range_header = request.META.get("HTTP_RANGE", "")

    if range_header.startswith("bytes="):
        # Parse "bytes=start-end"
        range_spec = range_header[6:]
        start_str, _, end_str = range_spec.partition("-")
        start = int(start_str) if start_str else 0
        end = int(end_str) if end_str else file_size - 1
        end = min(end, file_size - 1)
        length = end - start + 1

        def _range_iter(path, s, l, chunk=64 * 1024):
            with open(path, "rb") as f:
                f.seek(s)
                remaining = l
                while remaining > 0:
                    data = f.read(min(chunk, remaining))
                    if not data:
                        break
                    remaining -= len(data)
                    yield data

        response = StreamingHttpResponse(
            _range_iter(full_path, start, length),
            status=206,
            content_type="video/mp4",
        )
        response["Content-Range"] = f"bytes {start}-{end}/{file_size}"
        response["Content-Length"] = length
    else:
        response = StreamingHttpResponse(
            open(full_path, "rb"),  # noqa: WPS515
            content_type="video/mp4",
        )
        response["Content-Length"] = file_size

    response["Accept-Ranges"] = "bytes"
    response["Content-Disposition"] = f'inline; filename="event_{event_id}.mp4"'
    return response


@login_required
def event_delete(request, event_id):
    """
    Delete an event (POST only).

    Also removes the associated media file and decrements user quota counters.
    Cascades to EventTrigger and AnalysisResult via DB ON DELETE CASCADE.
    """
    if request.method != "POST":
        raise Http404("Method not allowed.")

    event = get_object_or_404(Event, id=event_id, user_id=request.user.id)

    # Delete media file if present
    if event.media_path:
        full_path = os.path.join(settings.MEDIA_ROOT, event.media_path)
        if os.path.exists(full_path):
            os.remove(full_path)

    # Update user quota counters
    user = request.user
    if event.media_size_bytes:
        user.current_storage_bytes = max(
            0, user.current_storage_bytes - (event.media_size_bytes or 0)
        )
    user.current_event_count = max(0, user.current_event_count - 1)
    user.save(update_fields=["current_storage_bytes", "current_event_count"])

    # Delete event — cascades triggers + analysis in DB
    event.delete()

    messages.success(request, "Event deleted.")
    return redirect("events:list")


@login_required
@require_POST
def bulk_archive(request):
    """
    Archive multiple events owned by the current user.

    Reads event_ids from POST body and sets is_archived=True.
    Always filters by user_id to prevent cross-user tampering (T-08-03).
    Returns updated event table partial (HTMX) or redirects to list.
    """
    event_ids = request.POST.getlist("event_ids")
    status = request.POST.get("status", "active")

    if event_ids:
        Event.objects.filter(user_id=request.user.id, id__in=event_ids).update(is_archived=True)

    if request.htmx:
        return _render_event_table(request, status=status)
    return redirect("events:list")


@login_required
@require_POST
def bulk_unarchive(request):
    """
    Restore multiple archived events owned by the current user.

    Reads event_ids from POST body and sets is_archived=False.
    Always filters by user_id to prevent cross-user tampering (T-08-03).
    Returns updated event table partial (HTMX) or redirects to list.
    """
    event_ids = request.POST.getlist("event_ids")
    status = request.POST.get("status", "archived")

    if event_ids:
        Event.objects.filter(user_id=request.user.id, id__in=event_ids).update(is_archived=False)

    if request.htmx:
        return _render_event_table(request, status=status)
    return redirect("events:list")


@login_required
@require_POST
def bulk_delete(request):
    """
    Permanently delete multiple events owned by the current user.

    Reads event_ids from POST body. Deletes associated media files before
    removing DB rows (mirrors event_delete media deletion logic).
    Always filters by user_id to prevent cross-user tampering (T-08-05).
    Returns updated event table partial (HTMX) or redirects to list.
    """
    event_ids = request.POST.getlist("event_ids")
    status = request.POST.get("status", "active")

    if event_ids:
        events_qs = Event.objects.filter(user_id=request.user.id, id__in=event_ids)

        # Delete associated media files before removing DB rows
        for event in events_qs:
            if event.media_path:
                full_path = os.path.join(settings.MEDIA_ROOT, event.media_path)
                if os.path.exists(full_path):
                    os.remove(full_path)

        events_qs.delete()

    if request.htmx:
        return _render_event_table(request, status=status)
    return redirect("events:list")
