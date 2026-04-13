"""
FastAPI application factory.

Creates the app with lifespan context manager, includes all routers, and
configures OpenAPI metadata.

Usage:
    uvicorn app.main:app --host 0.0.0.0 --port 8000
"""

import logging
import logging.config
from contextlib import asynccontextmanager
from typing import AsyncGenerator

from fastapi import FastAPI

# Configure structured DEBUG logging for the server.app namespace so all
# pipeline log statements are visible in `docker compose logs app`.
logging.config.dictConfig({
    "version": 1,
    "disable_existing_loggers": False,
    "formatters": {
        "default": {
            "format": "%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        },
    },
    "handlers": {
        "console": {
            "class": "logging.StreamHandler",
            "formatter": "default",
            "stream": "ext://sys.stdout",
        },
    },
    "loggers": {
        "app": {
            "handlers": ["console"],
            "level": "DEBUG",
            "propagate": False,
        },
    },
    "root": {
        "handlers": ["console"],
        "level": "INFO",
    },
})

from app.routers import admin, auth, devices, events, health, notifications


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Application startup and shutdown lifecycle."""
    # Startup: initialize resources (DB connections, model loading, etc.)
    # Resources are lazy-initialized on first use via the async engine.
    yield
    # Shutdown: release resources
    from app.database import engine
    await engine.dispose()


def create_app() -> FastAPI:
    """
    Application factory.

    Constructs and configures the FastAPI instance. Called once at module level
    for normal operation and once per test session in tests/conftest.py.
    """
    application = FastAPI(
        title="Haven Cloud",
        description="Self-hosted cloud backend for the Haven 2.0 security app",
        version="1.0.0",
        lifespan=lifespan,
        docs_url="/docs",
        redoc_url="/redoc",
    )

    # Health endpoint — mounted at root level (no /api/v1/ prefix)
    application.include_router(health.router)

    # API v1 routers
    application.include_router(auth.router, prefix="/api/v1/auth", tags=["auth"])
    application.include_router(devices.router, prefix="/api/v1/devices", tags=["devices"])
    application.include_router(admin.router, prefix="/api/v1/admin", tags=["admin"])
    application.include_router(events.router, prefix="/api/v1")
    application.include_router(notifications.router, prefix="/api/v1/notifications", tags=["notifications"])

    return application


app = create_app()
