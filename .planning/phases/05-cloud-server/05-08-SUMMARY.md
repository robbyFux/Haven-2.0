---
phase: 05-cloud-server
plan: "08"
subsystem: notifications
tags: [android, kotlin, okhttp, datastore, compose, multipart-upload, cloud-channel]
dependency_graph:
  requires:
    - app/src/main/java/org/havenapp/main/notify/HavenAlertChannel.kt (interface)
    - app/src/main/java/org/havenapp/main/notify/PushoverChannel.kt (implementation pattern)
    - app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt (DataStore key pattern)
    - app/src/main/java/org/havenapp/main/ui/settings/SettingsViewModel.kt (StateFlow pattern)
    - app/src/main/java/org/havenapp/main/MonitorService.kt (channel wiring in buildList)
    - server/app/routers/events.py (POST /api/v1/devices/{app_key}/events — plan 05-04)
    - server/app/routers/devices.py (POST /api/v1/devices/{app_key}/heartbeat — plan 05-04)
  provides:
    - app/src/main/java/org/havenapp/main/notify/CloudChannel.kt (HavenAlertChannel for cloud upload)
  affects:
    - app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt (3 new DataStore keys)
    - app/src/main/java/org/havenapp/main/ui/settings/SettingsViewModel.kt (3 StateFlows + 3 setters)
    - app/src/main/java/org/havenapp/main/ui/settings/SettingsScreen.kt (Cloud Server section + dialog)
    - app/src/main/java/org/havenapp/main/MonitorService.kt (CloudChannel wired in notifChannels)
    - app/src/main/res/values/strings.xml (7 cloud string resources)
    - app/src/main/res/values-de/strings.xml (7 German cloud string resources)
tech_stack:
  added: []
  patterns:
    - OkHttp MultipartBody for event upload (same as PushoverChannel)
    - withContext(Dispatchers.IO) + runCatching for suspend channel methods
    - java.time.Instant.ofEpochMilli for ISO-8601 timestamp formatting
    - Individual ViewModel StateFlows (not in SettingsUiState) for notification channel settings
    - Settings snapshot via .first() in MonitorService.startMonitoring()
key_files:
  created:
    - app/src/main/java/org/havenapp/main/notify/CloudChannel.kt
  modified:
    - app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt
    - app/src/main/java/org/havenapp/main/ui/settings/SettingsViewModel.kt
    - app/src/main/java/org/havenapp/main/ui/settings/SettingsScreen.kt
    - app/src/main/java/org/havenapp/main/MonitorService.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-de/strings.xml
decisions:
  - "CloudChannel uses X-App-Key header (not Bearer token) matching the server API contract from plan 05-04"
  - "No per-channel heartbeat coroutine added to MonitorService — CloudChannel.sendHeartbeat() exists for interface compliance but is not scheduled; matches Mattermost pattern"
  - "Cloud settings exposed as individual ViewModel StateFlows (not in SettingsUiState) — consistent with Pushover/Signal/Mattermost pattern, avoids bloating the combined data class"
metrics:
  duration_minutes: 15
  completed_date: "2026-04-07"
  tasks_completed: 2
  files_created: 1
  files_modified: 6
---

# Phase 05 Plan 08: Android CloudChannel Upload Client + Settings UI Summary

**One-liner:** CloudChannel implements HavenAlertChannel with OkHttp multipart upload to POST /api/v1/devices/{appKey}/events and JSON heartbeat, wired into MonitorService and configurable via a new Cloud Server section in SettingsScreen.

## What Was Built

### Task 1: CloudChannel.kt + DataStore keys in SettingsRepository

**app/src/main/java/org/havenapp/main/notify/CloudChannel.kt** — new HavenAlertChannel:
- Constructor: `CloudChannel(httpClient: OkHttpClient, serverUrl: String, appKey: String)`
- `id = "cloud"`, `isEnabled = serverUrl.isNotBlank() && appKey.isNotBlank()`
- `send()`: multipart/form-data POST to `{serverUrl}/api/v1/devices/{appKey}/events` with fields: `event_type`, `severity`, `timestamp` (ISO-8601 via `java.time.Instant`), `sensor_value`; optional `media` JPEG field when attachment non-null; `X-App-Key` auth header
- `sendHeartbeat()`: JSON POST to `{serverUrl}/api/v1/devices/{appKey}/heartbeat` with `{"message":"..."}` body and `X-App-Key` header; message is double-quote escaped
- Both methods use `withContext(Dispatchers.IO) { runCatching { ... } }` matching PushoverChannel pattern

**app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt** — three new DataStore keys:
- `KEY_CLOUD_ENABLED` (boolean), `KEY_CLOUD_SERVER_URL` (string), `KEY_CLOUD_APP_KEY` (string)
- `cloudEnabled`, `cloudServerUrl`, `cloudAppKey` Flow properties
- `setCloudEnabled()`, `setCloudServerUrl()`, `setCloudAppKey()` suspend setters

### Task 2: SettingsViewModel + SettingsScreen UI + MonitorService wiring + strings

**app/src/main/java/org/havenapp/main/ui/settings/SettingsViewModel.kt**:
- `cloudEnabled`, `cloudServerUrl`, `cloudAppKey` as `StateFlow` via `stateIn(WhileSubscribed(5_000))`
- `setCloudEnabled()`, `setCloudServerUrl()`, `setCloudAppKey()` setters

**app/src/main/java/org/havenapp/main/ui/settings/SettingsScreen.kt**:
- State collection: `cloudEnabled`, `cloudServerUrl`, `cloudAppKey` via `collectAsStateWithLifecycle()`
- `var showCloudDialog` dialog state
- Cloud Server `SettingsSection` in the Notifications card: toggle row + conditional `OutlinedButton` showing configured URL or "Not configured"
- `CloudConfigDialog` invocation after PushoverConfigDialog
- `CloudConfigDialog` private composable: two `OutlinedTextField` inputs (Server URL + App-Key), Save/Cancel buttons — mirrors PushoverConfigDialog structure

**app/src/main/java/org/havenapp/main/MonitorService.kt**:
- Import `CloudChannel` added
- Settings snapshot: `cloudEnabled`, `cloudServerUrl`, `cloudAppKey` read via `.first()`
- `if (cloudEnabled) { add(CloudChannel(httpClient, cloudServerUrl, cloudAppKey)) }` in `buildList<HavenAlertChannel>`

**String resources (EN + DE):** `settings_cloud_title`, `settings_cloud_configure`, `settings_cloud_enabled`, `settings_cloud_server_url`, `settings_cloud_app_key`, `settings_cloud_configured`, `settings_cloud_not_configured`

## Verification Results

```
./gradlew :app:compileDebugKotlin
BUILD SUCCESSFUL in 840ms
16 actionable tasks: 16 up-to-date
```

All acceptance criteria checks passed:
- `CloudChannel` class with `id = "cloud"` and `X-App-Key` header exists
- `api/v1/devices` URL pattern in CloudChannel
- `sendHeartbeat` method present
- All three DataStore keys in SettingsRepository
- All cloud flows and setters present
- SettingsViewModel StateFlows and setters present
- SettingsScreen Cloud Server section and CloudConfigDialog present
- MonitorService CloudChannel import and wiring present
- EN and DE string resources complete

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — CloudChannel is fully implemented. The upload path is wired end-to-end: MonitorService reads settings, constructs CloudChannel, NotificationRouter calls `send()` on trigger events.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: credentials-in-datastore | app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt | `cloud_app_key` stored as plain DataStore string. Not encrypted at rest until Phase 5 Keystore integration. Consistent with existing signal_bearer_token, pushover_app_token pattern — acceptable for MVP. |

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 | `24606c3` | feat(05-08): CloudChannel.kt + DataStore keys in SettingsRepository |
| Task 2 | `e0628b1` | feat(05-08): SettingsViewModel + SettingsScreen Cloud UI + MonitorService wiring + strings |

## Self-Check: PASSED
