"""
Admin panel URL patterns (implemented in plan 06-05).

All views require is_admin=True (enforced by @admin_required decorator).
"""

from django.urls import path

from admin_panel import views

app_name = "admin_panel"

urlpatterns = [
    path("", views.dashboard, name="dashboard"),
    path("users/<int:user_id>/", views.user_detail, name="user_detail"),
    path("users/<int:user_id>/quota/", views.quota_edit, name="quota_edit"),
    path("users/<int:user_id>/toggle-active/", views.toggle_active, name="toggle_active"),
    path("ai-settings/", views.ai_settings, name="ai_settings"),
    path("settings/", views.smtp_settings, name="smtp_settings"),
    path("settings/test/", views.smtp_test, name="smtp_test"),
]
