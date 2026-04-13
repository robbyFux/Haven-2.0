"""
Admin-panel-owned Django models.

AISettings is a singleton row that stores the AI analysis backend configuration.
It is managed by Django (not by FastAPI/Alembic) and lives in the same database
under the table name `admin_panel_aisettings`.

The FastAPI Celery worker reads AI config from environment variables at startup.
The webui writes to this table and the worker reads it via a thin helper in
app/services/ai_settings.py — allowing runtime reconfiguration without a server
restart.

Usage::

    settings = AISettings.get()
    settings.ai_backend = "openrouter"
    settings.save()
"""

from django.db import models


class AISettings(models.Model):
    """
    Singleton model for AI analysis backend configuration.

    Only one row exists (id=1). Use AISettings.get() to retrieve or
    initialise it with defaults. Writable only by admin users via the
    admin panel AI settings view.
    """

    AI_BACKEND_CHOICES = [
        ("none", "None"),
        ("tflite", "TFLite"),
        ("openrouter", "OpenRouter"),
    ]

    ai_backend = models.CharField(
        max_length=20,
        choices=AI_BACKEND_CHOICES,
        default="none",
    )
    openrouter_api_key = models.CharField(max_length=255, blank=True, default="")
    openrouter_model = models.CharField(
        max_length=120,
        blank=True,
        default="google/gemini-flash-1.5",
    )
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = "AI Settings"
        verbose_name_plural = "AI Settings"

    @classmethod
    def get(cls) -> "AISettings":
        """Return the singleton row, creating it with defaults if absent."""
        obj, _ = cls.objects.get_or_create(pk=1)
        return obj

    def __str__(self) -> str:
        return f"AISettings(backend={self.ai_backend})"
