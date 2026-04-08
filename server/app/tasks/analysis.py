"""
AI analysis Celery task.

This module is a stub that will be implemented in plan 05-06.
The events router (plan 05-04) imports analyze_event_task from here
so the import resolves at startup, but the task is only enqueued
when AI_BACKEND != "none".
"""

from app.celery_app import celery_app


@celery_app.task(name="app.tasks.analysis.analyze_event_task")
def analyze_event_task(event_id: int, media_path: str | None, encryption_key_hex: str | None) -> None:
    """
    Placeholder: Run AI analysis on an uploaded event media file.

    Will be implemented in plan 05-06 (AI analysis worker).

    @param event_id: database ID of the Event to analyze
    @param media_path: MEDIA_ROOT-relative path to the media file, or None
    @param encryption_key_hex: hex-encoded AES key if file is encrypted, else None
    """
    pass  # Stub: implemented in plan 05-06
