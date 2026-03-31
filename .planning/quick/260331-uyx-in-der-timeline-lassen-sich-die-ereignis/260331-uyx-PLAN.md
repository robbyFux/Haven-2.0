---
phase: quick
plan: 260331-uyx
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/org/havenapp/main/storage/dao/EventDao.kt
  - app/src/main/java/org/havenapp/main/storage/EventRepository.kt
  - app/src/main/java/org/havenapp/main/ui/timeline/TimelineViewModel.kt
  - app/src/main/java/org/havenapp/main/ui/timeline/TimelineScreen.kt
  - app/src/main/java/org/havenapp/main/ui/timeline/EventDetailScreen.kt
  - app/src/main/java/org/havenapp/main/ui/timeline/EventDetailViewModel.kt
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values-de/strings.xml
autonomous: true
requirements: [EVENT-01, EVENT-02]

must_haves:
  truths:
    - "User can delete a HavenEvent (Zusammenfassung) from the Timeline"
    - "Deleting an event removes both the EventEntity and all associated EventTriggerEntities"
    - "After deletion the event disappears from the Timeline list immediately"
  artifacts:
    - path: "app/src/main/java/org/havenapp/main/storage/dao/EventDao.kt"
      provides: "deleteById(id) query"
      contains: "DELETE FROM events WHERE id"
    - path: "app/src/main/java/org/havenapp/main/storage/EventRepository.kt"
      provides: "deleteEvent(eventId) suspend function"
    - path: "app/src/main/java/org/havenapp/main/ui/timeline/TimelineScreen.kt"
      provides: "Swipe-to-delete on EventCards"
    - path: "app/src/main/java/org/havenapp/main/ui/timeline/EventDetailScreen.kt"
      provides: "Delete IconButton in TopAppBar"
  key_links:
    - from: "TimelineScreen.kt"
      to: "TimelineViewModel.deleteEvent()"
      via: "SwipeToDismiss onDismissed callback"
      pattern: "viewModel\\.deleteEvent"
    - from: "EventDetailScreen.kt"
      to: "EventDetailViewModel.deleteEvent()"
      via: "IconButton onClick"
      pattern: "viewModel\\.deleteEvent"
    - from: "EventRepository.deleteEvent()"
      to: "EventDao.deleteById()"
      via: "suspend function call"
      pattern: "eventDao\\.deleteById"
---

<objective>
Enable deletion of HavenEvents (Zusammenfassungen/summaries) from the Timeline.

Purpose: Users currently cannot delete events from the Timeline. The delete should target the top-level HavenEvent rows (not individual triggers). Room's ForeignKey CASCADE on EventTriggerEntity already handles cascading trigger deletion when an EventEntity is removed.

Output: Swipe-to-delete on Timeline items + delete button in EventDetailScreen TopAppBar. Both trigger full removal of the event and all its triggers from Room.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@app/src/main/java/org/havenapp/main/storage/dao/EventDao.kt
@app/src/main/java/org/havenapp/main/storage/dao/EventTriggerDao.kt
@app/src/main/java/org/havenapp/main/storage/EventRepository.kt
@app/src/main/java/org/havenapp/main/ui/timeline/TimelineScreen.kt
@app/src/main/java/org/havenapp/main/ui/timeline/TimelineViewModel.kt
@app/src/main/java/org/havenapp/main/ui/timeline/EventDetailScreen.kt
@app/src/main/java/org/havenapp/main/ui/timeline/EventDetailViewModel.kt
@app/src/main/java/org/havenapp/main/storage/entity/EventTriggerEntity.kt

<interfaces>
<!-- EventTriggerEntity already has ForeignKey CASCADE on eventId -->
<!-- So deleting from "events" table automatically deletes from "event_triggers" -->

From EventDao.kt (current):
```kotlin
@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: EventEntity): Long
    @Query("UPDATE events SET endTime = :endTime WHERE id = :id")
    suspend fun closeEvent(id: Long, endTime: Long)
    @Query("SELECT * FROM events ORDER BY startTime DESC")
    fun observeAll(): Flow<List<EventEntity>>
    @Query("SELECT * FROM events ORDER BY startTime DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<EventEntity>>
    @Query("DELETE FROM events")
    suspend fun deleteAll()
}
```

From EventRepository.kt (current):
```kotlin
@Singleton
class EventRepository @Inject constructor(
    private val eventDao: EventDao,
    private val triggerDao: EventTriggerDao,
) {
    // ... no deleteEvent(id) method exists
}
```

From TimelineViewModel.kt (current):
```kotlin
@HiltViewModel
class TimelineViewModel @Inject constructor(
    repository: EventRepository,
) : ViewModel() {
    val uiState: StateFlow<TimelineUiState> = repository.observeRecentEvents()
        .map { TimelineUiState(events = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState())
}
```

From EventDetailViewModel.kt (current):
```kotlin
@HiltViewModel
class EventDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: EventRepository,
) : ViewModel() {
    private val eventId: Long = checkNotNull(savedStateHandle["eventId"])
    val triggers: StateFlow<List<EventTriggerEntity>> = repository
        .observeTriggersForEvent(eventId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add deleteById to EventDao and EventRepository</name>
  <files>
    app/src/main/java/org/havenapp/main/storage/dao/EventDao.kt
    app/src/main/java/org/havenapp/main/storage/EventRepository.kt
  </files>
  <action>
1. In `EventDao.kt`, add a new `@Query` method:
   ```kotlin
   @Query("DELETE FROM events WHERE id = :id")
   suspend fun deleteById(id: Long)
   ```
   Place it after `deleteAll()`. No need to explicitly delete triggers — Room's ForeignKey CASCADE on `EventTriggerEntity` handles that automatically.

2. In `EventRepository.kt`, add a public suspend function:
   ```kotlin
   suspend fun deleteEvent(eventId: Long) {
       eventDao.deleteById(eventId)
   }
   ```
   Place it after `discardTriggersSince()`. The repository method is a thin wrapper; cascade handles trigger cleanup.
  </action>
  <verify>
    <automated>cd /home/user/Dokumente/Projekte/Haven\ 2.0 && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5</automated>
  </verify>
  <done>EventDao has deleteById(id) query. EventRepository has deleteEvent(eventId) suspend function. Compilation succeeds.</done>
</task>

<task type="auto">
  <name>Task 2: Add delete UI to TimelineScreen (swipe-to-delete) and EventDetailScreen (delete button)</name>
  <files>
    app/src/main/java/org/havenapp/main/ui/timeline/TimelineViewModel.kt
    app/src/main/java/org/havenapp/main/ui/timeline/TimelineScreen.kt
    app/src/main/java/org/havenapp/main/ui/timeline/EventDetailViewModel.kt
    app/src/main/java/org/havenapp/main/ui/timeline/EventDetailScreen.kt
    app/src/main/res/values/strings.xml
    app/src/main/res/values-de/strings.xml
  </files>
  <action>
**TimelineViewModel.kt:**
1. Change `repository` from constructor-only to a `private val` property (needed to call delete).
2. Add:
   ```kotlin
   fun deleteEvent(eventId: Long) {
       viewModelScope.launch { repository.deleteEvent(eventId) }
   }
   ```

**TimelineScreen.kt:**
1. Add imports: `SwipeToDismissBox`, `SwipeToDismissBoxValue`, `rememberSwipeToDismissBoxState`, `SwipeToDismissBoxState`, `Icons.Filled.Delete`, `androidx.compose.foundation.background`, `Color`, `androidx.compose.material3.ExperimentalMaterial3Api`.
2. Wrap each `EventCard` inside a `SwipeToDismissBox` (Material 3 API). Use `rememberSwipeToDismissBoxState` with a `confirmValueChange` lambda that calls `viewModel.deleteEvent(event.id)` when `SwipeToDismissBoxValue.EndToStart` is confirmed, then returns `true`.
3. The `backgroundContent` of SwipeToDismissBox should show a red background (`MaterialTheme.colorScheme.error`) with a white delete icon (`Icons.Filled.Delete`) aligned to the end.
4. Use `animateItemPlacement()` on the LazyColumn items for smooth removal animation.
5. Add the `key` parameter to items (already present: `key = { it.id }`).

**EventDetailViewModel.kt:**
1. Store `repository` as a `private val` property.
2. Add:
   ```kotlin
   fun deleteEvent(onDeleted: () -> Unit) {
       viewModelScope.launch {
           repository.deleteEvent(eventId)
           onDeleted()
       }
   }
   ```
   The `onDeleted` callback lets the screen navigate back after deletion.

**EventDetailScreen.kt:**
1. Add a delete `IconButton` in the `TopAppBar` `actions` slot:
   ```kotlin
   actions = {
       IconButton(onClick = { viewModel.deleteEvent(onDeleted = onBack) }) {
           Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete_event))
       }
   }
   ```
   This uses the existing `onBack` lambda to navigate back after deletion.
2. Import `Icons.Filled.Delete`.

**String resources:**
- In `values/strings.xml`, add: `<string name="action_delete_event">Delete event</string>`
- In `values-de/strings.xml`, add: `<string name="action_delete_event">Ereignis löschen</string>`

**Important conventions to follow:**
- No confirmation dialog for now (swipe gesture is intentional enough; can be added later).
- Use `MaterialTheme.colorScheme.error` for swipe background (matches severity CRITICAL color convention).
- `contentDescription` on the delete icon.
- `collectAsStateWithLifecycle()` (already used).
- No `Column` for lists, only `LazyColumn` (already correct).
  </action>
  <verify>
    <automated>cd /home/user/Dokumente/Projekte/Haven\ 2.0 && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5</automated>
  </verify>
  <done>Timeline items can be swiped end-to-start to delete. EventDetailScreen has a delete icon in the TopAppBar that deletes and navigates back. Both remove the HavenEvent + all triggers (via CASCADE). Compilation succeeds.</done>
</task>

</tasks>

<verification>
1. `./gradlew :app:compileDebugKotlin` compiles without errors
2. Manual: Launch app, create some events via monitoring, go to Timeline, swipe an event left — it disappears
3. Manual: Tap an event to open EventDetailScreen, tap delete icon in top bar — event is deleted and navigates back to Timeline
4. Manual: Verify in Diagnostics trigger log that associated triggers are also gone after event deletion
</verification>

<success_criteria>
- HavenEvents (Zusammenfassungen) can be deleted from the Timeline via swipe-to-delete
- HavenEvents can be deleted from EventDetailScreen via delete button in TopAppBar
- Deletion cascades to all associated EventTriggerEntities (Room ForeignKey CASCADE)
- Timeline list updates reactively after deletion (Room Flow)
- App compiles and runs without errors
</success_criteria>

<output>
After completion, create `.planning/quick/260331-uyx-in-der-timeline-lassen-sich-die-ereignis/260331-uyx-SUMMARY.md`
</output>
