"""
Celery application instance configuration.

Workers are started with:
    celery -A app.celery_app worker --loglevel=info
"""

from celery import Celery

from app.config import settings

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
