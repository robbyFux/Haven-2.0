"""
SQLAlchemy declarative base and common mixins shared by all models.
"""

from datetime import datetime

from sqlalchemy import DateTime, func
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


class Base(DeclarativeBase):
    """Declarative base for all Haven Cloud ORM models."""
    pass


class TimestampMixin:
    """
    Mixin that adds created_at and updated_at columns to any model.

    created_at is set by the database server on INSERT.
    updated_at is set by the database server on INSERT and UPDATE.
    """

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        nullable=False,
    )
    updated_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=True,
    )
