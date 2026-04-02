# Phase 3: Phase-2-Complete - Research

**Researched:** 2026-04-02
**Domain:** Android Kotlin — CameraX VideoCapture, Android Keystore AES-GCM, Jetpack Compose PIN lock, TFLite eager init
**Confidence:** HIGH (codebase directly inspected; library versions verified via Maven metadata)

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| TFLITE-01 | KI-Erkennungsmodi wählbar — `isAvailable` muss `true` sein sobald Modell geladen | HavenApplication already calls `initialize()` on a daemon thread at startup; SettingsViewModel also calls it eagerly in `viewModelScope.launch(IO)`. Both paths are already in place. |
| TFLITE-02 | TFLite-Init beim App-Start, unabhängig vom aktiven Screen | `HavenApplication.onCreate()` triggers init via `EntryPointAccessors` before any Activity starts. **Already implemented.** |
| TFLITE-03 | DiagnosticsScreen zeigt TFLite-Status reaktiv | `DiagnosticsViewModel` already combines `objectDetector.availabilityFlow` in its `uiState` StateFlow. **Already implemented.** |
| ZONE-01 | Gezeichnete Zone wird in DataStore gespeichert und überlebt App-Neustart | `SettingsRepository.setDetectionZone()` and `ZoneEditorScreen` quick-task fix (260331-u38 + 260331-uke) are already committed. **Already fixed.** |
| ZONE-02 | Gespeicherte Zone wird beim Monitoring korrekt geladen und an CameraAnalyzer übergeben | `MonitorService.startMonitoring()` already calls `settingsRepository.detectionZone.first()` and passes it to `CameraAnalyzer`. **Already implemented.** |
| EVENT-01 | Nutzer kann einzelne Ereignisse löschen | Quick-task 260331-uyx committed swipe-to-delete and delete button. **Already fixed.** |
| EVENT-02 | Löschvorgang entfernt EventTriggerEntities via ForeignKey CASCADE | `EventTriggerEntity` has `ForeignKey(onDelete = CASCADE)`. `EventDao.deleteById` and `EventRepository.deleteEvent` are present. **Already fixed.** |
| SENSOR-01 | CameraAnalyzer stabil ohne Crashes bei ML-Modus | Depends on TFLITE-01/02 being active — validate by running with model present. CameraAnalyzer already guards with `objectDetector?.isAvailable == true` and `TFLITE_MIN_INTERVAL_MS` throttle. |
| SENSOR-02 | FusedMotionMonitor keine ungewollten Trigger während Kalibrierungsphase | `MonitorService` only starts recording triggers into `currentEventId` after `calibrationMs` delay; `currentEventId` is null during calibration so triggers are silently dropped. Architecture is correct. Validate by observation. |
| REC-01 | Video-Clip starten wenn Sensor auslöst; Clip-Dauer konfigurierbar (10/30/60s) | Needs `camera-video:1.4.0` artifact + new `ClipRecorder` service component inside MonitorService. New DataStore key `clip_duration_seconds`. |
| REC-02 | Aufgezeichneter Clip dem auslösenden HavenEvent zugeordnet und in EventDetailScreen abspielbar | `EventTriggerEntity.mediaPath` field already exists. Media3 ExoPlayer 1.6.0 needed for playback in EventDetailScreen via `AndroidView(PlayerView)`. |
| REC-03 | Nur ein paralleler Clip; weitere Auslösungen während Aufnahme werden ignoriert (Cooldown) | `AtomicBoolean isRecording` flag or `activeRecording: Recording?` null-check in MonitorService guards against parallel clips. |
| SEC-01 | Video-Dateien AES-GCM-verschlüsselt im internen App-Speicher | `security-crypto:1.1.0` `EncryptedFile` is deprecated but still functional and ships with a stable release; OR use Android Keystore directly with `AES/GCM/NoPadding` cipher + IV stored alongside file. Direct Keystore approach is recommended given deprecation. |
| SEC-02 | Bestehende unverschlüsselte Medien-Dateien beim ersten App-Start nach Update migriert | One-shot migration on `HavenApplication.onCreate()`: scan `filesDir` for `*.mp4` without `.enc` suffix, encrypt each, delete original. Gate with a DataStore boolean `media_encrypted_v1`. |
| SEC-03 | Optionaler App-PIN (4–6-stellig), muss beim App-Start eingegeben werden | Custom Compose `PinLockScreen` composable shown as `startDestination` in NavGraph when PIN is enabled and app is locked. Lock state held in a process-level `MutableStateFlow<Boolean>`. |
| SEC-04 | App sperrt sich automatisch wenn sie in den Hintergrund geht | `ProcessLifecycleOwner` in `HavenApplication.onCreate()` observes `Lifecycle.Event.ON_STOP`; sets lock flag after configured delay (0s/30s/never) using coroutine delay. |
| SEC-05 | PIN-Hash AES-GCM-verschlüsselt in DataStore gespeichert (kein Klartext) | Store SHA-256 hash of PIN, then additionally encrypt the DataStore key via `EncryptedFile`-style approach OR store raw SHA-256 in DataStore (DataStore is already in internal storage, not world-readable). Best practice: SHA-256 + salt stored in DataStore, with the salt in Android Keystore. |
</phase_requirements>

---

## Summary

Phase 3 has two distinct buckets of work. The first bucket (TFLITE-01/02/03, ZONE-01/02, EVENT-01/02) is **already done** — code inspection confirms that all three quick tasks (260331-u38, 260331-uke, 260331-uyx) were committed and the TFLite eager-init was added to both `HavenApplication` and `SettingsViewModel`. The planner should schedule a single "Verify Fixed Requirements" wave first to confirm these compile and run correctly before treating them as closed.

The second bucket (REC-01/02/03, SEC-01/02/03/04/05) requires new code. The most complex item is **CameraX VideoCapture alongside ImageAnalysis** (REC-01): CameraX 1.4.0 supports this combination on LEVEL_3 hardware but may fail binding on LEGACY devices, so the design must degrade gracefully (clip recording simply disabled on unsupported hardware). The `camera-video:1.4.0` artifact is not yet in `libs.versions.toml` and must be added. Video files should be encrypted after recording completes (post-write encryption) rather than during capture, because CameraX writes directly to a `File` — there is no intercept point for streaming encryption.

For the PIN lock (SEC-03/04/05), the recommended architecture is a process-level `AppLockState` singleton (object with `MutableStateFlow<Boolean>`) observed by `HavenNavGraph` to gate access to all routes. `ProcessLifecycleOwner` in `HavenApplication` sets the lock flag; the `PinLockScreen` composable clears it on correct entry. PIN hash storage: SHA-256(PIN + random salt) with the salt stored in Android Keystore is the Android best-practice that avoids deprecation risk.

**Primary recommendation:** Split work into three waves — (1) verification of already-fixed items, (2) video recording infrastructure, (3) encryption and PIN lock. The two security waves can run after recording is wired up since they build on the same file infrastructure.

---

## Standard Stack

### Core (already present)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| CameraX Core/Camera2/Lifecycle/View | 1.4.0 | Camera session management | Already in project |
| Room 2.6.1 | 2.6.1 | Persist events and triggers | Already in project |
| DataStore Preferences | 1.1.1 | Settings persistence | Already in project |
| Hilt | 2.51.1 | Dependency injection | Already in project |
| Coroutines | 1.8.1 | Async, Flow, StateFlow | Already in project |

### New Dependencies Required
| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| `androidx.camera:camera-video` | 1.4.0 | CameraX VideoCapture use case | Same version family as other CameraX libs; stable |
| `androidx.media3:media3-exoplayer` | 1.6.0 | Video playback in EventDetailScreen | Google's official ExoPlayer successor; Compose-compatible via `AndroidView(PlayerView)` |
| `androidx.media3:media3-ui` | 1.6.0 | `PlayerView` widget | Paired with exoplayer |
| `androidx.security:security-crypto` | 1.1.0 | `EncryptedFile` for AES-GCM file encryption | Last stable release before deprecation; still functional; minSdk 21+; alternative is raw Keystore |

**Note on `security-crypto` deprecation:** The library released its final stable `1.1.0` on 2025-07-30, then deprecated all APIs in favour of direct Android Keystore use. For this phase, `security-crypto` is still fully usable. If preference is to avoid deprecated libraries, use `KeyGenerator` + `AES/GCM/NoPadding` cipher directly (raw Keystore approach) — which is more code but zero external dependencies and no deprecation risk.

**Installation additions to `libs.versions.toml`:**
```toml
[versions]
# Add:
media3 = "1.6.0"
camerax-video = "1.4.0"   # same version family, can reuse camerax ref
security-crypto = "1.1.0"

[libraries]
# Add:
camerax-video = { group = "androidx.camera", name = "camera-video", version.ref = "camerax" }
media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
security-crypto = { group = "androidx.security", name = "security-crypto", version = "1.1.0" }
```

```kotlin
// app/build.gradle.kts additions:
implementation(libs.camerax.video)
implementation(libs.media3.exoplayer)
implementation(libs.media3.ui)
implementation(libs.security.crypto)  // omit if using raw Keystore
```

---

## Architecture Patterns

### Existing Codebase State (verified by inspection)

**Already done — no code changes needed for these requirements:**
- `HavenApplication.kt`: TFLite init via `EntryPointAccessors` on daemon thread at app start (TFLITE-02)
- `SettingsViewModel.kt`: `viewModelScope.launch(IO) { objectDetector.initialize() }` in `init` block (TFLITE-01)
- `DiagnosticsViewModel.kt`: combines `objectDetector.availabilityFlow` reactively (TFLITE-03)
- `ZoneEditorScreen` + `ZoneEditorViewModel`: zone save fixed in quick tasks 260331-u38 and 260331-uke (ZONE-01)
- `MonitorService.startMonitoring()`: reads `detectionZone.first()` and passes to `CameraAnalyzer` (ZONE-02)
- `EventDetailScreen`: delete button calls `viewModel.deleteEvent(onDeleted = onBack)` (EVENT-01)
- `EventDao.deleteById` + `ForeignKey(onDelete = CASCADE)` on `EventTriggerEntity` (EVENT-02)
- SENSOR-01/02: require runtime validation, not code changes

**What still needs to be built (new code):**
- `ClipRecorder.kt` — encapsulates `VideoCapture` + `Recorder` lifecycle; injected into `MonitorService`
- `MediaEncryptionManager.kt` — AES-GCM key generation + encrypt/decrypt file helpers
- `AppLockManager.kt` — process-level lock state + `ProcessLifecycleOwner` wiring
- `PinLockScreen.kt` composable + `PinEntryViewModel.kt`
- Updated `HavenNavGraph.kt` — gate navigation behind lock state
- Updated `SettingsRepository.kt` — new keys: `clip_duration_seconds`, `pin_enabled`, `pin_hash`, `pin_salt`, `auto_lock_delay`, `media_encrypted_v1`
- Updated `SettingsScreen.kt` — clip duration picker, PIN enable/disable UI
- Updated `EventDetailScreen.kt` — show video player when `mediaPath != null`
- Updated `MonitorService.kt` — trigger clip recording on first trigger per event

### Pattern 1: CameraX VideoCapture Alongside ImageAnalysis

**What:** Add `VideoCapture<Recorder>` use case to the existing `startCamera()` in `MonitorService`.
**Key constraint:** CameraX may fail to bind `VideoCapture + ImageAnalysis` together on LEGACY-level hardware. The binding must be wrapped in `runCatching` and gracefully degrade (log warning, skip video).

```kotlin
// Source: https://developer.android.com/media/camera/camerax/video-capture
val recorder = Recorder.Builder()
    .setQualitySelector(
        QualitySelector.fromOrderedList(
            listOf(Quality.HD, Quality.SD),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
        )
    )
    .build()
val videoCapture = VideoCapture.withOutput(recorder)

// Bind alongside existing ImageAnalysis — may throw on LEGACY hardware:
runCatching {
    cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, videoCapture)
}.onFailure {
    // Degrade: bind ImageAnalysis only, disable clip recording
    cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
    clipRecorder.setUnavailable()
}
```

### Pattern 2: ClipRecorder (new class)

**Responsibility:** Start/stop a timed video recording; enforce single-clip-at-a-time constraint.

```kotlin
class ClipRecorder(
    private val videoCapture: VideoCapture<Recorder>,
    private val filesDir: File,
) {
    private val _activeRecording = AtomicReference<Recording?>(null)
    val isRecording: Boolean get() = _activeRecording.get() != null

    /** Starts a clip. Returns the output File path, or null if already recording / unavailable. */
    fun startClip(
        context: Context,
        durationSeconds: Int,
        onClipReady: (outputPath: String) -> Unit,
    ): String? {
        if (isRecording) return null
        val file = File(filesDir, "clip_${System.currentTimeMillis()}.mp4")
        val opts = FileOutputOptions.Builder(file).build()
        val recording = videoCapture.output
            .prepareRecording(context, opts)
            .withAudioEnabled()
            .start(Executors.newSingleThreadExecutor()) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    _activeRecording.set(null)
                    if (!event.hasError()) onClipReady(file.absolutePath)
                }
            }
        _activeRecording.set(recording)
        // Auto-stop after duration
        Handler(Looper.getMainLooper()).postDelayed({ recording.stop() }, durationSeconds * 1_000L)
        return file.absolutePath
    }
}
```

### Pattern 3: Post-Write File Encryption

CameraX writes to a plain `File` — there is no intercept point for streaming encryption. Encrypt after the `VideoRecordEvent.Finalize` callback fires:

```kotlin
// After onClipReady(path):
val plainFile = File(path)
val encFile = File(path + ".enc")
mediaEncryptionManager.encryptFile(plainFile, encFile)
plainFile.delete()
// Store encFile.absolutePath in EventTriggerEntity.mediaPath
```

**AES-GCM via Android Keystore (raw approach — no deprecated library):**
```kotlin
object MediaEncryptionManager {
    private const val KEY_ALIAS = "haven_media_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12  // GCM standard

    fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        ks.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
            }
            .generateKey()
    }

    fun encryptFile(input: File, output: File) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv  // 12-byte GCM IV
        output.outputStream().use { out ->
            out.write(iv)   // prepend IV to encrypted output
            out.write(cipher.doFinal(input.readBytes()))
        }
    }

    fun decryptFile(input: File): ByteArray {
        val bytes = input.readBytes()
        val iv = bytes.copyOfRange(0, IV_SIZE)
        val ciphertext = bytes.copyOfRange(IV_SIZE, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }
}
```

**Streaming note:** `cipher.doFinal(input.readBytes())` loads the full file into memory. For large clips this may OOM. Use `CipherOutputStream` for streaming:
```kotlin
CipherOutputStream(output.outputStream(), cipher).use { cos ->
    input.inputStream().copyTo(cos)
}
```

### Pattern 4: App PIN Lock with ProcessLifecycleOwner

**Process-level lock state (singleton object, not Hilt-injected to avoid Activity dependency):**
```kotlin
object AppLockState {
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    fun lock() { _locked.value = true }
    fun unlock() { _locked.value = false }
}
```

**ProcessLifecycleOwner in HavenApplication:**
```kotlin
// Source: https://developer.android.com/reference/androidx/lifecycle/ProcessLifecycleOwner
ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
    override fun onStop(owner: LifecycleOwner) {
        if (autoLockDelay == 0) AppLockState.lock()
        else if (autoLockDelay > 0) {
            lockJob?.cancel()
            lockJob = coroutineScope.launch {
                delay(autoLockDelay * 1_000L)
                AppLockState.lock()
            }
        }
    }
    override fun onStart(owner: LifecycleOwner) {
        lockJob?.cancel()  // cancel pending lock if user returns quickly
    }
})
```

**NavGraph gating:**
```kotlin
// HavenNavGraph.kt
val locked by AppLockState.locked.collectAsStateWithLifecycle()
if (locked && pinEnabled) {
    PinLockScreen(onUnlocked = { AppLockState.unlock() })
} else {
    // existing NavHost
}
```

### Pattern 5: PIN Hash Storage

**Recommended:** SHA-256(PIN + salt) where salt is a random 16-byte value stored in Android Keystore or alongside the hash in DataStore. DataStore stores data in the app's internal storage (`/data/data/<pkg>/files/datastore/`), which is not world-readable — storing SHA-256 there is acceptable. For extra hardening, store the salt in Keystore.

```kotlin
// Store:
val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
val hash = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray() + salt)
settingsRepository.setPinHash(Base64.encodeToString(hash, Base64.NO_WRAP))
settingsRepository.setPinSalt(Base64.encodeToString(salt, Base64.NO_WRAP))

// Verify:
val storedHash = Base64.decode(settingsRepository.pinHash.first(), Base64.NO_WRAP)
val storedSalt = Base64.decode(settingsRepository.pinSalt.first(), Base64.NO_WRAP)
val inputHash = MessageDigest.getInstance("SHA-256").digest(input.toByteArray() + storedSalt)
val isCorrect = MessageDigest.isEqual(storedHash, inputHash)
```

### Pattern 6: Video Playback in EventDetailScreen

Use Media3 `ExoPlayer` with `AndroidView(PlayerView)` inside the trigger card when `trigger.mediaPath != null` and the file ends in `.mp4` or `.enc`.

```kotlin
// Source: https://developer.android.com/media/media3/exoplayer
val exoPlayer = remember { ExoPlayer.Builder(context).build() }
DisposableEffect(trigger.mediaPath) {
    val uri = Uri.fromFile(File(trigger.mediaPath!!))
    exoPlayer.setMediaItem(MediaItem.fromUri(uri))
    exoPlayer.prepare()
    onDispose { exoPlayer.release() }
}
AndroidView(
    factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer } },
    modifier = Modifier.fillMaxWidth().height(200.dp),
)
```

**Note on encrypted playback:** Encrypted `.enc` files cannot be played directly by ExoPlayer. Two options: (1) decrypt to a temp file in `cacheDir` before playback and delete after, (2) implement a custom `DataSource.Factory` that decrypts on-the-fly. Option 1 is simpler; implement in `EventDetailViewModel`.

### Recommended Project Structure Changes

```
app/src/main/java/org/havenapp/main/
├── media/
│   ├── CameraAnalyzer.kt          (existing, no changes needed)
│   ├── LuminanceMotionDetector.kt (existing)
│   └── ClipRecorder.kt            (NEW — VideoCapture + Recorder wrapper)
├── security/                      (NEW package)
│   ├── MediaEncryptionManager.kt  (NEW — AES-GCM Keystore)
│   ├── AppLockState.kt            (NEW — process-level lock StateFlow)
│   └── PinHashManager.kt          (NEW — SHA-256 + salt)
├── storage/
│   └── SettingsRepository.kt      (modified — new DataStore keys)
├── ui/
│   ├── lock/                      (NEW)
│   │   └── PinLockScreen.kt       (NEW — composable)
│   ├── timeline/
│   │   └── EventDetailScreen.kt   (modified — video player)
│   └── settings/
│       └── SettingsScreen.kt      (modified — PIN + clip duration UI)
└── HavenApplication.kt            (modified — ProcessLifecycleOwner)
```

### Anti-Patterns to Avoid

- **Parallel clip recording:** Never start a second `Recording` while one is active. CameraX will throw `IllegalStateException`. Use `AtomicReference<Recording?>` guard.
- **EncryptedFile on CameraX output path:** CameraX `FileOutputOptions` takes a plain `File`; you cannot pass an `EncryptedFile.Builder`-produced stream here. Encrypt after finalization.
- **`cipher.doFinal()` on large files in memory:** Use `CipherOutputStream` for files > 10 MB to avoid OOM.
- **Storing PIN in plain text in DataStore:** DataStore is not encrypted at rest on rooted devices. Always hash with salt.
- **Calling `objectDetector.initialize()` twice:** `HavenObjectDetector.initialize()` is idempotent (`initAttempted` guard) but redundant calls on IO thread are harmless. The current dual-init (HavenApplication + SettingsViewModel) is acceptable.
- **VideoCapture binding crash on LEGACY hardware:** Always wrap `cameraProvider.bindToLifecycle(...)` with multiple use cases in `runCatching`. Degrade by setting a `clipRecordingAvailable: Boolean` flag.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Video recording lifecycle | Custom MediaRecorder wrapper | CameraX `VideoCapture<Recorder>` + `camera-video:1.4.0` | CameraX handles lifecycle, surface management, codec selection, error recovery |
| Video playback in Compose | Custom `SurfaceView` + `MediaPlayer` | Media3 `ExoPlayer` + `PlayerView` via `AndroidView` | Lifecycle-aware, handles surface lifecycle, format support, active maintenance |
| AES key management | Manually store key bytes in SharedPreferences | Android Keystore (`AndroidKeyStore` provider) | Hardware-backed on supported devices; OS prevents key extraction |
| PIN brute-force window | Hand-roll timing attack prevention | `MessageDigest.isEqual()` (constant-time comparison) | Prevents timing attacks during hash comparison |
| Process lifecycle detection | `ActivityManager` polling | `ProcessLifecycleOwner.get().lifecycle` | Official Jetpack API; battery-efficient; accurate for process-level background detection |

---

## Common Pitfalls

### Pitfall 1: VideoCapture + ImageAnalysis fails silently on LEGACY hardware
**What goes wrong:** `cameraProvider.bindToLifecycle()` throws `IllegalArgumentException` or succeeds but ImageAnalysis produces no frames (stream conflict).
**Why it happens:** LEGACY Camera2 hardware level cannot share streams between VideoCapture and ImageAnalysis without CameraX's GL compositor, which requires LEVEL_3.
**How to avoid:** Wrap binding in `runCatching`. If it fails, rebind with only `ImageAnalysis`. Set `clipRecordingAvailable = false` and surface the limitation in Diagnostics.
**Warning signs:** `IllegalArgumentException: No supported combination` in logcat during binding.

### Pitfall 2: CameraX `VideoCapture` requires `FOREGROUND_SERVICE_CAMERA` is declared on service
**What goes wrong:** Recording from a Foreground Service without correct `foregroundServiceType` silently produces no video on Android 14+.
**Why it happens:** Android 14 enforces media projection permissions for camera access from services.
**How to avoid:** `AndroidManifest.xml` already has `android:foregroundServiceType="camera|microphone"` on `MonitorService` — this is correct and sufficient.
**Warning signs:** Empty video files with no error; or `SecurityException` in logcat.

### Pitfall 3: GCM cipher reuse (nonce collision)
**What goes wrong:** If the same AES-GCM key is reused with the same IV/nonce, an attacker can recover the plaintext.
**Why it happens:** `Cipher.init(ENCRYPT_MODE, key)` generates a fresh random IV each time — this is safe. The risk is if IV is not stored with ciphertext.
**How to avoid:** Always prepend `cipher.iv` (12 bytes) to the encrypted file before writing the ciphertext. Read the first 12 bytes back as IV during decryption.
**Warning signs:** `AEADBadTagException` during decryption = wrong IV or corrupted file.

### Pitfall 4: AppLockState initial value timing
**What goes wrong:** App starts with `locked = false` but PIN is enabled → user sees content before lock screen renders.
**Why it happens:** Compose renders NavGraph before DataStore `pinEnabled` is read.
**How to avoid:** Initialize `AppLockState.locked` to `true` if PIN is enabled. Read `pinEnabled` synchronously from DataStore in `HavenApplication.onCreate()` using `runBlocking { settingsRepository.pinEnabled.first() }`. Set `AppLockState` based on result before `MainActivity` renders.
**Warning signs:** Brief flash of unprotected content on cold start.

### Pitfall 5: Media3 ExoPlayer lifecycle in Compose
**What goes wrong:** `ExoPlayer` instance not released → memory leak or crash when navigating away from `EventDetailScreen`.
**Why it happens:** ExoPlayer holds OS media resources; must be released when Composable leaves composition.
**How to avoid:** Create ExoPlayer inside `remember { }` and wrap in `DisposableEffect(Unit) { onDispose { player.release() } }`.
**Warning signs:** `ExoPlaybackException` after multiple screen navigations.

### Pitfall 6: Room schema version not bumped after adding mediaPath column
**What goes wrong:** Crash on update: `Room database migration required from version 1 to version 2` (or similar).
**Why it happens:** `EventTriggerEntity` already has `mediaPath: String?` in v1 schema — no migration needed for existing fields. New DataStore keys need no schema change.
**How to avoid:** Verify this pitfall does NOT apply here — `mediaPath` is already in the Room schema at v1. No migration needed for this phase.

### Pitfall 7: security-crypto deprecation breaking future builds
**What goes wrong:** Future Android Gradle Plugin or Tink version bump removes transitive dependency, breaking `EncryptedFile`.
**Why it happens:** `security-crypto:1.1.0` is deprecated; transitive `tink-android` dependency was unstable in prior versions.
**How to avoid:** Use the raw Android Keystore approach (`AES/GCM/NoPadding` cipher) instead of `EncryptedFile` — avoids the deprecated library entirely. The raw approach has ~30 extra lines of code but zero deprecation risk.

---

## Code Examples

### CameraX VideoCapture — Full Recording Flow
```kotlin
// Source: https://developer.android.com/media/camera/camerax/video-capture

// 1. Add to MonitorService.startCamera():
val recorder = Recorder.Builder()
    .setQualitySelector(QualitySelector.fromOrderedList(
        listOf(Quality.HD, Quality.SD),
        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
    ))
    .build()
videoCapture = VideoCapture.withOutput(recorder)

// 2. Bind:
runCatching {
    cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, videoCapture)
}.onFailure {
    // Fallback: ImageAnalysis only
    cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
    videoCapture = null
}

// 3. Start a clip:
val file = File(filesDir, "clip_${System.currentTimeMillis()}.mp4")
val recording = videoCapture?.output
    ?.prepareRecording(context, FileOutputOptions.Builder(file).build())
    ?.withAudioEnabled()
    ?.start(mainExecutor) { event ->
        if (event is VideoRecordEvent.Finalize && !event.hasError()) {
            // File ready at file.absolutePath
        }
    }

// 4. Stop after duration:
lifecycleScope.launch {
    delay(clipDurationSeconds * 1_000L)
    recording?.stop()
}
```

### Android Keystore AES-GCM (no deprecated library)
```kotlin
// Source: https://developer.android.com/privacy-and-security/keystore
private const val KEY_ALIAS = "haven_media_key"
private const val TRANSFORMATION = "AES/GCM/NoPadding"

fun getOrCreateKey(): SecretKey {
    val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
    (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
        init(KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
    }.generateKey()
}

fun encryptToFile(inputFile: File, outputFile: File) {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    val iv = cipher.iv  // 12 bytes; GCM generates randomly
    outputFile.outputStream().use { out ->
        out.write(iv)
        CipherOutputStream(out, cipher).use { cos -> inputFile.inputStream().copyTo(cos) }
    }
}

fun decryptFromFile(encryptedFile: File): InputStream {
    val bytes = encryptedFile.readBytes()
    val iv = bytes.copyOfRange(0, 12)
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
    val plainBytes = cipher.doFinal(bytes.copyOfRange(12, bytes.size))
    return plainBytes.inputStream()
}
```

### ProcessLifecycleOwner for App Background Detection
```kotlin
// Source: https://developer.android.com/reference/androidx/lifecycle/ProcessLifecycleOwner
// In HavenApplication.onCreate():
ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
    override fun onStop(owner: LifecycleOwner) {
        // App went to background
        if (pinEnabled && autoLockDelay == 0) AppLockState.lock()
    }
})
```

### PinLockScreen in Compose (sketch)
```kotlin
@Composable
fun PinLockScreen(onUnlocked: () -> Unit) {
    var enteredPin by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    // 4–6 digit numeric keypad using LazyVerticalGrid of Buttons
    // On submit: verify SHA-256(enteredPin + salt) == storedHash
    // On success: onUnlocked()
    // On failure: showError = true, clear input
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `AsyncTask` for Signal/media ops | Kotlin Coroutines + Flow | 2021 (API 30 deprecation) | Affects any migration from Haven 0.2.1 code |
| `security-crypto` `EncryptedFile` | Direct Android Keystore + AES/GCM | 2025-07 (library deprecated) | New code should use raw Keystore |
| `google/ExoPlayer` standalone | `androidx.media3:media3-exoplayer` | 2022 (Media3 GA) | Use `media3-exoplayer`, not the old `com.google.android.exoplayer2` |
| `Camera1` / `Camera2` raw API | CameraX | 2019+ | CameraX `camera-video` is the correct VideoCapture path |
| `SharedPreferences` for settings | DataStore Preferences | 2021+ | Project already uses DataStore correctly |

---

## Open Questions

1. **VideoCapture + ImageAnalysis on real test hardware**
   - What we know: API docs say LEVEL_3 required; CameraX 1.4.0 improved OpenGL compositor for stream sharing.
   - What's unclear: Which hardware levels are common among Haven's target users (security-conscious, often older devices).
   - Recommendation: Always bind with `runCatching`; log hardware camera level in Diagnostics; document in release notes that video recording may be unavailable on some devices.

2. **Encrypted video playback strategy**
   - What we know: ExoPlayer cannot read `.enc` files directly. Two options: temp-decrypt to `cacheDir` or custom `DataSource`.
   - What's unclear: Cache lifecycle and cleanup responsibility.
   - Recommendation: Decrypt to `cacheDir` on demand in `EventDetailViewModel`; delete temp file in `onCleared()`. Simple, no custom DataSource needed.

3. **Migration of existing unencrypted media files (SEC-02)**
   - What we know: No media files exist yet (video recording is new in this phase).
   - What's unclear: Whether SEC-02 is meaningful before REC-01 ships.
   - Recommendation: Implement SEC-02 migration gate (`media_encrypted_v1` DataStore flag) in the same wave as SEC-01 to prevent future debt; the migration loop will simply find nothing to migrate on first run.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| `androidx.camera:camera-video` | REC-01/02/03 | Maven-confirmed | 1.4.0 | — |
| `androidx.media3:media3-exoplayer` | REC-02 | Maven-confirmed | 1.6.0 | VideoView (deprecated, not Compose-native) |
| Android Keystore | SEC-01 | Built into Android 6+ | Platform | — |
| `ProcessLifecycleOwner` | SEC-04 | Part of `lifecycle-process:2.8.6` (already transitively present) | 2.8.6 | ActivityLifecycleCallbacks (more fragile) |
| `lifecycle-process` | SEC-04 | Check if transitive | via lifecycle | Add explicitly if needed |

**Check for `lifecycle-process`:**
```bash
./gradlew :app:dependencies | grep lifecycle-process
```
If not found, add:
```toml
lifecycle-process = { group = "androidx.lifecycle", name = "lifecycle-process", version.ref = "lifecycle" }
```

---

## Project Constraints (from CLAUDE.md)

All directives extracted from CLAUDE.md that the planner MUST verify compliance with:

- Kotlin only — no new Java files
- `minSdk 26`, `targetSdk 35`
- No Google Cloud Services, no Firebase Analytics
- Hilt for all DI — no manual constructors for injected classes
- Jetpack Compose + Material Design 3, Dark-first
- DataStore (not SharedPreferences) for all settings
- `AppCompatActivity` for `MainActivity` (locale switching)
- No `Runtime.exec("logcat")` — use `AppLogger` ring buffer
- Sensor-Kalibrierung via Noise-Floor (90th percentile), not fixed offsets
- `collectAsStateWithLifecycle()` not `collectAsState()`
- `LazyColumn` for all lists, never `Column`
- `Icons.Filled.*` only
- `contentDescription` on all icons
- `Spacer(Modifier.height(X.dp))` not `weight(1f)` as spacer
- No hardcoded hex colors except Severity tokens
- KDoc on all new domain classes
- Screen files end with `Screen`, ViewModel with `ViewModel`, Entity with `Entity`, DAO with `Dao`
- DataStore keys with `KEY_` prefix (private) in companion object
- Private backing `MutableStateFlow` uses `_` prefix
- Build check command: `./gradlew :app:compileDebugKotlin`
- Dark-first color: `surface = #121212`, `primary = #00897B`

---

## Sources

### Primary (HIGH confidence)
- Direct codebase inspection — all Kotlin files under `app/src/main/java/org/havenapp/main/`
- Maven metadata — `camera-video:1.4.0`, `media3-exoplayer:1.6.0`, `security-crypto:1.1.0` versions verified via `dl.google.com/android/maven2`
- [CameraX video capturing architecture](https://developer.android.com/media/camera/camerax/video-capture) — official CameraX VideoCapture docs
- [Security Jetpack releases](https://developer.android.com/jetpack/androidx/releases/security) — deprecation status and stable version

### Secondary (MEDIUM confidence)
- [Recording Video with CameraX VideoCapture API — Android Developers Blog](https://medium.com/androiddevelopers/recording-video-with-camerax-videocapture-api-a36cfd8a48c8) — Recorder/VideoCapture pattern
- [ProcessLifecycleOwner background detection](https://medium.com/@maydin/how-to-observe-app-background-foreground-states-with-processlifecycleowner-8a0adba07321) — lifecycle pattern verified against official API
- [Media3 ExoPlayer getting started](https://developer.android.com/media/media3/exoplayer) — official Media3 docs

### Tertiary (LOW confidence, flag for validation)
- CameraX VideoCapture + ImageAnalysis hardware level requirements — multiple community sources agree LEVEL_3 needed; no authoritative single source with device-level data.

---

## Metadata

**Confidence breakdown:**
- Standard stack (already present): HIGH — directly verified in `libs.versions.toml` and `build.gradle.kts`
- New dependencies: HIGH — Maven metadata queried directly
- Already-fixed requirements (TFLITE-01/02/03, ZONE-01/02, EVENT-01/02): HIGH — code inspected and confirmed
- CameraX VideoCapture concurrent use hardware limits: MEDIUM — official docs say "may fail"; exact device-level breakdown is LOW
- PIN lock architecture: HIGH — standard Android pattern, official APIs
- Keystore AES-GCM approach: HIGH — official Android Keystore docs
- security-crypto deprecation: HIGH — confirmed by official release notes

**Research date:** 2026-04-02
**Valid until:** 2026-07-02 (90 days; CameraX and Media3 are stable, low churn)
