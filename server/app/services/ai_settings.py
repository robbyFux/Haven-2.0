"""
AI settings bridge between Django WebUI and FastAPI worker.

The WebUI stores AI configuration in the Django-managed table
`admin_panel_aisettings` (singleton row, pk=1).  FastAPI/Celery need to read
this at runtime so that changes made through the admin panel take effect on
the next event upload without a server restart.

Public API
----------
get_ai_backend()          — async, uses an AsyncSession (for FastAPI/router use)
get_ai_backend_sync()     — sync wrapper using asyncio.run() (for Celery tasks)

Both fall back to settings.AI_BACKEND (env var) when the DB row is absent
(first-run before the admin has saved settings, or if the table hasn't been
created yet by Django migrations).
"""

import asyncio
import logging

from sqlalchemy import text

from app.config import settings
from app.database import AsyncSessionLocal

logger = logging.getLogger(__name__)

# Django table/column names — must match admin_panel/models.py
_TABLE = "admin_panel_aisettings"
_QUERY = text(f"SELECT ai_backend FROM {_TABLE} WHERE id = 1")  # noqa: S608


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

        if row is not None:
            return str(row[0])
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
    Synchronous wrapper around get_ai_backend() for use in Celery tasks.

    Celery workers are synchronous by default; this spins a temporary event
    loop to resolve the async query.

    @return: one of "none", "tflite", "openrouter"
    """
    return asyncio.run(get_ai_backend())


def get_openrouter_settings_sync() -> tuple[str, str]:
    """
    Synchronous wrapper around get_openrouter_settings() for use in Celery tasks.

    @return: (api_key, model_name)
    """
    return asyncio.run(get_openrouter_settings())
