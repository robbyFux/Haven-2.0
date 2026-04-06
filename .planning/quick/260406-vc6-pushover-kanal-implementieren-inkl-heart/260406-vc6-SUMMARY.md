---
id: 260406-vc6
type: quick
date: 2026-04-06
status: complete
one_liner: "Pushover alert channel with multipart/form-data API, JPEG attachments, emergency priority, and heartbeat coroutine"
commits:
  - hash: 10168d7
    message: "feat(260406-vc6): add PushoverChannel implementing HavenAlertChannel"
  - hash: 5a92b0c
    message: "feat(260406-vc6): add Pushover settings persistence and ViewModel wiring"
  - hash: 380eca0
    message: "feat(260406-vc6): wire PushoverChannel into SettingsScreen and MonitorService"
key_files:
  created:
    - app/src/main/java/org/havenapp/main/notify/PushoverChannel.kt
  modified:
    - app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt
    - app/src/main/java/org/havenapp/main/ui/settings/SettingsViewModel.kt
    - app/src/main/java/org/havenapp/main/ui/settings/SettingsScreen.kt
    - app/src/main/java/org/havenapp/main/MonitorService.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-de/strings.xml
decisions:
  - "PushoverChannel is a plain Kotlin class (not Hilt singleton), instantiated per monitoring session with a settings snapshot — consistent with SignalRestChannel and MattermostChannel pattern"
  - "Priority mapping: LOW/MEDIUM→0, HIGH→1, CRITICAL→2; CRITICAL requires retry=60 and expire=3600 per Pushover emergency acknowledgment API"
  - "JPEG attachment passed as ByteArray to toRequestBody('image/jpeg'), filed as 'attachment' form field per Pushover attachment API"
  - "isEnabled = userKey.isNotBlank() && appToken.isNotBlank() — NotificationRouter skips channel automatically when false"
metrics:
  duration_min: 15
  tasks_completed: 3
  tasks_total: 3
  files_changed: 7
---

# Quick Task 260406-vc6: Pushover Channel Summary

## What Was Done

Implemented a Pushover alert channel following the existing MattermostChannel / SignalRestChannel pattern. Added 4 DataStore settings, ViewModel StateFlows, a Pushover section in the SettingsScreen Notifications card, and wired the channel + heartbeat coroutine into MonitorService.

## Tasks Completed

| Task | Name | Commit | Status |
|------|------|--------|--------|
| 1 | Create PushoverChannel.kt | 10168d7 | Done |
| 2 | Settings persistence and ViewModel wiring | 5a92b0c | Done |
| 3 | UI section, MonitorService wiring, and strings | 380eca0 | Done |

## Implementation Details

**PushoverChannel.kt** (`notify/` package):
- Implements `HavenAlertChannel` with `id = "pushover"`
- `send()`: OkHttp multipart/form-data POST to `https://api.pushover.net/1/messages.json`
- `sendHeartbeat()`: same endpoint, priority=0, title="[Haven] Heartbeat", no attachment
- Priority mapping: LOW/MEDIUM → "0", HIGH → "1", CRITICAL → "2" + retry=60 + expire=3600
- JPEG attachment via `toRequestBody("image/jpeg".toMediaType())` in "attachment" field
- `isEnabled = userKey.isNotBlank() && appToken.isNotBlank()`

**SettingsRepository.kt**:
- 4 new DataStore keys: `pushover_enabled`, `pushover_user_key`, `pushover_app_token`, `heartbeat_pushover_minutes`
- 4 Flows + 3 setters: `setPushoverEnabled()`, `setPushoverConfig()`, `setHeartbeatPushoverMinutes()`

**SettingsViewModel.kt**:
- 4 new StateFlows: `pushoverEnabled`, `pushoverUserKey`, `pushoverAppToken`, `heartbeatPushoverMinutes`
- 3 setters: `setPushoverEnabled()`, `setPushoverConfig()`, `setHeartbeatPushoverMinutes()`

**SettingsScreen.kt**:
- Pushover section in Card 4 (Notifications): Signal → Mattermost → Pushover → Alert rules order
- `PushoverConfigDialog`: two OutlinedTextFields (User Key, App Token)
- Heartbeat radio group: Off / 15 min / 30 min / 60 min

**MonitorService.kt**:
- Snapshots 4 Pushover settings at session start
- Adds `PushoverChannel(httpClient, pushoverAppToken, pushoverUserKey)` to `notifChannels` when enabled
- Heartbeat coroutine: same pattern as Signal/Mattermost (wait for ACTIVE, then delay loop)

**strings.xml (EN + DE)**: 7 new string resources for the Pushover UI section.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None. All Pushover settings flow from DataStore → ViewModel → UI → MonitorService and are fully wired.

## Self-Check: PASSED

Files exist:
- `app/src/main/java/org/havenapp/main/notify/PushoverChannel.kt` — FOUND
- `app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt` — FOUND (modified)
- `app/src/main/java/org/havenapp/main/ui/settings/SettingsViewModel.kt` — FOUND (modified)
- `app/src/main/java/org/havenapp/main/ui/settings/SettingsScreen.kt` — FOUND (modified)
- `app/src/main/java/org/havenapp/main/MonitorService.kt` — FOUND (modified)

Commits exist:
- 10168d7 — FOUND
- 5a92b0c — FOUND
- 380eca0 — FOUND

Build: `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL (0 errors)
