---
phase: 04-notification-engine
plan: 01
subsystem: notify
tags: [okhttp, hilt, datastore, notification, signal, mattermost, applogger]

# Dependency graph
requires:
  - phase: 03-phase-2-complete
    provides: SettingsRepository, AppLogger, TriggerEvent, Severity, TriggerType
provides:
  - HavenAlertChannel interface (notify package)
  - NotificationRule data class (notify package)
  - OkHttpClient singleton via NetworkModule (Hilt)
  - 14 new DataStore keys in SettingsRepository (Signal/Mattermost/Rule/Heartbeat/LogLevel)
  - AppLogger.LogLevel enum with DEBUG-filtering at capture layer
affects:
  - 04-02 (SignalRestChannel implementation uses HavenAlertChannel, OkHttpClient, DataStore flows)
  - 04-03 (MattermostChannel implementation uses same foundation)
  - 04-04 (NotificationRouter uses NotificationRule and channel list)
  - 04-05 (Settings UI reads/writes all new DataStore flows)

# Tech tracking
tech-stack:
  added:
    - OkHttp 4.12.0 (com.squareup.okhttp3:okhttp)
  patterns:
    - HavenAlertChannel interface avoids android.app.NotificationChannel name collision
    - DataStore fields (not serialized object) for NotificationRule persistence
    - @Volatile currentLogLevel field for thread-safe LogLevel reads without @Synchronized

key-files:
  created:
    - app/src/main/java/org/havenapp/main/notify/HavenAlertChannel.kt
    - app/src/main/java/org/havenapp/main/notify/NotificationRule.kt
    - app/src/main/java/org/havenapp/main/di/NetworkModule.kt
  modified:
    - gradle/libs.versions.toml
    - app/build.gradle.kts
    - app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt
    - app/src/main/java/org/havenapp/main/storage/AppLogger.kt

key-decisions:
  - "HavenAlertChannel named to avoid android.app.NotificationChannel import clash in MonitorService"
  - "NotificationRule fields persisted as individual DataStore keys (not serialized), matching existing SettingsRepository pattern"
  - "AppLogger.LogLevel uses @Volatile (not @Synchronized) for currentLogLevel: read-only in hot path, write is rare"

patterns-established:
  - "notify/ package established as home for all outbound alert abstractions"
  - "NetworkModule in di/ provides OkHttpClient; channel implementations inject it"
  - "New DataStore keys grouped by feature section in SettingsRepository companion object"

requirements-completed: [NOTIF-01, NOTIF-04]

# Metrics
duration: 8min
completed: 2026-04-05
---

# Phase 4 Plan 01: NotificationEngine Foundation Summary

**HavenAlertChannel interface + NotificationRule data class + OkHttp singleton + 14 DataStore keys for Signal/Mattermost/Rule/Heartbeat/LogLevel, with AppLogger DEBUG filtering**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-04-05T21:03:00Z
- **Completed:** 2026-04-05T21:11:03Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- Established `notify/` package with `HavenAlertChannel` interface and `NotificationRule` data class as shared contracts for all downstream channel implementations
- Added OkHttp 4.12.0 dependency with a Hilt-provided `OkHttpClient` singleton in `NetworkModule` (15s connect/read, 30s write timeouts)
- Extended `SettingsRepository` with 14 new DataStore keys covering Signal channel config, Mattermost webhook, global NotificationRule fields, per-channel heartbeat intervals, and log level
- Added `AppLogger.LogLevel` enum with `@Volatile currentLogLevel` and per-entry DEBUG filtering in `log()` — DEBUG entries are dropped from the ring buffer when level is NORMAL

## Task Commits

Each task was committed atomically:

1. **Task 1: Add OkHttp dep, HavenAlertChannel, NotificationRule, NetworkModule** - `021c46f` (feat)
2. **Task 2: Add 14 DataStore keys to SettingsRepository, LogLevel filtering to AppLogger** - `2be3862` (feat)

**Plan metadata:** (this commit)

## Files Created/Modified
- `app/src/main/java/org/havenapp/main/notify/HavenAlertChannel.kt` - Channel abstraction interface with `send()` and `sendHeartbeat()`
- `app/src/main/java/org/havenapp/main/notify/NotificationRule.kt` - Global rule data class (minSeverity, cooldownMs, triggerTypes, attachMedia)
- `app/src/main/java/org/havenapp/main/di/NetworkModule.kt` - Hilt `@Singleton` OkHttpClient provider
- `gradle/libs.versions.toml` - Added `okhttp = "4.12.0"` version and library entry
- `app/build.gradle.kts` - Added `implementation(libs.okhttp)` dependency
- `app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt` - 14 new DataStore keys + flows + setters
- `app/src/main/java/org/havenapp/main/storage/AppLogger.kt` - `LogLevel` enum, `currentLogLevel`, DEBUG filter in `log()`

## Decisions Made
- Named the interface `HavenAlertChannel` (not `NotificationChannel`) to avoid import collision with `android.app.NotificationChannel` which is used in `MonitorService` for system notification channels.
- NotificationRule fields are persisted as individual DataStore keys (not a serialized object), consistent with the existing SettingsRepository pattern and avoiding deserialization errors on schema evolution.
- `currentLogLevel` uses `@Volatile` rather than `@Synchronized` since reads dominate and the field is set rarely — avoids contention on the hot path in `log()`.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All foundation contracts are in place for plans 02-05
- Plan 02 (SignalRestChannel) can immediately inject `OkHttpClient`, read `signalEnabled`/`signalServerUrl`/`signalSender`/`signalRecipient`/`signalBearerToken`, and implement `HavenAlertChannel`
- Plan 03 (MattermostChannel) can read `mattermostEnabled`/`mattermostWebhookUrl`
- Plan 04 (NotificationRouter) can use `NotificationRule` flows and the channel interface list
- Plan 05 (Settings UI) has all DataStore flows available for reading and writing

---
*Phase: 04-notification-engine*
*Completed: 2026-04-05*
