---
status: diagnosed
trigger: "cloud-event-video-split-and-no-pushover"
created: 2026-04-09T00:00:00Z
updated: 2026-04-09T12:00:00Z
---

## Current Focus

hypothesis: CONFIRMED for both bugs — see Resolution section.
test: Full code trace completed.
expecting: n/a
next_action: Return ROOT CAUSE FOUND

## Symptoms

expected: Pro Sensor-/Kamera-Trigger ein Event in der Cloud UI. Pushover erhält eine Benachrichtigung wenn ein Event eingeht.
actual: (1) Kamera-Events erscheinen doppelt: einmal ohne Video, einmal mit Video. (2) Keine Pushover-Nachrichten ankommen, obwohl Pushover konfiguriert ist.
errors: Keine expliziten Fehlermeldungen bekannt.
reproduction: Android-App löst Kamera-Event aus → zwei Einträge in der Web-UI. Pushover-Benachrichtigung kommt nie an.
timeline: Seit letztem Fix-Versuch (heute): NotificationRouter.route() wurde geändert.

## Eliminated

- hypothesis: route() calls CloudChannel for camera events (since deferresToVideo=true filters it out in route())
  evidence: NotificationRouter.route() line 68-72 correctly skips channels where deferresToVideo=true for camera-type events. CloudChannel.deferresToVideo=true. So route() never touches CloudChannel.
  timestamp: 2026-04-09

- hypothesis: Server-side deduplication is missing but single HTTP request is sent
  evidence: Two separate HTTP POSTs are confirmed (see Evidence below). Server always creates a new Event row per POST — no deduplication logic exists. The server is behaving correctly per its design; the bug is the double POST from the client.
  timestamp: 2026-04-09

## Evidence

- timestamp: 2026-04-09
  checked: MonitorService.kt lines 303-341 (sensorFlow.collect block)
  found: For every trigger, MonitorService calls BOTH `notificationRouter.route(trigger, frame)` (line 307) AND `clipRecorder?.startClip(...)` with a callback that calls `notificationRouter.uploadVideo(trigger, bytes)` (line 325). The route() call is in the main collect block, uploadVideo() is in the clip-finish callback. Both execute for camera-type triggers.
  implication: For a camera event: route() runs immediately — CloudChannel is skipped (deferresToVideo=true) — CORRECT. Then the clip finishes and uploadVideo() runs — CloudChannel sends the event WITH video. That is one POST → one DB entry. This path looks correct.

- timestamp: 2026-04-09
  checked: MonitorService.kt lines 295-302 (clipRecorder?.isRecording guard)
  found: The guard `if (clipRecorder?.isRecording == true) return@collect` suppresses ALL subsequent triggers during a recording, not just duplicate camera triggers. But the FIRST camera trigger is NOT suppressed — it goes through route() AND starts a clip.
  implication: The first camera trigger: route() fires (non-deferred channels only) AND clip starts. After clip finishes, uploadVideo() fires (CloudChannel only). This is ONE DB write on the server per trigger that starts a clip. Single POST on uploadVideo().

- timestamp: 2026-04-09
  checked: NotificationRouter.route() lines 68-73, CloudChannel.deferresToVideo
  found: route() filters `channels.filter { !it.deferresToVideo }` for camera events. CloudChannel.deferresToVideo = true. So route() sends to Pushover, Signal, Mattermost but NOT CloudChannel for camera events. CloudChannel is only called from uploadVideo().
  implication: If Pushover IS configured in NotificationRule, route() WILL call PushoverChannel.send() for camera events. PushoverChannel.deferresToVideo defaults to false (HavenAlertChannel default). So Pushover gets a notification from route() for camera events. This is the Android-side Pushover path.

- timestamp: 2026-04-09
  checked: MonitorService.kt lines 168-177 (channel list construction) + lines 158-163 (NotificationRule)
  found: PushoverChannel is only added to notifChannels if `pushoverEnabled` is true (line 171). NotificationRule includes triggerTypes from `settingsRepository.notificationTriggerTypes`. If camera trigger types are not in that set, Pushover won't fire even if enabled.
  implication: Android-side Pushover only fires if (1) pushoverEnabled=true, (2) camera TriggerType is in notificationTriggerTypes, (3) severity >= minSeverity, (4) cooldown not active. This is a configuration gate, not a code bug.

- timestamp: 2026-04-09
  checked: server/.env line 19
  found: `PUSHOVER_APP_TOKEN=` — empty string. No Pushover app token is set.
  implication: In send_notification_task (notifications.py line 71): `if user.pushover_user_key and settings.PUSHOVER_APP_TOKEN:` — this condition is FALSE because PUSHOVER_APP_TOKEN is empty. Server-side Pushover notifications are NEVER sent regardless of user configuration.

- timestamp: 2026-04-09
  checked: server/app/tasks/notifications.py lines 63-76 (send_notification_task)
  found: send_notification_task is only called when AI_BACKEND="none" (events.py line 210). With AI_BACKEND="none" (set in .env), it IS called. But the Pushover condition requires BOTH user.pushover_user_key (user-side) AND settings.PUSHOVER_APP_TOKEN (server-side). The server token is empty.
  implication: Server-side Pushover is broken by missing PUSHOVER_APP_TOKEN in .env.

- timestamp: 2026-04-09
  checked: BUG 1 — Two DB entries root cause investigation
  found: Re-examining the flow: route() for a camera trigger skips CloudChannel (correct). uploadVideo() sends to CloudChannel → one POST → one DB entry. But: does anything ELSE send to CloudChannel? Looking at MonitorService: there's no other call path to CloudChannel. So the server should receive only ONE POST per camera trigger.
  WAIT — re-read MonitorService line 307 and startClip callback: route() fires immediately for ALL channels except deferred ones. uploadVideo() fires after clip finishes. For a non-camera trigger (motion/mic/light), route() sends to ALL channels including CloudChannel (since they're not deferred). CloudChannel.deferresToVideo=true, but the filter in route() only skips deferred channels for CAMERA events. For non-camera events, route() sends to ALL channels including CloudChannel — that creates a DB entry WITHOUT video. Then if a clip also starts (because clipRecorder.startClip is called regardless of trigger type at line 312), uploadVideo() ALSO sends to CloudChannel → second DB entry WITH video.
  implication: THIS IS BUG 1 ROOT CAUSE. For non-camera trigger types (MICROPHONE, ACCELEROMETER, LIGHT, etc.) that also start a clip: route() sends all channels including CloudChannel (no video), THEN uploadVideo() also sends to CloudChannel (with video) → TWO DB entries.

- timestamp: 2026-04-09
  checked: NotificationRouter.route() lines 68-72 — the camera-type guard
  found: `if (event.type.isCameraType())` only protects CloudChannel from being called in route() when the event IS a camera type. For sensor events (motion, mic, light), CloudChannel IS called in route() (no filter applied). Then uploadVideo() is called in the clip callback regardless of trigger type (no type check on line 314-326).
  implication: Confirmed — the deferresToVideo mechanism only works for camera-type events. Sensor events (which can also trigger clip recording) cause a double-send to CloudChannel.

## Resolution

root_cause: |
  BUG 1 (Double DB entry):
  NotificationRouter.route() only skips CloudChannel (deferresToVideo=true) for camera-type trigger events
  (CAMERA, CAMERA_PERSON, CAMERA_PET, CAMERA_VEHICLE). For all other trigger types (MICROPHONE,
  ACCELEROMETER/motion via FusedMotionMonitor, LIGHT), route() sends to ALL channels including
  CloudChannel — creating one DB entry WITHOUT video. Then MonitorService.startClip() is called for
  ANY trigger type (line 312 has no type guard), and when the clip finishes, uploadVideo() sends to
  CloudChannel again — creating a SECOND DB entry WITH video.
  
  Fix direction: The isCameraType() check in route() should be replaced with a check against
  deferresToVideo — i.e., ANY trigger type that starts a clip should defer CloudChannel. Since
  clipRecorder.startClip() fires for ALL trigger types, ALL trigger types should be treated as
  "deferred" for CloudChannel. Change the filter in NotificationRouter.route() from
  `if (event.type.isCameraType())` to `if (clipRecorder is active / clip will be recorded)` OR
  make the rule simpler: always filter deferresToVideo channels from route() regardless of trigger
  type, relying solely on uploadVideo() for CloudChannel. The cleanest fix: in route(), ALWAYS
  skip deferresToVideo channels (remove the isCameraType() condition). uploadVideo() already
  handles CloudChannel exclusively. Non-camera sensor-only events (when clipRecorder is null or
  unavailable) would then never reach CloudChannel — but that is the correct behavior since
  CloudChannel is video-centric by design.

  BUG 2 (No Pushover notifications):
  Server-side: PUSHOVER_APP_TOKEN is empty in server/.env. The send_notification_task condition
  `if user.pushover_user_key and settings.PUSHOVER_APP_TOKEN` is False because the server token
  is missing. Server-side Pushover is completely disabled.
  Android-side: PushoverChannel in the Android app sends directly to api.pushover.net using
  pushoverAppToken from SettingsRepository (user-configured in the app settings). This is a
  SEPARATE code path from the server-side task. If the user has configured pushoverEnabled=true
  and valid pushoverAppToken + pushoverUserKey in the Android app settings, the Android channel
  SHOULD work independently of the server. However, if the symptom is "no Pushover notifications
  at all," the likely scenario is that the Android pushoverAppToken setting is also empty/unconfigured
  in the app, OR the trigger type is not in notificationTriggerTypes, OR severity is below minSeverity.
  The definitive server-side fix is: set PUSHOVER_APP_TOKEN in server/.env.

fix:
  BUG 1: In NotificationRouter.route(), change the deferresToVideo filter from camera-type-conditional
  to unconditional — always skip deferresToVideo channels in route(), not just for isCameraType() events.
  Line 68-72 in NotificationRouter.kt: remove the `if (event.type.isCameraType())` branch and always
  apply `channels.filter { !it.deferresToVideo }`.
  
  BUG 2: Set PUSHOVER_APP_TOKEN=<actual_token> in server/.env.

verification:
files_changed: []
