"""
EventFilter: django-filter based filter set for the event list view.

Allows filtering events by date range, device, event type, and severity.
"""

import django_filters

from .models import Event


class EventFilter(django_filters.FilterSet):
    """Filter set for the Event model, used in the event list view."""

    timestamp__gte = django_filters.DateTimeFilter(
        field_name="timestamp",
        lookup_expr="gte",
        label="From",
    )
    timestamp__lte = django_filters.DateTimeFilter(
        field_name="timestamp",
        lookup_expr="lte",
        label="To",
    )
    event_type = django_filters.CharFilter(
        field_name="event_type",
        lookup_expr="iexact",
        label="Event Type",
    )
    severity = django_filters.ChoiceFilter(
        choices=[
            ("LOW", "Low"),
            ("MEDIUM", "Medium"),
            ("HIGH", "High"),
            ("CRITICAL", "Critical"),
        ],
        label="Severity",
    )
    device_id = django_filters.NumberFilter(
        field_name="device_id",
        label="Device",
    )

    class Meta:
        model = Event
        fields = ["timestamp__gte", "timestamp__lte", "event_type", "severity", "device_id"]
