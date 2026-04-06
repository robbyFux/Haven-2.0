---
quick_id: 260406-vc9
type: quick-fix
completed: 2026-04-06
commit: 4c0cfaf
duration_min: 3
files_modified: 2
tags: [storage, media, deletion, bug-fix]
---

# Quick Fix 260406-vc9: Fix event deletion to also delete associated media files

**One-liner:** `deleteEvent()` and `discardTriggersSince()` now collect `mediaPath` values from `EventTriggerDao` before removing DB rows, then delete each file with `runCatching` for race-condition safety.

## Problem

`EventRepository.deleteEvent()` relied solely on Room CASCADE to remove `event_triggers` rows but never touched the filesystem. Encrypted video clips (`clip_*.mp4.enc`) and any other `mediaPath` files accumulated indefinitely. `discardTriggersSince()` (stop-cooldown path) had the same omission.

## Fix

**`EventTriggerDao.kt`** — two new suspend queries:
- `getMediaPathsByEvent(eventId)` — all non-null media paths for a full event deletion
- `getMediaPathsSince(eventId, cutoffMs)` — non-null media paths for triggers at or after the cutoff

**`EventRepository.kt`** — both deletion methods updated:
- `deleteEvent()`: fetch paths → delete files → `eventDao.deleteById()` (CASCADE handles triggers)
- `discardTriggersSince()`: fetch paths → delete files → `triggerDao.deleteSince()`

Each file deletion wrapped in `runCatching { File(path).delete() }` — silent no-op if file already absent.

## Files Modified

| File | Change |
|------|--------|
| `app/src/main/java/org/havenapp/main/storage/dao/EventTriggerDao.kt` | Added `getMediaPathsByEvent` and `getMediaPathsSince` queries |
| `app/src/main/java/org/havenapp/main/storage/EventRepository.kt` | Updated `deleteEvent()` and `discardTriggersSince()` to delete files |

## Verification

`./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check: PASSED

- Commit 4c0cfaf exists in git log
- Both modified files contain the new queries and file-deletion logic
- Compilation succeeded with zero errors
