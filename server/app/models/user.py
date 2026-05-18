"""
User ORM model.

Each user has a username/password pair for login, a long-lived User-Key
for API access from Haven devices, optional TOTP 2FA, and configurable
per-user quotas (set by admin).

User-Key format: `haven_u_<32 hex chars>`
"""

from typing import TYPE_CHECKING

from sqlalchemy import BigInteger, Boolean, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import Base, TimestampMixin

if TYPE_CHECKING:
    from app.models.device import Device
    from app.models.event import Event


class User(TimestampMixin, Base):
    """
    Represents a Haven Cloud account.

    Quota fields are admin-settable; defaults allow 1 GB storage and 10 000 events.
    TOTP secret is stored encrypted with the server SECRET_KEY via Fernet.
    """

    __tablename__ = "users"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    username: Mapped[str] = mapped_column(String(50), unique=True, index=True, nullable=False)

    # Password stored as bcrypt hash (never plaintext)
    password_hash: Mapped[str] = mapped_column(String(255), nullable=False)

    # Long-lived API key for device-to-server authentication (format: haven_u_<32hex>)
    user_key: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)

    # Role flags
    is_admin: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)

    # TOTP 2FA — secret is Fernet-encrypted with server SECRET_KEY
    totp_secret: Mapped[str | None] = mapped_column(String(255), nullable=True)
    totp_enabled: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)

    # Admin-configurable quotas (CLOUD-05)
    storage_quota_mb: Mapped[int] = mapped_column(Integer, default=1024, nullable=False)
    max_events: Mapped[int] = mapped_column(Integer, default=10000, nullable=False)

    # Running counters — updated on each upload / deletion
    current_storage_bytes: Mapped[int] = mapped_column(BigInteger, default=0, nullable=False)
    current_event_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)

    # Notification contact info
    notification_email: Mapped[str | None] = mapped_column(String(255), nullable=True)
    notification_signal_number: Mapped[str | None] = mapped_column(String(20), nullable=True)
    pushover_user_key: Mapped[str | None] = mapped_column(String(50), nullable=True)
    pushover_app_token: Mapped[str | None] = mapped_column(String(50), nullable=True)
    notifications_enabled: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)

    # Relationships
    devices: Mapped[list["Device"]] = relationship("Device", back_populates="user", cascade="all, delete-orphan")
    events: Mapped[list["Event"]] = relationship("Event", back_populates="user", cascade="all, delete-orphan")
