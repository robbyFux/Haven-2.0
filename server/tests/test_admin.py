"""
Integration tests for admin API endpoints (/api/v1/admin/*).

Covers:
  - list users as admin (200)
  - list users as non-admin (403)
  - get user detail as admin (200)
  - update quota (storage_quota_mb + max_events)
  - system stats (counts)
  - deactivate user (soft-delete)
  - cannot self-delete (403)
"""

import secrets

import httpx
import pytest

BASE = "/api/v1/admin"
AUTH = "/api/v1/auth"


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


async def register_and_login(client: httpx.AsyncClient, suffix: str = "") -> tuple[str, int]:
    """Register a fresh user and return (access_token, user_id)."""
    username = f"admin_test_{suffix}_{secrets.token_hex(4)}"
    password = "Password123!"

    reg_resp = await client.post(
        f"{AUTH}/register",
        json={"username": username, "password": password},
    )
    assert reg_resp.status_code == 201, f"Register failed: {reg_resp.text}"
    user_id = reg_resp.json()["id"]

    login_resp = await client.post(
        f"{AUTH}/login",
        json={"username": username, "password": password},
    )
    assert login_resp.status_code == 200, f"Login failed: {login_resp.text}"
    return login_resp.json()["access_token"], user_id


async def make_admin(client: httpx.AsyncClient, admin_token: str, user_id: int) -> None:
    """Promote a user to admin using an existing admin token."""
    resp = await client.patch(
        f"{BASE}/users/{user_id}/admin",
        json={"is_admin": True},
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert resp.status_code == 200, f"make_admin failed: {resp.text}"


async def create_admin_user(client: httpx.AsyncClient) -> tuple[str, int]:
    """
    Create a brand-new user and bootstrap admin rights.

    Strategy: register two users so we can use the second to promote the first,
    or — since we have no existing admin — we directly call the toggle endpoint
    after creating a first admin via a chain. In practice, we seed by creating
    user A, then having user A promote itself (which only works once the first
    admin exists in DB). For tests we instead rely on the DB fixture.

    Simpler approach used here: create user, then call the admin toggle directly
    with a second admin account. For test isolation we create admin user once per
    test using the bootstrap helper below.
    """
    token, user_id = await register_and_login(client, "admin_bootstrap")
    return token, user_id


async def bootstrap_admin(client: httpx.AsyncClient) -> tuple[str, int]:
    """
    Create the very first admin user by direct DB-level promotion.

    Since there is no admin yet, we register a user and then use the raw
    SQLAlchemy session exposed by the test app to set is_admin=True.

    Workaround: we register a second user with a known username, then we need
    a privileged path. The cleanest approach for tests is to use the SQLAlchemy
    session directly via a helper endpoint that only exists in tests, or to
    chain promotions.

    Practical approach: we create user A and user B. We call
    PATCH /admin/users/{A.id}/admin with B's token — but B is not admin either.

    Resolution: the test conftest's SQLite DB is shared. We use the internal
    override to directly update via the test session. Since that's not exposed,
    we instead implement it the same way test_auth does: update the DB directly
    through a custom override placed in conftest, or by adding a test-only
    promotion endpoint.

    Simplest working pattern for this test suite:
      1. Register user A.
      2. Register user B.
      3. Manually set user A as admin by reaching into the DB via an
         async_client that exposes a test helper.

    However, since the conftest does not expose the DB session to tests, the
    cleanest approach is to use the SQLAlchemy session inside a helper that
    the test calls via the dependency override pattern already in conftest.

    Practical decision: we create a second fixture-level admin using a POST to
    a test-only debug endpoint — but that pollutes the app. Instead, we create
    a module-level admin using the PATCH endpoint bootstrapped via a two-step:

      Step 1: Register user A and user B.
      Step 2: Since no admin exists yet, PATCH /admin/users/{A.id}/admin with
              B's token will return 403. We cannot bootstrap without DB access.

    Final approach: expose the DB session via a pytest fixture parameter using
    the dependency override mechanism already wired in conftest.py. We add a
    `db_session` fixture to this module that re-uses the conftest engine.

    For simplicity in this plan, we use a db_session fixture approach below.
    """
    raise NotImplementedError("Use make_admin_via_db fixture instead")


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


@pytest.fixture
async def admin_token_and_id(async_client: httpx.AsyncClient) -> tuple[str, int]:
    """
    Register a new user and promote them to admin via direct DB write.

    We re-use the conftest engine by importing and running a short SQL update
    through the test's overridden get_db. This is the cleanest approach without
    modifying conftest.py.
    """
    from sqlalchemy import update
    from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

    from app.models.user import User

    # Identical test DB URL used in conftest.py
    TEST_DATABASE_URL = "sqlite+aiosqlite:///./test.db"
    engine = create_async_engine(TEST_DATABASE_URL, connect_args={"check_same_thread": False})
    SessionLocal = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)

    token, user_id = await register_and_login(async_client, "admin")

    async with SessionLocal() as session:
        await session.execute(update(User).where(User.id == user_id).values(is_admin=True))
        await session.commit()

    await engine.dispose()
    return token, user_id


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------


async def test_list_users_as_admin(
    async_client: httpx.AsyncClient,
    admin_token_and_id: tuple[str, int],
) -> None:
    """Admin can list all users; response contains at least the admin user."""
    admin_token, _ = admin_token_and_id

    resp = await async_client.get(
        f"{BASE}/users",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert resp.status_code == 200, resp.text
    data = resp.json()
    assert "users" in data
    assert "total" in data
    assert data["total"] >= 1
    # Each user entry should have expected fields
    user = data["users"][0]
    assert "id" in user
    assert "username" in user
    assert "storage_quota_mb" in user
    assert "device_count" in user


async def test_list_users_as_non_admin(async_client: httpx.AsyncClient) -> None:
    """Non-admin user receives 403 on the list-users endpoint."""
    token, _ = await register_and_login(async_client, "nonadmin")

    resp = await async_client.get(
        f"{BASE}/users",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert resp.status_code == 403, resp.text


async def test_get_user_detail(
    async_client: httpx.AsyncClient,
    admin_token_and_id: tuple[str, int],
) -> None:
    """Admin can retrieve a single user's full detail including quota fields."""
    admin_token, admin_id = admin_token_and_id

    resp = await async_client.get(
        f"{BASE}/users/{admin_id}",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert resp.status_code == 200, resp.text
    data = resp.json()
    assert data["id"] == admin_id
    assert data["is_admin"] is True
    assert "storage_quota_mb" in data
    assert "max_events" in data
    assert "current_storage_bytes" in data
    assert "current_event_count" in data
    assert "device_count" in data


async def test_update_quota(
    async_client: httpx.AsyncClient,
    admin_token_and_id: tuple[str, int],
) -> None:
    """Admin can update storage_quota_mb and max_events for a user."""
    admin_token, _ = admin_token_and_id

    # Create a target user
    target_token, target_id = await register_and_login(async_client, "quota_target")

    resp = await async_client.patch(
        f"{BASE}/users/{target_id}/quota",
        json={"storage_quota_mb": 500, "max_events": 5000},
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert resp.status_code == 200, resp.text
    data = resp.json()
    assert data["storage_quota_mb"] == 500
    assert data["max_events"] == 5000


async def test_get_system_stats(
    async_client: httpx.AsyncClient,
    admin_token_and_id: tuple[str, int],
) -> None:
    """Admin can retrieve server-wide stats with correct field types."""
    admin_token, _ = admin_token_and_id

    resp = await async_client.get(
        f"{BASE}/stats",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert resp.status_code == 200, resp.text
    data = resp.json()
    assert "total_users" in data
    assert "total_events" in data
    assert "total_devices" in data
    assert "total_storage_bytes" in data
    assert data["total_users"] >= 1
    assert data["total_storage_bytes"] >= 0


async def test_deactivate_user(
    async_client: httpx.AsyncClient,
    admin_token_and_id: tuple[str, int],
) -> None:
    """Admin can deactivate a user; deactivated user cannot log in."""
    admin_token, _ = admin_token_and_id

    # Register a fresh user to deactivate
    suffix = secrets.token_hex(4)
    username = f"todeact_{suffix}"
    password = "Password123!"
    reg = await async_client.post(
        f"{AUTH}/register",
        json={"username": username, "password": password},
    )
    assert reg.status_code == 201
    target_id = reg.json()["id"]

    # Deactivate
    resp = await async_client.delete(
        f"{BASE}/users/{target_id}",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert resp.status_code == 200, resp.text
    assert resp.json()["status"] == "deactivated"

    # Deactivated user cannot log in (get_current_user checks is_active)
    login_resp = await async_client.post(
        f"{AUTH}/login",
        json={"username": username, "password": password},
    )
    # Login returns 200 with a token but get_current_user will reject it
    # OR the login endpoint itself may reject inactive users.
    # Verify by attempting an authenticated request:
    if login_resp.status_code == 200:
        token = login_resp.json()["access_token"]
        me_resp = await async_client.get(
            "/api/v1/auth/me",
            headers={"Authorization": f"Bearer {token}"},
        )
        assert me_resp.status_code == 401


async def test_cannot_self_delete(
    async_client: httpx.AsyncClient,
    admin_token_and_id: tuple[str, int],
) -> None:
    """Admin cannot deactivate their own account (returns 403)."""
    admin_token, admin_id = admin_token_and_id

    resp = await async_client.delete(
        f"{BASE}/users/{admin_id}",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert resp.status_code == 403, resp.text
