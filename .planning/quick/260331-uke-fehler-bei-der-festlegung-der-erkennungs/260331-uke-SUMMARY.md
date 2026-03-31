---
phase: quick
plan: 260331-uke
subsystem: ui/settings
tags: [bugfix, gestures, compose, zone-editor]
dependency_graph:
  requires: []
  provides: [working-zone-drag-gesture]
  affects: [ZoneEditorScreen, draftZone, Save button]
tech_stack:
  added: []
  patterns: [local-state-in-pointerInput]
key_files:
  modified:
    - app/src/main/java/org/havenapp/main/ui/settings/ZoneEditorScreen.kt
decisions:
  - "Track drag state locally inside pointerInput block instead of reading stale captured parameters"
metrics:
  duration: 3
  completed: 2026-03-31
  tasks: 1
  files: 1
---

# Quick Task 260331-uke: Fix Save Button Stays Disabled After Drawing Zone

**One-liner:** Fixed stale closure bug in `ZoneCameraBox.pointerInput(Unit)` by tracking drag coordinates locally, so `onDragEnd` correctly sets `draftZone` and enables the Save button.

## What Was Done

Fixed `ZoneEditorScreen.kt` where the Save button remained disabled after drawing a detection zone rectangle.

## Root Cause

`ZoneCameraBox` used `.pointerInput(Unit)` with `detectDragGestures`. The `onDragEnd` lambda read the composable parameters `dragStart` and `dragCurrent` to get drag coordinates. Because the `pointerInput` key is `Unit` (stable, never changes), the lambda is captured once at first composition and never updated. At drag-end time, `dragStart` and `dragCurrent` still held their initial values (`null`), so the `onDragEnd(start, end, w, h)` callback was never invoked, `draftZone` stayed `null`, and the Save button (enabled only when `draftZone != null`) remained disabled.

## Fix Applied

Inside the `.pointerInput(Unit)` block, two local variables were added:

```kotlin
var localStart: Offset? = null
var localCurrent: Offset? = null
```

- `onDragStart`: sets `localStart = it; localCurrent = it` before calling `onDragStart(it)`
- `onDrag`: sets `localCurrent = change.position` before calling `onDrag(change.position)`
- `onDragEnd`: reads `localStart`/`localCurrent` (not parameters), calls `onDragEnd(...)` if both non-null, then resets locals
- `onDragCancel`: resets locals, calls `onDragCancel()`

These locals live in the same coroutine scope as the `detectDragGestures` call and are correctly updated across the full drag lifecycle — no stale closure.

## Deviations from Plan

None — plan executed exactly as written.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | db40801 | fix(quick-260331-uke): fix stale closure in ZoneCameraBox pointerInput onDragEnd |

## Known Stubs

None.

## Self-Check: PASSED

- File modified: `app/src/main/java/org/havenapp/main/ui/settings/ZoneEditorScreen.kt` — exists
- Commit db40801 — exists
- Compilation: BUILD SUCCESSFUL
