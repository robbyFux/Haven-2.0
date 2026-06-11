"""
Replace the boolean use_tls field on SMTPSettings with a tls_mode CharField.

Existing rows with use_tls=True are migrated to 'starttls' (the most common
case for port 587). Rows with use_tls=False become 'none'.
"""

from django.db import migrations, models


def migrate_use_tls_to_tls_mode(apps, schema_editor):
    SMTPSettings = apps.get_model("admin_panel", "SMTPSettings")
    for row in SMTPSettings.objects.all():
        row.tls_mode = "starttls" if row.use_tls else "none"
        row.save(update_fields=["tls_mode"])


class Migration(migrations.Migration):

    dependencies = [
        ("admin_panel", "0002_smtpsettings"),
    ]

    operations = [
        migrations.AddField(
            model_name="smtpsettings",
            name="tls_mode",
            field=models.CharField(
                choices=[
                    ("none", "None (plain SMTP)"),
                    ("starttls", "STARTTLS (port 587)"),
                    ("ssl", "SSL/TLS (port 465)"),
                ],
                default="starttls",
                max_length=10,
            ),
        ),
        migrations.RunPython(migrate_use_tls_to_tls_mode, migrations.RunPython.noop),
        migrations.RemoveField(
            model_name="smtpsettings",
            name="use_tls",
        ),
    ]
