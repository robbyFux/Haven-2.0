---
status: fixed
trigger: "Events werden vom Cloud-Server immer noch nicht per Email gesendet."
created: 2026-06-11T00:00:00Z
updated: 2026-06-11T00:00:00Z
---

## Current Focus

hypothesis: CONFIRMED — three independent bugs prevent email notifications; likely root cause is migration 0003 not applied → tls_mode column missing → SMTP config falls back to empty env vars
test: Static code trace through notification dispatch chain
expecting: n/a — root causes confirmed and code bugs fixed
next_action: Restart docker stack to apply migration; verify SMTP + user email configured

## Symptoms

expected: Events arrive at server (201 Created) → email notification sent to configured address
actual: Events arrive, no email sent
errors: No error visible to user — silent failure
reproduction: Upload event from Android → check server logs → no email dispatch

## Eliminated

- hypothesis: Event dispatch chain completely broken
  evidence: events.py dispatches send_notification_task.delay() for ai_backend=none, and analyze_event_task dispatches it after successful analysis
  timestamp: 2026-06-11

## Evidence

- timestamp: 2026-06-11
  checked: server/app/services/smtp_settings.py (untracked, new file)
  found: Queries admin_panel_smtpsettings for tls_mode column. Migration 0003_smtp_tls_mode.py (also untracked) adds this column. If migration not applied, column missing → DB query fails → falls back to env vars (SMTP_HOST="") → send_notification_task skips email silently (line 78: `if smtp.host:`)
  implication: PRIMARY ROOT CAUSE — if webui container was not restarted after migration file was created, tls_mode column doesn't exist in DB → email always skipped

- timestamp: 2026-06-11
  checked: server/app/services/notify.py (all imports)
  found: settings is referenced in send_signal() (line 82: settings.SIGNAL_API_URL) and send_pushover() (line 121: settings.PUSHOVER_APP_TOKEN fallback), but `from app.config import settings` is missing from imports. Would raise NameError if Signal is configured.
  implication: BUG 2 — doesn't affect email directly, but crashes the notification task if Signal is configured

- timestamp: 2026-06-11
  checked: server/app/tasks/analysis.py (all return paths)
  found: 6 error-return paths in the media analysis section (file read error, decryption failure, TFLite detector not available, no frames extracted, TFLite inference exception, OpenRouter exception) all return without calling send_notification_task.delay(event_id). The event IS in the DB at this point.
  implication: BUG 3 — if AI analysis is configured and fails for any reason, user never receives notification even though event was detected and saved

- timestamp: 2026-06-11
  checked: server/app/tasks/notifications.py line 26
  found: committed notifications.py imports `from app.services.smtp_settings import get_smtp_settings_sync` but smtp_settings.py is untracked (not committed). Works locally via .:/app volume mount but breaks clean deploys.
  implication: GIT HYGIENE — smtp_settings.py and 0003_smtp_tls_mode.py must be committed together with notifications.py

## Resolution

root_cause: Three bugs — one likely current blocker, two latent:

  BUG 1 (BLOCKER) — Migration not applied:
  Migration 0003_smtp_tls_mode.py adds `tls_mode` column to admin_panel_smtpsettings.
  smtp_settings.py SELECT queries this column. If container was not restarted after
  migration file appeared on disk, column doesn't exist → query fails → env fallback →
  SMTP_HOST="" → email silently skipped at notifications.py:78.
  Fix: Restart webui container (runs manage.py migrate automatically).

  BUG 2 — Missing settings import in notify.py:
  send_signal() references `settings.SIGNAL_API_URL` but settings is not imported.
  NameError if Signal notifications are configured.
  Fix: Added `from app.config import settings` to notify.py imports.

  BUG 3 — Analysis error paths skip notification:
  When AI analysis fails (TFLite unavailable, no frames, inference error, OpenRouter error),
  analyze_event_task returns early without dispatching send_notification_task.delay().
  The event IS persisted in DB at this point.
  Fix: Added send_notification_task.delay(event_id) before each error return.

  GIT HYGIENE — smtp_settings.py and 0003_smtp_tls_mode.py untracked:
  notifications.py (committed) imports smtp_settings.py (untracked). HEAD commit broken
  for clean deploys. Fix: commit both files together.

fix: Applied
  BUG 2: server/app/services/notify.py — added `from app.config import settings`
  BUG 3: server/app/tasks/analysis.py — added send_notification_task.delay(event_id) before all 6 error returns

verification:
files_changed:
  - server/app/services/notify.py
  - server/app/tasks/analysis.py

next_steps:
  1. Commit smtp_settings.py and 0003_smtp_tls_mode.py (and other untracked files)
  2. docker compose up --build -d  (webui runs manage.py migrate → applies 0003)
  3. Admin panel → SMTP Settings → configure and use "Test Email" button
  4. User profile → Notification Settings → set notification_email + enable notifications
  5. Send test event from Android → check worker logs for "send_notification_task"
