"""Account URL patterns — login, register, logout, 2FA, profile, password management."""

from django.urls import path

from . import views

app_name = "accounts"

urlpatterns = [
    path("login/", views.login_view, name="login"),
    path("register/", views.register_view, name="register"),
    path("logout/", views.logout_view, name="logout"),
    path("totp-verify/", views.totp_verify_view, name="totp_verify"),
    path("2fa-setup/", views.totp_setup_view, name="totp_setup"),
    path("profile/", views.profile_view, name="profile"),
    path("change-password/", views.change_password_view, name="change_password"),
    path("delete-account/", views.delete_account_view, name="delete_account"),
]
