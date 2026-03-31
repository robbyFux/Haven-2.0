---
phase: quick
plan: 260331-uke
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/org/havenapp/main/ui/settings/ZoneEditorScreen.kt
autonomous: true
requirements: []
must_haves:
  truths:
    - "After drawing a rectangle in ZoneEditorScreen, the Save button becomes enabled"
    - "The drawn zone is visually shown with dim overlay and corner handles after drag ends"
    - "Tapping Save after drawing persists the zone and navigates back"
  artifacts:
    - path: "app/src/main/java/org/havenapp/main/ui/settings/ZoneEditorScreen.kt"
      provides: "Working drag-to-draw zone with functional Save button"
  key_links:
    - from: "ZoneCameraBox pointerInput onDragEnd"
      to: "draftZone mutableStateOf"
      via: "buildZone() result assigned to draftZone"
      pattern: "draftZone = buildZone"
---

<objective>
Fix: Save button stays greyed out after drawing a detection zone rectangle in ZoneEditorScreen.

Purpose: The `pointerInput(Unit)` block in `ZoneCameraBox` captures the `dragStart` and `dragCurrent` **parameters** by value at first composition. Because the key is `Unit` (stable), the lambda never recaptures updated parameter values on recomposition. Inside `onDragEnd`, `dragStart` and `dragCurrent` are always the stale initial values (`null`), so `onDragEnd(start, end, w, h)` is never called, `draftZone` stays `null`, and the Save button (enabled when `draftZone != null`) remains disabled.

Output: Fixed ZoneEditorScreen.kt where drag gestures correctly produce a draftZone.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@app/src/main/java/org/havenapp/main/ui/settings/ZoneEditorScreen.kt
@app/src/main/java/org/havenapp/main/ui/settings/ZoneEditorViewModel.kt
@app/src/main/java/org/havenapp/main/detection/DetectionZone.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Fix stale closure in pointerInput onDragEnd</name>
  <files>app/src/main/java/org/havenapp/main/ui/settings/ZoneEditorScreen.kt</files>
  <action>
The bug is in `ZoneCameraBox` composable (line ~221-234). The `pointerInput(Unit)` block captures `dragStart` and `dragCurrent` parameters at first composition. Since the key is `Unit`, these captured values are never updated — they remain the initial `null`. When `onDragEnd` fires, it reads stale nulls and never calls the `onDragEnd` callback, so `draftZone` is never set.

**Fix approach — track drag state locally inside pointerInput:**

In `ZoneCameraBox`, replace the current `pointerInput(Unit)` + `detectDragGestures` block. Instead of reading the outer `dragStart`/`dragCurrent` parameters inside `onDragEnd`, track drag coordinates locally within the `pointerInput` suspend block and call the callbacks with the local values.

Concrete implementation:

1. Inside the `.pointerInput(Unit)` block, declare local `var localStart: Offset? = null` and `var localCurrent: Offset? = null`.

2. In `detectDragGestures`:
   - `onDragStart`: set `localStart = it; localCurrent = it;` AND call `onDragStart(it)` (to update visual state for the Canvas)
   - `onDrag`: set `localCurrent = change.position;` AND call `onDrag(change.position)`
   - `onDragEnd`: read `localStart` and `localCurrent` (NOT the parameters). If both non-null, call `onDragEnd(localStart, localCurrent, size.width.toFloat(), size.height.toFloat())`. Then reset both locals to null.
   - `onDragCancel`: reset locals to null, call `onDragCancel()`

This ensures the `onDragEnd` callback receives the actual drag coordinates that were tracked within the same `pointerInput` coroutine scope, bypassing the stale closure problem entirely.

Do NOT change `pointerInput` key to include state variables — that would restart gesture detection on every recomposition and break mid-drag.
Do NOT use `rememberUpdatedState` for `dragStart`/`dragCurrent` — they are parameters, not callbacks, and the real fix is to not depend on recomposition-delivered values inside the gesture block at all.
  </action>
  <verify>
    <automated>cd /home/user/Dokumente/Projekte/Haven\ 2.0 && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5</automated>
  </verify>
  <done>After drawing a rectangle via drag gesture in ZoneEditorScreen, `draftZone` is set to a valid DetectionZone, the zone info text appears, and the Save button becomes enabled (not greyed out). Compilation succeeds with no errors.</done>
</task>

</tasks>

<verification>
1. `./gradlew :app:compileDebugKotlin` compiles without errors
2. Manual verification: Open Zone Editor, draw a rectangle by dragging — Save button should become active immediately after releasing the drag
</verification>

<success_criteria>
- ZoneEditorScreen compiles successfully
- Drag gesture correctly sets draftZone to a non-null DetectionZone
- Save button is enabled after drawing a zone
- Visual feedback (dim overlay, corner handles) still renders correctly for the drawn zone
</success_criteria>

<output>
After completion, create `.planning/quick/260331-uke-fehler-bei-der-festlegung-der-erkennungs/260331-uke-SUMMARY.md`
</output>
