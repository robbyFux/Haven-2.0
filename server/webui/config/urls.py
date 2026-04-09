"""
Haven Web UI URL configuration.

Root "/" redirects to /events/ (login_required on that view bounces
unauthenticated users to LOGIN_URL = /accounts/login/).
"""

from django.urls import include, path
from django.views.generic import RedirectView

urlpatterns = [
    path("", RedirectView.as_view(url="/events/", permanent=False)),
    path("accounts/", include("accounts.urls")),
    path("devices/", include("devices.urls")),
    path("events/", include("events.urls")),
    path("admin/", include("admin_panel.urls")),
    path("notifications/", include("notifications.urls")),
]
