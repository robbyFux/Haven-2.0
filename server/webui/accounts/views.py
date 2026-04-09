"""
Account auth views for Haven Web UI.

Implements: registration, login (with optional TOTP 2FA), TOTP setup,
profile, logout, change password, and account deletion.

All views use the custom HavenAuthBackend for password verification and
core.totp for TOTP operations. The TOTP flow is two-step:
  1. login_view verifies password → stores pending_2fa_user_id in session
  2. totp_verify_view verifies code → completes login (calls django.contrib.auth.login)
"""

import base64
import os
import secrets

from django.contrib import messages
from django.contrib.auth import authenticate
from django.contrib.auth import login as auth_login
from django.contrib.auth import logout as auth_logout
from django.contrib.auth import update_session_auth_hash
from django.contrib.auth.decorators import login_required
from django.shortcuts import redirect, render
from passlib.context import CryptContext

from .forms import (
    ChangePasswordForm,
    DeleteAccountForm,
    LoginForm,
    RegisterForm,
    TotpForm,
    TotpSetupConfirmForm,
)

_pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
_BACKEND = "core.auth_backend.HavenAuthBackend"


def register_view(request):
    """
    User registration: create account with bcrypt hash + haven_u_ user_key.

    Checks HAVEN_OPEN_REGISTRATION env var (default "true"). Returns 403 if
    registration is disabled.
    """
    if os.environ.get("HAVEN_OPEN_REGISTRATION", "true").lower() == "false":
        from django.http import HttpResponseForbidden

        return HttpResponseForbidden("Registration is disabled.")

    form = RegisterForm(request.POST or None)
    if request.method == "POST" and form.is_valid():
        from django.utils import timezone

        from accounts.models import HavenUser

        username = form.cleaned_data["username"]
        password = form.cleaned_data["password"]
        HavenUser.objects.create(
            username=username,
            password_hash=_pwd_context.hash(password),
            user_key=f"haven_u_{secrets.token_hex(32)}",
            created_at=timezone.now(),
            is_active=True,
        )
        messages.success(request, "Account created — you can now log in.")
        return redirect("accounts:login")

    return render(request, "accounts/register.html", {"form": form})


def login_view(request):
    """
    Login with username + bcrypt password.

    If TOTP is enabled: stores user.id in session and redirects to TOTP verify.
    Otherwise: completes login immediately and redirects to LOGIN_REDIRECT_URL.
    """
    form = LoginForm(request.POST or None)
    if request.method == "POST" and form.is_valid():
        username = form.cleaned_data["username"]
        password = form.cleaned_data["password"]
        user = authenticate(request, username=username, password=password)
        if user is None:
            messages.error(request, "Invalid credentials.")
            return render(request, "accounts/login.html", {"form": form})

        if user.totp_enabled:
            request.session["pending_2fa_user_id"] = user.id
            return redirect("accounts:totp_verify")

        auth_login(request, user, backend=_BACKEND)
        return redirect("events:list")

    return render(request, "accounts/login.html", {"form": form})


def totp_verify_view(request):
    """
    Second factor: verify a 6-digit TOTP code for users with 2FA enabled.

    Requires pending_2fa_user_id in session (set by login_view). Completes the
    login on success and clears the pending session key.
    """
    pending_id = request.session.get("pending_2fa_user_id")
    if pending_id is None:
        return redirect("accounts:login")

    from accounts.models import HavenUser

    try:
        user = HavenUser.objects.get(pk=pending_id)
    except HavenUser.DoesNotExist:
        return redirect("accounts:login")

    form = TotpForm(request.POST or None)
    if request.method == "POST" and form.is_valid():
        from core.totp import decrypt_totp_secret, verify_totp

        code = form.cleaned_data["code"]
        secret = decrypt_totp_secret(user.totp_secret)
        if verify_totp(secret, code):
            del request.session["pending_2fa_user_id"]
            auth_login(request, user, backend=_BACKEND)
            return redirect("events:list")
        else:
            messages.error(request, "Invalid authentication code.")

    return render(request, "accounts/totp_verify.html", {"form": form})


@login_required
def totp_setup_view(request):
    """
    2FA setup: generate QR code, confirm with valid TOTP code.

    GET: generates a random secret, stores it in session, renders QR code.
    POST: validates code, encrypts + saves secret, enables TOTP.
    """
    from core.totp import (
        encrypt_totp_secret,
        generate_totp_secret,
        get_totp_qr_png,
        get_totp_uri,
        verify_totp,
    )

    if request.method == "GET":
        secret = generate_totp_secret()
        request.session["pending_totp_secret"] = secret
        uri = get_totp_uri(secret, request.user.username)
        qr_png = get_totp_qr_png(uri)
        qr_b64 = base64.b64encode(qr_png).decode()
        return render(
            request,
            "accounts/totp_setup.html",
            {
                "form": TotpSetupConfirmForm(),
                "qr_image": qr_b64,
                "secret": secret,
            },
        )

    # POST
    form = TotpSetupConfirmForm(request.POST)
    if form.is_valid():
        secret = request.session.get("pending_totp_secret")
        if secret and verify_totp(secret, form.cleaned_data["code"]):
            user = request.user
            user.totp_secret = encrypt_totp_secret(secret)
            user.totp_enabled = True
            user.save(update_fields=["totp_secret", "totp_enabled"])
            request.session.pop("pending_totp_secret", None)
            messages.success(request, "Two-factor authentication enabled.")
            return redirect("accounts:profile")
        else:
            messages.error(request, "Invalid code — please try again.")
            # Regenerate QR to avoid stale secret
            if not secret:
                secret = generate_totp_secret()
                request.session["pending_totp_secret"] = secret
            uri = get_totp_uri(secret, request.user.username)
            qr_png = get_totp_qr_png(uri)
            qr_b64 = base64.b64encode(qr_png).decode()
            return render(
                request,
                "accounts/totp_setup.html",
                {
                    "form": form,
                    "qr_image": qr_b64,
                    "secret": secret,
                },
            )

    return render(request, "accounts/totp_setup.html", {"form": form})


@login_required
def profile_view(request):
    """Profile page: username, 2FA status, links to account actions."""
    return render(request, "accounts/profile.html", {"user": request.user})


def logout_view(request):
    """POST only: clear session and redirect to login."""
    if request.method == "POST":
        auth_logout(request)
        return redirect("accounts:login")
    return redirect("accounts:profile")


@login_required
def change_password_view(request):
    """
    Change password: verify current bcrypt hash, hash new password, keep session.

    Uses update_session_auth_hash() so the user remains logged in after the change.
    """
    form = ChangePasswordForm(request.POST or None)
    if request.method == "POST" and form.is_valid():
        user = request.user
        current = form.cleaned_data["current_password"]
        new_pw = form.cleaned_data["new_password"]

        if not _pwd_context.verify(current, user.password_hash):
            messages.error(request, "Current password is incorrect.")
            return render(request, "accounts/change_password.html", {"form": form})

        user.password_hash = _pwd_context.hash(new_pw)
        user.save(update_fields=["password_hash"])
        update_session_auth_hash(request, user)
        messages.success(request, "Password changed successfully.")
        return redirect("accounts:profile")

    return render(request, "accounts/change_password.html", {"form": form})


@login_required
def delete_account_view(request):
    """
    Account deletion: require typing username to confirm, then hard-delete.

    Logs out the user and redirects to login after deletion.
    """
    form = DeleteAccountForm(request.POST or None)
    if request.method == "POST" and form.is_valid():
        confirm_username = form.cleaned_data["confirm_username"]
        if confirm_username == request.user.username:
            user = request.user
            auth_logout(request)
            user.delete()
            messages.success(request, "Your account has been deleted.")
            return redirect("accounts:login")
        else:
            messages.error(request, "Username did not match — account not deleted.")
            return render(request, "accounts/delete_account.html", {"form": form})

    return render(request, "accounts/delete_account.html", {"form": form})
