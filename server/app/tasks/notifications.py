"""
Notification Celery task.

This module is a stub that will be implemented in plan 05-07.
The events router (plan 05-04) imports send_notification_task from here
so the import resolves at startup. It is enqueued immediately on upload
when AI_BACKEND == "none".
"""

from app.celery_app import celery_app


@celery_app.task(name="app.tasks.notifications.send_notification_task")
def send_notification_task(event_id: int) -> None:
    """
    Placeholder: Send a notification for an uploaded event.

    Will be implemented in plan 05-07 (notification worker).

    @param event_id: database ID of the Event to notify about
    """
    pass  # Stub: implemented in plan 05-07
