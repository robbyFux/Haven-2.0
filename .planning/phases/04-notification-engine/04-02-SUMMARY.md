---
phase: 04-notification-engine
plan: 02
subsystem: notify
tags: [okhttp, signal, mattermost, cameraanalyzer, jpeg, notification]

# Dependency graph
requires:
  - phase: 04-notification-engine
    plan: 01
    provides: HavenAlertChannel interface, OkHttpClient singleton (NetworkModule), NotificationRule

provides:
  - SignalRestChannel class (notify package): POST /v2/send with base64 JPEG attachment + Bearer auth
  - MattermostChannel class (notify package): Markdown webhook POST, attachment-ignored by design
  - CameraAnalyzer.lastJpegFrame: @Volatile ByteArray? capturing full-color NV21 JPEG on confirmed motion

affects:
  - 04-04 (NotificationRouter: instantiates SignalRestChannel and MattermostChannel, reads lastJpegFrame)
  - 04-03 (if separate — these implementations are the concrete channels)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Plain Kotlin class (not Hilt singleton) for channel implementations — instantiated per session with current config
    - Hand-built JSON with jsonStr() escaper — no JSON library needed for two-field payloads
    - @Volatile var lastJpegFrame for cross-thread frame sharing (single writer, multiple readers)
    - attachment parameter explicitly @Suppress("UNUSED_PARAMETER") in MattermostChannel.send()

key-files:
  created:
    - app/src/main/java/org/havenapp/main/notify/SignalRestChannel.kt
    - app/src/main/java/org/havenapp/main/notify/MattermostChannel.kt
  modified:
    - app/src/main/java/org/havenapp/main/media/CameraAnalyzer.kt

key-decisions:
  - "SignalRestChannel and MattermostChannel are plain Kotlin classes (not Hilt singletons): instantiated per monitoring session with a settings snapshot, same pattern as other session-scoped objects"
  - "MattermostChannel.send() explicitly ignores attachment parameter — Mattermost Incoming Webhooks do not support binary file uploads (Pitfall 2 from research)"
  - "lastJpegFrame uses full-color NV21 JPEG (not grayscale) by reusing buildBitmap UV-plane extraction — same quality as TFLite bitmaps"

patterns-established:
  - "Channel implementations are plain classes injected by MonitorService, not Hilt-managed — enables per-session config snapshots without re-initialization complexity"
  - "JSON strings hand-built with jsonStr() escaper for short payloads; avoids adding a JSON library for two static POST shapes"

requirements-completed: [NOTIF-02, NOTIF-03]

# Metrics
duration: 2min
completed: 2026-04-05
---

# Phase 4 Plan 02: Channel Implementations Summary

**SignalRestChannel (POST /v2/send with base64 JPEG) and MattermostChannel (Markdown webhook, attachment ignored) implementing HavenAlertChannel, plus @Volatile lastJpegFrame in CameraAnalyzer for notification thumbnails**

## Performance

- **Duration:** ~2 min
- **Started:** 2026-04-05T21:15:44Z
- **Completed:** 2026-04-05T21:17:53Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- Implemented `SignalRestChannel` with POST `/v2/send`, optional Bearer auth, and base64 JPEG attachment in `base64_attachments` array format
- Implemented `MattermostChannel` with Markdown-formatted webhook POST; explicitly ignores `attachment` parameter per Mattermost webhook limitation
- Added `@Volatile var lastJpegFrame: ByteArray?` to `CameraAnalyzer`, capturing a full-color NV21 JPEG at quality 60 on every confirmed-motion frame for use as notification attachment

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement SignalRestChannel and MattermostChannel** - `e5e5bbf` (feat)
2. **Task 2: Add lastJpegFrame capture to CameraAnalyzer** - `1501db6` (feat)

**Plan metadata:** (this commit)

## Files Created/Modified
- `app/src/main/java/org/havenapp/main/notify/SignalRestChannel.kt` - Signal REST API channel: POST /v2/send, base64 JPEG attachment, optional Bearer token
- `app/src/main/java/org/havenapp/main/notify/MattermostChannel.kt` - Mattermost webhook channel: Markdown POST, attachment intentionally ignored
- `app/src/main/java/org/havenapp/main/media/CameraAnalyzer.kt` - Added @Volatile lastJpegFrame field + NV21 JPEG capture on confirmed motion

## Decisions Made
- Channel implementations are plain Kotlin classes (not `@Singleton @Inject constructor`) because they need to be re-instantiated each monitoring session with a fresh snapshot of user settings (server URL, tokens, etc.). The `NotificationRouter` singleton (plan 04) holds the channel list for the current session.
- `MattermostChannel.send()` ignores the `attachment` parameter — Mattermost Incoming Webhooks only support Slack-style rich text "attachments" (structured formatting), not binary file uploads. Documented in KDoc and suppressed the unused parameter warning.
- `lastJpegFrame` uses full-color NV21 construction (same UV-plane extraction as `buildBitmap`) rather than grayscale. The image planes are still open inside `image.use {}` at the capture point, so reusing the same pattern costs nothing extra and produces a better thumbnail.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - channel implementations are inert until configured in Settings (plan 05). External Signal server and Mattermost instance are user-provided.

## Known Stubs
None - both channels are fully implemented. `isEnabled` gracefully returns false when config fields are blank, so they are safely no-ops without configuration.

## Next Phase Readiness
- `SignalRestChannel` and `MattermostChannel` are ready for instantiation in `NotificationRouter` (plan 04)
- `CameraAnalyzer.lastJpegFrame` is accessible as a volatile field read by `MonitorService` and passed to `NotificationRouter.route(trigger, frame)`
- Plan 04 (NotificationRouter) can directly call `channel.send(event, lastJpegFrame)` for CAMERA-type triggers

## Self-Check: PASSED

- FOUND: SignalRestChannel.kt
- FOUND: MattermostChannel.kt
- FOUND: 04-02-SUMMARY.md
- FOUND: commit e5e5bbf (Task 1)
- FOUND: commit 1501db6 (Task 2)

---
*Phase: 04-notification-engine*
*Completed: 2026-04-05*
