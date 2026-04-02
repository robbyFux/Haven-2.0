# Roadmap: Haven 2.0 — Phase-2-Bugfix Milestone

## Overview

Phases 1 and 2 built the sensor core and detection intelligence. Phase 3 completes the picture:
fix what broke (TFLite, zone persistence, event deletion, sensor stability) and add three missing
Phase 2 features — video clip recording on sensor trigger, AES-GCM encrypted local storage, and
an optional app PIN. One phase delivers a fully secured, fully functional monitoring app.

## Milestones

- ✅ **v0.1 Foundation** — Phase 1 (shipped)
- ✅ **v0.2 Detection Intelligence** — Phase 2 (shipped)
- 🚧 **v0.3 Phase-2-Complete** — Phase 3 (in progress)

## Phases

- [ ] **Phase 3: Phase-2-Complete** - Fix all Phase 2 bugs, add video clip recording on trigger, encrypt stored media (Android Keystore), and add optional app PIN

## Phase Details

### Phase 3: Phase-2-Complete
**Goal**: All Phase 2 features work as specified + video clip recording on sensor trigger + encrypted local storage + optional app PIN
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
**Plans**: TBD
**UI hint**: yes

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 3. Phase-2-Complete | 0/? | Not started | - |
