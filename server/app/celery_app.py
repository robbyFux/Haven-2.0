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
