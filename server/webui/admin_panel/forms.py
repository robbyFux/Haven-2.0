"""
Forms for the Haven admin panel (plan 06-05).
"""

from django import forms


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
