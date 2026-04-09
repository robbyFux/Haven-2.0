"""
Forms for the Device management views.
"""

from django import forms


class DeviceCreateForm(forms.Form):
    """Form for creating a new device with a custom name."""

    name = forms.CharField(
        max_length=100,
        required=True,
        label="Device Name",
        widget=forms.TextInput(attrs={
            "placeholder": "e.g. Bedroom Camera",
            "class": (
                "block w-full rounded-md bg-gray-800 border border-gray-600 "
                "text-gray-100 placeholder-gray-500 px-3 py-2 text-sm "
                "focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-teal-500"
            ),
        }),
    )
