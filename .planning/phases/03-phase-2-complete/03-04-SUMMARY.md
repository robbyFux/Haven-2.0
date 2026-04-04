---
plan: 03-04
phase: 03-phase-2-complete
status: complete
completed: 2026-04-04
requirements:
  - SEC-03
  - SEC-04
  - SEC-05
---

## What Was Built

Optional app PIN lock — 4-6 digit PIN required on cold start and after app is backgrounded,
with configurable auto-lock delay (immediate / 30s / never).

## Key Files Created / Modified

- `security/AppLockState.kt` — Process-level lock singleton; starts `locked=true` (pessimistic). `lock()` / `unlock()` called by HavenApplication and HavenNavGraph.
- `security/PinHashManager.kt` — SHA-256 + random salt hashing; constant-time comparison via `MessageDigest.isEqual`.
- `ui/lock/PinLockScreen.kt` — Numeric keypad Compose screen; `BackHandler` prevents back-press escape; dot indicators show entry progress; error state on wrong PIN.
- `HavenApplication.kt` — Reads `pinEnabled` via `ProcessLifecycleOwner` + coroutine scope; calls `AppLockState.lock()` on cold start if PIN enabled; delays lock per `autoLockDelaySeconds` setting.
- `ui/HavenNavGraph.kt` — Gates entire NavHost behind `PinLockScreen` when `locked && pinEnabled == true && pinHash != null`.
- `storage/SettingsRepository.kt` — Added `pinEnabled`, `pinHash`, `pinSalt`, `autoLockDelaySeconds`, `mediaEncryptionEnabled` DataStore keys.
- `ui/settings/SettingsScreen.kt` — PIN enable/disable switch, change-PIN dialog, auto-lock delay radio group (Immediate / 30s / Never), media encryption toggle.
- `MonitorService.kt` — Skip duplicate DB writes while clip is recording (REC-04); conditional AES-GCM encryption based on `mediaEncryptionEnabled` setting.

## Decisions Made

- `AppLockState` starts `locked=true` (pessimistic) instead of false — `HavenNavGraph` auto-unlocks when DataStore confirms PIN is disabled, preventing momentary content flash.
- `pinEnabled: StateFlow<Boolean?>` is nullable in ViewModel so the NavGraph can distinguish "loading" (null) from "PIN off" (false).
- `ProcessLifecycleOwner` caches settings in-memory via coroutine collectors (no `runBlocking` on main thread during lifecycle events).
- Media encryption is now a user-configurable setting (default: on); existing `.enc` files are never re-processed.

## Checkpoint: Human Verification Required

**Build:** `./gradlew :app:assembleDebug` then `adb install app/build/outputs/apk/debug/app-debug.apk`

**Verification steps:**
1. Open Settings → enable PIN → set a 4-digit PIN
2. Background the app (press Home), wait 1 second, return → PIN screen should appear
3. Enter wrong PIN → error shown, content still locked
4. Enter correct PIN → app unlocks
5. Go to Settings → set auto-lock to "30 seconds" → confirm it persists
6. Toggle media encryption off → confirm setting persists across app restart
7. Start monitoring, trigger motion → stop → verify only one event per trigger window in Timeline

## Self-Check

- [x] `compileDebugKotlin` passes (BUILD SUCCESSFUL)
- [x] `AppLockState` starts locked=true, unlocks when PIN is disabled
- [x] `PinHashManager` uses `MessageDigest.isEqual` for constant-time comparison
- [x] `PinHashManager` uses `SecureRandom` for salt generation
- [x] `PinLockScreen` has `BackHandler` to prevent back-press escape
- [x] `HavenNavGraph` checks null-safety on `pinEnabled` during loading
- [x] `ProcessLifecycleOwner` handles auto-lock (no runBlocking on main thread)
- [x] PIN stored as SHA-256+salt, not plain text (SEC-05)
- [x] Media encryption toggle added with default=true
