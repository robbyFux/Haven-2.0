"""
Pydantic v2 schemas for event upload, listing, and retrieval.

EventCreateSchema — request body metadata for POST /devices/{app_key}/events
TriggerSchema — a single sensor trigger embedded in EventCreateSchema
EventResponse — response model for a single event
EventListResponse — paginated event list response
"""

from datetime import datetime

from pydantic import BaseModel, field_validator


class TriggerSchema(BaseModel):
    """A single sensor trigger that contributed to an event."""

    trigger_type: str
    sensor_value: float | None = None


class EventCreateSchema(BaseModel):
    """
    Metadata for an event upload request.

    event_type should mirror Android TriggerType names (e.g. CAMERA, ACCELEROMETER).
    severity must be one of: LOW, MEDIUM, HIGH, CRITICAL.
    """

    event_type: str
    severity: str
    timestamp: datetime
    sensor_value: float | None = None
    triggers: list[TriggerSchema] | None = None

    @field_validator("severity")
    @classmethod
    def validate_severity(cls, v: str) -> str:
        allowed = {"LOW", "MEDIUM", "HIGH", "CRITICAL"}
        if v not in allowed:
            raise ValueError(f"severity must be one of {allowed}")
        return v


class EventResponse(BaseModel):
    """Response model for a single event."""

    model_config = {"from_attributes": True}

    id: int
    event_type: str
    severity: str
    timestamp: datetime
    sensor_value: float | None
    media_path: str | None
    has_analysis: bool
    device_name: str
    created_at: datetime


class EventListResponse(BaseModel):
    """Paginated event list response."""

    events: list[EventResponse]
    total: int
    page: int
    page_size: int
