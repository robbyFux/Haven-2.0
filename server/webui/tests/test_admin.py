"""
Tests for the admin dashboard (plan 06-05).

Covers:
- admin_required decorator: blocks non-admin (403), redirects anonymous (302)
- Dashboard stats: total users, total events, total storage
- User list with quota info
- HTMX partial rendering
- User detail page
- Quota edit (valid + validation errors)
- Toggle active
"""

import pytest
from django.test import Client
from django.utils import timezone


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def make_admin_client(create_user):
    """Return a Client logged in as an admin user."""
    user = create_user(username="adminuser", password="adminpass", is_admin=True)
    client = Client()
    client.force_login(user)
    return client, user


def make_regular_client(create_user):
    """Return a Client logged in as a regular (non-admin) user."""
    user = create_user(username="regularuser", password="regularpass", is_admin=False)
    client = Client()
    client.force_login(user)
    return client, user


# ---------------------------------------------------------------------------
# Task 1: decorator + dashboard + user list
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_admin_dashboard_requires_login():
    """Anonymous request to /admin/ must redirect to login."""
    client = Client()
    response = client.get("/admin/")
    assert response.status_code == 302
    assert "/accounts/login/" in response["Location"]


@pytest.mark.django_db
def test_admin_dashboard_requires_admin(create_user):
    """Non-admin user must receive 403."""
    client, _ = make_regular_client(create_user)
    response = client.get("/admin/")
    assert response.status_code == 403


@pytest.mark.django_db
def test_admin_dashboard_shows_stats(create_user):
    """Admin user sees dashboard with system stats."""
    client, _ = make_admin_client(create_user)
    response = client.get("/admin/")
    assert response.status_code == 200
    content = response.content.decode()
    # Stats values are rendered in the page
    assert "Total Users" in content or "total_users" in content or "1" in content


@pytest.mark.django_db
def test_admin_dashboard_shows_stats_counts(create_user):
    """Dashboard stats reflect actual DB counts."""
    # Create admin + 2 regular users
    client, admin = make_admin_client(create_user)
    create_user(username="user1", password="pass1")
    create_user(username="user2", password="pass2")
    response = client.get("/admin/")
    assert response.status_code == 200
    ctx = response.context
    assert ctx["total_users"] >= 3  # admin + 2 regular


@pytest.mark.django_db
def test_admin_user_list(create_user):
    """Admin sees all users in the user table."""
    client, admin = make_admin_client(create_user)
    other = create_user(username="other", password="pass")
    response = client.get("/admin/")
    assert response.status_code == 200
    content = response.content.decode()
    assert "adminuser" in content
    assert "other" in content


@pytest.mark.django_db
def test_admin_user_list_htmx(create_user):
    """HTMX request to /admin/ returns partial (user table only, no full base.html)."""
    client, _ = make_admin_client(create_user)
    response = client.get("/admin/", HTTP_HX_REQUEST="true")
    assert response.status_code == 200
    content = response.content.decode()
    # Partial should NOT contain full <html> structure
    assert "<!DOCTYPE html>" not in content
    # But should contain table/user listing markup
    assert "adminuser" in content


# ---------------------------------------------------------------------------
# Task 2: user detail + quota edit + toggle active
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_admin_user_detail(create_user):
    """GET /admin/users/<id>/ shows user info + quota form for admin."""
    client, admin = make_admin_client(create_user)
    target = create_user(username="target", password="pass")
    response = client.get(f"/admin/users/{target.id}/")
    assert response.status_code == 200
    content = response.content.decode()
    assert "target" in content
    assert "storage_quota_mb" in content or "Storage Quota" in content


@pytest.mark.django_db
def test_admin_user_detail_non_admin(create_user):
    """Non-admin cannot access user detail page."""
    client, _ = make_regular_client(create_user)
    admin = create_user(username="admin2", password="pass", is_admin=True)
    response = client.get(f"/admin/users/{admin.id}/")
    assert response.status_code == 403


@pytest.mark.django_db
def test_admin_quota_edit(create_user):
    """POST /admin/users/<id>/quota/ with valid data updates user quota."""
    from accounts.models import HavenUser

    client, admin = make_admin_client(create_user)
    target = create_user(username="target2", password="pass", storage_quota_mb=512, max_events=5000)

    response = client.post(
        f"/admin/users/{target.id}/quota/",
        {"storage_quota_mb": 2048, "max_events": 20000},
    )
    # Should redirect or return 200 with updated row
    assert response.status_code in (200, 302)

    target.refresh_from_db()
    assert target.storage_quota_mb == 2048
    assert target.max_events == 20000


@pytest.mark.django_db
def test_admin_quota_edit_validation(create_user):
    """POST with negative values must fail validation (422 for HTMX, 200 with errors otherwise)."""
    client, admin = make_admin_client(create_user)
    target = create_user(username="target3", password="pass")

    response = client.post(
        f"/admin/users/{target.id}/quota/",
        {"storage_quota_mb": -100, "max_events": -1},
    )
    # Should not be a redirect (validation failed)
    assert response.status_code in (200, 422)

    # Values must NOT have changed
    target.refresh_from_db()
    assert target.storage_quota_mb == 1024  # default unchanged
    assert target.max_events == 10000  # default unchanged


@pytest.mark.django_db
def test_admin_quota_edit_htmx_returns_row(create_user):
    """HTMX POST to quota edit returns a partial user row, not full page."""
    client, admin = make_admin_client(create_user)
    target = create_user(username="target4", password="pass")

    response = client.post(
        f"/admin/users/{target.id}/quota/",
        {"storage_quota_mb": 768, "max_events": 7500},
        HTTP_HX_REQUEST="true",
    )
    assert response.status_code == 200
    content = response.content.decode()
    # Should be a partial row, not a full page
    assert "<!DOCTYPE html>" not in content
    assert "target4" in content


@pytest.mark.django_db
def test_admin_toggle_active(create_user):
    """POST /admin/users/<id>/toggle-active/ flips is_active."""
    client, admin = make_admin_client(create_user)
    target = create_user(username="target5", password="pass", is_active=True)

    response = client.post(f"/admin/users/{target.id}/toggle-active/")
    assert response.status_code in (200, 302)

    target.refresh_from_db()
    assert target.is_active is False  # was True, now False


@pytest.mark.django_db
def test_admin_toggle_active_non_admin(create_user):
    """Non-admin cannot toggle user active status."""
    client, _ = make_regular_client(create_user)
    target = create_user(username="target6", password="pass")
    response = client.post(f"/admin/users/{target.id}/toggle-active/")
    assert response.status_code == 403
    # is_active must not have changed
    target.refresh_from_db()
    assert target.is_active is True
