---
phase: 04-notification-engine
plan: 04
subsystem: notify
tags: [okhttp, kotlin, hilt, coroutines, notifications, signal, mattermost]

# Dependency graph
requires:
  - phase: 04-notification-engine plan 01
    provides: HavenAlertChannel interface, NotificationRule data class, SettingsRepository notification fields
  - phase: 04-notification-engine plan 02
    provides: SignalRestChannel and MattermostChannel implementations, NetworkModule/OkHttpClient

provides:
  - NotificationRouter: Singleton with severity/type/cooldown filtering, dispatches to active channels
  - MonitorService notification integration: reads settings snapshot, initializes router, routes triggers
  - Heartbeat coroutines: periodic alive messages via Signal and Mattermost, auto-cancel on stop

affects: [05-settings-ui, MonitorService]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - NotificationRouter initialized per session with settings snapshot (not reactive flows)
    - Heartbeat coroutines inside monitoringJob scope for automatic lifecycle management
    - launch { notificationRouter.route() } pattern decouples notification from trigger collect loop

key-files:
  created:
    - app/src/main/java/org/havenapp/main/notify/NotificationRouter.kt
  modified:
    - app/src/main/java/org/havenapp/main/MonitorService.kt

key-decisions:
  - "NotificationRouter uses ordinal comparison for Severity enum since it is ordered LOW < MEDIUM < HIGH < CRITICAL"
  - "Heartbeat coroutines use while(_state != ACTIVE) delay(500) guard before first heartbeat to avoid premature sends during calibration"
  - "route() called in a child launch{} to avoid blocking the trigger collection loop on slow HTTP calls"

patterns-established:
  - "Session-scoped initialization: NotificationRouter.initialize() called once per monitoring session with snapshot settings"
  - "Heartbeat lifecycle: heartbeat coroutines are children of monitoringJob — structured concurrency ensures automatic cancel"

requirements-completed: [NOTIF-01, NOTIF-05]

# Metrics
duration: 15min
completed: 2026-04-05
---

# Phase 04 Plan 04: NotificationRouter and MonitorService Integration Summary

**End-to-end notification pipeline: sensor trigger filtering by severity/type/cooldown → HTTP dispatch via Signal REST or Mattermost webhook, with periodic heartbeat coroutines auto-cancelled on monitoring stop**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-04-05T21:25:00Z
- **Completed:** 2026-04-05T21:40:00Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Created NotificationRouter singleton with three-level filter chain (severity gate, trigger type whitelist, anti-flood cooldown) and per-channel dispatch with JPEG attachment gating for camera events
- Wired NotificationRouter into MonitorService: settings snapshot at session start, route() call after every trigger recording, heartbeat coroutines inside monitoringJob scope
- Heartbeat coroutines for Signal and Mattermost channels wait for ACTIVE state before first send, then loop at configured minute intervals, and auto-cancel when monitoringJob is cancelled

## Task Commits

Each task was committed atomically:

1. **Task 1: Create NotificationRouter with rule filtering and channel dispatch** - `0840eac` (feat)
2. **Task 2: Wire NotificationRouter into MonitorService and add heartbeat coroutines** - `fc441b7` (feat)

**Plan metadata:** (docs commit to follow)

## Files Created/Modified
- `app/src/main/java/org/havenapp/main/notify/NotificationRouter.kt` - Singleton router applying NotificationRule filters before dispatching to enabled HavenAlertChannels
- `app/src/main/java/org/havenapp/main/MonitorService.kt` - Added NotificationRouter + OkHttpClient injection, session initialization, trigger routing, heartbeat coroutines, and reset on stop

## Decisions Made
- Severity comparison uses `ordinal` — Severity enum is naturally ordered LOW(0) < MEDIUM(1) < HIGH(2) < CRITICAL(3)
- Heartbeat wait loop uses `delay(500)` polling rather than Flow collection to avoid holding a coroutine Flow reference inside monitoringJob
- `route()` is called inside a child `launch {}` to prevent slow HTTP sends from blocking the sensor event collection loop

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- First build failed transiently; second run with `--rerun-tasks` succeeded cleanly with only one pre-existing deprecation warning (`setTargetResolution` in CameraX) unrelated to this plan's changes.

## User Setup Required
None - no external service configuration required for this plan. Channel configuration (server URL, phone numbers, webhook URL) is managed via SettingsRepository and set by the user through the UI.

## Next Phase Readiness
- Full notification pipeline is now operational: triggers flow through NotificationRouter to Signal REST API or Mattermost webhook
- Plan 05 (Settings UI) can expose the notification rule configuration and channel toggles to the user
- Heartbeat is ready but requires user to configure the interval in settings (currently 0 = off by default)

---
*Phase: 04-notification-engine*
*Completed: 2026-04-05*
