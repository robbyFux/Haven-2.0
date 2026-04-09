"""
Scaffold smoke tests — verify Django settings, model imports, and auth backend.

These tests run against in-memory SQLite (see settings.py pytest detection).
"""

import django
import pytest
from django.conf import settings


class TestSettingsLoaded:
    """Verify critical Django settings are configured correctly."""

    def test_auth_user_model(self):
        assert settings.AUTH_USER_MODEL == "accounts.HavenUser"

    def test_custom_auth_backend(self):
        assert "core.auth_backend.HavenAuthBackend" in settings.AUTHENTICATION_BACKENDS

    def test_whitenoise_in_middleware(self):
        assert "whitenoise.middleware.WhiteNoiseMiddleware" in settings.MIDDLEWARE

    def test_htmx_middleware(self):
        assert "django_htmx.middleware.HtmxMiddleware" in settings.MIDDLEWARE

    def test_session_engine(self):
        assert settings.SESSION_ENGINE == "django.contrib.sessions.backends.db"

    def test_csrf_cookie_secure_default_false(self):
        # In test/dev mode SECURE_COOKIES env var is not set → should be False
        assert settings.CSRF_COOKIE_SECURE is False

    def test_login_url(self):
        assert settings.LOGIN_URL == "/accounts/login/"

    def test_login_redirect(self):
        assert settings.LOGIN_REDIRECT_URL == "/events/"


class TestModelsImportable:
    """Verify all 5 unmanaged models can be imported."""

    def test_haven_user_importable(self):
        from accounts.models import HavenUser

        assert HavenUser._meta.db_table == "users"
        assert HavenUser._meta.managed is False

    def test_device_importable(self):
        from devices.models import Device

        assert Device._meta.db_table == "devices"
        assert Device._meta.managed is False

    def test_event_importable(self):
        from events.models import Event

        assert Event._meta.db_table == "events"
        assert Event._meta.managed is False

    def test_event_trigger_importable(self):
        from events.models import EventTrigger

        assert EventTrigger._meta.db_table == "event_triggers"
        assert EventTrigger._meta.managed is False

    def test_analysis_result_importable(self):
        from events.models import AnalysisResult

        assert AnalysisResult._meta.db_table == "analysis_results"
        assert AnalysisResult._meta.managed is False


class TestCreateUser:
    """Verify that create_user creates a HavenUser in the DB with correct fields."""

    @pytest.mark.django_db
    def test_create_user(self, create_user):
        user = create_user(username="alice", password="secret123")
        assert user.pk is not None
        assert user.username == "alice"
        # Password should be bcrypt hash, not plaintext
        assert user.password_hash.startswith("$2")
        assert user.user_key.startswith("haven_u_")

    @pytest.mark.django_db
    def test_create_admin_user(self, admin_user):
        assert admin_user.is_admin is True
        assert admin_user.is_staff is True  # property shim

    @pytest.mark.django_db
    def test_user_count_in_db(self, create_user):
        from accounts.models import HavenUser

        create_user(username="bob", password="pw123")
        assert HavenUser.objects.filter(username="bob").exists()


class TestAuthBackend:
    """Verify HavenAuthBackend verifies bcrypt passwords correctly."""

    @pytest.mark.django_db
    def test_correct_password_returns_user(self, create_user):
        from core.auth_backend import HavenAuthBackend

        create_user(username="carol", password="correct_pw")
        backend = HavenAuthBackend()
        user = backend.authenticate(None, username="carol", password="correct_pw")
        assert user is not None
        assert user.username == "carol"

    @pytest.mark.django_db
    def test_wrong_password_returns_none(self, create_user):
        from core.auth_backend import HavenAuthBackend

        create_user(username="dave", password="right_pw")
        backend = HavenAuthBackend()
        result = backend.authenticate(None, username="dave", password="wrong_pw")
        assert result is None

    @pytest.mark.django_db
    def test_unknown_user_returns_none(self):
        from core.auth_backend import HavenAuthBackend

        backend = HavenAuthBackend()
        result = backend.authenticate(None, username="nobody", password="whatever")
        assert result is None

    @pytest.mark.django_db
    def test_get_user(self, create_user):
        from core.auth_backend import HavenAuthBackend

        user = create_user(username="eve", password="pw")
        backend = HavenAuthBackend()
        loaded = backend.get_user(user.pk)
        assert loaded is not None
        assert loaded.username == "eve"

    @pytest.mark.django_db
    def test_get_user_missing_returns_none(self):
        from core.auth_backend import HavenAuthBackend

        backend = HavenAuthBackend()
        assert backend.get_user(99999) is None


class TestTotpHelper:
    """Verify TOTP helper uses same Fernet derivation as FastAPI."""

    def test_encrypt_decrypt_roundtrip(self):
        from core.totp import decrypt_totp_secret, encrypt_totp_secret

        secret = "JBSWY3DPEHPK3PXP"
        encrypted = encrypt_totp_secret(secret)
        assert encrypted != secret
        assert decrypt_totp_secret(encrypted) == secret

    def test_generate_secret_is_base32(self):
        import base64

        from core.totp import generate_totp_secret

        secret = generate_totp_secret()
        # Should be valid base32 (no exception)
        base64.b32decode(secret)
        assert len(secret) > 0

    def test_verify_totp_valid(self):
        import pyotp

        from core.totp import verify_totp

        secret = pyotp.random_base32()
        token = pyotp.TOTP(secret).now()
        assert verify_totp(secret, token) is True

    def test_verify_totp_invalid(self):
        from core.totp import verify_totp

        assert verify_totp("JBSWY3DPEHPK3PXP", "000000") is False
