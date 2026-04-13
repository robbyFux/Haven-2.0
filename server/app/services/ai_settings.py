"""
AI settings bridge between Django WebUI and FastAPI worker.

The WebUI stores AI configuration in the Django-managed table
`admin_panel_aisettings` (singleton row, pk=1).  FastAPI/Celery need to read
this at runtime so that changes made through the admin panel take effect on
the next event upload without a server restart.

Public API
----------
get_ai_backend()          — async, uses an AsyncSession (for FastAPI/router use)
get_ai_backend_sync()     — sync, uses psycopg2 directly (for Celery tasks)

Both fall back to settings.AI_BACKEND (env var) when the DB row is absent
(first-run before the admin has saved settings, or if the table hasn't been
created yet by Django migrations).
"""

import logging
import re

from sqlalchemy import text

from app.config import settings
from app.database import AsyncSessionLocal

logger = logging.getLogger(__name__)

# Django table/column names — must match admin_panel/models.py
_TABLE = "admin_panel_aisettings"
_QUERY = text(f"SELECT ai_backend FROM {_TABLE} WHERE id = 1")  # noqa: S608


def _sync_dsn() -> str:
    """
    Convert the async DATABASE_URL (postgresql+asyncpg://...) to a plain
    psycopg2 DSN (postgresql://...) for synchronous use in Celery workers.
    """
    url = settings.DATABASE_URL
    # Strip driver suffix: postgresql+asyncpg:// → postgresql://
    return re.sub(r"\+[^:]+://", "://", url, count=1)


async def get_ai_backend(session=None) -> str:
    """
    Return the active AI backend identifier by reading from the Django
    admin_panel_aisettings singleton row.

    Falls back to settings.AI_BACKEND (env var) when:
    - the row does not exist yet, or
    - the table has not been created by Django migrations, or
    - any other DB error occurs.

    @param session: optional existing AsyncSession; if None a new one is opened
    @return: one of "none", "tflite", "openrouter"
    """
    try:
        if session is not None:
            row = (await session.execute(_QUERY)).fetchone()
        else:
            async with AsyncSessionLocal() as s:
                row = (await s.execute(_QUERY)).fetchone()

        logger.debug("get_ai_backend: DB query result row=%r", row)
        if row is not None:
            value = str(row[0])
            logger.info("get_ai_backend: found ai_backend=%r in %s", value, _TABLE)
            return value
        else:
            logger.warning("get_ai_backend: no row found in %s (table empty or not yet created), falling back to env=%r", _TABLE, settings.AI_BACKEND)
    except Exception as exc:  # noqa: BLE001
        logger.warning(
            "ai_settings.get_ai_backend: could not read %s, falling back to env (%s): %s",
            _TABLE,
            settings.AI_BACKEND,
            exc,
        )

    return settings.AI_BACKEND


async def get_openrouter_settings(session=None) -> tuple[str, str]:
    """
    Return (openrouter_api_key, openrouter_model) from the admin_panel_aisettings row.

    Falls back to (settings.OPENROUTER_API_KEY, settings.OPENROUTER_MODEL) on
    any error or missing row.

    @return: (api_key, model_name)
    """
    _q = text(
        f"SELECT openrouter_api_key, openrouter_model FROM {_TABLE} WHERE id = 1"  # noqa: S608
    )
    try:
        if session is not None:
            row = (await session.execute(_q)).fetchone()
        else:
            async with AsyncSessionLocal() as s:
                row = (await s.execute(_q)).fetchone()

        if row is not None:
            return str(row[0]), str(row[1])
    except Exception as exc:  # noqa: BLE001
        logger.warning(
            "ai_settings.get_openrouter_settings: could not read %s, falling back to env: %s",
            _TABLE,
            exc,
        )

    return settings.OPENROUTER_API_KEY, settings.OPENROUTER_MODEL


def get_ai_backend_sync() -> str:
    """
    Read the active AI backend from the DB using a synchronous psycopg2
    connection — safe to call from Celery prefork workers where asyncpg's
    connection pool is bound to the parent process's event loop.

    Falls back to settings.AI_BACKEND on any error.

    @return: one of "none", "tflite", "openrouter"
    """
    try:
        import psycopg2  # noqa: PLC0415

        dsn = _sync_dsn()
        with psycopg2.connect(dsn) as conn:
            with conn.cursor() as cur:
                cur.execute(
                    f"SELECT ai_backend FROM {_TABLE} WHERE id = 1"  # noqa: S608
                )
                row = cur.fetchone()
        if row is not None:
            value = str(row[0])
            logger.info("get_ai_backend_sync: found ai_backend=%r in %s", value, _TABLE)
            return value
        logger.warning(
            "get_ai_backend_sync: no row found in %s, falling back to env=%r",
            _TABLE,
            settings.AI_BACKEND,
        )
    except Exception as exc:  # noqa: BLE001
        logger.warning(
            "ai_settings.get_ai_backend_sync: could not read %s, falling back to env (%s): %s",
            _TABLE,
            settings.AI_BACKEND,
            exc,
        )
    return settings.AI_BACKEND


def get_openrouter_settings_sync() -> tuple[str, str]:
    """
    Read OpenRouter credentials from the DB using a synchronous psycopg2
    connection — safe to call from Celery prefork workers.

    Falls back to (settings.OPENROUTER_API_KEY, settings.OPENROUTER_MODEL) on
    any error or missing row.

    @return: (api_key, model_name)
    """
    try:
        import psycopg2  # noqa: PLC0415

        dsn = _sync_dsn()
        with psycopg2.connect(dsn) as conn:
            with conn.cursor() as cur:
                cur.execute(
                    f"SELECT openrouter_api_key, openrouter_model FROM {_TABLE} WHERE id = 1"  # noqa: S608
                )
                row = cur.fetchone()
        if row is not None:
            return str(row[0]), str(row[1])
        logger.warning(
            "get_openrouter_settings_sync: no row found in %s, falling back to env",
            _TABLE,
        )
    except Exception as exc:  # noqa: BLE001
        logger.warning(
            "ai_settings.get_openrouter_settings_sync: could not read %s, falling back to env: %s",
            _TABLE,
            exc,
        )
    return settings.OPENROUTER_API_KEY, settings.OPENROUTER_MODEL
