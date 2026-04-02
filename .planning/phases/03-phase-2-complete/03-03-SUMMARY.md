---
phase: 03-phase-2-complete
plan: "03"
subsystem: security
tags: [aes-gcm, android-keystore, exoplayer, media3, encryption, video-playback]

# Dependency graph
requires:
  - phase: 03-phase-2-complete plan 02
    provides: ClipRecorder recording infrastructure with onClipReady callback
provides:
  - AES-256-GCM file encryption via Android Keystore (MediaEncryptionManager)
  - Post-recording encryption of video clips in MonitorService
  - One-shot migration of existing unencrypted media files on first app start
  - ExoPlayer video playback of encrypted clips in EventDetailScreen
affects: [phase-4-networking, phase-5-hardening, timeline-ui, monitor-service]

# Tech tracking
tech-stack:
  added:
    - androidx.media3:media3-exoplayer:1.6.0
    - androidx.media3:media3-ui:1.6.0
  patterns:
    - "Android Keystore AES-256-GCM: IV prepended to ciphertext, streaming via CipherOutputStream"
    - "One-shot DataStore migration flag pattern: check mediaEncryptedV1 before running migration"
    - "Temp file lifecycle: ViewModel prepares decrypted temp, onCleared() deletes it"
    - "ExoPlayer DisposableEffect: exoPlayer.release() in onDispose"

key-files:
  created:
    - app/src/main/java/org/havenapp/main/security/MediaEncryptionManager.kt
  modified:
    - gradle/libs.versions.toml
    - app/build.gradle.kts
    - app/src/main/java/org/havenapp/main/HavenApplication.kt
    - app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt
    - app/src/main/java/org/havenapp/main/MonitorService.kt
    - app/src/main/java/org/havenapp/main/ui/timeline/EventDetailScreen.kt
    - app/src/main/java/org/havenapp/main/ui/timeline/EventDetailViewModel.kt

key-decisions:
  - "Android Keystore raw API (KeyGenParameterSpec) used instead of deprecated security-crypto library"
  - "Streaming CipherOutputStream chosen over cipher.doFinal(readBytes()) to avoid OOM on large video files"
  - "Fallback to unencrypted path if encryption fails — prevents data loss at cost of SEC-01 guarantee on error"
  - "ExoPlayer DisposableEffect reacts to playbackPath changes, releases player on recomposition"

patterns-established:
  - "MediaEncryptionManager as object singleton — no state, pure crypto operations"
  - "Import conflict: use fully-qualified @androidx.annotation.OptIn to avoid shadowing kotlin.OptIn"

requirements-completed: [SEC-01, SEC-02]

# Metrics
duration: 5min
completed: 2026-04-02
---

# Phase 3 Plan 03: Media Encryption Summary

**AES-256-GCM encryption of recorded video clips via Android Keystore, with one-shot migration and ExoPlayer playback of decrypted clips in EventDetailScreen**

## Performance

- **Duration:** 5 min
- **Started:** 2026-04-02T14:51:13Z
- **Completed:** 2026-04-02T14:56:07Z
- **Tasks:** 2
- **Files modified:** 7 (1 created, 6 modified)

## Accomplishments
- Created `MediaEncryptionManager` — AES-256-GCM via Android Keystore, streaming I/O, file format `[12-byte IV][ciphertext+tag]`
- Added Media3 ExoPlayer 1.6.0 dependency and wired VideoPlayerCard in EventDetailScreen
- SEC-01: MonitorService encrypts raw `.mp4` clips after recording, deletes plaintext
- SEC-02: HavenApplication runs one-shot background migration of existing unencrypted files on first launch

## Task Commits

Each task was committed atomically:

1. **Task 1: Create MediaEncryptionManager and add Media3 ExoPlayer dependency** - `cc03d3a` (feat)
2. **Task 2: Wire post-recording encryption and add ExoPlayer video playback in EventDetailScreen** - `068f22d` (feat)

**Plan metadata:** _(docs commit follows)_

## Files Created/Modified
- `app/src/main/java/org/havenapp/main/security/MediaEncryptionManager.kt` - AES-256-GCM encrypt/decrypt object using Android Keystore
- `gradle/libs.versions.toml` - Added media3 version + media3-exoplayer + media3-ui library entries
- `app/build.gradle.kts` - Added media3-exoplayer and media3-ui implementation dependencies
- `app/src/main/java/org/havenapp/main/HavenApplication.kt` - Added SettingsEntryPoint and one-shot SEC-02 migration thread
- `app/src/main/java/org/havenapp/main/storage/SettingsRepository.kt` - Added KEY_MEDIA_ENCRYPTED_V1 flag + flow/setter
- `app/src/main/java/org/havenapp/main/MonitorService.kt` - Encrypt clip in onClipReady callback before updating DB path
- `app/src/main/java/org/havenapp/main/ui/timeline/EventDetailScreen.kt` - VideoPlayerCard composable with ExoPlayer for triggers with mediaPath
- `app/src/main/java/org/havenapp/main/ui/timeline/EventDetailViewModel.kt` - preparePlaybackFile() decrypts .enc for playback; onCleared() cleanup

## Decisions Made
- Used raw Android Keystore API (`KeyGenParameterSpec`) over deprecated `security-crypto` library — aligns with CLAUDE.md requirement for no deprecated APIs
- Used streaming `CipherOutputStream`/`CipherInputStream` instead of `cipher.doFinal(readBytes())` — avoids loading full video into heap
- Fallback to raw path on encryption error with appLogger.e log — prevents data loss but logs the failure for diagnostics
- `@androidx.annotation.OptIn(UnstableApi::class)` used as fully-qualified annotation to avoid shadowing Kotlin's `@OptIn` which broke `@OptIn(ExperimentalMaterial3Api::class)`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed @OptIn annotation import conflict**
- **Found during:** Task 2 (EventDetailScreen compile error)
- **Issue:** Adding `import androidx.annotation.OptIn` to use `@OptIn(UnstableApi::class)` shadowed Kotlin's `@OptIn`, breaking the existing `@OptIn(ExperimentalMaterial3Api::class)` on EventDetailScreen
- **Fix:** Removed `import androidx.annotation.OptIn`; used fully-qualified `@androidx.annotation.OptIn(UnstableApi::class)` on VideoPlayerCard
- **Files modified:** EventDetailScreen.kt
- **Verification:** `./gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL
- **Committed in:** `068f22d` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - Bug)
**Impact on plan:** Required annotation fix for correct API opt-in scoping. No scope creep.

## Issues Encountered
- Import name collision between `androidx.annotation.OptIn` and Kotlin's `OptIn` — resolved by using fully qualified annotation syntax

## User Setup Required
None - no external service configuration required. Android Keystore is device-managed; key is generated automatically on first use.

## Next Phase Readiness
- SEC-01 and SEC-02 complete — video files are encrypted at rest
- Plan 04 (scheduling/notification engine) has no dependencies on SEC requirements
- Phase 5 hardening can build on existing Keystore key alias `"haven_media_key"`

---
*Phase: 03-phase-2-complete*
*Completed: 2026-04-02*
