---
quick_id: 260406-c5e
type: execute
date: 2026-04-06
duration_min: 5
tasks_completed: 2
files_modified: 2
commits:
  - f67ba73
  - e9d56ca
tags: [bug-fix, notification, signal, ui-validation]
---

# Quick Task 260406-c5e: SignalIntentChannel notification+PendingIntent fix

## One-liner

Replaced direct `startActivity()` (silently blocked on Android 10+) with a local notification whose
`contentIntent` opens the Signal conversation via `Intent.ACTION_VIEW` deep link, and added E.164
format validation to the config dialog.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Replace startActivity with notification+PendingIntent | f67ba73 | `notify/SignalIntentChannel.kt` |
| 2 | Add E.164 validation to SignalIntentConfigDialog | e9d56ca | `ui/settings/SettingsScreen.kt` |

## Changes Made

### Task 1 — SignalIntentChannel.kt

**Bug fixed:** `context.startActivity()` from a background service is silently dropped on Android
10+ due to background activity start restrictions. This meant alerts were never delivered.

**Fix:** Post a local `NotificationManager` notification instead. The notification's `contentIntent`
is a `PendingIntent` wrapping `Intent.ACTION_VIEW` with the Signal deep link
`https://signal.me/#p/$recipientNumber`. Tapping the notification is a user gesture exempt from
the restriction.

**Also fixed:** The old code used `Intent.EXTRA_EMAIL` which Signal ignores entirely. The new
deep-link approach opens the correct conversation directly.

Key implementation details:
- `NotificationChannel(CHANNEL_ID, "Signal (App) Alerts", IMPORTANCE_HIGH)` created on first call
- `PendingIntent.FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE` for Android 12+ compatibility
- `NotificationCompat.BigTextStyle` for multi-line alert text in expanded view
- `nm.notify(event.timestamp.toInt(), ...)` — timestamp as notification ID prevents duplicates
  from replacing each other when events fire in quick succession

### Task 2 — SettingsScreen.kt (SignalIntentConfigDialog)

Added live E.164 format validation:
- `isValidE164 = recipient.matches(Regex("^\\+[1-9]\\d{6,14}\$"))`
- Red error text appears below TextField when input is non-empty and invalid
- Save button disabled (`enabled = isValidE164`) — requires a valid number to save

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Self-Check

- [x] `SignalIntentChannel.kt` — file exists, rewrites complete
- [x] `SettingsScreen.kt` — E.164 validation added
- [x] Commit f67ba73 exists
- [x] Commit e9d56ca exists
- [x] `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL (only pre-existing deprecation warning in MonitorService.kt)
