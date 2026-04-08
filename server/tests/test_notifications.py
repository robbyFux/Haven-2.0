"""
Tests for notification settings API and send_notification_task Celery task.

All external senders (send_email, send_signal, send_pushover) are mocked
via unittest.mock.patch so no real SMTP/HTTP calls are made.
"""

import asyncio as real_asyncio
from datetime import datetime, timezone
from unittest.mock import patch

import pytest
import pytest_asyncio
import httpx
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.database import get_db
from app.main import create_app
from app.models.base import Base
from app.models.device import Device
from app.models.event import Event
from app.models.user import User
from app.services.notify import format_notification_message

# ---------------------------------------------------------------------------
# Test fixtures
# ---------------------------------------------------------------------------

TEST_DATABASE_URL = "sqlite+aiosqlite:///./test_notifications.db"


@pytest_asyncio.fixture(loop_scope="function", scope="function")
async def db_session():
    """Function-scoped async session backed by a fresh SQLite DB."""
    engine = create_async_engine(TEST_DATABASE_URL, connect_args={"check_same_thread": False})
    TestSessionLocal = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    async with TestSessionLocal() as session:
        yield session

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)

    await engine.dispose()


@pytest_asyncio.fixture(loop_scope="function", scope="function")
async def notif_client(db_session: AsyncSession):
    """Function-scoped async HTTP client with isolated test DB."""
    async def override_get_db():
        yield db_session

    app = create_app()
    app.dependency_overrides[get_db] = override_get_db

    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        yield client


async def _register_and_login(client: httpx.AsyncClient, username: str, password: str = "password") -> str:
    """Register a user and return a Bearer access token."""
    await client.post("/api/v1/auth/register", json={"username": username, "password": password})
    resp = await client.post(
        "/api/v1/auth/login",
        json={"username": username, "password": password},
    )
    assert resp.status_code == 200, f"Login failed: {resp.text}"
    return resp.json()["access_token"]


async def _create_user_direct(
    db: AsyncSession,
    username: str = "notifuser",
    notifications_enabled: bool = True,
    notification_email: str | None = None,
    notification_signal_number: str | None = None,
    pushover_user_key: str | None = None,
) -> User:
    """Create and persist a test user directly (bypasses password hashing for speed)."""
    from passlib.context import CryptContext
    pwd = CryptContext(schemes=["bcrypt"]).hash("password")

    user = User(
        username=username,
        password_hash=pwd,
        user_key=f"haven_u_{username.ljust(32, 'x')[:32]}",
        notifications_enabled=notifications_enabled,
        notification_email=notification_email,
        notification_signal_number=notification_signal_number,
        pushover_user_key=pushover_user_key,
    )
    db.add(user)
    await db.commit()
    await db.refresh(user)
    return user


async def _create_event(db: AsyncSession, user: User) -> Event:
    """Create a Device and Event linked to the given user."""
    device = Device(
        user_id=user.id,
        app_key=f"hav_{'c' * 32}",
        name="Test Device",
    )
    db.add(device)
    await db.flush()

    event = Event(
        user_id=user.id,
        device_id=device.id,
        event_type="CAMERA",
        severity="HIGH",
        timestamp=datetime(2026, 1, 1, 12, 0, 0, tzinfo=timezone.utc),
    )
    db.add(event)
    await db.commit()
    await db.refresh(event)
    return event


def _run_task_with_mocked_data(event_id: int, user: User, extra_settings: dict | None = None) -> dict:
    """
    Run send_notification_task with _load_event_data patched to return controlled data.

    asyncio.run is also patched to avoid nested-event-loop issues in test environments
    where an event loop may already be running.
    """
    from app.tasks.notifications import send_notification_task

    default_load_result = {
        "user": user,
        "device_name": "Test Device",
        "event_type": user.__dict__.get("_event_type", "CAMERA"),
        "severity": "HIGH",
        "timestamp": "2026-01-01 12:00:00 UTC",
        "analysis_summary": None,
    }

    settings_defaults = {
        "SMTP_HOST": "",
        "SIGNAL_API_URL": "",
        "PUSHOVER_APP_TOKEN": "",
    }
    if extra_settings:
        settings_defaults.update(extra_settings)

    def fake_asyncio_run(coro):
        """Run the coroutine in the existing event loop or a new one."""
        try:
            loop = real_asyncio.get_event_loop()
            if loop.is_running():
                import concurrent.futures
                with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
                    future = pool.submit(real_asyncio.run, coro)
                    return future.result(timeout=5)
            else:
                return loop.run_until_complete(coro)
        except RuntimeError:
            return real_asyncio.run(coro)

    with patch("app.tasks.notifications._load_event_data") as mock_load, \
         patch("app.tasks.notifications.asyncio.run", side_effect=lambda coro: default_load_result) as mock_run:
        mock_load.return_value = default_load_result
        # First asyncio.run call is for _load_event_data — return the data dict directly
        mock_run.side_effect = lambda coro: default_load_result

        with patch("app.tasks.notifications.settings") as mock_settings:
            for k, v in settings_defaults.items():
                setattr(mock_settings, k, v)
            result = send_notification_task(event_id)

    return result


# ---------------------------------------------------------------------------
# Notification settings API tests
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_notification_settings_get(notif_client: httpx.AsyncClient):
    """GET /settings returns default notification settings for a new user."""
    token = await _register_and_login(notif_client, "getuser")
    resp = await notif_client.get(
        "/api/v1/notifications/settings",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["notifications_enabled"] is True
    assert data["notification_email"] is None
    assert data["notification_signal_number"] is None
    assert data["pushover_user_key"] is None


@pytest.mark.asyncio
async def test_notification_settings_update(notif_client: httpx.AsyncClient):
    """PATCH /settings persists the updated email field and returns the new value."""
    token = await _register_and_login(notif_client, "patchuser")
    resp = await notif_client.patch(
        "/api/v1/notifications/settings",
        json={"notification_email": "alert@example.com"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["notification_email"] == "alert@example.com"
    assert data["notifications_enabled"] is True


# ---------------------------------------------------------------------------
# Celery task tests
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_send_notification_disabled(db_session: AsyncSession):
    """Task returns {"status": "disabled"} when notifications_enabled=False."""
    user = await _create_user_direct(db_session, "disableduser", notifications_enabled=False)
    event = await _create_event(db_session, user)

    from app.tasks.notifications import send_notification_task

    def fake_run(coro):
        return {"user": user, "device_name": "D", "event_type": "CAMERA",
                "severity": "LOW", "timestamp": "now", "analysis_summary": None}

    with patch("app.tasks.notifications.asyncio.run", side_effect=fake_run):
        result = send_notification_task(event.id)

    assert result == {"status": "disabled"}


@pytest.mark.asyncio
async def test_send_notification_email(db_session: AsyncSession):
    """Task calls send_email when notification_email is set and SMTP_HOST is configured."""
    user = await _create_user_direct(
        db_session, "emailuser",
        notification_email="test@example.com",
    )
    event = await _create_event(db_session, user)

    from app.tasks.notifications import send_notification_task

    load_result = {
        "user": user,
        "device_name": "Front Door",
        "event_type": "CAMERA",
        "severity": "HIGH",
        "timestamp": "2026-01-01 12:00:00 UTC",
        "analysis_summary": None,
    }

    call_count = [0]

    def fake_run(coro):
        call_count[0] += 1
        if call_count[0] == 1:
            # First call: _load_event_data
            return load_result
        # Second call: send_email coroutine
        return True

    with patch("app.tasks.notifications.asyncio.run", side_effect=fake_run), \
         patch("app.tasks.notifications.settings") as mock_settings:
        mock_settings.SMTP_HOST = "smtp.example.com"
        mock_settings.SIGNAL_API_URL = ""
        mock_settings.PUSHOVER_APP_TOKEN = ""

        result = send_notification_task(event.id)

    assert result["status"] == "sent"
    assert result["results"].get("email") is True


@pytest.mark.asyncio
async def test_send_notification_signal(db_session: AsyncSession):
    """Task calls send_signal when notification_signal_number is set."""
    user = await _create_user_direct(
        db_session, "signaluser",
        notification_signal_number="+15551234567",
    )
    event = await _create_event(db_session, user)

    from app.tasks.notifications import send_notification_task

    load_result = {
        "user": user,
        "device_name": "Front Door",
        "event_type": "CAMERA",
        "severity": "HIGH",
        "timestamp": "2026-01-01 12:00:00 UTC",
        "analysis_summary": None,
    }

    with patch("app.tasks.notifications.asyncio.run", return_value=load_result), \
         patch("app.tasks.notifications.send_signal", return_value=True) as mock_send_signal, \
         patch("app.tasks.notifications.settings") as mock_settings:
        mock_settings.SMTP_HOST = ""
        mock_settings.SIGNAL_API_URL = "http://signal.example.com"
        mock_settings.PUSHOVER_APP_TOKEN = ""

        result = send_notification_task(event.id)

    assert result["status"] == "sent"
    mock_send_signal.assert_called_once()
    assert mock_send_signal.call_args[0][0] == "+15551234567"


@pytest.mark.asyncio
async def test_send_notification_pushover(db_session: AsyncSession):
    """Task calls send_pushover when pushover_user_key is set."""
    user = await _create_user_direct(
        db_session, "pushoveruser",
        pushover_user_key="userkeyabc123",
    )
    event = await _create_event(db_session, user)

    from app.tasks.notifications import send_notification_task

    load_result = {
        "user": user,
        "device_name": "Porch",
        "event_type": "MICROPHONE",
        "severity": "MEDIUM",
        "timestamp": "2026-01-01 12:00:00 UTC",
        "analysis_summary": "Loud noise detected",
    }

    with patch("app.tasks.notifications.asyncio.run", return_value=load_result), \
         patch("app.tasks.notifications.send_pushover", return_value=True) as mock_send_pushover, \
         patch("app.tasks.notifications.settings") as mock_settings:
        mock_settings.SMTP_HOST = ""
        mock_settings.SIGNAL_API_URL = ""
        mock_settings.PUSHOVER_APP_TOKEN = "apptoken123"

        result = send_notification_task(event.id)

    assert result["status"] == "sent"
    mock_send_pushover.assert_called_once()
    assert mock_send_pushover.call_args[0][0] == "userkeyabc123"


@pytest.mark.asyncio
async def test_send_notification_no_channels(db_session: AsyncSession):
    """Task returns {"status": "no_channels"} when enabled but no channels configured."""
    user = await _create_user_direct(db_session, "nochanneluser", notifications_enabled=True)
    event = await _create_event(db_session, user)

    from app.tasks.notifications import send_notification_task

    load_result = {
        "user": user,
        "device_name": "Front Door",
        "event_type": "CAMERA",
        "severity": "LOW",
        "timestamp": "2026-01-01 12:00:00 UTC",
        "analysis_summary": None,
    }

    with patch("app.tasks.notifications.asyncio.run", return_value=load_result), \
         patch("app.tasks.notifications.settings") as mock_settings:
        mock_settings.SMTP_HOST = ""
        mock_settings.SIGNAL_API_URL = ""
        mock_settings.PUSHOVER_APP_TOKEN = ""

        result = send_notification_task(event.id)

    assert result == {"status": "no_channels"}


def test_format_notification_message():
    """format_notification_message produces correct subject and body."""
    subject, body = format_notification_message(
        event_type="CAMERA_PERSON",
        severity="CRITICAL",
        timestamp="2026-01-01 12:00:00 UTC",
        device_name="Main Entrance",
        analysis_summary="Person detected with 92% confidence",
    )

    assert "CRITICAL" in subject
    assert "CAMERA_PERSON" in subject
    assert "Main Entrance" in subject
    assert "Main Entrance" in body
    assert "Person detected" in body
    assert "CRITICAL" in body
    assert "CAMERA_PERSON" in body
