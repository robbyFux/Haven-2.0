"""
Tests for notification settings: view, form, and HTMX partial rendering.

All tests use conftest fixtures (create_user, authenticated_client).
"""

import pytest


# ---------------------------------------------------------------------------
# Authentication guard
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_notification_settings_requires_login(client):
    """GET /notifications/ without auth redirects to login."""
    response = client.get("/notifications/")
    assert response.status_code == 302
    assert "/accounts/login/" in response["Location"]


# ---------------------------------------------------------------------------
# GET: shows current values
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_notification_settings_shows_current(create_user):
    """GET /notifications/ shows current user notification fields in the form."""
    from django.test import Client

    user = create_user(
        "alice",
        "pass123",
        notification_email="alice@example.com",
        notification_signal_number="+49123456789",
        pushover_user_key="abc123",
        notifications_enabled=True,
    )
    client = Client()
    client.force_login(user)

    response = client.get("/notifications/")
    assert response.status_code == 200
    content = response.content.decode()
    assert "alice@example.com" in content
    assert "+49123456789" in content
    assert "abc123" in content


# ---------------------------------------------------------------------------
# POST: update individual fields
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_notification_settings_update_email(create_user):
    """POST with notification_email saves the value to the user."""
    from django.test import Client

    user = create_user("bob", "pass123")
    client = Client()
    client.force_login(user)

    response = client.post(
        "/notifications/",
        {
            "notification_email": "bob@example.com",
            "notification_signal_number": "",
            "pushover_user_key": "",
            "notifications_enabled": "on",
        },
    )
    # Should redirect after successful save (non-HTMX)
    assert response.status_code == 302

    user.refresh_from_db()
    assert user.notification_email == "bob@example.com"


@pytest.mark.django_db
def test_notification_settings_update_signal(create_user):
    """POST with notification_signal_number saves to the user."""
    from django.test import Client

    user = create_user("charlie", "pass123")
    client = Client()
    client.force_login(user)

    response = client.post(
        "/notifications/",
        {
            "notification_email": "",
            "notification_signal_number": "+49123456789",
            "pushover_user_key": "",
            "notifications_enabled": "on",
        },
    )
    assert response.status_code == 302

    user.refresh_from_db()
    assert user.notification_signal_number == "+49123456789"


@pytest.mark.django_db
def test_notification_settings_update_pushover(create_user):
    """POST with pushover_user_key saves to the user."""
    from django.test import Client

    user = create_user("dave", "pass123")
    client = Client()
    client.force_login(user)

    response = client.post(
        "/notifications/",
        {
            "notification_email": "",
            "notification_signal_number": "",
            "pushover_user_key": "mykey123",
            "notifications_enabled": "on",
        },
    )
    assert response.status_code == 302

    user.refresh_from_db()
    assert user.pushover_user_key == "mykey123"


@pytest.mark.django_db
def test_notification_settings_toggle_enabled(create_user):
    """POST without notifications_enabled checkbox saves as False."""
    from django.test import Client

    user = create_user("eve", "pass123", notifications_enabled=True)
    client = Client()
    client.force_login(user)

    # Omit notifications_enabled to simulate unchecked checkbox
    response = client.post(
        "/notifications/",
        {
            "notification_email": "",
            "notification_signal_number": "",
            "pushover_user_key": "",
            # notifications_enabled intentionally omitted
        },
    )
    assert response.status_code == 302

    user.refresh_from_db()
    assert user.notifications_enabled is False


# ---------------------------------------------------------------------------
# HTMX: partial rendering
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_notification_settings_htmx(create_user):
    """POST with HX-Request header returns partial (no <html> tag)."""
    from django.test import Client

    user = create_user("frank", "pass123")
    client = Client()
    client.force_login(user)

    response = client.post(
        "/notifications/",
        {
            "notification_email": "frank@example.com",
            "notification_signal_number": "",
            "pushover_user_key": "",
            "notifications_enabled": "on",
        },
        HTTP_HX_REQUEST="true",
    )
    assert response.status_code == 200
    content = response.content.decode()
    # Partial response — no full HTML document
    assert "<html" not in content
    # Should contain the form
    assert "frank@example.com" in content or "Save Settings" in content


# ---------------------------------------------------------------------------
# POST: clear field saves as None
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_notification_settings_clear_field(create_user):
    """POST with empty email clears the field to None (not empty string)."""
    from django.test import Client

    user = create_user("grace", "pass123", notification_email="grace@example.com")
    client = Client()
    client.force_login(user)

    response = client.post(
        "/notifications/",
        {
            "notification_email": "",
            "notification_signal_number": "",
            "pushover_user_key": "",
            "notifications_enabled": "on",
        },
    )
    assert response.status_code == 302

    user.refresh_from_db()
    assert user.notification_email is None


# ---------------------------------------------------------------------------
# Validation: invalid email format
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_notification_settings_invalid_email(create_user):
    """POST with invalid email format returns form with errors."""
    from django.test import Client

    user = create_user("heidi", "pass123")
    client = Client()
    client.force_login(user)

    response = client.post(
        "/notifications/",
        {
            "notification_email": "not-an-email",
            "notification_signal_number": "",
            "pushover_user_key": "",
            "notifications_enabled": "on",
        },
    )
    # Form is invalid — re-render (200) or HTMX partial with errors
    assert response.status_code == 200
    content = response.content.decode()
    assert "not-an-email" in content or "valid" in content.lower() or "email" in content.lower()


# ---------------------------------------------------------------------------
# Validation: invalid Signal number format
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_notification_settings_invalid_signal_number(create_user):
    """POST with Signal number not starting with '+' returns form with errors."""
    from django.test import Client

    user = create_user("ivan", "pass123")
    client = Client()
    client.force_login(user)

    response = client.post(
        "/notifications/",
        {
            "notification_email": "",
            "notification_signal_number": "0049123456789",  # missing leading +
            "pushover_user_key": "",
            "notifications_enabled": "on",
        },
    )
    assert response.status_code == 200
    content = response.content.decode()
    assert "0049123456789" in content or "+" in content or "E.164" in content or "signal" in content.lower()
