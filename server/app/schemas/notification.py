"""
Pydantic v2 schemas for notification settings endpoints.
"""

from pydantic import BaseModel


class NotificationSettingsRequest(BaseModel):
    """Request body for PATCH /api/v1/notifications/settings."""

    notification_email: str | None = None
    notification_signal_number: str | None = None
    pushover_user_key: str | None = None
    notifications_enabled: bool | None = None


class NotificationSettingsResponse(BaseModel):
    """Response body for GET and PATCH /api/v1/notifications/settings."""

    notification_email: str | None
    notification_signal_number: str | None
    pushover_user_key: str | None
    notifications_enabled: bool

    model_config = {"from_attributes": True}
