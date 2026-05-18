---
status: awaiting_human_verify
trigger: "AI analysis does not trigger when an event is uploaded to the Haven cloud server"
created: 2026-04-13T00:00:00Z
updated: 2026-04-13T00:00:00Z
---

## Current Focus

hypothesis: analyze_event_task and the events router both read AI_BACKEND from config.py (env var, default "none"). The WebUI writes to admin_panel_aisettings (Django table) which FastAPI never reads. app/services/ai_settings.py referenced in the model docstring does not exist.
test: Confirmed by reading events.py:206, analysis.py:48, config.py:28, admin_panel/models.py
expecting: Fix: create ai_settings.py that reads from admin_panel_aisettings via raw SQL; update router and task to use it
next_action: implement fix

## Symptoms

expected: When event uploaded, analyze_event_task fires and stores AnalysisResult
actual: No AI analysis happens — no AnalysisResult rows created
errors: none
reproduction: Upload event → check event detail → no AI analysis results
started: Since AI settings WebUI was added (DB-backed config disconnected from FastAPI)

## Eliminated

- hypothesis: analyze_event_task is not being called at all
  evidence: events.py:206 shows it IS called when settings.AI_BACKEND != "none", but AI_BACKEND env default is "none" so the call is skipped entirely
  timestamp: 2026-04-13

## Evidence

- timestamp: 2026-04-13
  checked: server/app/config.py
  found: AI_BACKEND defaults to "none" (line 28); read once at import time via pydantic_settings
  implication: Unless env var AI_BACKEND is set, FastAPI always skips analysis

- timestamp: 2026-04-13
  checked: server/app/routers/events.py:206
  found: `if settings.AI_BACKEND != "none":` — uses module-level settings singleton
  implication: Router dispatch decision never reads from DB

- timestamp: 2026-04-13
  checked: server/app/tasks/analysis.py:48
  found: `if settings.AI_BACKEND == "none": return {"status": "skipped"}` — same env-based check
  implication: Task also bypasses analysis when env var is not set

- timestamp: 2026-04-13
  checked: server/webui/admin_panel/models.py
  found: AISettings Django model writes to admin_panel_aisettings table; docstring claims app/services/ai_settings.py exists as bridge
  implication: That file does NOT exist (confirmed by glob of server/app/services/)

- timestamp: 2026-04-13
  checked: server/app/services/ directory
  found: No ai_settings.py present
  implication: The described integration was never implemented

## Resolution

root_cause: Two code paths (events router dispatch + analyze_event_task early-exit) both read AI_BACKEND from config.py (env var, default "none"). The WebUI writes ai_backend to admin_panel_aisettings Django table. FastAPI never reads that table. app/services/ai_settings.py bridge described in model docstring was never created.
fix: Create app/services/ai_settings.py with get_ai_backend() that queries admin_panel_aisettings via raw async SQL, falling back to settings.AI_BACKEND. Update events.py and analysis.py to call get_ai_backend() instead of reading settings.AI_BACKEND directly.
verification: 54/57 tests pass; 3 pre-existing failures unrelated to this change (MEDIA_ROOT /app permission + pushover test setup). 9 new tests added and all green.
files_changed:
  - server/app/services/ai_settings.py (new)
  - server/app/routers/events.py (line 206: use get_ai_backend())
  - server/app/tasks/analysis.py (lines 48, 83, 100, 119: use get_ai_backend())
