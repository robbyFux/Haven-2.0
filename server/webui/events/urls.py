"""Event URL patterns (plan 06-04)."""

from django.urls import path

from . import views

app_name = "events"

urlpatterns = [
    path("", views.event_list, name="list"),
    path("<int:event_id>/", views.event_detail, name="detail"),
    path("<int:event_id>/video/", views.serve_video, name="video"),
    path("<int:event_id>/delete/", views.event_delete, name="delete"),
]
