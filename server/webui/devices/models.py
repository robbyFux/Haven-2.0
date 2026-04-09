"""
Unmanaged Django model mapping to the existing 'devices' PostgreSQL table.

managed=False: DDL is owned by FastAPI/Alembic — Django never alters this table.
ForeignKey to HavenUser uses db_constraint=False to prevent Django from generating
DDL constraints (the real FK already exists in Postgres, created by Alembic).
"""

from django.db import models


class Device(models.Model):
    """
    A registered Haven Android device.

    App-Key format: hav_<32 hex chars>.
    """

    id = models.AutoField(primary_key=True)

    # FK stored as plain integer — db_constraint=False avoids DDL conflicts.
    user = models.ForeignKey(
        "accounts.HavenUser",
        on_delete=models.CASCADE,
        db_constraint=False,
        related_name="devices",
    )

    app_key = models.CharField(max_length=68, unique=True)
    name = models.CharField(max_length=100)
    is_active = models.BooleanField(default=True)
    last_seen_at = models.DateTimeField(null=True, blank=True)

    # --- timestamps ---
    created_at = models.DateTimeField()
    updated_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        managed = False
        db_table = "devices"

    def __str__(self) -> str:
        return f"{self.name} ({self.app_key[:12]}…)"
