"""
Integration tests for the /api/v1/auth/* endpoints.

All tests use the session-scoped async_client fixture from conftest.py,
which runs against an in-memory SQLite database.
"""

import pyotp
import pytest


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

BASE = "/api/v1/auth"


async def register_user(client, username: str = "testuser", password: str = "password123"):
    """Register a user and return the response JSON."""
    resp = await client.post(
        f"{BASE}/register",
        json={"username": username, "password": password},
    )
    return resp


async def login_user(client, username: str = "testuser", password: str = "password123", totp_code=None):
    """Log in and return the response."""
    body = {"username": username, "password": password}
    if totp_code is not None:
        body["totp_code"] = totp_code
    return await client.post(f"{BASE}/login", json=body)


# ---------------------------------------------------------------------------
# Registration
# ---------------------------------------------------------------------------


async def test_register_success(async_client):
    resp = await register_user(async_client, "alice", "securepass1")
    assert resp.status_code == 201
    data = resp.json()
    assert data["username"] == "alice"
    assert data["user_key"].startswith("haven_u_")
    assert "id" in data


async def test_register_duplicate_username(async_client):
    await register_user(async_client, "bob", "securepass1")
    resp = await register_user(async_client, "bob", "differentpass2")
    assert resp.status_code == 409


# ---------------------------------------------------------------------------
# Login
# ---------------------------------------------------------------------------


async def test_login_success(async_client):
    await register_user(async_client, "carol", "securepass1")
    resp = await login_user(async_client, "carol", "securepass1")
    assert resp.status_code == 200
    data = resp.json()
    assert "access_token" in data
    assert "refresh_token" in data
    assert data["token_type"] == "bearer"


async def test_login_wrong_password(async_client):
    await register_user(async_client, "dave", "securepass1")
    resp = await login_user(async_client, "dave", "wrongpassword")
    assert resp.status_code == 401


# ---------------------------------------------------------------------------
# Token refresh
# ---------------------------------------------------------------------------


async def test_refresh_token(async_client):
    await register_user(async_client, "eve", "securepass1")
    login_resp = await login_user(async_client, "eve", "securepass1")
    refresh_token = login_resp.json()["refresh_token"]

    resp = await async_client.post(f"{BASE}/refresh", json={"refresh_token": refresh_token})
    assert resp.status_code == 200
    data = resp.json()
    assert "access_token" in data
    assert "refresh_token" in data


# ---------------------------------------------------------------------------
# /me endpoint
# ---------------------------------------------------------------------------


async def test_me_authenticated(async_client):
    await register_user(async_client, "frank", "securepass1")
    login_resp = await login_user(async_client, "frank", "securepass1")
    token = login_resp.json()["access_token"]

    resp = await async_client.get(
        f"{BASE}/me",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert resp.status_code == 200
    assert resp.json()["username"] == "frank"


async def test_me_unauthenticated(async_client):
    resp = await async_client.get(f"{BASE}/me")
    assert resp.status_code == 401


# ---------------------------------------------------------------------------
# 2FA setup and verify
# ---------------------------------------------------------------------------


async def test_2fa_setup_and_verify(async_client):
    await register_user(async_client, "grace", "securepass1")
    login_resp = await login_user(async_client, "grace", "securepass1")
    token = login_resp.json()["access_token"]
    headers = {"Authorization": f"Bearer {token}"}

    # Setup — should return secret + QR code
    setup_resp = await async_client.post(f"{BASE}/2fa/setup", headers=headers)
    assert setup_resp.status_code == 200
    setup_data = setup_resp.json()
    assert "secret" in setup_data
    assert "qr_code_base64" in setup_data

    # Generate valid TOTP code from the returned secret
    totp = pyotp.TOTP(setup_data["secret"])
    code = totp.now()

    # Verify — should enable 2FA
    verify_resp = await async_client.post(
        f"{BASE}/2fa/verify",
        json={"code": code},
        headers=headers,
    )
    assert verify_resp.status_code == 200
    assert verify_resp.json()["status"] == "2fa_enabled"


async def test_login_with_2fa(async_client):
    # Register and enable 2FA for a user
    await register_user(async_client, "henry", "securepass1")
    login_resp = await login_user(async_client, "henry", "securepass1")
    token = login_resp.json()["access_token"]
    headers = {"Authorization": f"Bearer {token}"}

    # Setup 2FA
    setup_resp = await async_client.post(f"{BASE}/2fa/setup", headers=headers)
    secret = setup_resp.json()["secret"]

    # Enable 2FA
    totp = pyotp.TOTP(secret)
    await async_client.post(
        f"{BASE}/2fa/verify",
        json={"code": totp.now()},
        headers=headers,
    )

    # Login without code → 401 with TOTP required indicator
    resp_no_code = await login_user(async_client, "henry", "securepass1")
    assert resp_no_code.status_code == 401
    # Server sets X-TOTP-Required header to signal the client
    assert resp_no_code.headers.get("X-TOTP-Required") == "true"

    # Login with valid code → 200
    resp_with_code = await login_user(async_client, "henry", "securepass1", totp_code=totp.now())
    assert resp_with_code.status_code == 200
    assert "access_token" in resp_with_code.json()
