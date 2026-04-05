---
status: partial
phase: 04-notification-engine
source: [04-VERIFICATION.md]
started: 2026-04-06T00:00:00Z
updated: 2026-04-06T00:00:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Settings Notifications Card Visual Layout
expected: Five CategoryCards visible. Notifications card shows Signal section (enable Switch + Configure button), Mattermost section (enable Switch + Configure button), NotificationRule section (severity radios LOW/MEDIUM/HIGH/CRITICAL, cooldown radios Off/1/5/15/30 min, trigger type checkboxes, Attach Media switch), and per-channel Heartbeat radio groups (Off/15/30/60 min).
result: [pending]

### 2. Signal Config Dialog Save and Persistence
expected: Tap Signal Configure, fill in server URL / sender / recipient / bearer token, tap Save. Button label updates to show the server URL. Values persist across process restart.
result: [pending]

### 3. Mattermost Config Dialog Save and Persistence
expected: Tap Mattermost Configure, enter webhook URL, tap Save. Button label updates to show the webhook URL. Value persists across navigation and restart.
result: [pending]

### 4. Log Level Switch Persistence and Effect
expected: Toggle Log Level to DEBUG → DiagnosticsScreen shows DEBUG-level entries. Toggle to Normal → DEBUG entries filtered. State persists across app restart.
result: [pending]

### 5. End-to-End Heartbeat Delivery (Signal)
expected: With signal-cli REST API configured and heartbeat set to 15 min, start monitoring. First heartbeat arrives within 15 minutes. After stopping monitoring, no further heartbeats arrive.
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps
