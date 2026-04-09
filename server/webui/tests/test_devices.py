"""
Tests for Device management views: list, create, revoke.

Covers:
- Authentication requirement (login_required)
- User isolation (can only see/revoke own devices)
- Device creation with hav_ app_key generation
- HTMX partial rendering
- Revoke sets is_active=False, returns row partial
"""

import pytest
from django.test import Client
from django.urls import reverse
from django.utils import timezone

from devices.models import Device


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def make_device(user, name="Test Device", is_active=True):
    """Create a Device for the given user directly in the DB."""
    return Device.objects.create(
        user=user,
        app_key=f"hav_{'a' * 32}_{user.id}",
        name=name,
        is_active=is_active,
        created_at=timezone.now(),
    )


# ---------------------------------------------------------------------------
# test_device_list_requires_login
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_device_list_requires_login(client):
    """GET /devices/ without auth redirects to login page."""
    url = reverse("devices:device_list")
    response = client.get(url)
    assert response.status_code == 302
    assert "/accounts/login/" in response["Location"]


# ---------------------------------------------------------------------------
# test_device_list_shows_own_devices
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_device_list_shows_own_devices(create_user):
    """Logged-in user sees their own devices, NOT devices belonging to another user."""
    user1 = create_user("alice", "pass123")
    user2 = create_user("bob", "pass456")

    dev1 = Device.objects.create(
        user=user1,
        app_key="hav_" + "1" * 32,
        name="Alice's Camera",
        is_active=True,
        created_at=timezone.now(),
    )
    Device.objects.create(
        user=user2,
        app_key="hav_" + "2" * 32,
        name="Bob's Camera",
        is_active=True,
        created_at=timezone.now(),
    )

    client = Client()
    client.force_login(user1)
    url = reverse("devices:device_list")
    response = client.get(url)

    assert response.status_code == 200
    content = response.content.decode()
    assert "Alice&#x27;s Camera" in content or "Alice's Camera" in content
    assert "Bob" not in content


# ---------------------------------------------------------------------------
# test_device_create
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_device_create(create_user):
    """POST /devices/create/ creates a Device with a hav_ app_key and redirects to list."""
    user = create_user("creator", "pass123")
    client = Client()
    client.force_login(user)

    url = reverse("devices:device_create")
    response = client.post(url, {"name": "Bedroom Camera"})

    assert response.status_code == 302
    assert response["Location"] == reverse("devices:device_list")

    device = Device.objects.filter(user=user, name="Bedroom Camera").first()
    assert device is not None
    assert device.app_key.startswith("hav_")
    assert len(device.app_key) == 68  # "hav_" + 64 hex chars
    assert device.is_active is True


# ---------------------------------------------------------------------------
# test_device_create_empty_name
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_device_create_empty_name(create_user):
    """POST with empty name returns form with validation error, no device created."""
    user = create_user("creator2", "pass123")
    client = Client()
    client.force_login(user)

    url = reverse("devices:device_create")
    before_count = Device.objects.filter(user=user).count()
    response = client.post(url, {"name": ""})

    # Should re-render the form with errors, not redirect
    assert response.status_code == 200
    assert Device.objects.filter(user=user).count() == before_count


# ---------------------------------------------------------------------------
# test_device_revoke
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_device_revoke(create_user):
    """POST /devices/<id>/revoke/ sets is_active=False and returns updated row partial via HTMX."""
    user = create_user("revoker", "pass123")
    device = Device.objects.create(
        user=user,
        app_key="hav_" + "r" * 32,
        name="To Revoke",
        is_active=True,
        created_at=timezone.now(),
    )

    client = Client()
    client.force_login(user)

    url = reverse("devices:device_revoke", kwargs={"device_id": device.id})
    response = client.post(url, HTTP_HX_REQUEST="true")

    assert response.status_code == 200

    device.refresh_from_db()
    assert device.is_active is False

    content = response.content.decode()
    # Should contain the row partial with the revoked device
    assert str(device.id) in content or device.name in content


# ---------------------------------------------------------------------------
# test_device_revoke_other_user
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_device_revoke_other_user(create_user):
    """POST to another user's device revoke URL returns 404."""
    user1 = create_user("attacker", "pass1")
    user2 = create_user("victim", "pass2")

    victim_device = Device.objects.create(
        user=user2,
        app_key="hav_" + "v" * 32,
        name="Victim's Device",
        is_active=True,
        created_at=timezone.now(),
    )

    client = Client()
    client.force_login(user1)

    url = reverse("devices:device_revoke", kwargs={"device_id": victim_device.id})
    response = client.post(url)

    assert response.status_code == 404

    victim_device.refresh_from_db()
    assert victim_device.is_active is True  # Not changed


# ---------------------------------------------------------------------------
# test_device_list_htmx_partial
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_device_list_htmx_partial(create_user):
    """GET /devices/ with HX-Request header returns device_table.html partial only."""
    user = create_user("htmx_user", "pass123")
    Device.objects.create(
        user=user,
        app_key="hav_" + "h" * 32,
        name="HTMX Device",
        is_active=True,
        created_at=timezone.now(),
    )

    client = Client()
    client.force_login(user)

    url = reverse("devices:device_list")
    response = client.get(url, HTTP_HX_REQUEST="true")

    assert response.status_code == 200
    content = response.content.decode()
    # Partial should NOT contain full HTML document structure
    assert "<!DOCTYPE" not in content
    assert "<html" not in content
    # But should contain the device data
    assert "HTMX Device" in content
