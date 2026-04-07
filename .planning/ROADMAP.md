# Roadmap: Haven 2.0 — Phase-2-Complete Milestone

## Overview

Phases 1 and 2 built the sensor core and detection intelligence. Phase 3 completes the picture:
fix what broke (TFLite, zone persistence, event deletion, sensor stability) and add three missing
Phase 2 features — video clip recording on sensor trigger, AES-GCM encrypted local storage, and
an optional app PIN. One phase delivers a fully secured, fully functional monitoring app.

## Milestones

- ✅ **v0.1 Foundation** — Phase 1 (shipped)
- ✅ **v0.2 Detection Intelligence** — Phase 2 (shipped)
- ✅ **v0.3 Phase-2-Complete** — Phase 3 (complete)
- ✅ **v0.4 NotificationEngine** — Phase 4 (complete)
- 🚧 **v0.5 Cloud-Server** — Phase 5 (planned)

## Phases

- [x] **Phase 3: Phase-2-Complete** - Fix all Phase 2 bugs, add video clip recording on trigger, encrypt stored media (Android Keystore), and add optional app PIN (completed 2026-04-04)
- [x] **Phase 4: NotificationEngine** - Signal+Mattermost alerts, configurable channels and severity thresholds, anti-flood cooldown, Settings grouping, Debug logging level (completed 2026-04-05)
- [ ] **Phase 5: Cloud-Server** - Self-hosted Python backend (FastAPI) with multi-user auth (2FA), multi-device support, encrypted user data, admin quotas, event+video upload from Haven, optional AI analysis, and cloud-triggered notifications (Mail/Signal/Pushover)

## Phase Details

### Phase 3: Phase-2-Complete
**Goal**: All Phase 2 features work as specified + video clip recording on sensor trigger + encrypted local storage + optional app PIN + sensor calibration improvements
**Depends on**: Phase 2 (complete)
**Requirements**: TFLITE-01, TFLITE-02, TFLITE-03, ZONE-01, ZONE-02, EVENT-01, EVENT-02, SENSOR-01, SENSOR-02, REC-01, REC-02, REC-03, SEC-01, SEC-02, SEC-03, SEC-04, SEC-05
**Success Criteria** (what must be TRUE):
  1. User can select PERSON, PET, VEHICLE, or ALL detection modes in Settings (not greyed out)
  2. A zone drawn in ZoneEditorScreen is still active after the app is restarted and visible to CameraAnalyzer
  3. User can delete an event from the Timeline or EventDetailScreen and it is gone from the list
  4. CameraAnalyzer runs without crashes in ML mode and FusedMotionMonitor produces no spurious triggers during calibration
  5. When a sensor triggers, a video+audio clip starts automatically; clip duration is configurable (10s/30s/60s) in Settings
  6. Recorded clip is linked to the triggering event in Room and playable in EventDetailScreen
  7. Recorded video files are AES-GCM encrypted in internal storage (Android Keystore)
  8. When app PIN is enabled in Settings, the app requires PIN entry on start and after backgrounding
  9. LightMonitor uses dual-rate EMA and cross-sensor suppression to reduce false alarms
  10. FusedMotionMonitor responds faster with SENSOR_DELAY_GAME
  11. EventDetailScreen supports filtering triggers by sensor type
**Plans**: 7 plans

Plans:
- [x] 03-01-PLAN.md — Verify quick-task fixes and add FusedMotionMonitor calibration guard
- [x] 03-02-PLAN.md — Video recording infrastructure (ClipRecorder + CameraX VideoCapture)
- [x] 03-03-PLAN.md — Encrypted storage (AES-GCM) and ExoPlayer video playback
- [x] 03-04-PLAN.md — App PIN lock with auto-lock on background
- [x] 03-05-PLAN.md — LightMonitor dual-rate EMA + cross-sensor suppression + Settings
- [x] 03-06-PLAN.md — FusedMotionMonitor latency fix (SENSOR_DELAY_GAME)
- [x] 03-07-PLAN.md — EventDetailScreen trigger type filter chips

**UI hint**: yes

### Phase 4: NotificationEngine

**Goal:** NotificationEngine mit Signal- und Mattermost-Kanal — konfigurierbare Kanäle, Schwellwerte (LOW–CRITICAL), Anti-Flood-Cooldown; Settings-Gruppierung für bessere Übersichtlichkeit; App-Logging-Level (Normal/Debug) in den Einstellungen
**Requirements**: NOTIF-01, NOTIF-02, NOTIF-03, NOTIF-04, NOTIF-05
**Depends on:** Phase 3
**Plans:** 5/5 plans complete

Plans:
- [x] 04-01-PLAN.md — Foundation: OkHttp dep, HavenAlertChannel interface, NotificationRule, NetworkModule, DataStore keys, AppLogger LogLevel
- [x] 04-02-PLAN.md — Channel implementations: SignalRestChannel, MattermostChannel, CameraAnalyzer lastJpegFrame
- [x] 04-03-PLAN.md — Settings UI restructuring: CategoryCard layout, string resources
- [x] 04-04-PLAN.md — NotificationRouter + MonitorService integration + Heartbeat
- [x] 04-05-PLAN.md — Notification Settings UI: config dialogs, rule controls, LogLevel toggle

### Phase 5: Cloud-Server

**Goal:** Self-hosted Python backend (FastAPI + PostgreSQL + Redis + Celery) that provides multi-user auth with 2FA, per-user multi-device (App-Key) management, encrypted user data storage (PBKDF2/Argon2 key derivation from cloud password + username), admin-defined quotas, secure event+video upload from the Haven Android app, optional AI-based analysis of events/videos (local TFLite or OpenRouter), and cloud-triggered notifications via Mail, Signal, or Pushover after analysis.
**Requirements**: CLOUD-01, CLOUD-02, CLOUD-03, CLOUD-04, CLOUD-05, CLOUD-06, CLOUD-07, CLOUD-08
**Depends on:** Phase 4
**Tech Stack:** Python, FastAPI, Pydantic, SQLAlchemy, Alembic, PostgreSQL, Redis, Celery, Pytest, Uvicorn/Gunicorn
**Plans:** 0 plans

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 3. Phase-2-Complete | 7/7 | Complete   | 2026-04-04 |
| 4. NotificationEngine | 5/5 | Complete   | 2026-04-05 |
| 5. Cloud-Server | 0/? | Planned | — |
