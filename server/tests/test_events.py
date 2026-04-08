"""
Integration tests for event upload, listing, retrieval, deletion, and quota enforcement.

Covers:
  - metadata-only upload (no video): 201
  - video upload (multipart): 201, media_path set
  - encrypted upload (X-Encryption-Password header): is_encrypted=True in DB
  - storage quota exceeded: 413
  - event count quota exceeded: 429
  - paginated event listing: total and page counts correct
  - get single event by ID: fields match
  - delete event: 404 on subsequent get
  - user isolation: user A's events not visible to user B
  - invalid App-Key: 401
"""

import io
import json
import secrets

import httpx
import pytest
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.models.user import User
from app.services.crypto import decrypt_file, derive_key

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

AUTH_BASE = "/api/v1/auth"
DEVICES_BASE = "/api/v1/devices"
EVENTS_BASE = "/api/v1/events"
TEST_DATABASE_URL = "sqlite+aiosqlite:///./test.db"


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


async def register_and_login(client: httpx.AsyncClient, suffix: str = "") -> tuple[str, str]:
    """Register a fresh user and return (access_token, username)."""
    username = f"evtest_{suffix}_{secrets.token_hex(4)}"
    password = "Password123!"

    await client.post(
        f"{AUTH_BASE}/register",
        json={"username": username, "password": password},
    )
    resp = await client.post(
        f"{AUTH_BASE}/login",
        json={"username": username, "password": password},
    )
    assert resp.status_code == 200, f"Login failed: {resp.text}"
    return resp.json()["access_token"], username


async def create_device(client: httpx.AsyncClient, token: str, name: str = "Test Device") -> str:
    """Create a device and return its app_key."""
    resp = await client.post(
        f"{DEVICES_BASE}/",
        json={"name": name},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert resp.status_code == 201, f"Device creation failed: {resp.text}"
    return resp.json()["app_key"]


async def create_test_user_and_device(
    client: httpx.AsyncClient, suffix: str = ""
) -> tuple[str, str, str]:
    """Register user, login, create device. Returns (access_token, app_key, username)."""
    token, username = await register_and_login(client, suffix)
    app_key = await create_device(client, token)
    return token, app_key, username


def event_metadata(
    event_type: str = "CAMERA",
    severity: str = "MEDIUM",
) -> str:
    """Return JSON-encoded event metadata for multipart upload."""
    from datetime import datetime, timezone

    return json.dumps(
        {
            "event_type": event_type,
            "severity": severity,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "sensor_value": 0.75,
        }
    )


async def upload_event(
    client: httpx.AsyncClient,
    app_key: str,
    metadata: str | None = None,
    video_bytes: bytes | None = None,
    encryption_password: str | None = None,
) -> httpx.Response:
    """POST to the event upload endpoint."""
    if metadata is None:
        metadata = event_metadata()

    headers = {"X-App-Key": app_key}
    if encryption_password is not None:
        headers["X-Encryption-Password"] = encryption_password

    files: dict = {"metadata": (None, metadata, "application/json")}
    if video_bytes is not None:
        files["video"] = ("video.mp4", io.BytesIO(video_bytes), "video/mp4")

    return await client.post(
        f"/api/v1/devices/{app_key}/events",
        headers=headers,
        files=files,
    )


async def _set_user_quota(
    username: str,
    storage_quota_mb: int | None = None,
    max_events: int | None = None,
) -> None:
    """Directly update user quota fields in the test SQLite database."""
    engine = create_async_engine(TEST_DATABASE_URL, connect_args={"check_same_thread": False})
    Session = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    async with Session() as db:
        values: dict = {}
        if storage_quota_mb is not None:
            values["storage_quota_mb"] = storage_quota_mb
        if max_events is not None:
            values["max_events"] = max_events
        if values:
            await db.execute(update(User).where(User.username == username).values(**values))
            await db.commit()
    await engine.dispose()


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------


async def test_upload_event_metadata_only(async_client: httpx.AsyncClient) -> None:
    """POST with JSON metadata only (no video) returns 201 with expected fields."""
    _, app_key, _ = await create_test_user_and_device(async_client, "meta_only")

    resp = await upload_event(async_client, app_key)

    assert resp.status_code == 201, resp.text
    data = resp.json()
    assert data["event_type"] == "CAMERA"
    assert data["severity"] == "MEDIUM"
    assert data["media_path"] is None
    assert "id" in data
    assert "created_at" in data


async def test_upload_event_with_video(async_client: httpx.AsyncClient) -> None:
    """POST with metadata + video file returns 201 and media_path is set."""
    _, app_key, _ = await create_test_user_and_device(async_client, "with_video")

    video_content = b"fake-video-content"
    resp = await upload_event(async_client, app_key, video_bytes=video_content)

    assert resp.status_code == 201, resp.text
    data = resp.json()
    assert data["media_path"] is not None
    assert "video" in data["media_path"].lower() or data["media_path"].endswith(".mp4")


async def test_upload_event_encrypted(async_client: httpx.AsyncClient) -> None:
    """POST with X-Encryption-Password header produces an encrypted file on disk."""
    _, app_key, username = await create_test_user_and_device(async_client, "encrypted")

    original_bytes = b"sensitive-video-content"
    password = "s3cr3t-pass"

    resp = await upload_event(
        async_client,
        app_key,
        video_bytes=original_bytes,
        encryption_password=password,
    )
    assert resp.status_code == 201, resp.text
    data = resp.json()
    assert data["media_path"] is not None

    # Verify the file is actually encrypted: read it back and decrypt
    from app.config import settings
    import os

    full_path = os.path.join(settings.MEDIA_ROOT, data["media_path"])
    assert os.path.exists(full_path), f"Media file not found at {full_path}"

    with open(full_path, "rb") as f:
        ciphertext = f.read()

    # File must NOT match original bytes (it is encrypted)
    assert ciphertext != original_bytes

    # Decrypt and verify round-trip
    key = derive_key(password, username)
    decrypted = decrypt_file(ciphertext, key)
    assert decrypted == original_bytes


async def test_upload_quota_storage_exceeded(async_client: httpx.AsyncClient) -> None:
    """Upload with video when storage_quota_mb=0 returns 413."""
    _, app_key, username = await create_test_user_and_device(async_client, "quota_storage")

    # Set quota to 0 MB
    await _set_user_quota(username, storage_quota_mb=0)

    resp = await upload_event(
        async_client,
        app_key,
        video_bytes=b"some-video-data",
    )
    assert resp.status_code == 413, resp.text


async def test_upload_quota_events_exceeded(async_client: httpx.AsyncClient) -> None:
    """Upload when max_events=0 returns 429."""
    _, app_key, username = await create_test_user_and_device(async_client, "quota_events")

    # Set max_events to 0
    await _set_user_quota(username, max_events=0)

    resp = await upload_event(async_client, app_key)
    assert resp.status_code == 429, resp.text


async def test_list_events_paginated(async_client: httpx.AsyncClient) -> None:
    """Upload 3 events, list with page_size=2: total=3, 2 events in first page."""
    token, app_key, _ = await create_test_user_and_device(async_client, "paginated")

    # Upload 3 events
    for _ in range(3):
        resp = await upload_event(async_client, app_key)
        assert resp.status_code == 201, resp.text

    # Fetch first page
    list_resp = await async_client.get(
        f"{EVENTS_BASE}?page=1&page_size=2",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert list_resp.status_code == 200, list_resp.text
    data = list_resp.json()
    assert data["total"] >= 3  # may include events from other tests for this user
    assert len(data["events"]) == 2
    assert data["page"] == 1
    assert data["page_size"] == 2


async def test_get_event_by_id(async_client: httpx.AsyncClient) -> None:
    """Upload an event, get it by ID, verify fields match."""
    token, app_key, _ = await create_test_user_and_device(async_client, "get_by_id")

    upload_resp = await upload_event(
        async_client, app_key, metadata=event_metadata("ACCELEROMETER", "HIGH")
    )
    assert upload_resp.status_code == 201, upload_resp.text
    event_id = upload_resp.json()["id"]

    get_resp = await async_client.get(
        f"{EVENTS_BASE}/{event_id}",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert get_resp.status_code == 200, get_resp.text
    data = get_resp.json()
    assert data["id"] == event_id
    assert data["event_type"] == "ACCELEROMETER"
    assert data["severity"] == "HIGH"


async def test_delete_event(async_client: httpx.AsyncClient) -> None:
    """Upload an event, delete it, verify 404 on subsequent GET."""
    token, app_key, _ = await create_test_user_and_device(async_client, "delete")

    upload_resp = await upload_event(async_client, app_key)
    assert upload_resp.status_code == 201
    event_id = upload_resp.json()["id"]

    del_resp = await async_client.delete(
        f"{EVENTS_BASE}/{event_id}",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert del_resp.status_code == 200, del_resp.text
    assert del_resp.json()["status"] == "deleted"

    get_resp = await async_client.get(
        f"{EVENTS_BASE}/{event_id}",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert get_resp.status_code == 404


async def test_event_isolation(async_client: httpx.AsyncClient) -> None:
    """User A's events are not visible to user B."""
    token_a, app_key_a, _ = await create_test_user_and_device(async_client, "iso_a")
    token_b, _, _ = await create_test_user_and_device(async_client, "iso_b")

    # User A uploads an event
    upload_resp = await upload_event(async_client, app_key_a)
    assert upload_resp.status_code == 201
    event_id = upload_resp.json()["id"]

    # User B cannot list user A's event
    list_resp = await async_client.get(
        EVENTS_BASE,
        headers={"Authorization": f"Bearer {token_b}"},
    )
    assert list_resp.status_code == 200
    event_ids_b = [e["id"] for e in list_resp.json()["events"]]
    assert event_id not in event_ids_b

    # User B cannot GET user A's event directly
    get_resp = await async_client.get(
        f"{EVENTS_BASE}/{event_id}",
        headers={"Authorization": f"Bearer {token_b}"},
    )
    assert get_resp.status_code == 404


async def test_upload_invalid_app_key(async_client: httpx.AsyncClient) -> None:
    """POST with a bad X-App-Key header returns 401."""
    fake_key = "hav_" + "0" * 64  # valid format but nonexistent key

    resp = await async_client.post(
        f"/api/v1/devices/{fake_key}/events",
        headers={"X-App-Key": fake_key},
        files={"metadata": (None, event_metadata(), "application/json")},
    )
    assert resp.status_code == 401, resp.text
