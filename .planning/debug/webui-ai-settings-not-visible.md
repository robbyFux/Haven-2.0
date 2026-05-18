---
status: awaiting_human_verify
trigger: "AI Analysis settings page not visible in the WebUI after fix"
created: 2026-04-13T00:00:00Z
updated: 2026-04-13T00:00:00Z
---

## Current Focus

hypothesis: AISettings model has no Django migration; manage.py migrate never runs in Dockerfile.webui; table does not exist; view 500s on AISettings.get()
test: confirmed — no migrations/ dir in admin_panel, no migrate call in Dockerfile.webui
expecting: creating migration + adding migrate to Dockerfile.webui will fix the 500
next_action: create migration file, update Dockerfile.webui

## Symptoms

expected: Admin users can access AI Analysis settings via the WebUI (at /admin/ai-settings/ or linked from admin dashboard)
actual: The AI Analysis settings page does not appear — navigating to /admin/ai-settings/ causes a 500 (relation "admin_panel_aisettings" does not exist)
errors: none reported by user (but server logs would show ProgrammingError)
reproduction: Log in as admin → navigate to /admin/ → click AI Settings
started: Fix was just applied (commit a0d3eb6) — never worked

## Eliminated

- hypothesis: URL not registered in urls.py
  evidence: admin_panel/urls.py has path("ai-settings/", views.ai_settings, name="ai_settings") at line 18
  timestamp: 2026-04-13T00:00:00Z

- hypothesis: admin_panel not included in root urls.py
  evidence: config/urls.py includes path("admin/", include("admin_panel.urls")) at line 16
  timestamp: 2026-04-13T00:00:00Z

- hypothesis: nav/dashboard does not link to AI Settings
  evidence: nav.html has /admin/ link for is_admin users; dashboard.html has AI Settings quick-link at line 60
  timestamp: 2026-04-13T00:00:00Z

- hypothesis: permission check blocks access
  evidence: view uses @admin_required (same as all other admin views that work); not the blocker
  timestamp: 2026-04-13T00:00:00Z

## Evidence

- timestamp: 2026-04-13T00:00:00Z
  checked: server/webui/admin_panel/ directory structure
  found: no migrations/ subdirectory exists
  implication: Django has never created the admin_panel_aisettings table

- timestamp: 2026-04-13T00:00:00Z
  checked: all other webui models (accounts, events, devices)
  found: all use managed=False — they map to Alembic-managed tables and need no Django migrations
  implication: no migrations/ dirs exist anywhere in webui; the pattern is unmanaged throughout except AISettings

- timestamp: 2026-04-13T00:00:00Z
  checked: server/Dockerfile.webui
  found: runs tailwind build + collectstatic but NO manage.py migrate
  implication: even if migrations existed, they would never be applied on container start

- timestamp: 2026-04-13T00:00:00Z
  checked: admin_panel/models.py AISettings class
  found: managed=True (default), no Meta.managed = False — Django owns this table's DDL
  implication: requires Django migration + migrate call; table does not exist in DB

## Resolution

root_cause: AISettings Django model (managed=True) has no migration file and manage.py migrate is not called in Dockerfile.webui. The admin_panel_aisettings table is never created, so AISettings.get() in the view raises ProgrammingError: relation "admin_panel_aisettings" does not exist.
fix: (1) create admin_panel/migrations/0001_initial.py, (2) add `RUN python manage.py migrate --noinput` to Dockerfile.webui before collectstatic
verification: []
files_changed: []
