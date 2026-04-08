"""
Pydantic v2 schemas for admin API endpoints.

Used by the admin router (/api/v1/admin/*) to serialize/deserialize
user management and system statistics data.
"""

from datetime import datetime

from pydantic import BaseModel, ConfigDict


class UserAdminResponse(BaseModel):
    """Full user detail as returned to an admin."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    username: str
    is_admin: bool
    is_active: bool
    totp_enabled: bool
    storage_quota_mb: int
    max_events: int
    current_storage_bytes: int
    current_event_count: int
    device_count: int
    created_at: datetime


class UserListResponse(BaseModel):
    """Paginated list of users."""

    users: list[UserAdminResponse]
    total: int


class QuotaUpdateRequest(BaseModel):
    """Request body for updating a user's quota limits."""

    storage_quota_mb: int | None = None
    max_events: int | None = None

    # Validators — both fields must be non-negative if provided
    from pydantic import field_validator

    @field_validator("storage_quota_mb", "max_events", mode="before")
    @classmethod
    def must_be_non_negative(cls, v):
        if v is not None and v < 0:
            raise ValueError("Value must be >= 0")
        return v


class SystemStatsResponse(BaseModel):
    """Server-wide aggregate statistics for the admin dashboard."""

    total_users: int
    total_events: int
    total_devices: int
    total_storage_bytes: int
