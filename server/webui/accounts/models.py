"""
Unmanaged Django model mapping to the existing 'users' PostgreSQL table.

This model is managed=False, meaning Django will never run CREATE TABLE / ALTER TABLE
against it. The table is owned by FastAPI / Alembic migrations.

HavenUser extends AbstractBaseUser to integrate with Django's session auth
while storing passwords as bcrypt hashes (verified by HavenAuthBackend).
"""

import secrets

from django.contrib.auth.models import AbstractBaseUser, BaseUserManager
from django.db import models


class HavenUserManager(BaseUserManager):
    """Manager for HavenUser — minimal implementation for session auth."""

    def get_by_natural_key(self, username: str) -> "HavenUser":
        return self.get(username=username)

    def create_user(
        self,
        username: str,
        password: str,
        *,
        is_admin: bool = False,
        **extra_fields,
    ) -> "HavenUser":
        """
        Create and save a HavenUser with a bcrypt-hashed password.

        Used in tests and the admin seeder — not for production registration
        (that path goes through the FastAPI backend).
        """
        import bcrypt

        user = self.model(
            username=username,
            is_admin=is_admin,
            user_key=f"haven_u_{secrets.token_hex(16)}",
            **extra_fields,
        )
        user.password_hash = bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()
        user.save(using=self._db)
        return user


class HavenUser(AbstractBaseUser):
    """
    Read/write Django model for the 'users' table created by FastAPI/Alembic.

    managed=False: Django never touches the DDL for this table.
    db_table="users": maps to the existing PostgreSQL table name.

    Password is stored as a bcrypt hash in the password_hash column, NOT in
    Django's built-in password column. The custom property forwards get/set.
    """

    # --- identity ---
    id = models.AutoField(primary_key=True)
    username = models.CharField(max_length=50, unique=True)

    # bcrypt hash — stored separately from AbstractBaseUser's 'password' field
    password_hash = models.CharField(max_length=255)

    # Long-lived API key for device authentication
    user_key = models.CharField(max_length=80, unique=True)

    # --- roles ---
    is_admin = models.BooleanField(default=False)
    is_active = models.BooleanField(default=True)

    # --- TOTP 2FA ---
    totp_secret = models.CharField(max_length=255, null=True, blank=True)
    totp_enabled = models.BooleanField(default=False)

    # --- quota ---
    storage_quota_mb = models.IntegerField(default=1024)
    max_events = models.IntegerField(default=10000)
    current_storage_bytes = models.BigIntegerField(default=0)
    current_event_count = models.IntegerField(default=0)

    # --- notifications ---
    notification_email = models.CharField(max_length=255, null=True, blank=True)
    notification_signal_number = models.CharField(max_length=20, null=True, blank=True)
    pushover_user_key = models.CharField(max_length=50, null=True, blank=True)
    pushover_app_token = models.CharField(max_length=50, null=True, blank=True)
    notifications_enabled = models.BooleanField(default=True)

    # --- timestamps ---
    created_at = models.DateTimeField()
    updated_at = models.DateTimeField(null=True, blank=True)

    # Disable AbstractBaseUser's last_login field — the FastAPI-managed 'users'
    # table has no such column. Setting to None prevents Django from trying to
    # write it on login (update_last_login signal becomes a no-op).
    last_login = None

    # --- AbstractBaseUser config ---
    USERNAME_FIELD = "username"
    REQUIRED_FIELDS = []

    objects = HavenUserManager()

    class Meta:
        managed = False
        db_table = "users"

    # ------------------------------------------------------------------
    # Password compatibility shim
    # AbstractBaseUser stores its hash in 'password'; we use 'password_hash'.
    # Override the property so Django's session auth (get_user / authenticate)
    # can call user.password without error.
    # ------------------------------------------------------------------

    @property  # type: ignore[override]
    def password(self) -> str:  # noqa: D102
        return self.password_hash

    @password.setter
    def password(self, value: str) -> None:  # noqa: D102
        self.password_hash = value

    # ------------------------------------------------------------------
    # Django admin / permission compatibility
    # ------------------------------------------------------------------

    @property
    def is_staff(self) -> bool:
        """Maps to is_admin for Django admin compatibility."""
        return self.is_admin

    def has_perm(self, perm, obj=None) -> bool:  # noqa: ANN001
        return self.is_admin

    def has_module_perms(self, app_label) -> bool:  # noqa: ANN001
        return self.is_admin

    def __str__(self) -> str:
        return self.username
