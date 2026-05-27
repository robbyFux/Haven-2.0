"""
Unmanaged Django models mapping to the existing 'events', 'event_triggers',
and 'analysis_results' PostgreSQL tables.

managed=False on all models: DDL is owned by FastAPI/Alembic.
All ForeignKey fields use db_constraint=False to avoid duplicate DDL constraints.
"""

from django.db import models


class Event(models.Model):
    """
    A security event uploaded by a Haven device.

    Severity values: LOW / MEDIUM / HIGH / CRITICAL.
    event_type mirrors TriggerType names from the Android app.
    """

    id = models.AutoField(primary_key=True)

    user = models.ForeignKey(
        "accounts.HavenUser",
        on_delete=models.CASCADE,
        db_constraint=False,
        related_name="events",
    )
    device = models.ForeignKey(
        "devices.Device",
        on_delete=models.CASCADE,
        db_constraint=False,
        related_name="events",
    )

    event_type = models.CharField(max_length=50)
    severity = models.CharField(max_length=20)
    timestamp = models.DateTimeField()

    sensor_value = models.FloatField(null=True, blank=True)

    media_path = models.CharField(max_length=500, null=True, blank=True)
    media_size_bytes = models.BigIntegerField(null=True, blank=True)
    is_encrypted = models.BooleanField(default=False)
    is_archived = models.BooleanField(default=False)

    # --- timestamps ---
    created_at = models.DateTimeField()
    updated_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        managed = False
        db_table = "events"
        ordering = ["-timestamp"]

    def __str__(self) -> str:
        return f"Event({self.event_type}, {self.severity}, {self.timestamp})"


class EventTrigger(models.Model):
    """
    A single sensor trigger that contributed to an Event.

    Multiple triggers may fire for the same event (e.g. camera + accelerometer).
    """

    id = models.AutoField(primary_key=True)

    event = models.ForeignKey(
        "events.Event",
        on_delete=models.CASCADE,
        db_constraint=False,
        related_name="triggers",
    )

    trigger_type = models.CharField(max_length=50)
    sensor_value = models.FloatField(null=True, blank=True)
    media_path = models.CharField(max_length=500, null=True, blank=True)

    # --- timestamps ---
    created_at = models.DateTimeField()
    updated_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        managed = False
        db_table = "event_triggers"

    def __str__(self) -> str:
        return f"EventTrigger({self.trigger_type}, event_id={self.event_id})"


class AnalysisResult(models.Model):
    """
    AI analysis result for an Event, written by the Celery AI worker.

    One-to-one with Event. backend: 'tflite' or 'openrouter'.
    labels: JSON string of detected object labels.
    """

    id = models.AutoField(primary_key=True)

    event = models.OneToOneField(
        "events.Event",
        on_delete=models.CASCADE,
        db_constraint=False,
        related_name="analysis_result",
        unique=True,
    )

    backend = models.CharField(max_length=20)
    labels = models.TextField(null=True, blank=True)
    confidence = models.FloatField(null=True, blank=True)
    description = models.TextField(null=True, blank=True)
    raw_result = models.TextField(null=True, blank=True)

    # --- timestamps ---
    created_at = models.DateTimeField()
    updated_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        managed = False
        db_table = "analysis_results"

    def __str__(self) -> str:
        return f"AnalysisResult({self.backend}, event_id={self.event_id})"
