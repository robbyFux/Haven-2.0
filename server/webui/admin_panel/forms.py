"""
Forms for the Haven admin panel (plan 06-05, 08-03).
"""

from django import forms


AI_BACKEND_CHOICES = [
    ("none", "None — analysis disabled"),
    ("tflite", "TFLite — local on-device (EfficientDet Lite 0)"),
    ("openrouter", "OpenRouter — cloud vision API"),
]


class AISettingsForm(forms.Form):
    """
    Form for configuring the AI analysis backend.

    Changes are persisted to the AISettings singleton row in the database.
    Superusers only.
    """

    ai_backend = forms.ChoiceField(
        choices=AI_BACKEND_CHOICES,
        label="AI Backend",
        widget=forms.Select(
            attrs={
                "class": "w-full bg-gray-700 border border-gray-600 rounded px-3 py-2 text-gray-100 focus:outline-none focus:border-teal-500",
            }
        ),
    )
    openrouter_api_key = forms.CharField(
        max_length=255,
        required=False,
        label="OpenRouter API Key",
        widget=forms.TextInput(
            attrs={
                "class": "w-full bg-gray-700 border border-gray-600 rounded px-3 py-2 text-gray-100 focus:outline-none focus:border-teal-500",
                "placeholder": "sk-or-...",
                "autocomplete": "off",
            }
        ),
    )
    openrouter_model = forms.CharField(
        max_length=120,
        required=False,
        label="OpenRouter Model",
        widget=forms.TextInput(
            attrs={
                "class": "w-full bg-gray-700 border border-gray-600 rounded px-3 py-2 text-gray-100 focus:outline-none focus:border-teal-500",
                "placeholder": "google/gemini-flash-1.5",
            }
        ),
    )

    def clean(self):
        cleaned = super().clean()
        backend = cleaned.get("ai_backend")
        if backend == "openrouter" and not cleaned.get("openrouter_api_key"):
            self.add_error("openrouter_api_key", "API key is required when using OpenRouter.")
        return cleaned


class QuotaEditForm(forms.Form):
    """
    Form for editing a user's storage quota and max event count.

    Both fields must be non-negative integers.
    """

    storage_quota_mb = forms.IntegerField(
        min_value=0,
        label="Storage Quota (MB)",
        widget=forms.NumberInput(
            attrs={
                "class": "w-full bg-gray-700 border border-gray-600 rounded px-3 py-2 text-gray-100 focus:outline-none focus:border-teal-500",
                "min": "0",
            }
        ),
    )
    max_events = forms.IntegerField(
        min_value=0,
        label="Max Events",
        widget=forms.NumberInput(
            attrs={
                "class": "w-full bg-gray-700 border border-gray-600 rounded px-3 py-2 text-gray-100 focus:outline-none focus:border-teal-500",
                "min": "0",
            }
        ),
    )


# Shared Tailwind input class used across admin panel forms.
_INPUT_CLASS = (
    "w-full bg-gray-700 border border-gray-600 rounded px-3 py-2 "
    "text-gray-100 focus:outline-none focus:border-teal-500"
)


class SmtpSettingsForm(forms.Form):
    """
    Form for configuring the outgoing mail server (SMTP).

    Changes are persisted to the SMTPSettings singleton row in the database.
    The smtp_password field is write-only: leaving it blank preserves the existing
    encrypted password. Superusers only.
    """

    smtp_host = forms.CharField(
        max_length=255,
        required=False,
        label="SMTP Host",
        widget=forms.TextInput(
            attrs={
                "class": _INPUT_CLASS,
                "placeholder": "smtp.example.com",
            }
        ),
    )
    smtp_port = forms.IntegerField(
        min_value=1,
        max_value=65535,
        initial=587,
        label="Port",
        widget=forms.NumberInput(
            attrs={
                "class": _INPUT_CLASS,
                "min": "1",
                "max": "65535",
            }
        ),
    )
    smtp_user = forms.CharField(
        max_length=255,
        required=False,
        label="Username",
        widget=forms.TextInput(
            attrs={
                "class": _INPUT_CLASS,
                "autocomplete": "off",
            }
        ),
    )
    smtp_password = forms.CharField(
        required=False,
        label="Password",
        widget=forms.PasswordInput(
            attrs={
                "class": _INPUT_CLASS,
                "autocomplete": "new-password",
                "placeholder": "Leave blank to keep current",
            }
        ),
    )
    smtp_from = forms.CharField(
        max_length=255,
        required=False,
        label="From Address",
        widget=forms.TextInput(
            attrs={
                "class": _INPUT_CLASS,
                "placeholder": "haven@example.com",
            }
        ),
    )
    tls_mode = forms.ChoiceField(
        choices=[
            ("none", "None (plain SMTP)"),
            ("starttls", "STARTTLS (port 587)"),
            ("ssl", "SSL/TLS (port 465)"),
        ],
        initial="starttls",
        label="Security",
        widget=forms.Select(
            attrs={
                "class": _INPUT_CLASS,
            }
        ),
    )

    def clean_smtp_port(self):
        """Validate that port is in the valid TCP range."""
        port = self.cleaned_data.get("smtp_port")
        if port is not None and not (1 <= port <= 65535):
            raise forms.ValidationError("Port must be between 1 and 65535.")
        return port
