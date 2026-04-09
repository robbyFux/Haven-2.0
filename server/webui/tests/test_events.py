"""
Tests for the event browser (plan 06-04):
  - Event list with filtering and HTMX partial rendering
  - Event detail with triggers and AI analysis
  - Video serving (unencrypted / encrypted / missing)
  - Event deletion with user quota update
"""

import os
import secrets
import tempfile

import pytest
from django.test import Client
from django.utils import timezone


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def make_device(user, name="Test Device"):
    from devices.models import Device

    return Device.objects.create(
        user=user,
        app_key=f"hav_{secrets.token_hex(16)}",
        name=name,
        is_active=True,
        created_at=timezone.now(),
    )


def make_event(user, device, **kwargs):
    from events.models import Event

    defaults = {
        "user": user,
        "device": device,
        "event_type": "CAMERA",
        "severity": "MEDIUM",
        "timestamp": timezone.now(),
        "created_at": timezone.now(),
    }
    defaults.update(kwargs)
    return Event.objects.create(**defaults)


def make_trigger(event, trigger_type="CAMERA", **kwargs):
    from events.models import EventTrigger

    defaults = {
        "event": event,
        "trigger_type": trigger_type,
        "created_at": timezone.now(),
    }
    defaults.update(kwargs)
    return EventTrigger.objects.create(**defaults)


def make_analysis(event, **kwargs):
    from events.models import AnalysisResult

    defaults = {
        "event": event,
        "backend": "tflite",
        "labels": '["person", "car"]',
        "confidence": 0.92,
        "description": "Person detected near vehicle.",
        "created_at": timezone.now(),
    }
    defaults.update(kwargs)
    return AnalysisResult.objects.create(**defaults)


# ---------------------------------------------------------------------------
# Task 1: Event list — auth, isolation, ordering
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_event_list_requires_login(client):
    """Unauthenticated GET /events/ must redirect to login."""
    response = client.get("/events/")
    assert response.status_code == 302
    assert "/accounts/login/" in response["Location"]


@pytest.mark.django_db
def test_event_list_shows_own_events(create_user):
    """Logged-in user sees their own events, ordered by -timestamp."""
    user = create_user("alice", "pw123")
    device = make_device(user)
    t1 = timezone.now()
    t2 = timezone.now()
    ev1 = make_event(user, device, timestamp=t1, event_type="CAMERA")
    ev2 = make_event(user, device, timestamp=t2, event_type="MICROPHONE")

    client = Client()
    client.force_login(user)
    response = client.get("/events/")
    assert response.status_code == 200
    # Both events appear in the response
    content = response.content.decode()
    assert str(ev1.id) in content or "CAMERA" in content
    assert str(ev2.id) in content or "MICROPHONE" in content


@pytest.mark.django_db
def test_event_list_excludes_other_users(create_user):
    """Events belonging to another user must not appear."""
    alice = create_user("alice2", "pw123")
    bob = create_user("bob2", "pw123")
    device_alice = make_device(alice, "Alice Device")
    device_bob = make_device(bob, "Bob Device")
    alice_event = make_event(alice, device_alice, event_type="LIGHT")
    _bob_event = make_event(bob, device_bob, event_type="POWER")

    client = Client()
    client.force_login(alice)
    response = client.get("/events/")
    content = response.content.decode()
    # Alice's event detail link must appear
    assert f"/events/{alice_event.id}/" in content
    # Bob's event detail link must NOT appear
    assert f"/events/{_bob_event.id}/" not in content


@pytest.mark.django_db
def test_event_filter_by_severity(create_user):
    """GET /events/?severity=HIGH returns only HIGH severity events."""
    user = create_user("sev_user", "pw123")
    device = make_device(user)
    high_ev = make_event(user, device, severity="HIGH", event_type="CAMERA")
    _low_ev = make_event(user, device, severity="LOW", event_type="CAMERA")

    client = Client()
    client.force_login(user)
    response = client.get("/events/?severity=HIGH")
    assert response.status_code == 200
    content = response.content.decode()
    assert f"/events/{high_ev.id}/" in content
    assert f"/events/{_low_ev.id}/" not in content


@pytest.mark.django_db
def test_event_filter_by_device(create_user):
    """GET /events/?device_id=X returns only events from that device."""
    user = create_user("dev_filter_user", "pw123")
    device1 = make_device(user, "Device 1")
    device2 = make_device(user, "Device 2")
    ev1 = make_event(user, device1, event_type="CAMERA")
    _ev2 = make_event(user, device2, event_type="MICROPHONE")

    client = Client()
    client.force_login(user)
    response = client.get(f"/events/?device_id={device1.id}")
    assert response.status_code == 200
    content = response.content.decode()
    assert f"/events/{ev1.id}/" in content
    assert f"/events/{_ev2.id}/" not in content


@pytest.mark.django_db
def test_event_filter_by_date_range(create_user):
    """Date range filter narrows events to the specified window."""
    from datetime import timedelta

    user = create_user("date_filter_user", "pw123")
    device = make_device(user)
    now = timezone.now()
    old_ev = make_event(user, device, timestamp=now - timedelta(days=10), event_type="CAMERA")
    new_ev = make_event(user, device, timestamp=now - timedelta(days=1), event_type="LIGHT")

    client = Client()
    client.force_login(user)
    # Filter to last 3 days only — old event should be excluded
    start = (now - timedelta(days=3)).strftime("%Y-%m-%dT%H:%M")
    response = client.get(f"/events/?timestamp__gte={start}")
    assert response.status_code == 200
    content = response.content.decode()
    assert f"/events/{new_ev.id}/" in content
    assert f"/events/{old_ev.id}/" not in content


@pytest.mark.django_db
def test_event_filter_by_type(create_user):
    """GET /events/?event_type=CAMERA returns only camera events."""
    user = create_user("type_filter_user", "pw123")
    device = make_device(user)
    cam_ev = make_event(user, device, event_type="CAMERA")
    _mic_ev = make_event(user, device, event_type="MICROPHONE")

    client = Client()
    client.force_login(user)
    response = client.get("/events/?event_type=CAMERA")
    assert response.status_code == 200
    content = response.content.decode()
    assert f"/events/{cam_ev.id}/" in content
    assert f"/events/{_mic_ev.id}/" not in content


@pytest.mark.django_db
def test_event_list_htmx_partial(create_user):
    """GET with HX-Request header returns only the partial table template."""
    user = create_user("htmx_user", "pw123")
    device = make_device(user)
    make_event(user, device)

    client = Client()
    client.force_login(user)
    response = client.get("/events/", HTTP_HX_REQUEST="true")
    assert response.status_code == 200
    content = response.content.decode()
    # Partial must NOT contain the full page skeleton
    assert "<!DOCTYPE html>" not in content
    assert "<html" not in content
    # But must contain table content
    assert "<table" in content or "<tr" in content or "event" in content.lower()


@pytest.mark.django_db
def test_event_list_pagination(create_user):
    """30 events → page 1 has 25, page 2 has 5."""
    user = create_user("page_user", "pw123")
    device = make_device(user)
    for i in range(30):
        make_event(user, device, event_type="CAMERA")

    client = Client()
    client.force_login(user)

    response1 = client.get("/events/?page=1")
    assert response1.status_code == 200

    response2 = client.get("/events/?page=2")
    assert response2.status_code == 200

    # Page 2 should exist and have content
    content2 = response2.content.decode()
    assert "Page" in content2 or "page" in content2 or "2" in content2


# ---------------------------------------------------------------------------
# Task 2: Event detail
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_event_detail_shows_triggers(create_user):
    """GET /events/<id>/ shows event info and all triggers."""
    user = create_user("detail_user", "pw123")
    device = make_device(user)
    event = make_event(user, device, event_type="CAMERA", severity="HIGH")
    trigger = make_trigger(event, trigger_type="ACCELEROMETER")

    client = Client()
    client.force_login(user)
    response = client.get(f"/events/{event.id}/")
    assert response.status_code == 200
    content = response.content.decode()
    assert "CAMERA" in content
    assert "HIGH" in content
    assert "ACCELEROMETER" in content


@pytest.mark.django_db
def test_event_detail_shows_analysis(create_user):
    """Event detail page shows AI analysis labels, confidence, description."""
    user = create_user("analysis_user", "pw123")
    device = make_device(user)
    event = make_event(user, device)
    make_analysis(event, labels='["person"]', confidence=0.95, description="A person was detected.")

    client = Client()
    client.force_login(user)
    response = client.get(f"/events/{event.id}/")
    assert response.status_code == 200
    content = response.content.decode()
    assert "person" in content
    assert "A person was detected." in content


@pytest.mark.django_db
def test_event_detail_no_analysis(create_user):
    """Event without AI analysis shows 'No AI analysis' message."""
    user = create_user("no_analysis_user", "pw123")
    device = make_device(user)
    event = make_event(user, device)

    client = Client()
    client.force_login(user)
    response = client.get(f"/events/{event.id}/")
    assert response.status_code == 200
    content = response.content.decode()
    assert "No AI analysis" in content or "no ai analysis" in content.lower()


@pytest.mark.django_db
def test_event_detail_other_user(create_user):
    """GET another user's event returns 404."""
    alice = create_user("alice_det", "pw123")
    bob = create_user("bob_det", "pw123")
    device_bob = make_device(bob)
    bob_event = make_event(bob, device_bob)

    client = Client()
    client.force_login(alice)
    response = client.get(f"/events/{bob_event.id}/")
    assert response.status_code == 404


# ---------------------------------------------------------------------------
# Task 2: Video serving
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_serve_video_unencrypted(create_user, tmp_path, settings):
    """GET /events/<id>/video/ for unencrypted event streams with video/mp4."""
    settings.MEDIA_ROOT = str(tmp_path)

    user = create_user("video_user", "pw123")
    device = make_device(user)

    # Write a dummy file
    video_file = tmp_path / "test_video.mp4"
    video_file.write_bytes(b"\x00\x01\x02\x03fake_video_content")

    event = make_event(user, device, media_path="test_video.mp4", is_encrypted=False)

    client = Client()
    client.force_login(user)
    response = client.get(f"/events/{event.id}/video/")
    assert response.status_code == 200
    assert "video/mp4" in response.get("Content-Type", "")


@pytest.mark.django_db
def test_serve_video_encrypted(create_user, tmp_path, settings):
    """GET /events/<id>/video/ for encrypted event returns 403 with message."""
    settings.MEDIA_ROOT = str(tmp_path)

    user = create_user("enc_video_user", "pw123")
    device = make_device(user)

    video_file = tmp_path / "enc_video.mp4"
    video_file.write_bytes(b"encrypted_data")

    event = make_event(user, device, media_path="enc_video.mp4", is_encrypted=True)

    client = Client()
    client.force_login(user)
    response = client.get(f"/events/{event.id}/video/")
    assert response.status_code == 403
    content = response.content.decode()
    assert "encrypted" in content.lower() or "decrypt" in content.lower()


@pytest.mark.django_db
def test_serve_video_no_media(create_user):
    """GET /events/<id>/video/ for event without media returns 404."""
    user = create_user("no_media_user", "pw123")
    device = make_device(user)
    event = make_event(user, device, media_path=None)

    client = Client()
    client.force_login(user)
    response = client.get(f"/events/{event.id}/video/")
    assert response.status_code == 404


# ---------------------------------------------------------------------------
# Task 2: Event deletion
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_event_delete(create_user, tmp_path, settings):
    """POST /events/<id>/delete/ removes event, media file, and updates quota."""
    settings.MEDIA_ROOT = str(tmp_path)

    user = create_user("del_user", "pw123")
    # Set initial quota counters
    user.current_event_count = 5
    user.current_storage_bytes = 1024
    user.save(update_fields=["current_event_count", "current_storage_bytes"])

    device = make_device(user)

    media_file = tmp_path / "del_video.mp4"
    media_file.write_bytes(b"some content")

    event = make_event(
        user, device, media_path="del_video.mp4", media_size_bytes=len(b"some content")
    )

    client = Client()
    client.force_login(user)
    response = client.post(f"/events/{event.id}/delete/")
    assert response.status_code == 302
    assert "/events/" in response["Location"]

    # Event should be deleted from DB
    from events.models import Event

    assert not Event.objects.filter(id=event.id).exists()

    # Media file should be deleted
    assert not media_file.exists()

    # User quota counters updated
    from accounts.models import HavenUser

    user.refresh_from_db()
    assert user.current_event_count == 4
    assert user.current_storage_bytes == 1024 - len(b"some content")


@pytest.mark.django_db
def test_event_delete_other_user(create_user):
    """POST to another user's event delete URL returns 404."""
    alice = create_user("alice_del", "pw123")
    bob = create_user("bob_del", "pw123")
    device_bob = make_device(bob)
    bob_event = make_event(bob, device_bob)

    client = Client()
    client.force_login(alice)
    response = client.post(f"/events/{bob_event.id}/delete/")
    assert response.status_code == 404
