"""
Pydantic v2 schemas for Device management endpoints.

DeviceCreateRequest  — POST /api/v1/devices/ request body
DeviceResponse       — single device representation
DeviceListResponse   — GET /api/v1/devices/ response body
"""

from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class DeviceCreateRequest(BaseModel):
    """Request body for creating a new device."""

    name: str = Field(..., min_length=1, max_length=100, description="Human-readable device label")

    model_config = ConfigDict(
        json_schema_extra={"example": {"name": "Bedroom Camera"}}
    )


class DeviceResponse(BaseModel):
    """Single device response.

    The app_key field is always included in the response.
    On creation (POST /), the app_key is shown once in full — the client must store it.
    On subsequent GET responses the same key is returned for reference.
    """

    id: int
    name: str
    app_key: str
    is_active: bool
    created_at: datetime
    last_seen_at: datetime | None

    model_config = ConfigDict(from_attributes=True)


class DeviceListResponse(BaseModel):
    """Response body for listing all devices belonging to the authenticated user."""

    devices: list[DeviceResponse]
