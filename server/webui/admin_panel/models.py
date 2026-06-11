"""
Admin-panel-owned Django models.

AISettings is a singleton row that stores the AI analysis backend configuration.
SMTPSettings is a singleton row that stores the outgoing mail server configuration.

Both are managed by Django (not by FastAPI/Alembic) and live in the same database
under their respective table names.

The FastAPI Celery worker reads AI config from environment variables at startup.
The webui writes to these tables and the worker reads AI config via a thin helper in
app/services/ai_settings.py — allowing runtime reconfiguration without a server
restart.

Usage::

    settings = AISettings.get()
    settings.ai_backend = "openrouter"
    settings.save()

    smtp = SMTPSettings.get()
    smtp.smtp_host = "smtp.example.com"
    smtp.save()
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


class SMTPSettings(models.Model):
    """
    Singleton model for outgoing mail server configuration.

    Only one row exists (id=1). Use SMTPSettings.get() to retrieve or
    initialise it with defaults. Writable only by admin users via the
    admin panel SMTP settings view.

    The SMTP password is stored Fernet-encrypted using SHA-256(settings.SECRET_KEY)
    as the key material — see _get_fernet() in admin_panel/views.py.
    The smtp_password_encrypted field always stores ciphertext; plaintext is
    never persisted. tls_mode controls the encryption handshake: 'none' for
    plain SMTP, 'starttls' for STARTTLS on port 587, 'ssl' for implicit
    TLS on port 465.
    """

    smtp_host = models.CharField(max_length=255, blank=True, default="")
    smtp_port = models.IntegerField(default=587)
    smtp_user = models.CharField(max_length=255, blank=True, default="")
    smtp_password_encrypted = models.TextField(blank=True, default="")
    smtp_from = models.CharField(max_length=255, blank=True, default="")

    TLS_NONE = "none"
    TLS_STARTTLS = "starttls"
    TLS_SSL = "ssl"
    TLS_MODE_CHOICES = [
        (TLS_NONE, "None (plain SMTP)"),
        (TLS_STARTTLS, "STARTTLS (port 587)"),
        (TLS_SSL, "SSL/TLS (port 465)"),
    ]
    tls_mode = models.CharField(
        max_length=10,
        choices=TLS_MODE_CHOICES,
        default=TLS_STARTTLS,
    )

    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = "SMTP Settings"
        verbose_name_plural = "SMTP Settings"

    @classmethod
    def get(cls) -> "SMTPSettings":
        """Return the singleton row, creating it with defaults if absent."""
        obj, _ = cls.objects.get_or_create(pk=1)
        return obj

    def __str__(self) -> str:
        return f"SMTPSettings(host={self.smtp_host}, port={self.smtp_port})"
