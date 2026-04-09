"""
pytest-django configuration for Haven Web UI tests.

Uses an in-memory SQLite database (configured in settings.py when pytest
is detected in sys.modules). Creates unmanaged model tables in a session-scoped
fixture so they are available for all tests without re-creating per-test.
"""

import secrets

import pytest
from django.test import Client


@pytest.fixture(scope="session")
def django_db_setup(django_test_environment, django_db_blocker):
    """
    Session-scoped fixture that creates unmanaged model tables in the test DB.

    Unmanaged models (managed=False) are skipped by Django's test runner when
    creating the test schema. This fixture manually creates them using the
    schema editor so tests can read/write them.

    Errors on CREATE TABLE are silently ignored — the table may already exist
    if a previous test run left it behind (e.g. file-backed SQLite).
    """
    with django_db_blocker.unblock():
        from django.db import connection

        with connection.schema_editor() as editor:
            from accounts.models import HavenUser
            from devices.models import Device
            from events.models import AnalysisResult, Event, EventTrigger

            for model in [HavenUser, Device, Event, EventTrigger, AnalysisResult]:
                try:
                    editor.create_model(model)
                except Exception:
                    pass  # table already exists — safe to continue


@pytest.fixture
def create_user(db):
    """
    Factory fixture: create a HavenUser with a bcrypt-hashed password.

    Usage::

        def test_something(create_user):
            user = create_user("alice", "secret123")
    """

    def _create(username: str = "testuser", password: str = "testpass123", **kwargs) -> "HavenUser":  # noqa: F821
        from django.utils import timezone

        from accounts.models import HavenUser

        kwargs.setdefault("created_at", timezone.now())
        return HavenUser.objects.create_user(username=username, password=password, **kwargs)

    return _create


@pytest.fixture
def admin_user(create_user):
    """Create a user with is_admin=True."""
    return create_user(username="admin", password="adminpass123", is_admin=True)


@pytest.fixture
def authenticated_client(create_user):
    """
    Return a Django test Client that is already logged in as a test user.

    The login uses force_login() to bypass the auth backend (no bcrypt cost
    in tests).
    """
    user = create_user()
    client = Client()
    client.force_login(user)
    return client
