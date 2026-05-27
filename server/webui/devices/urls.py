"""Device URL patterns."""

from django.urls import path

from . import views

app_name = "devices"

urlpatterns = [
    path("", views.device_list, name="device_list"),
    path("create/", views.device_create, name="device_create"),
    path("<int:device_id>/revoke/", views.device_revoke, name="device_revoke"),
    path("<int:device_id>/delete/", views.device_delete, name="device_delete"),
]
