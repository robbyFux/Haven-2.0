"""
Alembic async migration environment.

Uses the async engine from app.database so migrations run through the same
asyncpg connection pool as the application. All model modules are imported
before target_metadata is read so that autogenerate can detect all tables.
"""

import asyncio
from logging.config import fileConfig

from alembic import context
from sqlalchemy import pool
from sqlalchemy.engine import Connection
from sqlalchemy.ext.asyncio import async_engine_from_config

# Load app config and models before any Alembic operations
from app.config import settings  # noqa: F401

# Import all models so Base.metadata is fully populated
import app.models  # noqa: F401
from app.models.base import Base

# Alembic Config object — gives access to alembic.ini values
config = context.config

# Override the placeholder sqlalchemy.url with the real DATABASE_URL from settings
config.set_main_option("sqlalchemy.url", settings.DATABASE_URL)

# Set up Python logging from alembic.ini
if config.config_file_name is not None:
    fileConfig(config.config_file_name)

# This is the metadata object all migrations will be diffed against
target_metadata = Base.metadata


def run_migrations_offline() -> None:
    """
    Run migrations in 'offline' mode.

    Configures the context with just a URL, not an Engine.
    Migrations are emitted as SQL to stdout, not executed.
    """
    url = config.get_main_option("sqlalchemy.url")
    context.configure(
        url=url,
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
    )

    with context.begin_transaction():
        context.run_migrations()


def do_run_migrations(connection: Connection) -> None:
    """Run migrations using a synchronous connection (called via run_sync)."""
    context.configure(connection=connection, target_metadata=target_metadata)
    with context.begin_transaction():
        context.run_migrations()


async def run_async_migrations() -> None:
    """
    Run migrations in 'online' async mode.

    Creates an async engine, connects, and delegates to the synchronous
    do_run_migrations() via connection.run_sync().
    """
    connectable = async_engine_from_config(
        config.get_section(config.config_ini_section, {}),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )

    async with connectable.connect() as connection:
        await connection.run_sync(do_run_migrations)

    await connectable.dispose()


def run_migrations_online() -> None:
    """Entry point for online migrations — runs the async function via asyncio."""
    asyncio.run(run_async_migrations())


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
