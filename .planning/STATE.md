---
gsd_state_version: 1.0
milestone: v0.4
milestone_name: NotificationEngine
status: executing
stopped_at: "Checkpoint: 04-05 Task 3 human-verify — awaiting UI verification on device"
last_updated: "2026-04-05T21:25:36.252Z"
last_activity: 2026-04-05
progress:
  total_phases: 2
  completed_phases: 2
  total_plans: 12
  completed_plans: 12
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-31)

**Core value:** Zuverlässige, privacy-respektierende Bewegungserkennung — die App muss starten, kalibrieren und Ereignisse erfassen, ohne dass etwas stillschweigend fehlschlägt.
**Current focus:** Phase 04 — notification-engine

## Current Position

Phase: 04 (notification-engine) — EXECUTING
Plan: 5 of 5
Status: Ready to execute
Last activity: 2026-04-05

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**

- Total plans completed: 0
- Average duration: — min
- Total execution time: — hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 3. Phase-2-Bugfix | TBD | — | — |

**Recent Trend:**

- Last 5 plans: —
- Trend: —

*Updated after each plan completion*
| Phase 03-phase-2-complete P01 | 5 | 2 tasks | 55 files |
| Phase 03-phase-2-complete P02 | 5 | 2 tasks | 10 files |
| Phase 03-phase-2-complete P03 | 5 | 2 tasks | 7 files |
| Phase 03 P06 | 3 | 1 tasks | 1 files |
| Phase 03 P05 | 15 | 1 tasks | 8 files |
| Phase 03-phase-2-complete P07 | 1 | 1 tasks | 3 files |
| Phase 04-notification-engine P01 | 8 | 2 tasks | 7 files |
| Phase 04-notification-engine P03 | 5 | 2 tasks | 3 files |
| Phase 04-notification-engine P02 | 2 | 2 tasks | 3 files |
| Phase 04-notification-engine P04 | 15 | 2 tasks | 2 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- TFLite eager init in HavenApplication: Bricht Chicken-and-Egg-Loop — Pending
- DiagnosticsViewModel reaktiv auf availabilityFlow: `isAvailable` war Snapshot → Flow behebt das — Pending
- [Phase 03-phase-2-complete]: FusedMotionMonitor warmup guard already implemented via warmupDone flag; SENSOR-02 confirmed without code change
- [Phase 03-phase-2-complete]: 54 untracked app source files committed to git with .gitignore to exclude build artifacts
- [Phase 03-phase-2-complete]: ClipRecorder is not a Hilt singleton — owned by MonitorService, tied to camera lifecycle
- [Phase 03-phase-2-complete]: EventRepository.recordTrigger returns Long (trigger row ID) enabling async mediaPath linkage
- [Phase 03-phase-2-complete]: Android Keystore raw API (KeyGenParameterSpec) used for AES-256-GCM media encryption instead of deprecated security-crypto
- [Phase 03-phase-2-complete]: Streaming CipherOutputStream for video encryption avoids OOM on large files
- [Phase 03]: SENSOR_DELAY_GAME chosen for FusedMotionMonitor: ~10x faster sampling (~20ms vs ~200ms) with acceptable battery impact for security monitoring
- [Phase 03-phase-2-complete]: LightMonitor no longer implements SensorMonitor: 3-param observe() replaces 2-param interface; MonitorService injects concrete type
- [Phase 03-phase-2-complete]: RecentTriggerState is a plain Kotlin object (not Hilt) for process-global cross-sensor priority gate in LightMonitor
- [Phase 03-phase-2-complete]: derivedStateOf used for filteredTriggers in EventDetailScreen: more efficient than keyed remember for computed state depending on other Compose snapshot state
- [Phase 04-notification-engine]: HavenAlertChannel named to avoid android.app.NotificationChannel import clash in MonitorService
- [Phase 04-notification-engine]: NotificationRule fields persisted as individual DataStore keys, consistent with existing SettingsRepository pattern
- [Phase 04-notification-engine]: AppLogger.currentLogLevel uses @Volatile (not @Synchronized) for low-contention reads on the hot logging path
- [Phase 04-notification-engine]: LogLevel toggle uses local remember placeholder (plan 05 wires to SettingsViewModel.logLevelDebug)
- [Phase 04-notification-engine]: CategoryCard is private composable within SettingsScreen.kt; Notifications card uses placeholder text for plan 05
- [Phase 04-notification-engine]: SignalRestChannel and MattermostChannel are plain Kotlin classes (not Hilt singletons): instantiated per monitoring session with a settings snapshot
- [Phase 04-notification-engine]: MattermostChannel.send() explicitly ignores attachment parameter — Mattermost Incoming Webhooks do not support binary file uploads
- [Phase 04-notification-engine]: lastJpegFrame uses full-color NV21 JPEG (same UV-plane extraction as buildBitmap) for better notification thumbnails
- [Phase 04-notification-engine]: Notification StateFlows are individual ViewModel properties (not in SettingsUiState): avoids bloating combined data class and keeps UI collections independent
- [Phase 04-notification-engine]: setLogLevel() calls appLogger.setLogLevel() directly before coroutine for instant effect, then persists to DataStore asynchronously

### Roadmap Evolution

- Phase 4 added: NotificationEngine — Signal+Mattermost alerts, configurable channels and severity thresholds, anti-flood cooldown, Settings grouping, Debug logging level (2026-04-05)

### Pending Todos

None yet.

### Blockers/Concerns

- SENSOR-01 and SENSOR-02 can only be validated after TFLite and Zone fixes are in place — plan accordingly.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260331-u38 | Fix DetectionZone not being saved from ZoneEditorScreen | 2026-03-31 | 118fa19 | [260331-u38-fix-detectionzone-not-being-saved-from-z](./quick/260331-u38-fix-detectionzone-not-being-saved-from-z/) |
| 260331-uke | Fix Save button stays greyed out after drawing detection zone (stale closure in pointerInput) | 2026-03-31 | db40801 | [260331-uke-fehler-bei-der-festlegung-der-erkennungs](./quick/260331-uke-fehler-bei-der-festlegung-der-erkennungs/) |
| 260331-uyx | Enable event deletion from Timeline (swipe-to-delete) and EventDetailScreen (delete button) | 2026-03-31 | fab24eb | [260331-uyx-in-der-timeline-lassen-sich-die-ereignis](./quick/260331-uyx-in-der-timeline-lassen-sich-die-ereignis/) |

## Session Continuity

Last session: 2026-04-05T21:25:09.877Z
Stopped at: Checkpoint: 04-05 Task 3 human-verify — awaiting UI verification on device
Resume file: None
