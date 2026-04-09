"""
Django settings for Haven Web UI.

Shares the PostgreSQL database with the FastAPI backend (read-only via unmanaged models).
SECRET_KEY MUST match the FastAPI SECRET_KEY for Fernet TOTP compatibility.
"""

import os
import sys
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent

# ---------------------------------------------------------------------------
# Security
# ---------------------------------------------------------------------------

SECRET_KEY = os.environ.get("SECRET_KEY", "change-me-to-random-64-chars")

DEBUG = os.environ.get("DEBUG", "true").lower() == "true"

ALLOWED_HOSTS = os.environ.get("ALLOWED_HOSTS", "*").split(",")

SESSION_COOKIE_SECURE = os.environ.get("SECURE_COOKIES", "false").lower() == "true"
CSRF_COOKIE_SECURE = os.environ.get("SECURE_COOKIES", "false").lower() == "true"
SESSION_COOKIE_HTTPONLY = True  # Django default — explicit for clarity (T-06-07)
SESSION_COOKIE_AGE = 86400  # 24 hours

# ---------------------------------------------------------------------------
# Application definition
# ---------------------------------------------------------------------------

INSTALLED_APPS = [
    # Django built-ins needed for auth + sessions
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    # Third-party
    "django_tailwind_cli",
    "django_htmx",
    "django_filters",
    # Haven apps
    "accounts",
    "devices",
    "events",
    "admin_panel",
    "notifications",
    "core",
]

MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "whitenoise.middleware.WhiteNoiseMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django_htmx.middleware.HtmxMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]

ROOT_URLCONF = "config.urls"

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [BASE_DIR / "core" / "templates"],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.debug",
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
                "django.contrib.messages.context_processors.messages",
            ],
        },
    },
]

WSGI_APPLICATION = "config.wsgi.application"

# ---------------------------------------------------------------------------
# Database — shared with FastAPI (same PostgreSQL instance)
# ---------------------------------------------------------------------------

DATABASES = {
    "default": {
        "ENGINE": "django.db.backends.postgresql",
        "NAME": os.environ.get("DB_NAME", "haven"),
        "USER": os.environ.get("DB_USER", "haven"),
        "PASSWORD": os.environ.get("DB_PASSWORD", "haven"),
        "HOST": os.environ.get("DB_HOST", "db"),
        "PORT": os.environ.get("DB_PORT", "5432"),
    }
}

# Django manages only its own session table; all Haven tables are unmanaged.
# Use the same DB connection so unmanaged models can query Haven data.

# ---------------------------------------------------------------------------
# Authentication — custom backend that verifies bcrypt hashes
# ---------------------------------------------------------------------------

AUTH_USER_MODEL = "accounts.HavenUser"

AUTHENTICATION_BACKENDS = [
    "core.auth_backend.HavenAuthBackend",
]

LOGIN_URL = "/accounts/login/"
LOGIN_REDIRECT_URL = "/events/"
LOGOUT_REDIRECT_URL = "/accounts/login/"

# ---------------------------------------------------------------------------
# Session engine
# ---------------------------------------------------------------------------

SESSION_ENGINE = "django.contrib.sessions.backends.db"

# ---------------------------------------------------------------------------
# Static files (CSS, JavaScript, Images)
# ---------------------------------------------------------------------------

STATIC_URL = "/static/"
STATIC_ROOT = BASE_DIR / "staticfiles"
STATICFILES_DIRS = [BASE_DIR / "static"]

STORAGES = {
    "default": {
        "BACKEND": "django.core.files.storage.FileSystemStorage",
    },
    "staticfiles": {
        "BACKEND": "whitenoise.storage.CompressedManifestStaticFilesStorage",
    },
}

# ---------------------------------------------------------------------------
# Media (shared with FastAPI — read-only in webui)
# ---------------------------------------------------------------------------

MEDIA_ROOT = os.environ.get("MEDIA_ROOT", "/app/media")
MEDIA_URL = "/media/"

# ---------------------------------------------------------------------------
# Tailwind CSS CLI
# ---------------------------------------------------------------------------

TAILWIND_CLI_VERSION = "3.4.17"
TAILWIND_CLI_SRC_CSS = "static/src/input.css"
TAILWIND_CLI_DIST_CSS = "static/css/tailwind.css"

# ---------------------------------------------------------------------------
# Internationalisation
# ---------------------------------------------------------------------------

LANGUAGE_CODE = "en-us"
TIME_ZONE = "UTC"
USE_I18N = True
USE_TZ = True

# ---------------------------------------------------------------------------
# Default primary key field type
# ---------------------------------------------------------------------------

DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

# ---------------------------------------------------------------------------
# Test mode: switch to in-memory SQLite so tests need no PostgreSQL
# ---------------------------------------------------------------------------

if "pytest" in sys.modules:
    DATABASES = {
        "default": {
            "ENGINE": "django.db.backends.sqlite3",
            "NAME": ":memory:",
        }
    }
    # Use plain StaticFilesStorage in tests — CompressedManifestStaticFilesStorage
    # requires a pre-built staticfiles manifest which does not exist during testing.
    STORAGES = {
        "default": {
            "BACKEND": "django.core.files.storage.FileSystemStorage",
        },
        "staticfiles": {
            "BACKEND": "django.contrib.staticfiles.storage.StaticFilesStorage",
        },
    }
