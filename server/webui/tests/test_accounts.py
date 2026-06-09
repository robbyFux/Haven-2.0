"""
Tests for the accounts auth flows: registration, login, TOTP 2FA, profile,
change password, and account deletion.

All tests use the conftest fixtures (create_user, authenticated_client).
TOTP codes are generated via pyotp to match the real verification logic.
"""

import pyotp
import pytest
from django.test import Client


# ---------------------------------------------------------------------------
# Registration
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_register_creates_user(client):
    """POST /accounts/register/ with valid data creates a HavenUser."""
    from accounts.models import HavenUser

    response = client.post(
        "/accounts/register/",
        {"username": "newuser", "password": "securepass123", "password_confirm": "securepass123"},
    )
    assert response.status_code == 302
    assert "/accounts/login/" in response["Location"]

    user = HavenUser.objects.get(username="newuser")
    assert user.user_key.startswith("haven_u_")
    # Verify password is bcrypt-hashed (not stored in plaintext)
    import bcrypt

    assert bcrypt.checkpw(b"securepass123", user.password_hash.encode())


@pytest.mark.django_db
def test_register_duplicate_username(client, create_user):
    """POST /accounts/register/ with existing username shows form error."""
    from accounts.models import HavenUser

    create_user("existinguser", "pass123")
    count_before = HavenUser.objects.count()

    response = client.post(
        "/accounts/register/",
        {
            "username": "existinguser",
            "password": "anotherpass",
            "password_confirm": "anotherpass",
        },
    )
    assert response.status_code == 200  # stays on form
    assert HavenUser.objects.count() == count_before  # no new row


@pytest.mark.django_db
def test_register_password_mismatch(client):
    """POST /accounts/register/ with mismatched passwords shows form error."""
    from accounts.models import HavenUser

    count_before = HavenUser.objects.count()
    response = client.post(
        "/accounts/register/",
        {"username": "mismatchuser", "password": "pass1", "password_confirm": "pass2"},
    )
    assert response.status_code == 200
    assert HavenUser.objects.count() == count_before


# ---------------------------------------------------------------------------
# Login
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_login_valid(client, create_user):
    """POST /accounts/login/ with correct credentials creates session and redirects."""
    create_user("loginuser", "mypassword")
    response = client.post(
        "/accounts/login/",
        {"username": "loginuser", "password": "mypassword"},
    )
    assert response.status_code == 302
    assert "/events/" in response["Location"]
    # session should have _auth_user_id set
    assert "_auth_user_id" in client.session


@pytest.mark.django_db
def test_login_invalid(client, create_user):
    """POST /accounts/login/ with wrong password returns form with error, no session."""
    create_user("loginuser2", "correctpass")
    response = client.post(
        "/accounts/login/",
        {"username": "loginuser2", "password": "wrongpass"},
    )
    assert response.status_code == 200
    assert "_auth_user_id" not in client.session


@pytest.mark.django_db
def test_login_totp_required(client, create_user):
    """User with totp_enabled=True is redirected to TOTP verify after login."""
    from accounts.models import HavenUser
    from core.totp import encrypt_totp_secret, generate_totp_secret

    user = create_user("totpuser", "mypassword")
    secret = generate_totp_secret()
    user.totp_secret = encrypt_totp_secret(secret)
    user.totp_enabled = True
    user.save()

    response = client.post(
        "/accounts/login/",
        {"username": "totpuser", "password": "mypassword"},
    )
    assert response.status_code == 302
    assert "/accounts/totp-verify/" in response["Location"]
    assert client.session.get("pending_2fa_user_id") == user.id


# ---------------------------------------------------------------------------
# TOTP verification
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_totp_verify_valid(client, create_user):
    """POST /accounts/totp-verify/ with correct code creates session."""
    from core.totp import encrypt_totp_secret, generate_totp_secret

    user = create_user("totpverifyuser", "mypassword")
    secret = generate_totp_secret()
    user.totp_secret = encrypt_totp_secret(secret)
    user.totp_enabled = True
    user.save()

    # Simulate the session state that login_view sets
    session = client.session
    session["pending_2fa_user_id"] = user.id
    session.save()

    valid_code = pyotp.TOTP(secret).now()
    response = client.post("/accounts/totp-verify/", {"code": valid_code})
    assert response.status_code == 302
    assert "/events/" in response["Location"]
    assert "_auth_user_id" in client.session
    assert "pending_2fa_user_id" not in client.session


@pytest.mark.django_db
def test_totp_verify_invalid(client, create_user):
    """POST /accounts/totp-verify/ with wrong code shows error, no session created."""
    from core.totp import encrypt_totp_secret, generate_totp_secret

    user = create_user("totpwronguser", "mypassword")
    secret = generate_totp_secret()
    user.totp_secret = encrypt_totp_secret(secret)
    user.totp_enabled = True
    user.save()

    session = client.session
    session["pending_2fa_user_id"] = user.id
    session.save()

    response = client.post("/accounts/totp-verify/", {"code": "000000"})
    assert response.status_code == 200
    assert "_auth_user_id" not in client.session


@pytest.mark.django_db
def test_totp_verify_no_session_redirects(client):
    """GET /accounts/totp-verify/ without pending session redirects to login."""
    response = client.get("/accounts/totp-verify/")
    assert response.status_code == 302
    assert "/accounts/login/" in response["Location"]


# ---------------------------------------------------------------------------
# 2FA setup
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_totp_setup_generates_qr(authenticated_client):
    """GET /accounts/2fa-setup/ shows QR code (base64 PNG) and secret."""
    response = authenticated_client.get("/accounts/2fa-setup/")
    assert response.status_code == 200
    content = response.content.decode()
    # base64-encoded PNG starts with "data:image/png;base64,"
    assert "data:image/png;base64," in content


@pytest.mark.django_db
def test_totp_setup_confirm(authenticated_client):
    """POST /accounts/2fa-setup/ with valid code enables TOTP on user account."""
    from core.totp import generate_totp_secret

    # First GET to generate and store secret in session
    authenticated_client.get("/accounts/2fa-setup/")
    secret = authenticated_client.session.get("pending_totp_secret")
    assert secret is not None

    valid_code = pyotp.TOTP(secret).now()
    response = authenticated_client.post("/accounts/2fa-setup/", {"code": valid_code})
    assert response.status_code == 302
    assert "/accounts/profile/" in response["Location"]

    # Reload user from DB and verify totp_enabled
    from django.contrib.auth import get_user_model

    User = get_user_model()
    user = User.objects.get(username="testuser")
    assert user.totp_enabled is True
    assert user.totp_secret is not None


# ---------------------------------------------------------------------------
# Profile
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_profile_page(authenticated_client):
    """GET /accounts/profile/ shows username and 2FA status."""
    response = authenticated_client.get("/accounts/profile/")
    assert response.status_code == 200
    content = response.content.decode()
    assert "testuser" in content


@pytest.mark.django_db
def test_profile_requires_login(client):
    """GET /accounts/profile/ without session redirects to login."""
    response = client.get("/accounts/profile/")
    assert response.status_code == 302
    assert "/accounts/login/" in response["Location"]


# ---------------------------------------------------------------------------
# Logout
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_logout(authenticated_client):
    """POST /accounts/logout/ clears session and redirects to login."""
    response = authenticated_client.post("/accounts/logout/")
    assert response.status_code == 302
    assert "/accounts/login/" in response["Location"]
    assert "_auth_user_id" not in authenticated_client.session


# ---------------------------------------------------------------------------
# Change password
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_change_password_success(client, create_user):
    """POST /accounts/change-password/ with correct current password updates hash."""
    user = create_user("pwuser", "oldpassword")
    client.force_login(user)

    response = client.post(
        "/accounts/change-password/",
        {
            "current_password": "oldpassword",
            "new_password": "newpassword456",
            "new_password_confirm": "newpassword456",
        },
    )
    assert response.status_code == 302
    assert "/accounts/profile/" in response["Location"]

    user.refresh_from_db()
    import bcrypt

    assert bcrypt.checkpw(b"newpassword456", user.password_hash.encode())


@pytest.mark.django_db
def test_change_password_wrong_current(client, create_user):
    """POST /accounts/change-password/ with wrong current password shows error."""
    user = create_user("pwuser2", "correctpass")
    client.force_login(user)

    response = client.post(
        "/accounts/change-password/",
        {
            "current_password": "wrongpass",
            "new_password": "newpassword456",
            "new_password_confirm": "newpassword456",
        },
    )
    assert response.status_code == 200


@pytest.mark.django_db
def test_change_password_mismatch(client, create_user):
    """POST /accounts/change-password/ with mismatched new passwords shows error."""
    user = create_user("pwuser3", "correctpass")
    client.force_login(user)

    response = client.post(
        "/accounts/change-password/",
        {
            "current_password": "correctpass",
            "new_password": "newpass1",
            "new_password_confirm": "newpass2",
        },
    )
    assert response.status_code == 200


@pytest.mark.django_db
def test_change_password_requires_login(client):
    """GET /accounts/change-password/ without session redirects to login."""
    response = client.get("/accounts/change-password/")
    assert response.status_code == 302
    assert "/accounts/login/" in response["Location"]


# ---------------------------------------------------------------------------
# Delete account
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_delete_account_requires_confirmation(client, create_user):
    """GET /accounts/delete-account/ shows confirmation warning page."""
    user = create_user("deleteuser", "pass123")
    client.force_login(user)

    response = client.get("/accounts/delete-account/")
    assert response.status_code == 200
    content = response.content.decode()
    # Page should have a warning and confirmation input
    assert "deleteuser" in content or "confirm" in content.lower()


@pytest.mark.django_db
def test_delete_account_success(client, create_user):
    """POST /accounts/delete-account/ with correct username deletes user and logs out."""
    from accounts.models import HavenUser

    user = create_user("todelete", "pass123")
    client.force_login(user)

    response = client.post("/accounts/delete-account/", {"confirm_username": "todelete"})
    assert response.status_code == 302
    assert "/accounts/login/" in response["Location"]
    assert "_auth_user_id" not in client.session
    assert not HavenUser.objects.filter(username="todelete").exists()


@pytest.mark.django_db
def test_delete_account_wrong_confirmation(client, create_user):
    """POST /accounts/delete-account/ with wrong username shows error, user not deleted."""
    from accounts.models import HavenUser

    user = create_user("keepme", "pass123")
    client.force_login(user)

    response = client.post("/accounts/delete-account/", {"confirm_username": "wrongname"})
    assert response.status_code == 200
    assert HavenUser.objects.filter(username="keepme").exists()


@pytest.mark.django_db
def test_delete_account_requires_login(client):
    """GET /accounts/delete-account/ without session redirects to login."""
    response = client.get("/accounts/delete-account/")
    assert response.status_code == 302
    assert "/accounts/login/" in response["Location"]
