"""Notification URL patterns."""

from django.urls import path

from . import views

app_name = "notifications"

urlpatterns = [
    path("", views.notification_settings, name="settings"),
    path("email-test/", views.email_test, name="email_test"),
]
