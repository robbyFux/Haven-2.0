---
status: diagnosed
trigger: "Die Profilseite des Nutzers in der Cloud Web UI ist nicht erreichbar. Passwort ändern und 2FA einrichten funktionieren deshalb nicht."
created: 2026-04-09T00:00:00Z
updated: 2026-04-09T00:01:00Z
---

## Current Focus

hypothesis: CONFIRMED — nav.html has no link to /accounts/profile/, making the page unreachable via the UI
test: Read nav.html fully
expecting: Missing profile link
next_action: DONE — root cause identified

## Symptoms

expected: Nutzer kann unter /accounts/profile/ (oder ähnlicher URL) seine Profilseite aufrufen, Passwort ändern und 2FA einrichten.
actual: Profilseite nicht erreichbar — unklar ob 404, Redirect-Loop, oder fehlender Nav-Link.
errors: Keine bekannte Fehlermeldung.
reproduction: In der Cloud Web UI navigieren und versuchen die Profilseite zu erreichen.
started: Unklar ob es je funktioniert hat.

## Eliminated

- hypothesis: URL route missing in accounts/urls.py
  evidence: path("profile/", views.profile_view, name="profile") exists at line 15
  timestamp: 2026-04-09T00:01:00Z

- hypothesis: View function missing or broken
  evidence: profile_view exists in views.py at line 199, correctly decorated with @login_required
  timestamp: 2026-04-09T00:01:00Z

- hypothesis: Template file missing
  evidence: accounts/templates/accounts/profile.html exists and is complete
  timestamp: 2026-04-09T00:01:00Z

- hypothesis: accounts URLs not included in root config
  evidence: path("accounts/", include("accounts.urls")) exists in config/urls.py at line 13
  timestamp: 2026-04-09T00:01:00Z

## Evidence

- timestamp: 2026-04-09T00:01:00Z
  checked: server/webui/accounts/urls.py
  found: All 8 URL patterns defined including profile/, change-password/, 2fa-setup/, delete-account/
  implication: Routing layer is complete

- timestamp: 2026-04-09T00:01:00Z
  checked: server/webui/accounts/views.py
  found: profile_view, change_password_view, totp_setup_view, delete_account_view all exist and are @login_required
  implication: View layer is complete

- timestamp: 2026-04-09T00:01:00Z
  checked: server/webui/accounts/templates/accounts/
  found: profile.html, change_password.html, totp_setup.html, delete_account.html all exist
  implication: Template layer is complete

- timestamp: 2026-04-09T00:01:00Z
  checked: server/webui/core/templates/partials/nav.html
  found: The authenticated user menu (lines 44-54) shows username as plain <span> text and a Logout button — NO link to /accounts/profile/. The username is not clickable.
  implication: THIS IS THE ROOT CAUSE. Users have no navigation path to their profile page.

- timestamp: 2026-04-09T00:01:00Z
  checked: server/webui/core/templates/base.html
  found: Includes partials/nav.html and renders messages. No other profile link exists anywhere in the base layout.
  implication: Confirms the only entry point to the profile (nav) is missing the link.

## Resolution

root_cause: The username display in nav.html (line 46) is a plain <span> element — it is not a link to /accounts/profile/. There is no navigation entry point to the profile page anywhere in the UI. The page exists at /accounts/profile/ and works correctly (URL route + view + template all complete), but users cannot discover or reach it through normal navigation.
fix: In nav.html, replace `<span class="text-gray-400">{{ user.username }}</span>` with `<a href="/accounts/profile/" class="text-gray-400 hover:text-teal-400 transition-colors">{{ user.username }}</a>` so the username becomes a clickable link to the profile page.
verification:
files_changed:
  - server/webui/core/templates/partials/nav.html
