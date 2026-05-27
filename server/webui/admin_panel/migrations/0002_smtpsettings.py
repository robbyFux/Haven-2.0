"""
Migration to add the SMTPSettings singleton table.

Stores outgoing mail server configuration (host, port, credentials, TLS flag).
The smtp_password_encrypted field stores Fernet ciphertext — never plaintext.
"""

import django.utils.timezone
from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("admin_panel", "0001_initial"),
    ]

    operations = [
        migrations.CreateModel(
            name="SMTPSettings",
            fields=[
                (
                    "id",
                    models.BigAutoField(
                        auto_created=True,
                        primary_key=True,
                        serialize=False,
                        verbose_name="ID",
                    ),
                ),
                (
                    "smtp_host",
                    models.CharField(blank=True, default="", max_length=255),
                ),
                (
                    "smtp_port",
                    models.IntegerField(default=587),
                ),
                (
                    "smtp_user",
                    models.CharField(blank=True, default="", max_length=255),
                ),
                (
                    "smtp_password_encrypted",
                    models.TextField(blank=True, default=""),
                ),
                (
                    "smtp_from",
                    models.CharField(blank=True, default="", max_length=255),
                ),
                (
                    "use_tls",
                    models.BooleanField(default=True),
                ),
                (
                    "updated_at",
                    models.DateTimeField(auto_now=True),
                ),
            ],
            options={
                "verbose_name": "SMTP Settings",
                "verbose_name_plural": "SMTP Settings",
            },
        ),
    ]
