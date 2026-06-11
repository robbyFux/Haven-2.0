"""
SMTP settings bridge between Django WebUI and FastAPI worker.

The WebUI stores SMTP configuration in the Django-managed table
`admin_panel_smtpsettings` (singleton row, pk=1). FastAPI/Celery need to
read this at runtime so that changes made through the admin panel take
effect without a server restart.

The password is stored Fernet-encrypted. The Fernet key is derived via
SHA-256(SECRET_KEY) — identical to admin_panel/views.py _get_fernet().

Public API
----------
get_smtp_settings()       — async, uses AsyncSession (for FastAPI routers)
get_smtp_settings_sync()  — sync, uses psycopg2 (for Celery workers)

Both return a SmtpConfig dataclass. Fall back to env vars when the DB row
is absent or the table has not been created yet by Django migrations.
"""

import base64
import hashlib
import logging
import re
from dataclasses import dataclass

from sqlalchemy import text

from app.config import settings
from app.database import AsyncSessionLocal

logger = logging.getLogger(__name__)

_TABLE = "admin_panel_smtpsettings"
_QUERY = text(
    f"SELECT smtp_host, smtp_port, smtp_user, smtp_password_encrypted, smtp_from, tls_mode "  # noqa: S608
    f"FROM {_TABLE} WHERE id = 1"
)


@dataclass
class SmtpConfig:
    host: str
    port: int
    user: str
    password: str
    from_addr: str
    tls_mode: str  # "none" | "starttls" | "ssl"


def _decrypt_password(ciphertext: str) -> str:
    """Decrypt a Fernet-encrypted SMTP password using SHA-256(SECRET_KEY)."""
    if not ciphertext:
        return ""
    try:
        from cryptography.fernet import Fernet  # noqa: PLC0415

        key_bytes = hashlib.sha256(settings.SECRET_KEY.encode()).digest()
        return Fernet(base64.urlsafe_b64encode(key_bytes)).decrypt(ciphertext.encode()).decode()
    except Exception as exc:  # noqa: BLE001
        logger.error("smtp_settings: failed to decrypt SMTP password: %s", exc)
        return ""


def _row_to_config(row) -> SmtpConfig:
    smtp_host, smtp_port, smtp_user, smtp_password_encrypted, smtp_from, tls_mode = row
    return SmtpConfig(
        host=smtp_host or "",
        port=int(smtp_port or 587),
        user=smtp_user or "",
        password=_decrypt_password(smtp_password_encrypted or ""),
        from_addr=smtp_from or "",
        tls_mode=tls_mode or "starttls",
    )


def _env_fallback() -> SmtpConfig:
    return SmtpConfig(
        host=settings.SMTP_HOST,
        port=settings.SMTP_PORT,
        user=settings.SMTP_USER,
        password=settings.SMTP_PASSWORD,
        from_addr=settings.SMTP_FROM,
        tls_mode="starttls",
    )


def _sync_dsn() -> str:
    return re.sub(r"\+[^:]+://", "://", settings.DATABASE_URL, count=1)


async def get_smtp_settings(session=None) -> SmtpConfig:
    """
    Return SMTP configuration from the admin_panel_smtpsettings singleton row.

    Falls back to env vars when the row is absent or any DB error occurs.
    """
    try:
        if session is not None:
            row = (await session.execute(_QUERY)).fetchone()
        else:
            async with AsyncSessionLocal() as s:
                row = (await s.execute(_QUERY)).fetchone()
        if row is not None:
            return _row_to_config(row)
        logger.warning("get_smtp_settings: no row in %s, falling back to env", _TABLE)
    except Exception as exc:  # noqa: BLE001
        logger.warning(
            "smtp_settings.get_smtp_settings: could not read %s, falling back to env: %s",
            _TABLE,
            exc,
        )
    return _env_fallback()


def get_smtp_settings_sync() -> SmtpConfig:
    """
    Read SMTP configuration from the DB using a synchronous psycopg2 connection.

    Safe to call from Celery prefork workers where asyncpg's connection pool
    is bound to the parent process's event loop.
    Falls back to env vars on error or missing row.
    """
    try:
        import psycopg2  # noqa: PLC0415

        with psycopg2.connect(_sync_dsn()) as conn:
            with conn.cursor() as cur:
                cur.execute(
                    f"SELECT smtp_host, smtp_port, smtp_user, smtp_password_encrypted, smtp_from, tls_mode "  # noqa: S608
                    f"FROM {_TABLE} WHERE id = 1"
                )
                row = cur.fetchone()
        if row is not None:
            return _row_to_config(row)
        logger.warning("get_smtp_settings_sync: no row in %s, falling back to env", _TABLE)
    except Exception as exc:  # noqa: BLE001
        logger.warning(
            "smtp_settings.get_smtp_settings_sync: could not read %s, falling back to env: %s",
            _TABLE,
            exc,
        )
    return _env_fallback()
