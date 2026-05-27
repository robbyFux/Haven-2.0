# Phase 8: Android & WebUI UX Polish - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-27
**Phase:** 8-android-webui-ux-polish
**Areas discussed:** Archived events filter UI, SMTP config placement, Settings divider granularity, Revoked device delete safety

---

## Archived Events Filter UI

### Q1: How should the Active / Archived split be presented?

| Option | Description | Selected |
|--------|-------------|----------|
| Tab pair (Active \| Archived) | Two tabs above the event table | |
| Filter dropdown option | Add 'Status: Active / Archived / All' to existing filter bar | ✓ |
| Toggle checkbox | Single checkbox 'Show archived' | |

**User's choice:** Filter dropdown option
**Notes:** Stays consistent with the current filter UX — no new UI component needed.

### Q2: What does bulk 'Archive' do? Is it reversible?

| Option | Description | Selected |
|--------|-------------|----------|
| Soft-hide, reversible | Sets is_archived=True; admin can unarchive | ✓ |
| Soft-hide, permanent | Once archived, events stay hidden permanently | |
| Alias for delete | Archive = delete | |

**User's choice:** Soft-hide, reversible
**Notes:** Events stay in DB, just hidden from the default Active view.

### Q3: Bulk action confirmation dialog style?

| Option | Description | Selected |
|--------|-------------|----------|
| Alpine.js modal | Dark overlay modal showing count of affected events | ✓ |
| Browser native confirm() | window.confirm() — zero extra code but breaks dark theme | |
| HTMX inline confirmation | Inline "Really delete N events? [Confirm]" swap | |

**User's choice:** Alpine.js modal
**Notes:** Alpine.js already in the stack from Phase 6; consistent with dark theme.

---

## SMTP Config Placement

### Q1: Where should SMTP configuration live?

| Option | Description | Selected |
|--------|-------------|----------|
| New page in admin_panel | admin_panel/settings/ — dedicated admin settings page | ✓ |
| In the notifications app | Add to existing notifications settings page | |
| Admin dashboard addition | Add SMTP section to existing admin dashboard | |

**User's choice:** New page in admin_panel/settings/
**Notes:** SMTP is system-level (not per-user), so admin_panel is the right home.

### Q2: How should SMTP credentials be encrypted in the DB?

| Option | Description | Selected |
|--------|-------------|----------|
| Fernet symmetric key from env var | HAVEN_SECRET_KEY env var; cryptography.fernet | ✓ |
| Same Argon2id as user data | Per-user key derivation doesn't apply to system config | |
| Plaintext (no encryption) | Simple but contradicts encryption-first posture | |

**User's choice:** Fernet symmetric key from HAVEN_SECRET_KEY env var
**Notes:** Simple, proven, doesn't depend on per-user passwords.

---

## Settings Divider Granularity

### Q1: Where exactly should HorizontalDivider appear in a CategoryCard?

| Option | Description | Selected |
|--------|-------------|----------|
| Between SettingsSections (labeled groups) | Divider between logical groups (Sensitivity / Camera / Detection Mode) | ✓ |
| Between individual rows | Divider between every RadioRow, SensorToggleRow, etc. | |
| Only between CategoryCards | No intra-card dividers | |

**User's choice:** Between SettingsSections (labeled groups)
**Notes:** Visually separates logical groups without making the UI overly dense.

### Q2: Should dividers be added to ALL CategoryCards or only multi-section ones?

| Option | Description | Selected |
|--------|-------------|----------|
| Only cards with 2+ sections | Skip single-section cards — matches success criterion "multiple items" | ✓ |
| All CategoryCards unconditionally | Simpler implementation but adds no-op dividers | |

**User's choice:** Only cards with 2+ SettingsSections
**Notes:** Matches the ROADMAP success criterion exactly.

---

## Revoked Device Delete Safety

### Q1: Should Delete include a confirmation step?

| Option | Description | Selected |
|--------|-------------|----------|
| hx-confirm dialog | Browser confirm dialog — same pattern as existing Revoke button | ✓ |
| Direct delete, no confirmation | HTMX POST fires immediately on click | |
| Alpine.js modal confirmation | Styled modal — overkill for single-item action | |

**User's choice:** hx-confirm dialog
**Notes:** Consistent with existing Revoke button; zero extra code beyond the attribute.

---

## Claude's Discretion

- Comment translation scope: all Kotlin files with German inline comments (not just the 6 hot spots)
- KDoc completeness: add @param/@return only where genuinely useful, not boilerplate
- Bulk select HTMX/Alpine wiring implementation detail
- SMTP admin form exact field layout and "Test connection" button
- Divider styling: `padding(vertical = 8.dp)` suggested but open to adjustment

## Deferred Ideas

None — discussion stayed within phase scope.
