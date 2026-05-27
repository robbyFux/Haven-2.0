"""
Event, EventTrigger, and AnalysisResult ORM models.

Event represents a security event captured by a Haven device. Each event can
have multiple EventTrigger rows (one per sensor that fired) and at most one
AnalysisResult row (written asynchronously by the Celery AI worker).

Video media is stored on the server filesystem. media_path holds the server-side
path; is_encrypted indicates whether the file is AES-GCM encrypted at rest.
"""

from datetime import datetime
from typing import TYPE_CHECKING

from sqlalchemy import BigInteger, Boolean, DateTime, Float, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import Base, TimestampMixin

if TYPE_CHECKING:
    from app.models.device import Device
    from app.models.user import User


class Event(TimestampMixin, Base):
    """
    A security event uploaded by a Haven device.

    Severity values mirror the Android app: LOW / MEDIUM / HIGH / CRITICAL.
    event_type mirrors TriggerType names: CAMERA, ACCELEROMETER, MICROPHONE, etc.
    """

    __tablename__ = "events"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True
    )
    device_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("devices.id", ondelete="CASCADE"), nullable=False, index=True
    )

    event_type: Mapped[str] = mapped_column(String(50), nullable=False)
    severity: Mapped[str] = mapped_column(String(20), nullable=False)
    timestamp: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    sensor_value: Mapped[float | None] = mapped_column(Float, nullable=True)

    # Path to encrypted video file on server filesystem (MEDIA_ROOT-relative)
    media_path: Mapped[str | None] = mapped_column(String(500), nullable=True)
    media_size_bytes: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    is_encrypted: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    is_archived: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)

    # Relationships
    user: Mapped["User"] = relationship("User", back_populates="events")
    device: Mapped["Device"] = relationship("Device", back_populates="events")
    triggers: Mapped[list["EventTrigger"]] = relationship(
        "EventTrigger", back_populates="event", cascade="all, delete-orphan"
    )
    analysis_result: Mapped["AnalysisResult | None"] = relationship(
        "AnalysisResult", back_populates="event", uselist=False, cascade="all, delete-orphan"
    )


class EventTrigger(TimestampMixin, Base):
    """
    A single sensor trigger that contributed to an Event.

    Multiple triggers can fire for the same event (e.g. camera + accelerometer).
    Cascade-deletes with the parent Event.
    """

    __tablename__ = "event_triggers"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    event_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("events.id", ondelete="CASCADE"), nullable=False, index=True
    )

    trigger_type: Mapped[str] = mapped_column(String(50), nullable=False)
    sensor_value: Mapped[float | None] = mapped_column(Float, nullable=True)
    media_path: Mapped[str | None] = mapped_column(String(500), nullable=True)

    # Relationship
    event: Mapped["Event"] = relationship("Event", back_populates="triggers")


class AnalysisResult(TimestampMixin, Base):
    """
    AI analysis result for an Event, written by the Celery worker.

    One-to-one with Event. backend indicates which AI ran the analysis.
    labels is a JSON string of detected object labels.
    description is an optional free-text AI summary (OpenRouter only).
    """

    __tablename__ = "analysis_results"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    event_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("events.id", ondelete="CASCADE"), unique=True, nullable=False, index=True
    )

    # Which AI backend produced this result: "tflite" or "openrouter"
    backend: Mapped[str] = mapped_column(String(20), nullable=False)

    # JSON string of detected labels, e.g. '["person", "car"]'
    labels: Mapped[str | None] = mapped_column(Text, nullable=True)
    confidence: Mapped[float | None] = mapped_column(Float, nullable=True)

    # Human-readable AI description (OpenRouter / LLM backends)
    description: Mapped[str | None] = mapped_column(Text, nullable=True)

    # Full raw JSON response from the AI backend (for debugging)
    raw_result: Mapped[str | None] = mapped_column(Text, nullable=True)

    # Relationship
    event: Mapped["Event"] = relationship("Event", back_populates="analysis_result")
