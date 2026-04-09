---
gsd_state_version: 1.0
milestone: v0.6
milestone_name: Web-UI
status: complete
stopped_at: "Completed Phase 06 — web-ui Django frontend (6 plans) — 95/95 tests pass"
last_updated: "2026-04-09T00:00:00.000Z"
last_activity: 2026-04-09
progress:
  total_phases: 6
  completed_phases: 6
  total_plans: 6
  completed_plans: 6
  percent: 100
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-31)

**Core value:** Zuverlässige, privacy-respektierende Bewegungserkennung — die App muss starten, kalibrieren und Ereignisse erfassen, ohne dass etwas stillschweigend fehlschlägt.
**Current focus:** Phase 06 complete — web-ui

## Current Position

Phase: 06
Plan: All complete (6/6)
Status: Complete — verification passed (95/95 tests)
Last activity: 2026-04-06

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
- [Phase 04-notification-engine]: NotificationRouter uses ordinal comparison for Severity enum since it is ordered LOW < MEDIUM < HIGH < CRITICAL
- [Phase 04-notification-engine]: route() called in child launch{} to avoid blocking trigger collection loop on slow HTTP calls
- [Phase 04-notification-engine]: Heartbeat coroutines use while(_state != ACTIVE) delay(500) guard before first heartbeat send

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
| 260406-070 | Add SignalIntentChannel as P2 fallback alert channel via Android Intent to local Signal app | 2026-04-06 | 5d2e0d5 | [260406-070-signalintentchannel-in-phase-4-hinzuf-ge](./quick/260406-070-signalintentchannel-in-phase-4-hinzuf-ge/) |
| 260406-c5e | Fix SignalIntentChannel: notification+PendingIntent (Android 10+ compat) + E.164 dialog validation | 2026-04-06 | e9d56ca | [260406-c5e-signalintentchannel-notification-pending](./quick/260406-c5e-signalintentchannel-notification-pending/) |
| 260406-v3d | Remove SignalIntentChannel entirely — file, settings, ViewModel, UI, strings cleaned from all 7 files | 2026-04-06 | 1d71e5a | [260406-v3d-signalintentchannel-komplett-entfernen](./quick/260406-v3d-signalintentchannel-komplett-entfernen/) |
| 260406-vc9 | Fix event deletion to also delete associated media files | 2026-04-06 | 4c0cfaf | [260406-vc9-fix-event-l-schung-l-scht-nicht-die-zuge](./quick/260406-vc9-fix-event-l-schung-l-scht-nicht-die-zuge/) |
| 260406-vc6 | Pushover channel: PushoverChannel + settings + UI + MonitorService heartbeat | 2026-04-06 | 380eca0 | [260406-vc6-pushover-kanal-implementieren-inkl-heart](./quick/260406-vc6-pushover-kanal-implementieren-inkl-heart/) |

## Session Continuity

Last session: 2026-04-06T00:00:00Z
Stopped at: Completed quick task 260406-vc6: Pushover channel implementation
Resume file: None
