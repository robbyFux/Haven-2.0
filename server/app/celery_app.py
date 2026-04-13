"""
Celery application instance configuration.

Workers are started with:
    celery -A app.celery_app worker --loglevel=info
"""

import logging
import logging.config

from celery import Celery
from celery.signals import after_setup_logger, after_setup_task_logger

from app.config import settings


def _configure_logging(**kwargs):
    """Set app.* loggers to DEBUG so pipeline steps appear in `docker compose logs worker`."""
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
    })


after_setup_logger.connect(_configure_logging)
after_setup_task_logger.connect(_configure_logging)

celery_app = Celery(
    "haven",
    broker=settings.REDIS_URL,
    backend=settings.REDIS_URL,
    include=["app.tasks.analysis", "app.tasks.notifications"],
)

celery_app.conf.update(
    task_serializer="json",
    accept_content=["json"],
    result_serializer="json",
    timezone="UTC",
    enable_utc=True,
)

# In test environments (no Redis available), run tasks synchronously in-process.
# Set CELERY_TASK_ALWAYS_EAGER=true in the environment to enable this mode.
import os as _os
if _os.environ.get("CELERY_TASK_ALWAYS_EAGER", "").lower() in ("1", "true", "yes"):
    celery_app.conf.update(
        task_always_eager=True,
        task_eager_propagates=True,
    )
