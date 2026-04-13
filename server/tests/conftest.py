"""
pytest async test infrastructure for Haven Cloud.

Provides:
  - `async_client`: httpx.AsyncClient backed by an in-memory SQLite database.
    The get_db dependency is overridden to use the test session, ensuring
    tests never touch the real PostgreSQL database.
  - `mock_celery_tasks`: session-scoped autouse fixture that stubs out
    analyze_event_task and send_notification_task so they never run inside
    the async test event loop (avoids asyncio.run() conflicts with
    task_always_eager mode).

Usage in tests:
    async def test_health(async_client: httpx.AsyncClient) -> None:
        response = await async_client.get("/health")
        assert response.status_code == 200
"""

import os
from unittest.mock import MagicMock, patch

import pytest
import pytest_asyncio
import httpx
from httpx import ASGITransport
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.database import get_db
from app.main import create_app
from app.models.base import Base

# Run Celery tasks synchronously in-process during tests (no Redis required).
# Must be set before celery_app is first imported.
os.environ.setdefault("CELERY_TASK_ALWAYS_EAGER", "true")

# Apply eager mode to the already-imported celery_app instance as well,
# in case it was imported before this conftest ran.
from app.celery_app import celery_app as _celery_app  # noqa: E402
_celery_app.conf.update(task_always_eager=True, task_eager_propagates=True)

# SQLite in-memory database for fast isolated tests.
# aiosqlite is required (listed in [project.optional-dependencies] dev).
TEST_DATABASE_URL = "sqlite+aiosqlite:///./test.db"


@pytest.fixture(autouse=True, scope="session")
def mock_celery_tasks():
    """
    Stub out Celery tasks for all integration tests.

    analyze_event_task and send_notification_task both call asyncio.run()
    internally (to drive async DB/network operations from synchronous Celery
    workers). When task_always_eager=True is set, they run inline inside the
    async pytest event loop, causing "asyncio.run() cannot be called from a
    running event loop" errors.

    These tasks have dedicated unit tests (test_analysis.py,
    test_notifications.py) that exercise them with their own mocks. Integration
    tests only care that the HTTP endpoint returns the correct status code and
    that the task is *enqueued* — not that it runs.
    """
    noop = MagicMock(return_value={"status": "skipped"})
    noop.delay = MagicMock(return_value=None)

    with (
        patch("app.routers.events.analyze_event_task", noop),
        patch("app.routers.events.send_notification_task", noop),
    ):
        yield


@pytest_asyncio.fixture(loop_scope="session")
async def async_client() -> httpx.AsyncClient:
    """
    Session-scoped async HTTP client with a clean SQLite test database.

    All tables are created before the first test and dropped after the last.
    The get_db FastAPI dependency is overridden to use the test session.
    """
    engine = create_async_engine(TEST_DATABASE_URL, connect_args={"check_same_thread": False})
    TestSessionLocal = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)

    # Create all tables in the test database
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    async def override_get_db():
        async with TestSessionLocal() as session:
            yield session

    app = create_app()
    app.dependency_overrides[get_db] = override_get_db

    async with httpx.AsyncClient(
        transport=ASGITransport(app=app),
        base_url="http://test",
    ) as client:
        yield client

    # Teardown: drop all tables
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)

    await engine.dispose()
