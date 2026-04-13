"""
Tests for the admin AI settings page (admin_panel/ai_settings view).

Covers:
- Requires admin: blocks non-admin (403), redirects anonymous (302)
- GET: returns 200 with form populated from AISettings singleton
- POST valid none: saves backend=none
- POST valid openrouter with key: saves all fields
- POST openrouter without key: validation error
- HTMX POST valid: returns partial (no DOCTYPE)
- HTMX POST invalid: returns 422
"""

import pytest
from django.test import Client


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def make_admin_client(create_user):
    user = create_user(username="ai_admin", password="adminpass", is_admin=True)
    client = Client()
    client.force_login(user)
    return client


def make_regular_client(create_user):
    user = create_user(username="ai_regular", password="regularpass", is_admin=False)
    client = Client()
    client.force_login(user)
    return client


# ---------------------------------------------------------------------------
# Access control
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_ai_settings_requires_login():
    """Anonymous request redirects to login."""
    client = Client()
    response = client.get("/admin/ai-settings/")
    assert response.status_code == 302
    assert "/accounts/login/" in response["Location"]


@pytest.mark.django_db
def test_ai_settings_requires_admin(create_user):
    """Non-admin receives 403."""
    client = make_regular_client(create_user)
    response = client.get("/admin/ai-settings/")
    assert response.status_code == 403


# ---------------------------------------------------------------------------
# GET
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_ai_settings_get(create_user):
    """GET /admin/ai-settings/ returns 200 with the form."""
    client = make_admin_client(create_user)
    response = client.get("/admin/ai-settings/")
    assert response.status_code == 200
    content = response.content.decode()
    assert "AI" in content
    assert "ai_backend" in content or "AI Backend" in content


# ---------------------------------------------------------------------------
# POST — valid
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_ai_settings_post_none(create_user):
    """POST backend=none saves and redirects."""
    from admin_panel.models import AISettings

    client = make_admin_client(create_user)
    response = client.post(
        "/admin/ai-settings/",
        {"ai_backend": "none", "openrouter_api_key": "", "openrouter_model": ""},
    )
    assert response.status_code in (200, 302)
    assert AISettings.get().ai_backend == "none"


@pytest.mark.django_db
def test_ai_settings_post_openrouter(create_user):
    """POST backend=openrouter with key saves all fields."""
    from admin_panel.models import AISettings

    client = make_admin_client(create_user)
    response = client.post(
        "/admin/ai-settings/",
        {
            "ai_backend": "openrouter",
            "openrouter_api_key": "sk-or-testkey",
            "openrouter_model": "google/gemini-flash-1.5",
        },
    )
    assert response.status_code in (200, 302)
    saved = AISettings.get()
    assert saved.ai_backend == "openrouter"
    assert saved.openrouter_api_key == "sk-or-testkey"
    assert saved.openrouter_model == "google/gemini-flash-1.5"


@pytest.mark.django_db
def test_ai_settings_post_tflite(create_user):
    """POST backend=tflite does not require openrouter_api_key."""
    from admin_panel.models import AISettings

    client = make_admin_client(create_user)
    response = client.post(
        "/admin/ai-settings/",
        {"ai_backend": "tflite", "openrouter_api_key": "", "openrouter_model": ""},
    )
    assert response.status_code in (200, 302)
    assert AISettings.get().ai_backend == "tflite"


# ---------------------------------------------------------------------------
# POST — validation error
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_ai_settings_post_openrouter_no_key(create_user):
    """POST openrouter without api_key fails validation; backend not changed."""
    from admin_panel.models import AISettings

    # Ensure singleton exists with known state
    s = AISettings.get()
    s.ai_backend = "none"
    s.save()

    client = make_admin_client(create_user)
    response = client.post(
        "/admin/ai-settings/",
        {"ai_backend": "openrouter", "openrouter_api_key": "", "openrouter_model": ""},
    )
    # Should stay on the page (not redirect)
    assert response.status_code == 200
    # Value must not have changed
    assert AISettings.get().ai_backend == "none"


# ---------------------------------------------------------------------------
# HTMX
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_ai_settings_htmx_post_valid(create_user):
    """HTMX POST with valid data returns partial (no DOCTYPE)."""
    client = make_admin_client(create_user)
    response = client.post(
        "/admin/ai-settings/",
        {"ai_backend": "none", "openrouter_api_key": "", "openrouter_model": ""},
        HTTP_HX_REQUEST="true",
    )
    assert response.status_code == 200
    content = response.content.decode()
    assert "<!DOCTYPE html>" not in content


@pytest.mark.django_db
def test_ai_settings_htmx_post_invalid(create_user):
    """HTMX POST with invalid data returns 422."""
    client = make_admin_client(create_user)
    response = client.post(
        "/admin/ai-settings/",
        {"ai_backend": "openrouter", "openrouter_api_key": "", "openrouter_model": ""},
        HTTP_HX_REQUEST="true",
    )
    assert response.status_code == 422
