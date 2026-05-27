# Phase 8: Android & WebUI UX Polish - Research

**Researched:** 2026-05-27
**Domain:** Android Jetpack Compose (code documentation + UI polish) / Django + HTMX + Alpine.js + Tailwind (WebUI features)
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01** Archive filter: "Status" dropdown in existing filter bar (Active / Archived / All). No new tab component.
**D-02** Archive is soft-hide (is_archived=True), reversible. Archived events excluded from default Active view; unarchive supported.
**D-03** Bulk action confirmation: Alpine.js modal (dark overlay, shows count). No browser confirm for bulk actions.
**D-04** SMTP settings page at `admin_panel/settings/`. System-level concern, not per-user preference.
**D-05** SMTP password encrypted via Fernet. Key sourced from `HAVEN_SECRET_KEY` environment variable.
**D-06** HorizontalDivider placed between SettingsSection composables inside CategoryCard, NOT between individual rows.
**D-07** Dividers only added to CategoryCards with 2 or more SettingsSections.
**D-08** Delete button for revoked devices uses `hx-confirm` browser dialog, not a modal.
**D-09** Device deletion permanently removes DB row. HTMX swap removes the `<tr>` inline.

### Claude's Discretion

- Comment translation scope: all Kotlin files with German inline comments (priority: 6 hot-spots + MonitorService.kt + ZoneEditorScreen.kt; any other German comments found are also translated).
- KDoc completeness: `@param`/`@return` only where genuinely useful — no boilerplate.
- Bulk select HTMX wiring: implementation detail for the planner (checkboxes + form or Alpine state).
- SMTP model field layout: host, port, username, password, TLS toggle — exact form design is Claude's discretion.

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.

</user_constraints>

---

## Summary

Phase 8 is a pure-polish phase split across two surfaces: (A) Android Kotlin source documentation and one Compose UI change, and (B) five WebUI feature additions. There are no new sensors, no architectural changes, no new Gradle dependencies, and no new PyPI packages needed for the Android work.

The Android work is purely editorial (comment translation + KDoc) plus a single Material 3 component insertion (`HorizontalDivider`). Both the component and its import are already in the codebase (confirmed via SettingsScreen.kt imports of `androidx.compose.material3.*`). The divider is inserted literally between sequential `SettingsSection { }` call sites in each multi-section `CategoryCard` body — there is no data structure to iterate over; it is a direct call-site edit.

The WebUI work has one structural dependency: the `is_archived` field must exist in the PostgreSQL schema (Alembic migration) before the Django model change, filter, and bulk archive views work. The SMTP page follows the exact same singleton-model + admin-form + HTMX-swap pattern already established by `AISettings`/`AISettingsForm`/`ai_settings.html`. Fernet encryption is already used in `app/services/totp.py` — the same `_get_fernet()` pattern applies. The `cryptography` package is already in `pyproject.toml`. The Delete button for revoked devices requires only a new URL route + view function and a one-line template change.

**Primary recommendation:** Execute in three independent plan files: (1) Android documentation + HorizontalDivider, (2) WebUI event features (is_archived migration + bulk actions + status filter), (3) WebUI admin features (SMTP settings + device deletion).

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Android code comments / KDoc | Android source (Kotlin) | — | Editorial change to source files only |
| HorizontalDivider in SettingsScreen | Android UI (Compose) | — | Pure Compose composable insertion; no ViewModel change |
| is_archived field | Database (PostgreSQL via Alembic) | FastAPI model + Django model | DDL owned by Alembic; both ORM layers mirror it |
| Bulk archive / delete views | Django backend (WebUI views.py) | — | Business logic lives in view; state stored in DB |
| Archive status filter | Django backend (EventFilter) + WebUI template | — | Filter query logic in EventFilter; UI in list.html |
| Bulk select checkbox state | Browser (Alpine.js) | — | No server round-trip for checkbox selection state |
| Bulk action confirmation modal | Browser (Alpine.js) | HTMX POST | Modal state is client-side; action fires HTMX POST |
| SMTP settings storage | Database (Django managed model) | — | New Django-managed singleton row (admin_panel app) |
| SMTP password encryption | Django backend (admin_panel/views.py) | cryptography.Fernet | Encrypt on save, decrypt on read/test |
| SMTP test email | Django backend (admin_panel/views.py) | aiosmtplib (via asyncio.run) | Synchronous Django view wraps async send |
| Device deletion | Django backend (devices/views.py) | — | New view function + URL route |

---

## Standard Stack

### Core (already in place — no new dependencies)

| Library | Version | Purpose | Confirmed |
|---------|---------|---------|-----------|
| `androidx.compose.material3.HorizontalDivider` | bundled with M3 | Visual divider between SettingsSections | [VERIFIED: SettingsScreen.kt imports `androidx.compose.material3.*`] |
| `cryptography.fernet.Fernet` | pinned in pyproject.toml (`"cryptography"`) | SMTP password encryption | [VERIFIED: codebase — already used in `app/services/totp.py`] |
| `aiosmtplib` | 5.1.0 (pyproject.toml) | SMTP test-send from Django view | [VERIFIED: pyproject.toml line 24] |
| Alpine.js | CDN via base.html | Bulk select state, confirmation modal | [VERIFIED: CONTEXT.md + UI-SPEC.md — already used in Phase 6] |
| HTMX | 2.x via base.html | Inline partial swaps (bulk actions, device delete) | [VERIFIED: device_row.html, event_table.html] |
| `django_filters.FilterSet` | django-filter 25.2 | EventFilter (adding is_archived filter) | [VERIFIED: events/filters.py + pyproject.toml] |

### No new dependencies required

All packages needed for Phase 8 are already installed. Do NOT add new entries to `pyproject.toml` or `build.gradle`.

---

## Package Legitimacy Audit

> No new packages are installed in this phase. All libraries used are pre-existing project dependencies.

| Package | Status |
|---------|--------|
| All libraries | Pre-existing — no audit needed |

**Packages removed due to slopcheck verdict:** none
**Packages flagged as suspicious:** none

*slopcheck not available on this system — not needed as no new packages are introduced.*

---

## Architecture Patterns

### System Architecture Diagram

```
Android Surface
  ┌──────────────────────────────────────────────────┐
  │ SettingsScreen.kt (Compose UI)                   │
  │   CategoryCard { SettingsSection / HorizontalDivider │
  │   SettingsSection / HorizontalDivider / SettingsSection … } │
  └──────────────────────────────────────────────────┘
  ┌──────────────────────────────────────────────────┐
  │ 6 hot-spot .kt files + MonitorService + ZoneEditor │
  │   German inline comments → English               │
  │   WHY explanations added to algorithmic sections  │
  │   KDoc @param/@return on public functions         │
  └──────────────────────────────────────────────────┘

WebUI Surface
  Browser                Django backend           PostgreSQL
  ┌──────────┐           ┌─────────────────┐      ┌──────────┐
  │Alpine.js │──select──▶│events/views.py   │─────▶│ events   │
  │checkbox  │           │ bulk_archive()   │      │ table    │
  │state     │           │ bulk_delete()    │      │ (is_     │
  │          │           │ event_list()     │      │ archived)│
  │          │◀──partial─│ (Status filter)  │◀─────│          │
  └──────────┘  (HTMX)  └─────────────────┘      └──────────┘
       │                 ┌─────────────────┐      ┌──────────┐
       │                 │devices/views.py  │─────▶│ devices  │
       │──hx-confirm────▶│ device_delete()  │      │ table    │
       │◀──empty outerHTML│                │◀─────│          │
       │                 └─────────────────┘      └──────────┘
       │                 ┌──────────────────────┐  ┌──────────┐
       │                 │admin_panel/views.py   │  │admin_    │
       │                 │ smtp_settings() GET   │  │panel_    │
       └────────────────▶│ smtp_settings() POST  │  │smtpset   │
                         │ smtp_test()           │  │tings     │
                         └──────────────────────┘  └──────────┘
                                 │ Fernet encrypt/decrypt
                                 ▼
                         HAVEN_SECRET_KEY env var
```

### Recommended Project Structure (changes only)

```
server/
├── alembic/versions/
│   └── XXXX_add_is_archived_to_events.py   # NEW: Alembic migration
├── app/models/event.py                      # EDIT: add is_archived column
├── webui/
│   ├── events/
│   │   ├── filters.py                       # EDIT: add is_archived filter
│   │   ├── models.py                        # EDIT: add is_archived field
│   │   ├── views.py                         # EDIT: event_list + bulk_archive + bulk_delete
│   │   ├── urls.py                          # EDIT: add bulk_archive + bulk_delete + unarchive routes
│   │   └── templates/events/
│   │       ├── list.html                    # EDIT: Status dropdown + bulk action bar
│   │       ├── partials/event_row.html      # EDIT: checkbox column
│   │       └── partials/event_table.html    # EDIT: checkbox column header
│   ├── devices/
│   │   ├── views.py                         # EDIT: add device_delete()
│   │   ├── urls.py                          # EDIT: add device_delete route
│   │   └── templates/devices/partials/
│   │       └── device_row.html              # EDIT: Delete button in revoked branch
│   └── admin_panel/
│       ├── models.py                        # EDIT: add SmtpSettings singleton model
│       ├── forms.py                         # EDIT: add SmtpSettingsForm
│       ├── views.py                         # EDIT: add smtp_settings() + smtp_test()
│       ├── urls.py                          # EDIT: add settings/ and settings/test/ routes
│       ├── migrations/
│       │   └── 0002_smtpsettings.py         # NEW: Django migration for SmtpSettings
│       └── templates/admin_panel/
│           ├── smtp_settings.html           # NEW: SMTP settings page
│           ├── dashboard.html               # EDIT: add SMTP Settings nav link
│           └── partials/
│               └── smtp_settings_form.html  # NEW: HTMX-swappable form partial

app/src/main/java/org/havenapp/main/
├── ui/settings/SettingsScreen.kt            # EDIT: HorizontalDividers
├── detection/SensorFusionEngine.kt          # EDIT: comments + KDoc
├── sensor/LightMonitor.kt                   # EDIT: comments + KDoc
├── sensor/FusedMotionMonitor.kt             # EDIT: comments + KDoc
├── media/CameraAnalyzer.kt                  # EDIT: translate German + WHY
├── detection/PerceptualHashDetector.kt      # EDIT: comments + KDoc
├── detection/HavenObjectDetector.kt         # EDIT: translate German + WHY
├── MonitorService.kt                        # EDIT: translate German only
└── ui/settings/ZoneEditorScreen.kt          # EDIT: translate German only
```

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Fernet key derivation | Custom AES-GCM for SMTP password | `cryptography.Fernet` with SHA-256-derived key (same as `app/services/totp.py`) | Fernet handles IV, authentication tag, base64 encoding — exact same pattern already proven in codebase |
| SMTP connectivity test | Custom socket check | `aiosmtplib.send()` with short timeout via `asyncio.run()` in Django view | aiosmtplib already in pyproject.toml; tests real SMTP handshake including TLS, auth |
| Checkbox select-all state | Custom JS event listeners | Alpine.js `x-data`/`x-model`/`x-show` (already in stack) | Alpine.js is already loaded on every page; no additional library needed |
| HTMX row removal after delete | Server-side redirect + page reload | `hx-swap="outerHTML"` returning empty `<tbody>` row or HTTP 200 with empty body targeting `#device-{id}` | Established pattern from the revoke action; inline removal without full page reload |
| Singleton admin settings model | Multi-row config table | `get_or_create(pk=1)` singleton (same as `AISettings.get()`) | AISettings already demonstrates this exact pattern in admin_panel/models.py |

**Key insight:** Every pattern needed in Phase 8 already exists in the codebase. No new design work is required — match the established patterns exactly.

---

## Critical Implementation Details

### 1. is_archived: Two-Layer Schema Change

The `events` table schema is owned by **Alembic** (FastAPI side). The Django WebUI uses an **unmanaged model** (`managed = False` in `events/models.py` Meta class). This means:

- Step A: Add Alembic migration in `server/alembic/versions/` — `op.add_column("events", sa.Column("is_archived", Boolean, nullable=False, server_default="false"))`.
- Step B: Add `is_archived: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)` to `server/app/models/event.py` (FastAPI SQLAlchemy model).
- Step C: Add `is_archived = models.BooleanField(default=False)` to `server/webui/events/models.py` (Django unmanaged model — Django does NOT run a migration for this because `managed=False`; Django reads the column that Alembic created).

**No Django migration for the events app** — the table DDL is controlled solely by Alembic. Django will read the column without any migration.

[VERIFIED: events/models.py — `managed = False` on all three models; Alembic migration pattern confirmed in `alembic/versions/a3f2e1d4c5b6_add_pushover_app_token.py`]

### 2. Status Filter: EventFilter Extension

The existing `EventFilter` in `events/filters.py` uses `django_filters.FilterSet`. Add a character filter or method filter for `is_archived`:

```python
# Translate "active" / "archived" / "all" query param to is_archived queryset filter
# Default (no param or param="active"): filter(is_archived=False)
# param="archived": filter(is_archived=True)
# param="all": no filter
```

The `event_list` view must apply this filter before passing to `EventFilter` (or handle it separately since it's not a direct field lookup). The simplest approach: handle `status` in the view before the FilterSet, since `is_archived` is a Boolean but the filter presents three options.

Alternatively, add a `ChoiceFilter` with a custom `filter()` method on the FilterSet.

[VERIFIED: events/filters.py — current filter set structure confirmed; no is_archived field present]

### 3. Bulk Archive / Delete: HTMX + Alpine Pattern

The event table is a partial (`event_table.html`) loaded by HTMX into `#event-table`. Bulk actions POST event IDs and return the refreshed table partial. The Alpine.js `x-data` must wrap both the table and the bulk action bar — they are siblings in the DOM.

Wrapping element approach:
```html
<div x-data="{ selected: [], selectAll: false, showConfirmModal: false, ... }">
  <!-- bulk action bar (x-show="selected.length > 0") -->
  <!-- event table (id="event-table") -->
</div>
```

The HTMX partial swap of `#event-table` replaces only the table, preserving the outer Alpine.js context. After the swap, `selected` must reset to `[]` — use HTMX's `htmx:afterSwap` event listener in Alpine.js.

Bulk POST form: use a hidden `<form>` whose checkboxes are the row checkboxes, or submit `selected` array via Alpine.js as JSON in a fetch call. The simplest Django-compatible approach: a form where each checked row contributes `event_ids` as a multi-value POST parameter.

[VERIFIED: event_table.html + list.html — `hx-target="#event-table"` pattern confirmed]

### 4. SMTP Settings: New Django-Managed Singleton Model

The `SMTPSettings` model goes in `admin_panel/models.py`, following the `AISettings` singleton pattern exactly:

```python
class SMTPSettings(models.Model):
    smtp_host = models.CharField(max_length=255, blank=True, default="")
    smtp_port = models.IntegerField(default=587)
    smtp_user = models.CharField(max_length=255, blank=True, default="")
    smtp_password_encrypted = models.TextField(blank=True, default="")
    smtp_from = models.CharField(max_length=255, blank=True, default="")
    use_tls = models.BooleanField(default=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = "SMTP Settings"

    @classmethod
    def get(cls):
        obj, _ = cls.objects.get_or_create(pk=1)
        return obj
```

This **IS managed by Django** (no `managed = False`) because it is WebUI-specific config not shared with FastAPI. Run `python manage.py makemigrations admin_panel` to generate `0002_smtpsettings.py`.

[VERIFIED: admin_panel/models.py — AISettings singleton confirmed as the established pattern; admin_panel migrations directory exists with `0001_initial.py`]

### 5. SMTP Fernet Encryption: Key Source

The CONTEXT.md (D-05) specifies key from `HAVEN_SECRET_KEY` environment variable. The existing totp.py uses `settings.SECRET_KEY` (the FastAPI config). For the **Django side**, `settings.SECRET_KEY` is already available from `webui/config/settings.py` (which is the same `SECRET_KEY` env var). Use the same SHA-256 derivation pattern:

```python
# In admin_panel/views.py or a new admin_panel/crypto.py
import base64, hashlib
from cryptography.fernet import Fernet
from django.conf import settings

def _get_fernet() -> Fernet:
    key_bytes = hashlib.sha256(settings.SECRET_KEY.encode()).digest()
    return Fernet(base64.urlsafe_b64encode(key_bytes))

def encrypt_smtp_password(plaintext: str) -> str:
    return _get_fernet().encrypt(plaintext.encode()).decode()

def decrypt_smtp_password(ciphertext: str) -> str:
    return _get_fernet().decrypt(ciphertext.encode()).decode()
```

Note: CONTEXT.md D-05 names the variable `HAVEN_SECRET_KEY` but the actual env var is `SECRET_KEY` (used by both FastAPI and Django). If the user specifically wants a separate `HAVEN_SECRET_KEY`, the planner should flag this as a question. The current pattern uses `SECRET_KEY` for Fernet key derivation consistently across the stack.

[ASSUMED] Whether `HAVEN_SECRET_KEY` means a brand-new env var or refers to the existing `SECRET_KEY` — the codebase uses only `SECRET_KEY`; verify with user if a separate variable is intended.

### 6. SMTP Test Email: Sync Django → Async aiosmtplib

Django views are synchronous. `aiosmtplib.send()` is async. Use `asyncio.run()` to call it from the sync Django view — the same pattern that would apply here since aiosmtplib is already in the dependency list (not needed in Django's async request handling for this simple one-off test).

```python
import asyncio
import aiosmtplib
from email.mime.text import MIMEText

def smtp_test(request):
    # ... build settings ...
    async def _send():
        msg = MIMEText("Test email from Haven.")
        msg["From"] = smtp.smtp_from
        msg["To"] = request.user.email
        msg["Subject"] = "Haven SMTP Test"
        await aiosmtplib.send(msg, hostname=host, port=port, ...)
    asyncio.run(_send())
```

[VERIFIED: notify.py — aiosmtplib.send() pattern confirmed; aiosmtplib==5.1.0 in pyproject.toml]

### 7. HorizontalDivider: Exact Placement

`SettingsSection` is a `private` composable at the bottom of `SettingsScreen.kt` (line 1036). It renders a section title + content lambda. The divider is NOT inside SettingsSection — it is placed between sequential `SettingsSection { }` call sites inside each `CategoryCard` body.

Current structure in Detection card (Card 1):
```
CategoryCard {
  SettingsSection("Sensitivity") { ... }  // ← add HorizontalDivider AFTER
  SettingsSection("Camera") { ... }       // ← add HorizontalDivider AFTER
  SettingsSection("Detection") { ... }    // ← add HorizontalDivider AFTER
  ...
  SettingsSection("Light Suppress") { ... }  // ← NO divider (last section)
}
```

Cards with 1 section only (skip entirely per D-07): None found — all CategoryCards currently have 2+ SettingsSections.

`HorizontalDivider` is already imported via Material 3 but must be added as an explicit import if not present. Check: `SettingsScreen.kt` does not currently import `HorizontalDivider` explicitly (not visible in the imports list at the top of the file). Add: `import androidx.compose.material3.HorizontalDivider`.

[VERIFIED: SettingsScreen.kt lines 1–58 — `HorizontalDivider` is NOT in the current import list; needs to be added]

### 8. Device Delete: Swap Semantics

The revoke view returns the updated `device_row.html` partial (row with "Revoked" badge replacing "Active"). The delete view must **remove** the row entirely. Two approaches:

- **Option A:** Return an empty HTTP 200 with `hx-swap="outerHTML"` — HTMX replaces the `<tr>` with nothing, effectively deleting it.
- **Option B:** Return HTTP 200 with `Content-Type: text/html` and an empty string.

The HTMX docs support returning empty 200 for `outerHTML` swaps to remove elements. The button in the template targets `#device-{id}` which is the `<tr>` element.

[VERIFIED: device_row.html — `id="device-{{ device.id }}"` on `<tr>`; `hx-target="#device-{{ device.id }}"` + `hx-swap="outerHTML"` on existing revoke button confirms the swap mechanism works]

---

## Common Pitfalls

### Pitfall 1: Django Migration for Unmanaged Model

**What goes wrong:** Developer runs `python manage.py makemigrations events` expecting Django to add `is_archived` to the database.
**Why it happens:** `events/models.py` has `managed = False` — Django will accept the field in the Python model but will NOT generate a migration for it. The column must be added via Alembic. If only Django is updated without Alembic, queries referencing `is_archived` will fail at the DB level.
**How to avoid:** Always add `is_archived` to Alembic first, then add the field to the Django unmanaged model. No `makemigrations` is needed for the events app.
**Warning signs:** `column events.is_archived does not exist` in Django at runtime.

### Pitfall 2: Alpine.js State Lost on HTMX Partial Swap

**What goes wrong:** HTMX swaps `#event-table` with a new partial, but Alpine.js `selected` state (checkboxes) does not reset — or worse, Alpine loses its binding to the new DOM nodes.
**Why it happens:** HTMX replaces the inner DOM; Alpine.js must re-initialize on the new nodes.
**How to avoid:** Wrap the Alpine `x-data` on a container OUTSIDE `#event-table` (e.g., the parent `<div>` on the list page). After each HTMX swap, reset `selected = []` via the `htmx:afterSwap` event listener registered in Alpine.
**Warning signs:** Checkboxes remain visually checked after a bulk action completes, or the bulk action bar doesn't disappear.

### Pitfall 3: SMTP Test Email in Sync Django View

**What goes wrong:** `await aiosmtplib.send(...)` called without `asyncio.run()` in a sync view — raises `RuntimeError: no running event loop`.
**Why it happens:** Django views are sync by default; aiosmtplib requires an event loop.
**How to avoid:** Use `asyncio.run(_send_coroutine())`. Note: if Django is running with ASGI, use `async_to_sync` from `asgiref.sync`. For this project's WSGI setup, `asyncio.run()` is correct.
**Warning signs:** `RuntimeError` traceback when clicking "Send Test Email".

### Pitfall 4: Fernet Key Mismatch on SMTP Password

**What goes wrong:** SMTP password encrypted with one key, decrypted with another — `InvalidToken` exception at runtime.
**Why it happens:** If `HAVEN_SECRET_KEY` env var is introduced as a separate variable (per D-05 literal reading) but the Django view uses `settings.SECRET_KEY`, the keys will differ.
**How to avoid:** Use the same env var for both encryption and decryption (single source of truth). Clarify with the user whether `HAVEN_SECRET_KEY` is the same as `SECRET_KEY` or a new env var.
**Warning signs:** `cryptography.fernet.InvalidToken` when loading SMTP settings page after saving.

### Pitfall 5: CategoryCard with Single SettingsSection Gets Divider

**What goes wrong:** A divider is inserted after the only SettingsSection in a single-section CategoryCard (e.g., Card 2 — Recording has 3 sections; Card 5 — App has 3 sections; these are fine). But if the implementation uses a loop/index approach and has an off-by-one error, the last section gets a trailing divider.
**Why it happens:** D-07 specifies no divider after the LAST section. The loop must check `i < lastIndex`.
**How to avoid:** Use `forEachIndexed` or insert dividers explicitly between sections (before each section except the first). Manually verify by counting the SettingsSection blocks in each CategoryCard body after editing.
**Warning signs:** Unexpected divider appears at the bottom of a card.

### Pitfall 6: Bulk POST with CSRF

**What goes wrong:** HTMX POST for bulk archive/delete gets a 403 CSRF error.
**Why it happens:** Django CSRF protection requires the `X-CSRFToken` header or the `csrfmiddlewaretoken` field. HTMX does not add this automatically unless configured.
**How to avoid:** Add `hx-headers='{"X-CSRFToken": "{{ csrf_token }}"}'` on the form/element, OR configure HTMX globally in the base template with `document.addEventListener('htmx:configRequest', (e) => { e.detail.headers['X-CSRFToken'] = '{{ csrf_token }}'; })`. Check how existing HTMX POSTs handle CSRF — the revoke button uses `hx-post` and works, so the global configuration is already in place.
**Warning signs:** 403 Forbidden on bulk archive/delete POST.

---

## Code Examples

### HorizontalDivider Insertion (Android)

```kotlin
// Source: UI-SPEC.md §Component Inventory + CONTEXT.md D-06
// Between sequential SettingsSection blocks in a multi-section CategoryCard.
// Import needed: import androidx.compose.material3.HorizontalDivider

CategoryCard(title = stringResource(R.string.settings_cat_detection)) {
    SettingsSection(title = stringResource(R.string.settings_sensitivity_title)) {
        // ... content ...
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    SettingsSection(title = stringResource(R.string.settings_camera_title)) {
        // ... content ...
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    SettingsSection(title = stringResource(R.string.settings_detection_title)) {
        // ... content ...
    }
    // NO divider after last SettingsSection (D-07)
}
```

### WHY Comment Pattern (Kotlin — algorithmic hot spots)

```kotlin
// Source: UI-SPEC.md §Android Comment Standardization Contract
// Style: sentence fragments, English, explain WHY not WHAT

// alpha=0.7: gyro contributes 70% to the fused score — gyroscopes are accurate
// short-term but accumulate drift; the 30% accelerometer term corrects long-term drift.
// Net effect: table vibration (high accel delta, near-zero gyro) scores low;
// intentional movement (both high) scores high → fewer false positives.
val alpha: Float = 0.7f
```

### KDoc Pattern (Kotlin — public functions)

```kotlin
// Source: UI-SPEC.md §KDoc Rules + CONTEXT.md Claude's Discretion
// Only add @param where parameter name is insufficient

/**
 * Fuses accelerometer delta and gyroscope magnitude into a single motion score.
 *
 * @param accelDelta Euclidean distance between current and previous accelerometer sample.
 * @return Complementary-filter fused score: alpha × gyroMag + (1-alpha) × accelDelta.
 */
fun fuse(accelDelta: Float): Float =
    alpha * latestGyroMagnitude + (1f - alpha) * accelDelta
```

### Alembic Migration for is_archived

```python
# Source: server/alembic/versions/a3f2e1d4c5b6_add_pushover_app_token.py — pattern
"""add is_archived to events

Revision ID: <generate>
Revises: a3f2e1d4c5b6
Create Date: 2026-05-27
"""
import sqlalchemy as sa
from alembic import op

revision = "<generate>"
down_revision = "a3f2e1d4c5b6"

def upgrade() -> None:
    op.add_column(
        "events",
        sa.Column("is_archived", sa.Boolean(), nullable=False, server_default="false"),
    )

def downgrade() -> None:
    op.drop_column("events", "is_archived")
```

### SMTPSettings Singleton (Django — admin_panel/models.py)

```python
# Source: admin_panel/models.py — AISettings.get() pattern
class SMTPSettings(models.Model):
    smtp_host = models.CharField(max_length=255, blank=True, default="")
    smtp_port = models.IntegerField(default=587)
    smtp_user = models.CharField(max_length=255, blank=True, default="")
    smtp_password_encrypted = models.TextField(blank=True, default="")
    smtp_from = models.CharField(max_length=255, blank=True, default="haven@example.com")
    use_tls = models.BooleanField(default=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = "SMTP Settings"

    @classmethod
    def get(cls) -> "SMTPSettings":
        obj, _ = cls.objects.get_or_create(pk=1)
        return obj
```

### Device Delete View (devices/views.py)

```python
# Source: device_revoke() in devices/views.py — same pattern
@login_required
@require_POST
def device_delete(request, device_id):
    """Permanently delete a revoked device row. HTMX: returns empty 200 to remove <tr>."""
    device = get_object_or_404(Device, id=device_id, user_id=request.user.id)
    if device.is_active:
        return HttpResponseForbidden("Cannot delete an active device.")
    device.delete()
    if request.htmx:
        return HttpResponse("")  # empty response → outerHTML swap removes the <tr>
    messages.success(request, f"Device '{device.name}' permanently deleted.")
    return redirect("devices:device_list")
```

### Status Filter in event_list View

```python
# Source: events/views.py event_list() — extend existing filter logic
status = request.GET.get("status", "active")
base_qs = Event.objects.filter(user_id=request.user.id).select_related("device").order_by("-timestamp")
if status == "active":
    base_qs = base_qs.filter(is_archived=False)
elif status == "archived":
    base_qs = base_qs.filter(is_archived=True)
# status == "all": no filter
```

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|-----------------|--------|
| German inline comments in 6+ Kotlin files | English + WHY explanations | Readability for non-German speakers; algorithmic insight captured |
| No dividers between SettingsSections | HorizontalDivider between sections | Visual grouping clarity in long Settings cards |
| SMTP config only via environment variables | Admin SMTP settings UI in WebUI | Runtime reconfiguration without server restart (same pattern as AISettings) |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `HAVEN_SECRET_KEY` in D-05 refers to the existing `SECRET_KEY` env var (not a brand-new separate variable) | Critical Details §5 | Fernet InvalidToken errors at runtime if two different keys are used for encryption vs. decryption |
| A2 | All CategoryCards in SettingsScreen currently have 2+ SettingsSections (none are single-section cards that should be skipped) | Critical Details §7 | Dividers may be added where D-07 says to skip; harmless visually but violates the decision |
| A3 | The HTMX CSRF configuration is already global in base.html (since existing hx-post revoke button works without extra headers in templates) | Common Pitfalls §6 | 403 on bulk POST if CSRF header not set |

---

## Open Questions

1. **(RESOLVED) `HAVEN_SECRET_KEY` vs `SECRET_KEY`**
   - What we know: D-05 specifies `HAVEN_SECRET_KEY`. The existing codebase uses `SECRET_KEY` for Fernet derivation (totp.py). Both FastAPI and Django read `SECRET_KEY` from env.
   - Resolution: User confirmed — use `settings.SECRET_KEY` (Django's existing SECRET_KEY), consistent with `app/services/totp.py` SHA-256 derivation pattern. No new `HAVEN_SECRET_KEY` env var is introduced. D-05 named it informally; the actual key source is the existing `SECRET_KEY`.
   - Plan impact: Plan 03 `_get_fernet()` uses `settings.SECRET_KEY`; no Docker environment change needed.

2. **(RESOLVED) Bulk unarchive endpoint**
   - What we know: D-02 says "unarchive is supported." The Status filter includes "Active" view.
   - Resolution: `bulk_unarchive` view implemented (symmetric to `bulk_archive`). Plan 02 Task 3 adds an "Unarchive Events" button in the bulk action bar and a corresponding `x-ref="unarchiveForm"` hidden HTMX form targeting `#event-table`. The confirmation modal includes an 'unarchive' branch in title/body text alongside archive and delete.
   - Plan impact: Plan 02 Task 2 registers the `bulk_unarchive` view and URL route; Plan 02 Task 3 adds all template UI entry points.

3. **SMTP settings page link on admin dashboard**
   - What we know: UI-SPEC says to add a "SMTP Settings" nav link in `dashboard.html`.
   - What's unclear: Should SMTP Settings appear in the same navigation pattern as the existing "AI Settings" link, or in a separate "System" section?
   - Recommendation: Match the AI Settings link position exactly (same `<a>` element style in the dashboard action area).

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| `cryptography` (Fernet) | SMTP password encryption | ✓ | pinned in pyproject.toml | — |
| `aiosmtplib` | SMTP test send | ✓ | 5.1.0 | — |
| `django_filters` | EventFilter + is_archived filter | ✓ | 25.2 | — |
| `Alpine.js` | Bulk select state + modal | ✓ | loaded via CDN in base.html | — |
| `HTMX` | Inline partial swaps | ✓ | 2.x loaded via CDN in base.html | — |
| PostgreSQL | is_archived column migration | ✓ | running in Docker | — |
| `HorizontalDivider` (M3) | Android dividers | ✓ | bundled with Material 3 | — |

**Missing dependencies with no fallback:** None.
**Missing dependencies with fallback:** None.

---

## Security Domain

> `security_enforcement` not explicitly disabled in config.json — treating as enabled.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | All admin views already protected by `@admin_required` |
| V3 Session Management | no | Django sessions unchanged |
| V4 Access Control | yes | Device delete: verify `user_id=request.user.id` before delete (same as revoke); admin views: `@admin_required` |
| V5 Input Validation | yes | SMTP form fields: validate port is integer (1–65535), host is non-empty if saving, form validation in Django form `clean()` |
| V6 Cryptography | yes | Fernet for SMTP password: use SHA-256 key derivation (32 bytes → base64url), identical to totp.py pattern — do NOT hand-roll |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| CSRF on bulk POST | Tampering | HTMX global CSRF header (already configured for existing hx-post endpoints) |
| Unauthorized device deletion (wrong user's device) | Tampering | `get_object_or_404(Device, id=device_id, user_id=request.user.id)` — same as revoke |
| Unauthorized bulk archive/delete (wrong user's events) | Tampering | Filter bulk action queryset by `user_id=request.user.id` before update/delete |
| SMTP password in plaintext log | Information Disclosure | Never log the password field; log host/port only |
| Mass deletion via bulk delete | Elevation of Privilege | Confirm user owns all selected event_ids via queryset filter — do not trust client-supplied IDs without ownership check |

---

## Sources

### Primary (HIGH confidence)
- `server/webui/events/models.py` — confirmed `managed = False`; no migrations for events app
- `server/webui/admin_panel/models.py` — `AISettings.get_or_create(pk=1)` singleton pattern
- `server/webui/admin_panel/forms.py` — Django Form with Tailwind-styled widgets pattern
- `server/webui/admin_panel/views.py` — GET/POST with HTMX partial swap pattern
- `server/webui/admin_panel/templates/admin_panel/partials/ai_settings_form.html` — form partial with `hx-post`/`hx-target`/`hx-swap="innerHTML"`
- `server/app/services/totp.py` — Fernet key derivation from SECRET_KEY (SHA-256 → 32 bytes → base64url)
- `server/pyproject.toml` — confirmed: `cryptography`, `aiosmtplib==5.1.0` already present
- `server/webui/devices/templates/devices/partials/device_row.html` — `hx-confirm` + `hx-target` + `hx-swap="outerHTML"` pattern for revoke; confirmed deletion target is `#device-{id}` `<tr>`
- `server/webui/events/templates/events/list.html` — filter form with `hx-trigger="change"` pattern
- `server/webui/events/templates/events/partials/event_table.html` — table partial structure
- `app/src/main/java/org/havenapp/main/ui/settings/SettingsScreen.kt` — confirmed: `HorizontalDivider` NOT in import list; `SettingsSection` private composable at line 1036; all CategoryCards have 2+ sections
- `server/alembic/versions/a3f2e1d4c5b6_add_pushover_app_token.py` — Alembic `op.add_column` pattern for non-initial migrations
- `.planning/phases/08-android-webui-ux-polish/08-UI-SPEC.md` — approved visual and interaction contract

### Secondary (MEDIUM confidence)
- `server/app/config.py` — SMTP_HOST/PORT/USER/PASSWORD already exist as env-var config (FastAPI side); SMTP is already wired into `notify.py` — the WebUI admin page is the runtime configuration surface for these values (currently only configurable via env var restart)
- `server/app/models/event.py` — FastAPI SQLAlchemy Event model without `is_archived`; needs column addition symmetric with Alembic migration

---

## Metadata

**Confidence breakdown:**
- Android comment work: HIGH — all target files read; German comments identified; KDoc patterns confirmed from existing codebase
- Android HorizontalDivider: HIGH — composable structure confirmed; import gap identified; placement rule verified in UI-SPEC
- WebUI is_archived migration: HIGH — managed=False confirmed; Alembic pattern confirmed
- WebUI bulk actions: HIGH — Alpine.js/HTMX patterns confirmed; CSRF pattern needs verification (assumption A3)
- WebUI SMTP page: HIGH — AISettings pattern directly reusable; Fernet already in codebase; one open question on env var naming
- WebUI device delete: HIGH — revoke pattern directly reusable; swap semantics for row removal confirmed

**Research date:** 2026-05-27
**Valid until:** 2026-06-27 (stable stack; no fast-moving dependencies)
