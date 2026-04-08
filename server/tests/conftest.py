"""
pytest async test infrastructure for Haven Cloud.

Provides:
  - `async_client`: httpx.AsyncClient backed by an in-memory SQLite database.
    The get_db dependency is overridden to use the test session, ensuring
    tests never touch the real PostgreSQL database.

Usage in tests:
    async def test_health(async_client: httpx.AsyncClient) -> None:
        response = await async_client.get("/health")
        assert response.status_code == 200
"""

import pytest_asyncio
import httpx
from httpx import ASGITransport
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.database import get_db
from app.main import create_app
from app.models.base import Base

# SQLite in-memory database for fast isolated tests.
# aiosqlite is required (listed in [project.optional-dependencies] dev).
TEST_DATABASE_URL = "sqlite+aiosqlite:///./test.db"


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
