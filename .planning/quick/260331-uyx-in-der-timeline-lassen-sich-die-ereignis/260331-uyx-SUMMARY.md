---
phase: quick
plan: 260331-uyx
subsystem: ui/timeline, storage
tags: [deletion, swipe-to-delete, room, cascade, timeline, event-detail]
dependency_graph:
  requires: []
  provides: [event-deletion-from-timeline, event-deletion-from-detail]
  affects: [EventDao, EventRepository, TimelineViewModel, TimelineScreen, EventDetailViewModel, EventDetailScreen]
tech_stack:
  added: []
  patterns: [SwipeToDismissBox (Material3), Room CASCADE delete, ViewModel coroutine launch]
key_files:
  created: []
  modified:
    - app/src/main/java/org/havenapp/main/storage/dao/EventDao.kt
    - app/src/main/java/org/havenapp/main/storage/EventRepository.kt
    - app/src/main/java/org/havenapp/main/ui/timeline/TimelineViewModel.kt
    - app/src/main/java/org/havenapp/main/ui/timeline/TimelineScreen.kt
    - app/src/main/java/org/havenapp/main/ui/timeline/EventDetailViewModel.kt
    - app/src/main/java/org/havenapp/main/ui/timeline/EventDetailScreen.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-de/strings.xml
decisions:
  - No confirmation dialog for swipe-to-delete — swipe gesture is intentional enough; can be added in a future iteration
  - Room ForeignKey CASCADE on EventTriggerEntity handles trigger cleanup automatically — no explicit trigger deletion needed in repository
metrics:
  duration: 8 min
  completed: 2026-03-31T20:39:57Z
  tasks_completed: 2
  files_modified: 8
---

# Quick Task 260331-uyx: Event Deletion from Timeline and EventDetail Summary

**One-liner:** Swipe-to-delete on Timeline items and a delete IconButton in EventDetailScreen, both backed by Room ForeignKey CASCADE to remove events and all their triggers atomically.

## Tasks Completed

| # | Task | Commit | Status |
|---|------|--------|--------|
| 1 | Add deleteById to EventDao and deleteEvent to EventRepository | f794ba4 | Done |
| 2 | Add swipe-to-delete to TimelineScreen and delete button to EventDetailScreen | fab24eb | Done |

## What Was Built

### Task 1 — Data Layer

**EventDao.kt** — New `deleteById(id: Long)` query:
```kotlin
@Query("DELETE FROM events WHERE id = :id")
suspend fun deleteById(id: Long)
```
Room's ForeignKey CASCADE on `EventTriggerEntity.eventId` automatically deletes all associated triggers when the parent event row is removed.

**EventRepository.kt** — New `deleteEvent(eventId: Long)` suspend function, thin wrapper over `eventDao.deleteById()`.

### Task 2 — UI Layer

**TimelineViewModel** — `repository` promoted to `private val`; added `deleteEvent(eventId)` that launches in `viewModelScope`.

**TimelineScreen** — Each `EventCard` is now wrapped in a `SwipeToDismissBox` (Material 3, `@ExperimentalMaterial3Api`):
- Direction: `EndToStart` only (left-swipe)
- Background: `MaterialTheme.colorScheme.error` fill with white `Icons.Filled.Delete` icon aligned to end
- `confirmValueChange` lambda calls `viewModel.deleteEvent(event.id)` and returns `true`
- Items use `animateItem()` modifier for smooth removal animation

**EventDetailViewModel** — `repository` promoted to `private val`; added `deleteEvent(onDeleted: () -> Unit)` that deletes in coroutine and then invokes the callback on completion.

**EventDetailScreen** — Delete `IconButton` added to `TopAppBar` `actions` slot. On click calls `viewModel.deleteEvent(onDeleted = onBack)`, which deletes the event and navigates back to the Timeline.

**String resources** — `action_delete_event` added to both `values/strings.xml` (EN) and `values-de/strings.xml` (DE).

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None. All data wiring is complete.

## Self-Check: PASSED

- f794ba4 exists: `git log --oneline | grep f794ba4` → confirmed
- fab24eb exists: `git log --oneline | grep fab24eb` → confirmed
- All 8 modified files present and compile successfully (`BUILD SUCCESSFUL`)
