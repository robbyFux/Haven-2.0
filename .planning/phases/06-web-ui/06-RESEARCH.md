# Phase 6: Web-UI — Research

**Researched:** 2026-04-08
**Domain:** Django web application integrating with existing FastAPI + PostgreSQL backend
**Confidence:** HIGH (stack verified, patterns confirmed from official docs and registry)

---

## Summary

Phase 6 adds a Django-based web interface to the existing Haven Cloud-Server (FastAPI + PostgreSQL + Redis + Celery). The tech stack is locked: Django Templates + HTMX + Alpine.js + Tailwind CSS. The critical architectural decision is how Django integrates with the existing FastAPI backend and its PostgreSQL database.

The recommended integration pattern is **Option A: Shared PostgreSQL database**, where Django defines its own unmanaged models (`Meta: managed = False`) that mirror the existing SQLAlchemy-managed tables. Django reads/writes the same rows as FastAPI — no HTTP layer between them, no data duplication, no sync complexity. Django handles browser sessions; FastAPI continues to serve the Android app via JWT. This is the cleanest architecture and avoids two separate databases.

The auth duality (FastAPI JWT for Android, Django sessions for browser) is solved by a custom Django authentication backend that verifies bcrypt hashes already stored in the `users` table — Django never owns the password hash format. TOTP verification reuses the existing `pyotp` library and the Fernet-encrypted `totp_secret` column through a custom view flow.

**Primary recommendation:** Shared database with Django unmanaged models + custom auth backend. Deploy Django as a second service (`webui`) in docker-compose alongside FastAPI (`app`), sharing the same PostgreSQL and Redis containers.

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| WEBUI-01 | Nutzer-Selbstverwaltung — Registrierung, Login, 2FA-Setup (TOTP QR-Code), Passwort ändern, Account löschen | Django custom auth backend + pyotp + existing `users` table |
| WEBUI-02 | Geräte-Verwaltung — App-Keys anzeigen, erstellen (mit Name), widerrufen | Django views reading `devices` table; unmanaged Device model |
| WEBUI-03 | Event-Browser — Events nach Datum/Gerät/Typ filtern, Video abspielen, AI-Analyseergebnis anzeigen, Event löschen | django-filter + HTMX partial renders + Django streaming view for video |
| WEBUI-04 | Admin-Dashboard — Nutzerübersicht, Quota-Verwaltung pro Nutzer, Systemstatistiken (Speicher, Event-Zähler) | Custom admin views (not django-admin); unmanaged User model with quota fields |
| WEBUI-05 | Benachrichtigungs-Konfiguration — Mail/Signal/Pushover-Einstellungen, Heartbeat-Interval, Schweregrad-Schwellwert | Django forms updating notification_* columns in `users` table |
</phase_requirements>

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Django | 5.2.x (LTS) | Web framework, ORM, sessions, middleware | LTS until April 2028; latest stable [VERIFIED: pip registry] |
| django-tailwind-cli | 4.5.1 | Tailwind CSS via standalone CLI — no Node.js required | Zero Node.js dependency; django-commons org; active [VERIFIED: pip registry] |
| django-htmx | 1.27.0 | `request.htmx` detection + `HttpResponseClientRedirect` helpers | Simplifies HTMX partial/full response pattern [VERIFIED: pip registry] |
| django-filter | 25.2 | Filter querysets from URL params; Event browser filter forms | Official Django ecosystem [VERIFIED: pip registry] |
| WhiteNoise | 6.12.0 | Serve static files (compiled Tailwind CSS) from Django in Docker | No nginx needed for static in dev/single-container [VERIFIED: pip registry] |
| psycopg2-binary | 2.9.11 | Synchronous PostgreSQL driver for Django (Django ORM is sync) | psycopg3 sync driver also viable but psycopg2-binary is simpler [VERIFIED: pip registry] |
| pytest-django | 4.12.0 | Django-aware pytest fixtures (`db`, `client`, `rf`) | Standard test tool for Django [VERIFIED: pip registry] |
| pyotp | 2.9.0 | TOTP generation + verification (already in pyproject.toml) | Same lib as FastAPI backend — reuse Fernet-encrypted secrets [VERIFIED: codebase] |
| qrcode[pil] | 8.2 | QR code PNG generation for 2FA setup (already in pyproject.toml) | Same lib — reuse `get_totp_qr_png()` [VERIFIED: codebase] |
| cryptography | (latest) | Fernet decryption of stored TOTP secrets (already in pyproject.toml) | Must share Fernet key derivation logic from `app/services/totp.py` [VERIFIED: codebase] |
| passlib[bcrypt] | 1.7.4 | bcrypt verification in custom auth backend (already in pyproject.toml) | Same lib as FastAPI's `pwd_context` — avoids hash format mismatch [VERIFIED: codebase] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| django-tables2 | 2.9.0 | Table rendering with sorting/pagination for Event browser and admin | Optional; may use plain template loop + manual pagination instead [VERIFIED: pip registry] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| django-tailwind-cli | timonweb/django-tailwind | timonweb version requires Node.js; django-tailwind-cli is the no-Node.js solution |
| Custom auth backend | django-allauth | django-allauth brings its own user model tables; incompatible with shared-DB approach |
| Custom auth backend | django-two-factor-auth | Same problem: stores OTP device in its own tables, conflicts with existing Fernet-encrypted totp_secret |
| psycopg2-binary | psycopg (v3) | psycopg3 is the future, but psycopg2-binary has wider Django 5.2 testing coverage and zero surprises |
| Shared DB (Option A) | Internal HTTP calls (Option C) | Option C adds latency, retry logic, and a network hop on every page load — no benefit for single-host deploy |
| Shared DB (Option A) | Django replaces FastAPI (Option B) | Would require rewriting all async FastAPI endpoints in Django; breaks Android app during migration |

**Installation (Django webui):**
```bash
pip install "django==5.2.*" django-tailwind-cli==4.5.1 django-htmx==1.27.0 \
            django-filter==25.2 whitenoise==6.12.0 psycopg2-binary==2.9.11 \
            pytest-django==4.12.0
```

---

## Architecture Patterns

### Recommended Project Structure

```
server/
├── app/                    # Existing FastAPI app (unchanged)
│   ├── models/             # SQLAlchemy ORM models (source of truth for schema)
│   ├── routers/
│   └── services/
├── webui/                  # NEW: Django project
│   ├── manage.py
│   ├── config/             # Django settings package
│   │   ├── __init__.py
│   │   ├── settings.py     # Django settings (reads same .env)
│   │   ├── urls.py
│   │   └── wsgi.py
│   ├── accounts/           # Django app: login, register, 2FA, password, delete
│   │   ├── models.py       # Unmanaged User model (managed=False)
│   │   ├── views.py
│   │   ├── forms.py
│   │   ├── urls.py
│   │   └── templates/accounts/
│   ├── devices/            # Django app: device list, create, revoke
│   │   ├── models.py       # Unmanaged Device model (managed=False)
│   │   ├── views.py
│   │   ├── forms.py
│   │   ├── urls.py
│   │   └── templates/devices/
│   ├── events/             # Django app: event browser, video, delete
│   │   ├── models.py       # Unmanaged Event, EventTrigger, AnalysisResult (managed=False)
│   │   ├── views.py
│   │   ├── filters.py      # django-filter FilterSet
│   │   ├── urls.py
│   │   └── templates/events/
│   ├── admin_panel/        # Django app: admin dashboard (NOT django-admin)
│   │   ├── views.py
│   │   ├── urls.py
│   │   └── templates/admin_panel/
│   ├── notifications/      # Django app: notification settings form
│   │   ├── forms.py
│   │   ├── views.py
│   │   ├── urls.py
│   │   └── templates/notifications/
│   ├── core/               # Shared: base template, auth backend, TOTP helpers
│   │   ├── auth_backend.py # Custom authentication backend
│   │   ├── totp.py         # Reused from app/services/totp.py
│   │   └── templates/
│   │       ├── base.html
│   │       └── partials/   # HTMX partial templates
│   ├── static/             # Tailwind output CSS, Alpine.js CDN is linked in base.html
│   └── tests/
│       ├── conftest.py     # pytest-django setup
│       ├── test_accounts.py
│       ├── test_devices.py
│       ├── test_events.py
│       └── test_admin.py
└── docker-compose.yml      # Add webui service
```

### Pattern 1: Shared Database with Unmanaged Django Models

**What:** Django models mirror existing SQLAlchemy-managed tables. Setting `managed = False` in Django's `Meta` class tells Django NOT to create, alter, or drop the table — it simply maps to the existing table.

**When to use:** Always, for this phase. Django must not conflict with Alembic migrations.

**Example:**
```python
# webui/accounts/models.py
from django.contrib.auth.models import AbstractBaseUser, BaseUserManager

class HavenUserManager(BaseUserManager):
    def get_by_natural_key(self, username):
        return self.get(username=username)

class HavenUser(AbstractBaseUser):
    """
    Unmanaged Django user model that maps to the existing 'users' table.
    All schema changes go through Alembic — Django never touches this table's DDL.
    """
    id = models.AutoField(primary_key=True)
    username = models.CharField(max_length=50, unique=True)
    password_hash = models.CharField(max_length=255)  # bcrypt hash
    user_key = models.CharField(max_length=80, unique=True)
    is_admin = models.BooleanField(default=False)
    is_active = models.BooleanField(default=True)
    totp_secret = models.CharField(max_length=255, null=True, blank=True)
    totp_enabled = models.BooleanField(default=False)
    storage_quota_mb = models.IntegerField(default=1024)
    max_events = models.IntegerField(default=10000)
    current_storage_bytes = models.BigIntegerField(default=0)
    current_event_count = models.IntegerField(default=0)
    notification_email = models.CharField(max_length=255, null=True, blank=True)
    notification_signal_number = models.CharField(max_length=20, null=True, blank=True)
    pushover_user_key = models.CharField(max_length=50, null=True, blank=True)
    notifications_enabled = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=False)
    updated_at = models.DateTimeField(auto_now=False)

    USERNAME_FIELD = "username"
    REQUIRED_FIELDS = []

    objects = HavenUserManager()

    class Meta:
        managed = False        # CRITICAL: do not touch the table DDL
        db_table = "users"     # map to FastAPI's existing table
        app_label = "accounts"

    # Django expects a `password` field for its auth machinery.
    # We store bcrypt in `password_hash` — the custom backend handles this.
    @property
    def password(self):
        return self.password_hash

    @password.setter
    def password(self, value):
        self.password_hash = value
```

**Critical settings:**
```python
# webui/config/settings.py
AUTH_USER_MODEL = "accounts.HavenUser"
AUTHENTICATION_BACKENDS = ["core.auth_backend.HavenAuthBackend"]
```

**Warning:** `managed = False` means `pytest-django`'s `--create-db` will NOT create these tables. Tests need an in-memory SQLite DB with the tables created manually (same pattern as FastAPI's conftest.py). See Validation Architecture section.

### Pattern 2: Custom Authentication Backend

**What:** Django's authentication middleware calls `authenticate()` on each registered backend. A custom backend can verify bcrypt hashes stored in `password_hash` without using Django's password format.

**When to use:** For login view — Django session auth that works with existing bcrypt hashes.

**Example:**
```python
# webui/core/auth_backend.py
from passlib.context import CryptContext
from accounts.models import HavenUser

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

class HavenAuthBackend:
    def authenticate(self, request, username=None, password=None):
        try:
            user = HavenUser.objects.get(username=username)
        except HavenUser.DoesNotExist:
            return None
        if not user.is_active:
            return None
        if not pwd_context.verify(password, user.password_hash):
            return None
        return user  # 2FA check happens in the view layer, not here

    def get_user(self, user_id):
        try:
            return HavenUser.objects.get(pk=user_id)
        except HavenUser.DoesNotExist:
            return None
```

**TOTP in login view:**
The custom backend returns the user object before TOTP is checked. The login view checks `user.totp_enabled` — if True, stores `user.id` in the session as a pending 2FA state and redirects to a TOTP code entry form. Only after the TOTP code is verified does the view call `login(request, user)` to establish the Django session.

```python
# webui/accounts/views.py (login flow skeleton)
from core.totp import decrypt_totp_secret, verify_totp
from django.contrib.auth import authenticate, login

def login_view(request):
    if request.method == "POST":
        form = LoginForm(request.POST)
        if form.is_valid():
            user = authenticate(request,
                                username=form.cleaned_data["username"],
                                password=form.cleaned_data["password"])
            if user is None:
                form.add_error(None, "Invalid credentials")
            elif user.totp_enabled:
                request.session["pending_2fa_user_id"] = user.id
                return redirect("accounts:totp_verify")
            else:
                login(request, user, backend="core.auth_backend.HavenAuthBackend")
                return redirect("events:list")
    ...
```

### Pattern 3: HTMX Partial Rendering for Event Browser

**What:** On initial page load, return full HTML. On HTMX requests (filter, pagination, sort), return only the table partial. The `django-htmx` middleware provides `request.htmx` boolean.

**When to use:** Event browser (WEBUI-03), device list (WEBUI-02), admin user list (WEBUI-04).

**Example:**
```python
# webui/events/views.py
from django_htmx.http import trigger_client_event

def event_list(request):
    f = EventFilter(request.GET, queryset=Event.objects.filter(user=request.user)
                                                       .select_related("device", "analysis_result")
                                                       .order_by("-timestamp"))
    paginator = Paginator(f.qs, 25)
    page = paginator.get_page(request.GET.get("page", 1))

    if request.htmx:
        # Return partial: only the table + pagination row
        return render(request, "events/partials/table.html", {"page": page, "filter": f})
    return render(request, "events/list.html", {"page": page, "filter": f})
```

```html
<!-- base template pattern for HTMX filter form -->
<form hx-get="{% url 'events:list' %}"
      hx-target="#event-table"
      hx-trigger="change, submit"
      hx-push-url="true">
  {{ filter.form }}
</form>
<div id="event-table">
  {% include "events/partials/table.html" %}
</div>
```

### Pattern 4: Video Serving (Encrypted Files)

**What:** Media files under `MEDIA_ROOT` may be AES-256-GCM encrypted (`.enc` extension, `is_encrypted=True`). The Django view must:
1. Verify the requesting user owns the event.
2. If encrypted: read bytes, decrypt with `derive_key(password, username)` from the crypto service, stream decrypted bytes.
3. If unencrypted: stream the file directly.

**Critical constraint:** The Argon2id key derivation requires the user's plaintext cloud password, which is NOT stored anywhere server-side. If a video was uploaded with encryption, the client provided the password in the `X-Encryption-Password` header at upload time — that password is not recoverable server-side. **Implication:** The Django Web-UI cannot transparently decrypt client-encrypted videos. Options:
- Show encrypted videos as downloadable (user can decrypt locally).
- Web-UI video playback only works for unencrypted uploads (`is_encrypted=False`).
- Display a clear UI message: "This video was client-encrypted and cannot be played in the browser."

For **unencrypted videos**, use Django's `StreamingHttpResponse` with `FileWrapper` for range-request support, or — for production — `X-Accel-Redirect` via nginx. [CITED: https://www.technetexperts.com/django-vs-nginx-video-streaming/]

```python
# webui/events/views.py — unencrypted video serve
import os
from django.http import StreamingHttpResponse, Http404
from django.contrib.auth.decorators import login_required

@login_required
def serve_video(request, event_id):
    event = get_object_or_404(Event, id=event_id, user=request.user)
    if not event.media_path:
        raise Http404
    if event.is_encrypted:
        # Cannot decrypt without client password
        return HttpResponse("Video is client-encrypted; download to decrypt locally.", status=403)
    full_path = os.path.join(settings.MEDIA_ROOT, event.media_path)
    if not os.path.exists(full_path):
        raise Http404
    response = StreamingHttpResponse(
        open(full_path, "rb"),
        content_type="video/mp4",
    )
    response["Content-Length"] = os.path.getsize(full_path)
    response["Content-Disposition"] = f'inline; filename="event_{event_id}.mp4"'
    return response
```

For production with nginx, use `X-Accel-Redirect`:
```python
response = HttpResponse()
response["X-Accel-Redirect"] = f"/protected-media/{event.media_path}"
response["Content-Type"] = "video/mp4"
return response
```

### Pattern 5: Registration (New Users via Web-UI)

**What:** The web registration form must replicate the FastAPI `/register` endpoint's logic: hash password with bcrypt, generate `user_key = f"haven_u_{secrets.token_hex(32)}"`.

**Important:** This Django view writes a new row to the `users` table — the same table FastAPI manages. The `created_at` / `updated_at` columns use `server_default=func.now()` (DB-level default, confirmed). Django insert will work without providing these fields — PostgreSQL fills them automatically.

Check existing `base.py`:

```python
# server/app/models/base.py — inspect TimestampMixin
# If it uses server_default=func.now(), columns are auto-filled by PostgreSQL.
# Django view then does NOT need to set created_at explicitly.
```

[VERIFIED: app/models/base.py] TimestampMixin uses `server_default=func.now()` — both `created_at` and `updated_at` are DB-level defaults. Django inserts do NOT need to set these fields.

### Anti-Patterns to Avoid

- **Using `django-allauth` or `django-two-factor-auth`:** These create their own auth tables and can't share the existing `users` table without complex workarounds.
- **Putting Django in the same process as FastAPI:** WSGI (Django) and ASGI (FastAPI) cannot share a process without adapter complexity. Run as separate Docker services.
- **Using Django's built-in `User` model (`django.contrib.auth.models.User`):** This creates an `auth_user` table, creating two user tables. Always set `AUTH_USER_MODEL = "accounts.HavenUser"`.
- **Calling FastAPI endpoints from Django views:** Adds HTTP latency and retry complexity for every page render. Direct DB access via Django ORM is faster and simpler.
- **Running Alembic migrations from Django:** Alembic owns schema changes. Django's `makemigrations`/`migrate` must be disabled for all unmanaged models.
- **WhiteNoise serving media files:** WhiteNoise is for static assets only. Video files under `MEDIA_ROOT` must be served by dedicated Django views (or nginx in production).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| URL parameter filtering | Manual GET param parsing | `django-filter` FilterSet | Date range, multi-select, field type coercion handled |
| Password hashing verification | Raw bcrypt calls | `passlib.context.CryptContext` (already a dep) | Same context as FastAPI — no format drift |
| TOTP verification | Custom HOTP counter | `pyotp.TOTP().verify(token, valid_window=1)` (already a dep) | Already tested in FastAPI; same 30s window logic |
| Static file fingerprinting | Manual cache-buster | `WhiteNoise` with `WHITENOISE_MANIFEST_STRICT=False` | Immutable file hashing for Tailwind CSS output |
| Tailwind build step | Node.js + npm install | `django-tailwind-cli` standalone CLI | Downloads the standalone Tailwind binary; zero Node.js |
| CSRF protection | Manual token injection | Django's built-in `CsrfViewMiddleware` | HTMX requires CSRF header — include it in base template |
| Session fixation protection | Manual session rotation | `django.contrib.auth.login()` | Calls `request.session.cycle_key()` automatically |

**Key insight:** The existing FastAPI service library stack (`pyotp`, `passlib`, `cryptography`, `qrcode`) is already in `pyproject.toml`. Django webui should add to the same `pyproject.toml` (new optional dep group) to avoid version drift.

---

## Common Pitfalls

### Pitfall 1: `managed = False` Tables Break `pytest-django`'s `--create-db`

**What goes wrong:** `pytest-django` by default creates all tables for managed models and skips unmanaged ones. Tests that touch `HavenUser`, `Device`, `Event` get `relation "users" does not exist` errors.

**Why it happens:** `managed = False` tells Django's test runner not to create those tables. Django's test infra only creates tables for models it manages.

**How to avoid:** In `conftest.py`, use a `@pytest.fixture` with `django_db_setup` override that explicitly creates the unmanaged tables before tests run:
```python
# webui/tests/conftest.py
import pytest
from django.db import connection

@pytest.fixture(scope="session")
def django_db_setup(django_test_environment, django_db_blocker):
    with django_db_blocker.unblock():
        with connection.schema_editor() as editor:
            from accounts.models import HavenUser
            from devices.models import Device
            from events.models import Event, EventTrigger, AnalysisResult
            for model in [HavenUser, Device, Event, EventTrigger, AnalysisResult]:
                try:
                    editor.create_model(model)
                except Exception:
                    pass  # already exists
```

**Warning signs:** `ProgrammingError: relation "users" does not exist` in test output.

### Pitfall 2: HTMX CSRF Header Missing

**What goes wrong:** HTMX POST requests fail with 403 Forbidden because HTMX doesn't automatically include the Django CSRF token.

**Why it happens:** HTMX uses `fetch()` under the hood; Django's `CsrfViewMiddleware` requires `X-CSRFToken` header on non-GET requests.

**How to avoid:** Add to `base.html`:
```html
<script>
  document.addEventListener("DOMContentLoaded", () => {
    document.body.addEventListener("htmx:configRequest", (event) => {
      event.detail.headers["X-CSRFToken"] = "{{ csrf_token }}";
    });
  });
</script>
```

**Warning signs:** `403 Forbidden` on HTMX `POST` / `DELETE` requests.

### Pitfall 3: Password Hash Field Collision with Django's Auth

**What goes wrong:** Django's `AbstractBaseUser` expects a field named `password` (not `password_hash`). If not handled, Django's `set_password()` / `check_password()` methods write to the wrong field.

**Why it happens:** Django's default auth machinery uses `self.password` as the attribute for the hashed value.

**How to avoid:** Override the `password` property on `HavenUser` to redirect reads/writes to `password_hash`, as shown in Pattern 1 above. Never call `user.set_password()` — use `pwd_context.hash(raw_password)` directly and write to `user.password_hash`.

**Warning signs:** `IntegrityError: null value in column "password_hash"` after registration.

### Pitfall 4: `TimestampMixin` Columns in Django Insert

**What goes wrong:** When Django inserts a new `User` row via the registration view, `created_at` / `updated_at` may be `None` if they are application-level defaults (not DB-level `server_default`).

**Why it happens:** SQLAlchemy's `TimestampMixin` may use Python `datetime.utcnow` as `default=` (called by SQLAlchemy on insert), not `server_default=func.now()` (called by PostgreSQL). Django ORM does not call SQLAlchemy defaults.

**How to avoid:** Read `server/app/models/base.py` before writing the registration view. If `server_default` is not used, the Django registration view must explicitly set `created_at = timezone.now()`.

**Warning signs:** `NOT NULL constraint violation` on `created_at` column at registration time.

### Pitfall 5: `user_key` Must Be Generated for New Web-UI Registrations

**What goes wrong:** Forgetting that the FastAPI `/register` endpoint auto-generates `user_key = f"haven_u_{secrets.token_hex(32)}"`. The Django registration form must replicate this.

**Why it happens:** The `user_key` column is `NOT NULL UNIQUE` in the schema. Django form validation won't automatically generate it.

**How to avoid:** In the Django `register` view, call `user.user_key = f"haven_u_{secrets.token_hex(32)}"` before `user.save()`.

### Pitfall 6: Tailwind CSS Not Purging Unused Classes

**What goes wrong:** Production CSS file is enormous because Tailwind scans only configured paths.

**Why it happens:** `django-tailwind-cli` needs `TAILWIND_CLI_SRC_CSS` and template path config.

**How to avoid:** Configure `TAILWIND_CLI_DIST_CSS`, `TAILWIND_CLI_SRC_CSS`, and ensure `tailwind.config.js` content globs include `webui/**/*.html` and `webui/**/*.py`.

---

## Code Examples

### Django Settings (Shared DB)
```python
# webui/config/settings.py (key sections only)
import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent

# Read same DATABASE_URL as FastAPI
import dj_database_url  # optional; or parse manually
DATABASE_URL = os.environ.get(
    "DATABASE_URL",
    "postgresql://haven:haven@localhost:5432/haven"
)

DATABASES = {
    "default": {
        "ENGINE": "django.db.backends.postgresql",
        "NAME": "haven",
        "USER": "haven",
        "PASSWORD": "haven",
        "HOST": os.environ.get("DB_HOST", "localhost"),
        "PORT": "5432",
    }
}

INSTALLED_APPS = [
    # No django.contrib.admin — we build our own admin panel
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    "django_tailwind_cli",
    "django_htmx",
    "django_filters",
    "accounts",
    "devices",
    "events",
    "admin_panel",
    "notifications",
    "core",
]

AUTH_USER_MODEL = "accounts.HavenUser"
AUTHENTICATION_BACKENDS = ["core.auth_backend.HavenAuthBackend"]

# Tailwind CLI (no Node.js)
TAILWIND_CLI_VERSION = "3.4.17"  # or latest v3; v4 is a breaking change
TAILWIND_CLI_SRC_CSS = "static/src/input.css"
TAILWIND_CLI_DIST_CSS = "static/css/tailwind.css"

STATICFILES_STORAGE = "whitenoise.storage.CompressedManifestStaticFilesStorage"

MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "whitenoise.middleware.WhiteNoiseMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django_htmx.middleware.HtmxMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    ...
]
```

### HTMX + django-filter FilterSet
```python
# webui/events/filters.py
import django_filters
from .models import Event

class EventFilter(django_filters.FilterSet):
    timestamp__gte = django_filters.DateTimeFilter(
        field_name="timestamp", lookup_expr="gte", label="From"
    )
    timestamp__lte = django_filters.DateTimeFilter(
        field_name="timestamp", lookup_expr="lte", label="To"
    )
    event_type = django_filters.CharFilter(field_name="event_type", lookup_expr="iexact")
    severity = django_filters.ChoiceFilter(
        choices=[("LOW", "Low"), ("MEDIUM", "Medium"), ("HIGH", "High"), ("CRITICAL", "Critical")]
    )
    device_id = django_filters.NumberFilter(field_name="device_id")

    class Meta:
        model = Event
        fields = ["timestamp__gte", "timestamp__lte", "event_type", "severity", "device_id"]
```

### Docker Compose Addition
```yaml
# Add to server/docker-compose.yml
  webui:
    build:
      context: .
      dockerfile: Dockerfile.webui
    command: gunicorn config.wsgi:application --bind 0.0.0.0:8080 --workers 2
    ports:
      - "8080:8080"
    volumes:
      - ./media:/app/media
      - .:/app
    env_file:
      - .env
    depends_on:
      db:
        condition: service_healthy
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Node.js + npm for Tailwind | Standalone Tailwind CLI (no Node.js) | Tailwind v3.4+ | Zero Node.js dependency in production Docker image |
| django-allauth for TOTP | Custom pyotp integration (or django-otp for framework) | Ongoing | Needed for shared-DB pattern with existing Fernet-encrypted secrets |
| whitenoice serving media | Dedicated view or X-Accel-Redirect | Ongoing | WhiteNoise is static-only; media needs auth gating |
| `django-admin` customization | Custom views (admin_panel Django app) | Ongoing | django-admin hardwires to `auth.User`; unmanaged model requires custom views |

**Deprecated/outdated:**
- `timonweb/django-tailwind`: Requires Node.js. Replaced by `django-commons/django-tailwind-cli` for no-Node.js workflow.
- Django `DATABASES` with `asyncpg`: asyncpg is for async engines (FastAPI/SQLAlchemy). Django ORM is synchronous — use `psycopg2-binary` or `psycopg` (sync).

---

## Runtime State Inventory

Phase 6 is a greenfield Django service added to the existing infrastructure. No renames or migrations of existing data.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | PostgreSQL `users`, `devices`, `events`, `event_triggers`, `analysis_results` tables — already exist | Django uses `managed=False`; Alembic owns DDL |
| Live service config | FastAPI runs on port 8000; Django webui runs on port 8080 | Add `webui` service to docker-compose.yml |
| OS-registered state | None | — |
| Secrets/env vars | `SECRET_KEY` from `.env` used by both FastAPI (Fernet) and Django webui (for same Fernet key derivation) | Both services MUST share the same `SECRET_KEY` env var |
| Build artifacts | Tailwind CSS must be compiled and copied to `webui/static/css/tailwind.css` at Docker build time | Dockerfile.webui must run `python manage.py tailwind build` |

---

## Open Questions

1. **TimestampMixin: `server_default` or `default`?**
   - What we know: `app/models/base.py` defines `TimestampMixin` but was not read during research.
   - What's unclear: Whether `created_at` / `updated_at` use `server_default=func.now()` (PostgreSQL auto-fills) or `default=datetime.utcnow` (SQLAlchemy fills; Django won't).
   - Recommendation: Read `server/app/models/base.py` in Wave 0 of the plan and add `auto_now_add=True` / `auto_now=True` to Django model fields only if PostgreSQL does NOT handle it via server_default.

2. **Client-encrypted video playback strategy for Web-UI**
   - What we know: Videos uploaded with `X-Encryption-Password` are AES-GCM encrypted; the key is derived from the user's plaintext password which is not stored server-side.
   - What's unclear: Does the product require browser-based decryption (JS WASM crypto), or is "download only for encrypted videos" acceptable?
   - Recommendation: For Phase 6, show "Download" link for encrypted videos + clear message. Browser-side decryption is out of scope.

3. **Should web-UI registration be allowed or admin-only?**
   - What we know: FastAPI `/register` is open. WEBUI-01 says "Registrierung" is a requirement.
   - What's unclear: Is open self-registration intended, or should admin create accounts and users only log in?
   - Recommendation: Default to open registration matching FastAPI behavior. If admin-only is desired, add an `HAVEN_OPEN_REGISTRATION=true` setting toggle.

4. **Tailwind v3 vs v4 for django-tailwind-cli**
   - What we know: `django-tailwind-cli` 4.5.1 supports both v3 and v4 of the Tailwind CLI. Tailwind v4 is a major rewrite (config format changed from JS to CSS).
   - What's unclear: Which Tailwind major version to target.
   - Recommendation: Use Tailwind v3 for stability. Set `TAILWIND_CLI_VERSION = "3.4.17"` in settings. v4 is too new for stable production use.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|---------|
| Python | Django runtime | ✓ | 3.12.3 | — |
| PostgreSQL | Shared DB | ✓ (via Docker) | 16-alpine | — |
| Redis | Session store (optional) / Celery | ✓ (via Docker) | 7-alpine | Use DB session backend |
| Node.js | Tailwind CSS | ✓ (host) | 18.19.1 | Not needed — use django-tailwind-cli standalone |
| docker | Container build | ✓ | detected | — |
| psycopg2-binary | Django DB driver | not installed globally | 2.9.11 (registry) | — |
| django-tailwind-cli | Tailwind integration | not installed globally | 4.5.1 (registry) | — |

**Missing dependencies with no fallback:**
- None — all required packages installable from PyPI.

**Missing dependencies with fallback:**
- Node.js: Not required — `django-tailwind-cli` uses standalone Tailwind CLI binary.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | pytest + pytest-django 4.12.0 |
| Config file | `webui/pytest.ini` (new) |
| Quick run command | `cd server && pytest webui/tests/ -x -q` |
| Full suite command | `cd server && pytest webui/tests/ -v` |

### Phase Requirements to Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| WEBUI-01 | Registration creates user row with bcrypt hash + user_key | integration | `pytest webui/tests/test_accounts.py::test_register -x` | ❌ Wave 0 |
| WEBUI-01 | Login with correct password creates session | integration | `pytest webui/tests/test_accounts.py::test_login -x` | ❌ Wave 0 |
| WEBUI-01 | TOTP-enabled login requires 2FA code | integration | `pytest webui/tests/test_accounts.py::test_login_totp -x` | ❌ Wave 0 |
| WEBUI-01 | 2FA setup generates valid QR URI | unit | `pytest webui/tests/test_accounts.py::test_2fa_setup -x` | ❌ Wave 0 |
| WEBUI-02 | Device list shows only current user's devices | integration | `pytest webui/tests/test_devices.py::test_device_list -x` | ❌ Wave 0 |
| WEBUI-02 | Device create generates valid app_key | integration | `pytest webui/tests/test_devices.py::test_device_create -x` | ❌ Wave 0 |
| WEBUI-02 | Device revoke sets is_active=False | integration | `pytest webui/tests/test_devices.py::test_device_revoke -x` | ❌ Wave 0 |
| WEBUI-03 | Event list returns only current user's events | integration | `pytest webui/tests/test_events.py::test_event_list -x` | ❌ Wave 0 |
| WEBUI-03 | Event filter by date range works | integration | `pytest webui/tests/test_events.py::test_event_filter -x` | ❌ Wave 0 |
| WEBUI-03 | HTMX request returns partial template only | integration | `pytest webui/tests/test_events.py::test_htmx_partial -x` | ❌ Wave 0 |
| WEBUI-03 | Event delete removes row and redirects | integration | `pytest webui/tests/test_events.py::test_event_delete -x` | ❌ Wave 0 |
| WEBUI-04 | Admin sees all users in dashboard | integration | `pytest webui/tests/test_admin.py::test_admin_user_list -x` | ❌ Wave 0 |
| WEBUI-04 | Non-admin cannot access admin routes | integration | `pytest webui/tests/test_admin.py::test_admin_forbidden -x` | ❌ Wave 0 |
| WEBUI-04 | Quota update writes to user row | integration | `pytest webui/tests/test_admin.py::test_quota_update -x` | ❌ Wave 0 |
| WEBUI-05 | Notification settings form saves mail/signal/pushover fields | integration | `pytest webui/tests/test_notifications.py::test_notif_settings -x` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `cd server && pytest webui/tests/ -x -q --tb=short`
- **Per wave merge:** `cd server && pytest webui/tests/ -v`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `webui/tests/conftest.py` — pytest-django config, `django_db_setup` override for unmanaged tables, test user/device/event fixtures
- [ ] `webui/tests/test_accounts.py` — registration, login, TOTP flow
- [ ] `webui/tests/test_devices.py` — device CRUD
- [ ] `webui/tests/test_events.py` — event browser + HTMX partials
- [ ] `webui/tests/test_admin.py` — admin views + auth guard
- [ ] `webui/tests/test_notifications.py` — notification settings form
- [ ] `webui/pytest.ini` — `DJANGO_SETTINGS_MODULE = config.settings_test`

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Custom auth backend + passlib bcrypt; session via `django.contrib.auth.login()` |
| V3 Session Management | yes | Django session middleware; `SESSION_COOKIE_SECURE=True` in production; `SESSION_COOKIE_HTTPONLY=True` |
| V4 Access Control | yes | `@login_required` decorator on all views; `is_admin` check for admin_panel views |
| V5 Input Validation | yes | Django Forms + ModelForms for all user input; `django-filter` for query params |
| V6 Cryptography | yes | Reuse `passlib.CryptContext(bcrypt)` for password; `pyotp.TOTP` for 2FA; never hand-roll crypto |
| V8 Data Protection | yes | Unencrypted video served only after ownership check; encrypted video blocked from browser playback |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| CSRF on HTMX POST/DELETE | Tampering | `CsrfViewMiddleware` + HTMX `htmx:configRequest` JS snippet (see Pitfall 2) |
| Session fixation on login | Elevation | `login()` calls `session.cycle_key()` automatically |
| Horizontal privilege escalation (user A reads user B's events) | Tampering | Every queryset filtered by `user=request.user` |
| Admin panel access by non-admin | Elevation | `is_admin` check decorator on all `admin_panel/` views |
| Password hash leakage | Information Disclosure | `password_hash` field never exposed in templates; no admin view shows raw hashes |
| TOTP replay attack | Elevation | `pyotp.TOTP().verify(valid_window=1)` allows only ±30 s drift; add `used_tokens` set in session for replay protection if needed |
| Path traversal in video serve | Tampering | Construct path via `os.path.join(MEDIA_ROOT, event.media_path)` + ownership check; never allow user-supplied paths |
| Open redirect on login `next` param | Tampering | Use `url_has_allowed_host_and_scheme(next_url, allowed_hosts=request.get_host())` before redirecting |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| ~~A1~~ | ~~`TimestampMixin` uses `server_default=func.now()` so Django inserts don't need to set `created_at`~~ | Pattern 5 / Pitfall 4 | RESOLVED: Verified via `app/models/base.py` — `server_default=func.now()` confirmed. No risk. |
| A2 | The `users` table `user_key` column has no DB-level default; Django registration view must generate it | Pattern 5 | Unique constraint violation if two registrations generate the same key (collision extremely unlikely with 64 hex chars) |
| A3 | Tailwind v3.4.x is the intended version (not v4) | Standard Stack | v4 has breaking config format changes; using v4 would require different setup |
| A4 | Videos uploaded without encryption (`is_encrypted=False`) are playable in-browser without decryption | Pattern 4 | If all videos are encrypted, video playback section of WEBUI-03 needs browser-side JS crypto |

---

## Sources

### Primary (HIGH confidence)
- [VERIFIED: pip registry] — django 5.2.x, django-tailwind-cli 4.5.1, django-htmx 1.27.0, django-filter 25.2, whitenoise 6.12.0, psycopg2-binary 2.9.11, pytest-django 4.12.0, django-tables2 2.9.0 — all confirmed via `pip index versions`
- [VERIFIED: codebase] — `server/pyproject.toml`, `server/app/models/user.py`, `server/app/models/event.py`, `server/app/models/device.py`, `server/app/services/totp.py`, `server/app/services/storage.py`, `server/app/services/crypto.py` — full column inventory and crypto patterns confirmed

### Secondary (MEDIUM confidence)
- [CITED: https://django-tailwind-cli.readthedocs.io/latest/] — django-tailwind-cli setup, `TAILWIND_CLI_SRC_CSS`, `TAILWIND_CLI_DIST_CSS`, `{% tailwind_css %}` tag
- [CITED: https://docs.djangoproject.com/en/5.2/topics/auth/customizing/] — `AbstractBaseUser`, custom `AUTHENTICATION_BACKENDS`, `authenticate()` / `get_user()` contract
- [CITED: https://django.readthedocs.io/en/5.2.x/ref/models/options.html] — `managed = False`, `db_table` Meta options
- [CITED: https://www.technetexperts.com/django-vs-nginx-video-streaming/] — X-Accel-Redirect pattern for video serving via nginx
- [CITED: https://enzircle.com/column-filter-table-with-htmx-alpine-js-and-django] — HTMX + django-filter column filter pattern

### Tertiary (LOW confidence)
- WebSearch results on Django shared-DB with FastAPI — general architectural guidance; implementation details are from codebase inspection and official docs

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all versions verified against pip registry
- Architecture (shared DB with unmanaged models): HIGH — confirmed against Django docs for `managed=False`
- Auth duality pattern: HIGH — custom backend documented in Django 5.2 docs
- Pitfalls: HIGH (CSRF, session fixation, unmanaged table tests) to MEDIUM (TimestampMixin assumption)
- Video serving: MEDIUM — verified pattern exists but encrypted video playback limitation is design-constrained

**Research date:** 2026-04-08
**Valid until:** 2026-07-08 (stable stack; 90 days)
