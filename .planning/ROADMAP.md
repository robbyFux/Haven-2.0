# Roadmap: Haven 2.0 — Phase-2-Bugfix Milestone

## Overview

Phases 1 and 2 built the sensor core and detection intelligence. This milestone fixes what broke:
TFLite KI modes are not selectable in Settings, detection zones do not persist, and events cannot
be deleted. One phase delivers a fully usable Phase 2 — all features work as specified.

## Milestones

- ✅ **v0.1 Foundation** — Phase 1 (shipped)
- ✅ **v0.2 Detection Intelligence** — Phase 2 (shipped)
- 🚧 **v0.2.1 Phase-2-Bugfix** — Phase 3 (in progress)

## Phases

- [ ] **Phase 3: Phase-2-Bugfix** - Fix TFLite initialization, zone persistence, event deletion, and sensor stability so all Phase 2 features work as specified

## Phase Details

### Phase 3: Phase-2-Bugfix
**Goal**: All Phase 2 features work as specified — KI modes are selectable, detection zones survive restarts, events can be deleted, and the sensor pipeline is stable
**Depends on**: Phase 2 (complete)
**Requirements**: TFLITE-01, TFLITE-02, TFLITE-03, ZONE-01, ZONE-02, EVENT-01, EVENT-02, SENSOR-01, SENSOR-02
**Success Criteria** (what must be TRUE):
  1. User can select PERSON, PET, VEHICLE, or ALL detection modes in Settings (not greyed out)
  2. A zone drawn in ZoneEditorScreen is still active after the app is restarted and visible to CameraAnalyzer
  3. User can delete an event from the Timeline or EventDetailScreen and it is gone from the list
  4. CameraAnalyzer runs without crashes in ML mode and FusedMotionMonitor produces no spurious triggers during calibration
**Plans**: TBD
**UI hint**: yes

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 3. Phase-2-Bugfix | 0/? | Not started | - |
