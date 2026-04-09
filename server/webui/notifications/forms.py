"""
Notification settings form for Haven Web UI.

Provides the NotificationSettingsForm used by the notifications settings view
to allow users to configure their email, Signal, and Pushover channels and
enable/disable notifications globally.
"""

from django import forms
from django.core.validators import validate_email


class NotificationSettingsForm(forms.Form):
    """
    Form for editing user notification channel settings.

    All fields are optional — users may configure only the channels they use.
    Email and Signal number are validated for format if non-empty.
    """

    notifications_enabled = forms.BooleanField(
        required=False,
        label="Enable Notifications",
    )
    notification_email = forms.CharField(
        max_length=255,
        required=False,
        label="Email Address",
        widget=forms.EmailInput(attrs={"placeholder": "you@example.com"}),
    )
    notification_signal_number = forms.CharField(
        max_length=20,
        required=False,
        label="Signal Number",
        widget=forms.TextInput(attrs={"placeholder": "+49..."}),
    )
    pushover_user_key = forms.CharField(
        max_length=50,
        required=False,
        label="Pushover User Key",
        widget=forms.TextInput(attrs={"placeholder": "Pushover user key"}),
    )

    def clean_notification_email(self) -> str:
        """Validate email format if non-empty."""
        email = self.cleaned_data.get("notification_email", "").strip()
        if email:
            validate_email(email)
        return email

    def clean_notification_signal_number(self) -> str:
        """Validate Signal number starts with '+' (E.164 format) if non-empty."""
        number = self.cleaned_data.get("notification_signal_number", "").strip()
        if number and not number.startswith("+"):
            raise forms.ValidationError(
                "Signal number must be in E.164 format (e.g. +49123456789)."
            )
        return number
