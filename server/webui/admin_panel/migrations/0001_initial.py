"""
Initial migration for admin_panel app.

Creates the admin_panel_aisettings singleton table that stores the AI
analysis backend configuration (ai_backend, openrouter_api_key,
openrouter_model).
"""

import django.utils.timezone
from django.db import migrations, models


class Migration(migrations.Migration):

    initial = True

    dependencies = []

    operations = [
        migrations.CreateModel(
            name="AISettings",
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
                    "ai_backend",
                    models.CharField(
                        choices=[
                            ("none", "None"),
                            ("tflite", "TFLite"),
                            ("openrouter", "OpenRouter"),
                        ],
                        default="none",
                        max_length=20,
                    ),
                ),
                (
                    "openrouter_api_key",
                    models.CharField(blank=True, default="", max_length=255),
                ),
                (
                    "openrouter_model",
                    models.CharField(
                        blank=True,
                        default="google/gemini-flash-1.5",
                        max_length=120,
                    ),
                ),
                (
                    "updated_at",
                    models.DateTimeField(auto_now=True),
                ),
            ],
            options={
                "verbose_name": "AI Settings",
                "verbose_name_plural": "AI Settings",
            },
        ),
    ]
