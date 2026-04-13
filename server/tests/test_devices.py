"""
Tests for device management endpoints (/api/v1/devices/*).

Covers:
  - create device: 201, app_key format (hav_ prefix, 68 chars)
  - list devices: returns all devices for the user
  - get device by ID: name matches
  - revoke device: is_active set to False
  - unauthenticated create: 401
  - device isolation: user A cannot see user B's devices
"""

import secrets

import httpx
import pytest


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


async def register_and_login(client: httpx.AsyncClient, suffix: str = "") -> str:
    """Register a fresh user and return the access_token."""
    username = f"devtest_{suffix}_{secrets.token_hex(4)}"
    password = "Password123!"

    await client.post(
        "/api/v1/auth/register",
        json={"username": username, "password": password},
    )
    resp = await client.post(
        "/api/v1/auth/login",
        json={"username": username, "password": password},
    )
    assert resp.status_code == 200, f"Login failed: {resp.text}"
    return resp.json()["access_token"]


def auth_headers(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------


async def test_create_device(async_client: httpx.AsyncClient) -> None:
    """POST /api/v1/devices/ returns 201, app_key starts with 'hav_' and is 68 chars."""
    token = await register_and_login(async_client, "create")
    resp = await async_client.post(
        "/api/v1/devices/",
        json={"name": "Living Room"},
        headers=auth_headers(token),
    )
    assert resp.status_code == 201, resp.text
    data = resp.json()
    assert data["name"] == "Living Room"
    assert data["app_key"].startswith("hav_")
    # hav_ (4 chars) + 64 hex chars = 68 total
    assert len(data["app_key"]) == 68
    assert data["is_active"] is True


async def test_list_devices(async_client: httpx.AsyncClient) -> None:
    """GET /api/v1/devices/ returns all devices for the authenticated user."""
    token = await register_and_login(async_client, "list")
    headers = auth_headers(token)

    # Create 2 devices
    await async_client.post("/api/v1/devices/", json={"name": "Device A"}, headers=headers)
    await async_client.post("/api/v1/devices/", json={"name": "Device B"}, headers=headers)

    resp = await async_client.get("/api/v1/devices/", headers=headers)
    assert resp.status_code == 200, resp.text
    devices = resp.json()["devices"]
    assert len(devices) == 2


async def test_get_device(async_client: httpx.AsyncClient) -> None:
    """GET /api/v1/devices/{id} returns the device with matching name."""
    token = await register_and_login(async_client, "get")
    headers = auth_headers(token)

    create_resp = await async_client.post(
        "/api/v1/devices/", json={"name": "My Camera"}, headers=headers
    )
    device_id = create_resp.json()["id"]

    resp = await async_client.get(f"/api/v1/devices/{device_id}", headers=headers)
    assert resp.status_code == 200, resp.text
    assert resp.json()["name"] == "My Camera"


async def test_revoke_device(async_client: httpx.AsyncClient) -> None:
    """DELETE /api/v1/devices/{id} sets is_active=False; GET confirms it."""
    token = await register_and_login(async_client, "revoke")
    headers = auth_headers(token)

    create_resp = await async_client.post(
        "/api/v1/devices/", json={"name": "To Revoke"}, headers=headers
    )
    device_id = create_resp.json()["id"]

    revoke_resp = await async_client.delete(f"/api/v1/devices/{device_id}", headers=headers)
    assert revoke_resp.status_code == 200, revoke_resp.text
    assert revoke_resp.json() == {"status": "revoked"}

    # Verify is_active is now False
    get_resp = await async_client.get(f"/api/v1/devices/{device_id}", headers=headers)
    assert get_resp.status_code == 200
    assert get_resp.json()["is_active"] is False


async def test_create_device_unauthenticated(async_client: httpx.AsyncClient) -> None:
    """POST /api/v1/devices/ without a token returns 401."""
    resp = await async_client.post("/api/v1/devices/", json={"name": "No Auth"})
    assert resp.status_code == 401


async def test_device_heartbeat(async_client: httpx.AsyncClient) -> None:
    """POST /api/v1/devices/heartbeat returns 204 with a valid App-Key."""
    token = await register_and_login(async_client, "hb")
    create_resp = await async_client.post(
        "/api/v1/devices/",
        json={"name": "Heartbeat Device"},
        headers=auth_headers(token),
    )
    assert create_resp.status_code == 201, create_resp.text
    app_key = create_resp.json()["app_key"]

    resp = await async_client.post(
        "/api/v1/devices/heartbeat",
        headers={"X-App-Key": app_key},
    )
    assert resp.status_code == 204, resp.text


async def test_device_heartbeat_invalid_key(async_client: httpx.AsyncClient) -> None:
    """POST /api/v1/devices/heartbeat with unknown App-Key returns 401."""
    resp = await async_client.post(
        "/api/v1/devices/heartbeat",
        headers={"X-App-Key": "hav_" + "0" * 64},
    )
    assert resp.status_code == 401


async def test_device_isolation(async_client: httpx.AsyncClient) -> None:
    """User A's devices are not visible or accessible to user B."""
    token_a = await register_and_login(async_client, "isolation_a")
    token_b = await register_and_login(async_client, "isolation_b")

    # User A creates a device
    create_resp = await async_client.post(
        "/api/v1/devices/",
        json={"name": "User A Device"},
        headers=auth_headers(token_a),
    )
    device_id = create_resp.json()["id"]

    # User B should not see it in their list
    list_resp = await async_client.get("/api/v1/devices/", headers=auth_headers(token_b))
    assert list_resp.status_code == 200
    device_ids_b = [d["id"] for d in list_resp.json()["devices"]]
    assert device_id not in device_ids_b

    # User B should get 404 when fetching user A's device directly
    get_resp = await async_client.get(f"/api/v1/devices/{device_id}", headers=auth_headers(token_b))
    assert get_resp.status_code == 404
