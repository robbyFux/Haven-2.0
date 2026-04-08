"""
Device ORM model.

Each registered Haven Android app is a Device. Devices authenticate via their
unique App-Key (format: `hav_<32 hex chars>`). App-Keys are per-device and
revocable without affecting other devices of the same user.
"""

from datetime import datetime
from typing import TYPE_CHECKING

from sqlalchemy import Boolean, DateTime, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import Base, TimestampMixin

if TYPE_CHECKING:
    from app.models.event import Event
    from app.models.user import User


class Device(TimestampMixin, Base):
    """
    Represents a single registered Haven Android app (device).

    App-Key format: `hav_<32 random hex chars>` — easy to identify in logs.
    """

    __tablename__ = "devices"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True
    )

    # Device authentication token (format: hav_<32hex>)
    app_key: Mapped[str] = mapped_column(String(68), unique=True, index=True, nullable=False)

    # Human-readable name chosen by the user (e.g. "Bedroom Camera")
    name: Mapped[str] = mapped_column(String(100), nullable=False)

    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    last_seen_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    # Relationships
    user: Mapped["User"] = relationship("User", back_populates="devices")
    events: Mapped[list["Event"]] = relationship("Event", back_populates="device", cascade="all, delete-orphan")
