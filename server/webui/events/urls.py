"""Event URL patterns (plan 06-04 / 08-02)."""

from django.urls import path

from . import views

app_name = "events"

urlpatterns = [
    path("", views.event_list, name="list"),
    path("<int:event_id>/", views.event_detail, name="detail"),
    path("<int:event_id>/video/", views.serve_video, name="video"),
    path("<int:event_id>/delete/", views.event_delete, name="delete"),
    path("bulk-archive/", views.bulk_archive, name="bulk_archive"),
    path("bulk-unarchive/", views.bulk_unarchive, name="bulk_unarchive"),
    path("bulk-delete/", views.bulk_delete, name="bulk_delete"),
]
