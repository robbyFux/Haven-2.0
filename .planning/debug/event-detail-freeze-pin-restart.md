---
status: awaiting_human_verify
trigger: "EventDetailScreen freezes app when opening an event entry after monitoring; PIN lock prevents app restart after crash/freeze."
created: 2026-04-02T00:00:00Z
updated: 2026-04-02T00:00:00Z
---

## Current Focus

hypothesis: (1) Auto-lock: onStop launched a coroutine that suspended on two DataStore .first() calls before calling AppLockState.lock(). The suspension created a race — onStart could fire and the in-flight lock coroutine was not tracked or cancellable. For delay=0, AppLockState.lock() fired only after the suspensions resolved, possibly after the user had already returned. (2) Event flood: ClipRecorder had no public isRecording flag, so MonitorService had no way to suppress duplicate triggers written to Room during an active clip window.
test: (1) Refactored HavenApplication to cache pinEnabled + autoLockDelaySeconds as persistent collected flows. onStop reads the cached values synchronously (no coroutine, no suspension). AppLockState.lock() is called directly on the main thread for delay=0. lockJob tracks the delayed-lock timer and is cancelled by onStart. (2) Added ClipRecorder.isRecording property backed by the existing AtomicReference<Recording?>. MonitorService checks it at the top of sensorFlow.collect and returns@collect early if true, skipping both recordTrigger and startClip.
expecting: (1) Backgrounding the app with PIN+autoLock=Immediately causes PIN screen to appear on next resume, with no content flash. (2) Timeline shows only one trigger event per recording window, not ~100 duplicate mic/light events.
next_action: Awaiting human verification of both fixes.

## Symptoms

expected: After monitoring runs and user navigates to Timeline → taps an event entry → EventDetailScreen opens normally showing event details and any video player.
actual:
  1. App freezes (ANR/hang) when opening an event entry via Timeline after monitoring ran.
  2. After force-killing and restarting the app, if a PIN was previously set, the app cannot be restarted (gets stuck or crashes before showing PIN screen or content).
errors: Log shows only light trigger events and "TFLite model loaded: efficientdet_lite0.tflite (threshold=0.45, maxResults=5)" — no explicit crash stacktrace provided. The freeze requires force-stop to recover.
reproduction:
  1. Run monitoring session (triggers light events)
  2. Stop monitoring
  3. Go to Timeline
  4. Tap event entry → app freezes
  5. Force-stop, restart → if PIN was set, can't get past startup
started: Introduced in Phase 3 execution — specifically plans 03-02 (ClipRecorder), 03-03 (AES-GCM MediaEncryptionManager + ExoPlayer playback in EventDetailScreen), and 03-04 (PIN lock via HavenApplication + AppLockState)

## Eliminated

- hypothesis: H1 (EventDetail) — ExoPlayer initialized synchronously in Compose
  evidence: ExoPlayer.Builder(context).build() is inside remember{} which executes lazily on first composition, not on the main thread synchronously. However, the actual freeze is upstream: preparePlaybackFile() runs first and blocks.
  timestamp: 2026-04-02T00:00:00Z

- hypothesis: H3 (EventDetail) — wrong video path causes ExoPlayer to hang on nonexistent URI
  evidence: The path issue is secondary. Even if the path is wrong, ExoPlayer would fail asynchronously with a player error, not freeze the UI. The ANR is caused by blocking I/O before ExoPlayer even gets the path.
  timestamp: 2026-04-02T00:00:00Z

- hypothesis: H1 (PIN) — runBlocking { settingsRepo.pinEnabled.first() } in HavenApplication.onCreate() deadlocks
  evidence: This call runs on the main thread during Application.onCreate(). DataStore uses an internal coroutine dispatcher backed by a thread pool separate from the main thread. The .first() call collects one emission from the DataStore flow, which does NOT require the main thread. So runBlocking here is risky but does not deadlock in normal conditions. The actual PIN issue is in the NavGraph guard logic.
  timestamp: 2026-04-02T00:00:00Z

- hypothesis: H3 (PIN) — ProcessLifecycleOwner fires ON_STOP too aggressively on config change
  evidence: ProcessLifecycleOwner does NOT fire onStop during configuration changes (Activity rotation etc.) — it only fires when the entire app goes background. So aggressive re-locking on config change is not the cause.
  timestamp: 2026-04-02T00:00:00Z

## Evidence

- timestamp: 2026-04-02T00:00:00Z
  checked: EventDetailScreen.kt lines 187-189 (VideoPlayerCard composable)
  found: |
    val playbackPath = remember(mediaPath) {
        viewModel.preparePlaybackFile(mediaPath, cacheDir)
    }
    This calls preparePlaybackFile() SYNCHRONOUSLY inside a remember{} block. remember{} executes on the Composition thread (= main thread) during initial composition.
  implication: If the mediaPath ends with ".enc", MediaEncryptionManager.decryptToFile() is called on the main thread, performing full AES-GCM streaming decryption of a video file (could be 10–60 seconds of video = several MB). This is blocking I/O on the main thread → ANR.

- timestamp: 2026-04-02T00:00:00Z
  checked: EventDetailViewModel.kt preparePlaybackFile() — not a suspend function
  found: |
    fun preparePlaybackFile(mediaPath: String, cacheDir: File): String {
        if (!mediaPath.endsWith(".enc")) return mediaPath
        val encFile = File(mediaPath)
        if (!encFile.exists()) return mediaPath
        val tempFile = File(cacheDir, "playback_${System.currentTimeMillis()}.mp4")
        runCatching {
            MediaEncryptionManager.decryptToFile(encFile, tempFile)  // BLOCKING I/O
        }.onFailure {
            return mediaPath
        }
        tempPlaybackFile = tempFile
        return tempFile.absolutePath
    }
    The function is not a suspend function. It performs blocking file I/O (AES-GCM stream decryption of the full video file) synchronously. The caller in the Composable calls it inside remember{}, which runs on the main thread.
  implication: ROOT CAUSE for Bug 1. This is confirmed. Every time a trigger with a .enc mediaPath is displayed, the full video is decrypted synchronously on the composition thread.

- timestamp: 2026-04-02T00:00:00Z
  checked: HavenNavGraph.kt lines 65-77 (PIN guard logic)
  found: |
    val locked by AppLockState.locked.collectAsStateWithLifecycle()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val pinEnabled by settingsVm.pinEnabled.collectAsStateWithLifecycle()
    val pinHash by settingsVm.pinHash.collectAsStateWithLifecycle()
    val pinSalt by settingsVm.pinSalt.collectAsStateWithLifecycle()

    if (locked && pinEnabled && pinHash != null && pinSalt != null) {
        PinLockScreen(...)
        return
    }
    The guard requires ALL FOUR conditions simultaneously:
      - locked = true (set immediately in Application.onCreate())
      - pinEnabled = true (from SettingsViewModel StateFlow, starts with default false)
      - pinHash != null (from SettingsViewModel StateFlow, starts with null)
      - pinSalt != null (from SettingsViewModel StateFlow, starts with null)
  implication: On cold start with PIN enabled: AppLockState.lock() is called synchronously in onCreate(), so locked=true immediately. BUT pinEnabled/pinHash/pinSalt StateFlows in SettingsViewModel start with their initial values (false/null/null) until DataStore emits. During this brief window, the condition (locked && pinEnabled && ...) is FALSE, so the NavGraph renders the main app. Once DataStore emits, pinEnabled becomes true and pinHash/pinSalt become non-null, causing the condition to flip to TRUE and the PinLockScreen to appear — but the NavHost has already started rendering.

- timestamp: 2026-04-02T00:00:00Z
  checked: SettingsViewModel.kt lines 176-183
  found: |
    val pinEnabled: StateFlow<Boolean> = settingsRepository.pinEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val pinHash: StateFlow<String?> = settingsRepository.pinHash
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pinSalt: StateFlow<String?> = settingsRepository.pinSalt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    Initial values confirmed: pinEnabled=false, pinHash=null, pinSalt=null. These are the values HavenNavGraph reads on first composition, before DataStore emits.
  implication: CONFIRMED ROOT CAUSE for Bug 2. On cold start: locked=true (set by runBlocking in onCreate), but pinEnabled=false (initial StateFlow value). Guard condition (locked && pinEnabled && ...) = false. NavHost renders normally. When DataStore emits ~50-200ms later: pinEnabled=true, pinHash="...", pinSalt="..." — condition becomes true — PinLockScreen suddenly replaces the NavHost. This produces either a visible flash, or a stuck/blank screen state if the app is mid-navigation.

- timestamp: 2026-04-02T00:00:00Z
  checked: HavenApplication.kt line 44 — runBlocking on main thread
  found: |
    val pinEnabled = runBlocking { settingsRepo.pinEnabled.first() }
    This is called in Application.onCreate() on the main thread. runBlocking blocks the calling thread (main thread) until the coroutine completes. DataStore's internal implementation uses Dispatchers.IO for file reads. The .first() call suspends, waiting for DataStore to emit. This CAN cause an ANR if DataStore is slow to initialize (e.g., first cold start, large preferences file, storage under pressure). Even if it doesn't ANR, it extends Application.onCreate() unnecessarily.
  implication: CONTRIBUTING CAUSE for Bug 2. The runBlocking call on the main thread in Application.onCreate() is a correctness issue — it blocks main thread startup. Combined with the NavGraph guard using stale initial values, this creates the restart failure symptom.

- timestamp: 2026-04-02T00:00:00Z
  checked: HavenApplication.kt lines 54-55 — additional runBlocking calls in lifecycle observer
  found: |
    override fun onStop(owner: LifecycleOwner) {
        val currentPinEnabled = runBlocking { settingsRepo.pinEnabled.first() }
        val currentDelay = runBlocking { settingsRepo.autoLockDelaySeconds.first() }
    These runBlocking calls are on the main thread inside a ProcessLifecycleOwner.onStop callback. onStop fires on the main thread. Two sequential runBlocking calls to DataStore from onStop further blocks the main thread during app backgrounding.
  implication: Additional main-thread blocking. Not the primary cause of the restart failure, but contributes to ANR potential.

- timestamp: 2026-04-02T01:00:00Z
  checked: EventDetailViewModel.kt + EventDetailScreen.kt after async-decryption fix was applied
  found: |
    The current async decryption approach (LaunchedEffect → requestPlaybackPath → viewModelScope.launch → withContext(Dispatchers.IO)) is architecturally correct — no blocking on main thread. The spinner IS shown correctly while playbackPaths[path] == null. The issue is that the spinner "never resolves" because: AES-GCM decryption of a 30–60 second HD video clip on Dispatchers.IO can take many seconds (10–60 s) on a low-end device. User perceives a permanent spinner. Additionally, the eager decryption of ALL visible trigger media paths on list entry is wasteful — most users just want to scroll the list, not play every video.
  implication: The fix must change the UX pattern, not just move I/O off the main thread. The correct pattern is: static placeholder on list load (no decryption), decrypt-on-tap. This matches user's explicit requirement.

- timestamp: 2026-04-02T01:00:00Z
  checked: MonitorService.kt lines 194-213 — how mediaPath gets stored in DB
  found: |
    Every sensor trigger (regardless of type — light, accelerometer, camera) calls clipRecorder?.startClip(). Only the first trigger in a clip window gets a clip attached (activeRecording guard prevents parallel clips). The clip path is stored asynchronously via updateTriggerMediaPath(triggerId, encryptedPath) in lifecycleScope. Room's observeTriggersForEvent flow re-emits when the UPDATE lands, causing TriggerCard to recompose and show the spinner for that trigger.
  implication: Light triggers (user's reported scenario) can have non-null mediaPath if VideoCapture was successfully bound. The spinner is real and the path IS a valid .enc file.

- timestamp: 2026-04-02T02:00:00Z
  checked: HavenApplication.kt onStop coroutine — suspension race with onStart
  found: |
    onStop launched `appScope.launch { val pinEnabled = settingsRepo.pinEnabled.first(); val delay = settingsRepo.autoLockDelaySeconds.first(); ... AppLockState.lock() }`.
    Two suspension points inside the coroutine meant that for delay=0, AppLockState.lock() was not called until both DataStore .first() calls returned. During that window, onStart could fire, but onStart only cancelled `lockJob` (the delayed timer), NOT the outer coroutine. The outer coroutine then completed and locked the app AFTER the user had already returned — making the user think nothing happened (they see unlock, then PIN unexpectedly), or the .first() calls raced with something else causing the coroutine to be dropped entirely.
  implication: ROOT CAUSE for Issue 1. The fix: cache pinEnabled + autoLockDelay in-memory via persistent collectors, so onStop reads synchronously with zero suspension and AppLockState.lock() is called immediately on the main thread.

- timestamp: 2026-04-02T02:00:00Z
  checked: ClipRecorder.kt — no public isRecording property; MonitorService.kt — no recording guard before recordTrigger
  found: |
    ClipRecorder exposes `isAvailable` but not `isRecording`. The `activeRecording: AtomicReference<Recording?>` is private. MonitorService calls `eventRepository.recordTrigger(eventId, trigger)` for EVERY sensor event that arrives after the calibration period, with no check on whether a clip is already recording. A 30-second clip with microphone triggers arriving every ~1 second = ~30 Room inserts for what should be a single trigger event.
  implication: ROOT CAUSE for Issue 2. The fix: add `val isRecording: Boolean get() = activeRecording.get() != null` to ClipRecorder, then `if (clipRecorder?.isRecording == true) return@collect` at the top of the sensorFlow.collect block in MonitorService.

## Resolution

root_cause: |
  BUG 1 (EventDetail freeze — original):
  EventDetailScreen.kt VideoPlayerCard composable called viewModel.preparePlaybackFile() inside remember{}, which executed synchronously on the main/composition thread. preparePlaybackFile() was not a suspend function and performed blocking AES-GCM file decryption for .enc files. Decrypting a 10–60 second video clip on the main thread caused ANR. FIXED in previous session by moving I/O to Dispatchers.IO via viewModelScope.launch.

  BUG 1b (EventDetail — permanent spinner):
  After the async-IO fix, the spinner still never resolved because: (a) AES-GCM decryption of a 30–60 second HD video clip on Dispatchers.IO can take 10–60 seconds on a slow device, causing the user to perceive a "permanent" spinner, and (b) decryption was triggered eagerly for ALL trigger items on list entry, even ones the user has no intention of playing. The correct fix is a UX pattern change: show a static tappable placeholder on list load (no decryption), decrypt only when the user explicitly taps the thumbnail.

  BUG 2 (PIN restart failure — original):
  Two compounding issues:
  (a) HavenNavGraph PIN guard condition requires pinEnabled/pinHash/pinSalt to all be non-default simultaneously, but SettingsViewModel StateFlows started with initial defaults (false/null/null) before DataStore emitted. On cold start AppLockState is locked=true, but the guard saw pinEnabled=false (initial value) so it rendered the NavHost. When DataStore emitted, PinLockScreen suddenly appeared over partially-rendered content.
  (b) HavenApplication.onCreate() called runBlocking{settingsRepo.pinEnabled.first()} on the main thread, blocking startup until DataStore initialized.
  FIXED in previous session: pinEnabled StateFlow now uses null as initial value; NavGraph shows blank screen while pinEnabled==null; LaunchedEffect auto-unlocks when pinEnabled==false; AppLockState starts locked=true.

  BUG 3 (Auto-lock not firing on background):
  HavenApplication.onStop observer launched a coroutine on Dispatchers.Main that suspended on two sequential DataStore .first() calls before calling AppLockState.lock(). For delay=0, the lock call was deferred until both .first() suspensions resolved. onStart only cancelled `lockJob` (the delayed-lock timer Job), not the outer coroutine — so a quick background+foreground could still result in the lock firing after the user returned. The suspension also meant that if anything delayed DataStore emission, the lock never fired at all. FIXED: replaced async coroutine pattern with persistent cached collectors; onStop now reads synchronously with no suspension.

  BUG 4 (Event flood during clip recording):
  ClipRecorder had no public isRecording property. MonitorService wrote a Room trigger record for every sensor event emitted during an active clip window (~100 mic events for a 30-second clip). FIXED: added ClipRecorder.isRecording backed by the existing AtomicReference<Recording?>; MonitorService skips recordTrigger + startClip for events that arrive while recording is active.

fix: |
  FIX for BUG 3 (HavenApplication.kt — this session):
  Replaced the async coroutine in onStop with synchronous cached reads.
  - Added two persistent `appScope.launch { settingsRepo.xxx.collect { cached = it } }` collectors in onCreate to keep cachedPinEnabled + cachedAutoLockDelay in-memory.
  - onStop now reads those cached vars directly (no coroutine, no suspension).
  - For delay=0: AppLockState.lock() called directly on the main thread — guaranteed to fire before onStop returns.
  - For delay>0: lockJob launched and tracked. onStart cancels lockJob as before.
  - Removed the outer untracked coroutine entirely.

  FIX for BUG 4 (ClipRecorder.kt + MonitorService.kt — this session):
  - ClipRecorder: added `val isRecording: Boolean get() = activeRecording.get() != null`
  - MonitorService sensorFlow.collect: added `if (clipRecorder?.isRecording == true) return@collect` before recordTrigger call. Suppresses all DB writes and clip-start attempts while a clip is already recording. The CAMERA_VIDEO trigger written at clip-end remains the single DB record for the recording window.

  FIX for BUG 1b (previous session):
  Rewrote EventDetailViewModel to track "requested" paths separately from "resolved" paths:
  - Added _requestedPaths: MutableStateFlow<Set<String>> — paths the user has explicitly tapped.
  - Renamed requestPlaybackPath → requestPlayback(mediaPath, cacheDir), checks _requestedPaths before launching.
  - Removed LaunchedEffect-based eager decryption from EventDetailScreen.

  Rewrote TriggerCard media section to use three-state UX:
  1. mediaPath not in requestedPaths → MediaThumbnailPlaceholder (PlayCircle icon, "Tap to play") — no decryption, instant render.
  2. mediaPath in requestedPaths but not in playbackPaths → CircularProgressIndicator (decryption in progress).
  3. mediaPath in playbackPaths → VideoPlayerCard (player ready).

  FIX for BUG 1b continued (this session — user-requested optional encryption):
  User confirmed spinner still unresolvable on weak hardware (decryption too slow even async).
  Added optional media encryption setting so users on slow hardware can disable AES-GCM encryption:
  - SettingsRepository: added KEY_MEDIA_ENCRYPTION_ENABLED DataStore key and mediaEncryptionEnabled Flow (default true) + setter.
  - MonitorService: reads mediaEncryptionEnabled at session start; skips MediaEncryptionManager.encryptInPlace when false, stores plain .mp4 path directly.
  - EventDetailViewModel.requestPlayback: already checks .enc extension — no change needed.
  - SettingsViewModel: added mediaEncryptionEnabled to SettingsUiState and uiState combine chain; added setMediaEncryptionEnabled().
  - SettingsScreen: added Security section with SensorToggleRow + warning note below PIN section.
  - strings.xml (EN + DE): added settings_security_title, settings_media_encryption_label, settings_media_encryption_note.

verification: ./gradlew :app:compileDebugKotlin → BUILD SUCCESSFUL (1 pre-existing deprecation warning only). Runtime verification pending.
files_changed:
  - app/src/main/java/org/havenapp/main/ui/timeline/EventDetailViewModel.kt
  - app/src/main/java/org/havenapp/main/ui/timeline/EventDetailScreen.kt
  - app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt
  - app/src/main/java/org/havenapp/main/MonitorService.kt
  - app/src/main/java/org/havenapp/main/ui/settings/SettingsViewModel.kt
  - app/src/main/java/org/havenapp/main/ui/settings/SettingsScreen.kt
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values-de/strings.xml
  - app/src/main/java/org/havenapp/main/HavenApplication.kt
  - app/src/main/java/org/havenapp/main/media/ClipRecorder.kt
