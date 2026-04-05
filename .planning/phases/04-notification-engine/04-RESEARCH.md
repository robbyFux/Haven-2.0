# Phase 4: NotificationEngine — Research

**Researched:** 2026-04-05
**Domain:** Android outbound alerting — Signal REST API, Mattermost webhooks, Hilt DI, Compose settings restructuring, DataStore serialization, CameraX frame capture
**Confidence:** HIGH (core patterns verified against source code; API details verified against official docs and GitHub)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Two channels: `SignalRestChannel` + `MattermostChannel`. Both implement `NotificationChannel` interface (`send(event, attachment)` + `sendHeartbeat()`).
- **D-02:** Channel config via AlertDialog in SettingsScreen per channel card — analog to `PinSetupDialog` pattern. Signal fields: Server-URL, Sender-Nummer, Empfänger-Nummer, optionaler Bearer-JWT. Mattermost field: Webhook-URL.
- **D-03:** All connection data stored in DataStore (existing `KEY_` convention). No Android Keystore for config fields in Phase 4.
- **D-04:** (Claude's Discretion) AlertDialog layout, field order, validation, save button.
- **D-05:** One global `NotificationRule`: `minSeverity`, `cooldownMs` (Off/1/5/15/30 min), `triggerTypes: Set<TriggerType>` (CAMERA, CAMERA_PERSON, CAMERA_PET, CAMERA_VEHICLE, MICROPHONE defaults), `attachMedia: Boolean`.
- **D-06:** NotificationRule persisted in DataStore (individual fields, not serialized object).
- **D-07:** `quietHours` — NOT in Phase 4 (deferred).
- **D-08:** Heartbeat per channel: Off / 15 / 30 / 60 min.
- **D-09:** (Claude's Discretion) Heartbeat implementation — WorkManager vs. coroutine loop in MonitorService.
- **D-10:** Heartbeat message: timestamp + app version + current MonitorState.
- **D-11:** Attachment: JPEG still only (no video). Last CameraAnalyzer frame as base64-encoded JPEG in Signal REST body.
- **D-12:** `CameraAnalyzer` holds last valid bitmap as `ByteArray?` — readable by `NotificationRouter`.
- **D-13:** Attachment only for CAMERA trigger types. Other types: no attachment even if `attachMedia=true`.
- **D-14:** SettingsScreen rebuilt with Cards per category. `surfaceVariant` + `RoundedCornerShape(12.dp)`.
- **D-15:** Existing `SettingsSection` blocks kept inside Cards as sub-headers.
- **D-16:** Five category Cards: Erkennung / Aufzeichnung / Sicherheit / Benachrichtigungen / App.
- **D-17:** Two log levels: Normal (drop DEBUG) / Debug (capture all 4 levels).
- **D-18:** Level persisted in DataStore (`KEY_LOG_LEVEL`). Default: Normal.
- **D-19:** (Claude's Discretion) Thread-safe LogLevel check in `AppLogger`.
- **D-20:** DiagnosticsScreen shows all captured entries without additional filter.

### Claude's Discretion

- AlertDialog layout (field order, validation, save button)
- Heartbeat implementation strategy (WorkManager vs. MonitorService coroutine)
- Thread-safe LogLevel implementation in `AppLogger`
- Signal REST API error handling (retry strategy, timeout)
- Number of Signal recipients in Phase 4 (recommendation: one, extensible)

### Deferred Ideas (OUT OF SCOPE)

- Quiet Hours (`quietHours: TimeRange?`) — Phase 5
- Multiple Signal recipients — future phase
- Video clip attachment — Phase 5
- `SignalIntentChannel` — Phase 5
- Per-channel NotificationRule override — Phase 5
- Timeline-level filter (from Phase 3 CONTEXT.md) — Phase 5/UX
</user_constraints>

---

## Summary

Phase 4 adds the outbound alerting layer on top of the existing sensor/event architecture. The core technical challenge is integrating two heterogeneous external APIs (signal-cli REST and Mattermost webhooks) into a clean, testable `NotificationChannel` abstraction, while keeping configuration in DataStore and routing logic in a Hilt singleton injected into `MonitorService`.

The signal-cli REST API (`bbernhard/signal-cli-rest-api`) does NOT have built-in authentication — the project owner explicitly chose against it. The CLAUDE.md architecture doc references "Bearer JWT" but this is a user-side concern (a reverse proxy or the user's own setup). The app must accept an optional bearer token string and include it as `Authorization: Bearer <token>` if non-empty. The `/v2/send` endpoint accepts `base64_attachments` as an array of strings in three formats (plain base64, `data:<MIME>;base64,...`, or with filename). The Mattermost Incoming Webhook accepts JSON POST with `text`, `username`, and `icon_emoji` — no binary file attachments are supported.

The heartbeat decision (D-09) strongly favors a coroutine `delay` loop inside `MonitorService`'s `lifecycleScope` for Phase 4 use case, since the heartbeat is only meaningful while monitoring is active. WorkManager's minimum periodic interval is 15 minutes and persists across process death — inappropriate for session-scoped heartbeats where the MonitorService IS the session. Settings screen restructuring from `Column + verticalScroll` to Category Cards keeps the same scroll model (not LazyColumn) which is correct for a bounded, static settings list.

**Primary recommendation:** Use coroutine `delay` loop for heartbeat inside `MonitorService.startMonitoring()`, OkHttp 4.12.0 for HTTP calls (no Retrofit needed — two simple POSTs), `@Volatile` for LogLevel atomic flag in `AppLogger`, and `stringSetPreferencesKey` for TriggerType whitelist in DataStore.

---

## Standard Stack

### Core (New Dependencies)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| OkHttp | 4.12.0 | HTTP client for Signal REST + Mattermost POST | Ships with Android stdlib; no additional transitive deps; suspend-friendly via `withContext(Dispatchers.IO)` |

No other new library additions are needed. All existing dependencies (Hilt, DataStore, Coroutines, Compose) already cover the remaining requirements.

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| OkHttp 4.x | Retrofit | Retrofit adds value for complex APIs; two static POSTs don't justify the dependency |
| OkHttp 4.x | HttpURLConnection | Built-in but no coroutine integration, verbose, error-prone |
| OkHttp 4.x | Ktor Client | Clean coroutine API but adds 500KB+ multiplatform overhead; overkill |
| Coroutine delay loop | WorkManager | WorkManager min interval 15 min, persists across process death — wrong for session-scoped heartbeat |

**Installation (add to `app/build.gradle.kts` dependencies):**
```kotlin
implementation("com.squareup.okhttp3:okhttp:4.12.0")
```

**Add to `gradle/libs.versions.toml`:**
```toml
[versions]
okhttp = "4.12.0"

[libraries]
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
```

**Version verified:** OkHttp 4.12.0 is the latest stable 4.x release (October 2023). OkHttp 5.x is alpha and changes the Android artifact ID — avoid for now.

---

## Architecture Patterns

### Recommended Package Structure

```
app/src/main/java/org/havenapp/main/
└── notify/
    ├── NotificationChannel.kt       # interface: send(event, attachment?) + sendHeartbeat()
    ├── NotificationRule.kt          # data class: minSeverity, cooldownMs, triggerTypes, attachMedia
    ├── NotificationRouter.kt        # @Singleton: routes events through active channels
    ├── SignalRestChannel.kt          # POST /v2/send to signal-cli REST API
    └── MattermostChannel.kt         # POST <webhook-url> to Mattermost
```

Note: `NotificationChannel` naming conflicts with `android.app.NotificationChannel`. Use fully qualified name or rename to `AlertChannel` / `HavenNotificationChannel`. Given the existing CLAUDE.md architecture uses `NotificationChannel`, use explicit package imports and keep the name.

### Pattern 1: NotificationChannel Interface

```kotlin
// notify/NotificationChannel.kt
interface NotificationChannel {
    val id: String
    val isEnabled: Boolean
    suspend fun send(event: TriggerEvent, attachment: ByteArray?): Result<Unit>
    suspend fun sendHeartbeat(message: String): Result<Unit>
}
```

`send()` is a suspend function — callers use `withContext(Dispatchers.IO)` inside the implementation. `Result<Unit>` enables the router to log failures without crashing.

### Pattern 2: Signal REST /v2/send Payload

Verified from GitHub EXAMPLES.md and issue #111:

```kotlin
// POST http://<server-url>/v2/send
// Headers: Content-Type: application/json
//          Authorization: Bearer <token>  (only if token non-empty)
// Body:
val payload = buildJsonObject {
    put("message", messageText)
    put("number", senderNumber)
    putJsonArray("recipients") { add(recipientNumber) }
    if (attachment != null) {
        putJsonArray("base64_attachments") {
            // Format: "data:image/jpeg;filename=alert.jpg;base64,<BASE64>"
            add("data:image/jpeg;filename=alert.jpg;base64,${Base64.encodeToString(attachment, Base64.NO_WRAP)}")
        }
    }
}
```

**Response codes:**
- `201 Created` — success, returns `{ "timestamp": <long> }`
- `400 Bad Request` — returns `{ "error": "<message>" }`

**Authentication:** signal-cli REST API has NO built-in auth. The Bearer token field in the dialog is for a user-configured reverse proxy. Store as empty string (no auth) or non-empty (add Authorization header). This is confirmed by the project owner in issue #519.

### Pattern 3: Mattermost Webhook Payload

Verified from official Mattermost developer docs:

```kotlin
// POST <webhook-url>
// Headers: Content-Type: application/json
// Body:
val payload = buildJsonObject {
    put("text", markdownText)       // max 16,383 chars; auto-splits if longer
    put("username", "Haven")
    put("icon_emoji", ":shield:")
}
```

**File attachments:** NOT supported via incoming webhooks. Mattermost "attachments" in docs refers to Slack-style message formatting objects (structured text), NOT file uploads. Confirmed in forum discussion. This aligns with D-13 (attachment only for SignalRestChannel).

**Markdown formatting for event message:**
```
**[CAMERA_PERSON]** MEDIUM – Motion detected
Timestamp: 2026-04-05 14:32:11
Sensor value: 0.18
```

### Pattern 4: NotificationRouter (Singleton)

```kotlin
@Singleton
class NotificationRouter @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    private var rule: NotificationRule = NotificationRule()  // loaded at session start
    private var channels: List<NotificationChannel> = emptyList()
    private var lastNotifiedMs: Long = 0L

    fun initialize(rule: NotificationRule, channels: List<NotificationChannel>) { ... }

    suspend fun route(event: TriggerEvent, lastFrame: ByteArray?) {
        if (event.severity < rule.minSeverity) return
        if (event.type !in rule.triggerTypes) return
        val now = System.currentTimeMillis()
        if (now - lastNotifiedMs < rule.cooldownMs) return
        lastNotifiedMs = now
        val attachment = if (rule.attachMedia && event.type.isCameraType()) lastFrame else null
        channels.forEach { ch ->
            runCatching { ch.send(event, attachment) }
                .onFailure { appLogger.e("NotificationRouter", "Channel ${ch.id} failed: ${it.message}") }
        }
    }
}
```

The router is initialized in `MonitorService.startMonitoring()` with a snapshot of settings (same pattern as other settings reads via `.first()`).

### Pattern 5: Heartbeat Coroutine Loop

```kotlin
// Inside MonitorService.startMonitoring() monitoringJob:
// After _state.value = MonitorState.ACTIVE

if (heartbeatIntervalSignal > 0) {
    launch {
        while (true) {
            delay(heartbeatIntervalSignal * 60_000L)
            val msg = "Haven alive – ${BuildConfig.VERSION_NAME} – ${_state.value} – ${java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"
            runCatching { signalChannel.sendHeartbeat(msg) }
        }
    }
}
```

This runs inside `monitoringJob`'s scope — cancelled automatically when `monitoringJob.cancel()` is called on stop. No WorkManager needed.

### Pattern 6: JPEG Frame Capture in CameraAnalyzer

`CameraAnalyzer` already produces a JPEG `ByteArray` inside `buildBitmap()` (via `YuvImage.compressToJpeg`). The simplest extension is to hold the last JPEG output as a volatile field:

```kotlin
// In CameraAnalyzer:
@Volatile var lastJpegFrame: ByteArray? = null
    private set

// Inside analyze(), after motionConfirmed = true:
// Capture the JPEG for notification attachment (< 200 KB at quality 60)
runCatching {
    val out = ByteArrayOutputStream()
    YuvImage(nv21, ImageFormat.NV21, width, height, null)
        .compressToJpeg(Rect(0, 0, width, height), 60, out)
    lastJpegFrame = out.toByteArray()
}.onFailure { /* non-critical, ignore */ }
```

`@Volatile` is sufficient here: reads are from the notification coroutine (main dispatcher or IO), writes are from the camera executor thread. No compound operation (compare-and-swap) is needed — newest frame wins.

`NotificationRouter` reads `cameraAnalyzer?.lastJpegFrame` — `MonitorService` holds the reference and passes it to the router at route time.

### Pattern 7: DataStore for Set<TriggerType>

DataStore Preferences supports `stringSetPreferencesKey` natively for `Set<String>`. Store trigger type names as strings:

```kotlin
private val KEY_TRIGGER_TYPE_WHITELIST = stringSetPreferencesKey("notification_trigger_types")

val notificationTriggerTypes: Flow<Set<TriggerType>> = dataStore.data.map { prefs ->
    prefs[KEY_TRIGGER_TYPE_WHITELIST]
        ?.mapNotNull { runCatching { TriggerType.valueOf(it) }.getOrNull() }
        ?.toSet()
        ?: setOf(TriggerType.CAMERA, TriggerType.CAMERA_PERSON, TriggerType.CAMERA_PET,
                 TriggerType.CAMERA_VEHICLE, TriggerType.MICROPHONE)
}

suspend fun setNotificationTriggerTypes(types: Set<TriggerType>) {
    dataStore.edit { it[KEY_TRIGGER_TYPE_WHITELIST] = types.map { t -> t.name }.toSet() }
}
```

### Pattern 8: AppLogger LogLevel (Thread-Safe)

```kotlin
// In AppLogger:
@Volatile private var currentLevel: Level = Level.INFO  // Normal = ignore DEBUG

fun setLogLevel(level: Level) { currentLevel = level }

@Synchronized
fun log(level: Level, tag: String, message: String) {
    if (level == Level.DEBUG && currentLevel != Level.DEBUG) return  // filter at capture
    // ... existing ring-buffer logic
}
```

`@Volatile` for the level field ensures visibility across threads without synchronization overhead for reads. The existing `@Synchronized` on `log()` already protects the ring-buffer write. `setLogLevel()` only sets a single primitive-backed reference — volatile is sufficient.

The `LogLevel` enum maps to `AppLogger.Level`: Normal → `Level.INFO` (threshold), Debug → `Level.DEBUG` (threshold).

### Pattern 9: SettingsScreen Card Restructuring

Current: `Column + verticalScroll` with `HorizontalDivider` between sections.
After: `Column + verticalScroll` (SAME scroll container) with `Card` wrappers per category.

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp)
        .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(16.dp),
) {
    CategoryCard(title = stringResource(R.string.settings_cat_detection)) {
        SettingsSection(title = ...) { /* sensitivity */ }
        SettingsSection(title = ...) { /* camera */ }
        // ...
    }
    CategoryCard(title = stringResource(R.string.settings_cat_notifications)) {
        // Signal channel config
        // Mattermost channel config
        // NotificationRule config
    }
    // ...
}
```

**Do NOT use LazyColumn** for SettingsScreen. Settings screens have a bounded, known number of items — `Column + verticalScroll` is correct (matches existing pattern, no unbounded height issue, simpler state management). LazyColumn is inappropriate here and would require height constraints on Card children.

### Anti-Patterns to Avoid

- **LazyColumn inside verticalScroll:** Throws `IllegalStateException` (unbounded height). SettingsScreen uses Column + verticalScroll — keep it.
- **WorkManager for heartbeat:** Minimum 15-min period; persists after service death; wrong lifecycle scope. Use coroutine delay loop.
- **Serializing NotificationRule as a single JSON string:** DataStore handles individual keys atomically. Serializing the whole object as JSON adds parsing complexity and loses atomic field updates.
- **Calling `image.close()` in CameraAnalyzer:** `ImageProxy` is closed by the framework when `analyze()` returns. Never call `close()` manually inside `ImageAnalysis.Analyzer`.
- **Storing JPEG attachment as a file:** Keep as `ByteArray?` in memory. Writing to disk before sending adds latency, encryption complexity (Phase 5 concern), and cleanup overhead.
- **Retrofit for two POSTs:** Retrofit adds ~200KB AAR + Gson/Moshi dependency for two simple JSON requests. OkHttp alone is sufficient.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTTP client with suspend support | Custom HttpURLConnection wrapper | OkHttp 4.x + `withContext(Dispatchers.IO)` | Thread safety, timeouts, connection pooling, redirect handling |
| Base64 JPEG encoding | Manual byte manipulation | `android.util.Base64.encodeToString(bytes, Base64.NO_WRAP)` | Platform-provided, no dep needed |
| Set<String> DataStore serialization | JSON array string + custom parser | `stringSetPreferencesKey` (built-in DataStore) | Atomic, type-safe, no parsing |
| Concurrent last-frame access | Lock/mutex around ByteArray | `@Volatile var lastJpegFrame: ByteArray?` | Single write (camera thread), multiple reads — volatile visibility sufficient |

---

## Common Pitfalls

### Pitfall 1: signal-cli REST API — No Built-in Auth

**What goes wrong:** Developer assumes the `Authorization: Bearer <token>` header is validated by signal-cli itself. The API accepts any request without auth by default.
**Why it happens:** CLAUDE.md architecture lists "Bearer JWT" as a feature, but this is a reverse-proxy concern.
**How to avoid:** Store the token in DataStore (optional field, empty = no auth). If non-empty, add the header. Document in the UI that this is for a user-configured nginx/Caddy proxy layer.
**Warning signs:** "Why is auth not working?" — because there's nothing to authenticate against unless the user configures a proxy.

### Pitfall 2: Mattermost Webhook — No File Attachments

**What goes wrong:** Attempt to send a JPEG file via Mattermost webhook.
**Why it happens:** Mattermost "message attachments" = Slack-style rich text objects, NOT file uploads.
**How to avoid:** Mattermost channel never receives `attachment: ByteArray?`. Implement `MattermostChannel.send()` to ignore the attachment parameter entirely. Document this in the channel.
**Warning signs:** HTTP 400 from Mattermost webhook when including binary data.

### Pitfall 3: NotificationChannel Name Conflict

**What goes wrong:** Kotlin import ambiguity between `org.havenapp.main.notify.NotificationChannel` and `android.app.NotificationChannel`.
**Why it happens:** MonitorService uses `android.app.NotificationChannel` to create the system notification channel.
**How to avoid:** In `MonitorService.kt`, explicitly import `android.app.NotificationChannel` with an alias: `import android.app.NotificationChannel as AndroidNotificationChannel`. Or rename the Haven interface to `AlertChannel`.
**Warning signs:** Compile error "ambiguous reference to NotificationChannel".

### Pitfall 4: CameraAnalyzer lastJpegFrame — Stale Frame Risk

**What goes wrong:** Router sends a JPEG from a previous event, not the triggering event.
**Why it happens:** `lastJpegFrame` is updated on every confirmed motion frame, not just on trigger emission. The frame at router call time may be slightly later than the trigger.
**How to avoid:** This is acceptable — the "last frame" before the notification is sent is the closest available still to the event. Document behavior. Alternatively, capture the frame at trigger time and pass it directly through `TriggerEvent.mediaPath`-style, but that complicates the flow.
**Warning signs:** Not a bug, but a known approximate behavior. Document in KDoc.

### Pitfall 5: DataStore `stringSetPreferencesKey` — Ordering Not Guaranteed

**What goes wrong:** Assuming `Set<String>` from DataStore has consistent ordering for display in the MultiSelect UI.
**Why it happens:** `Set<String>` is unordered. If the Compose UI iterates the set for display, order may change between reads.
**How to avoid:** When displaying the trigger type checkboxes in the dialog, iterate over `TriggerType.entries` (stable enum order), NOT over the stored set. Use the stored set only for the checked/unchecked state lookup.

### Pitfall 6: OkHttp DNS on Main Thread

**What goes wrong:** `OkHttpClient.newCall(request).execute()` called on main thread → `NetworkOnMainThreadException`.
**Why it happens:** `execute()` is blocking. Even `enqueue()` callbacks run on OkHttp's internal thread.
**How to avoid:** Always call OkHttp inside `withContext(Dispatchers.IO)` in a suspend function. `suspend fun send(...)` in `SignalRestChannel` and `MattermostChannel` enforces this.

### Pitfall 7: Heartbeat After MonitorService Stop

**What goes wrong:** Heartbeat coroutine continues sending after monitoring has stopped.
**Why it happens:** Forgetting that the heartbeat launch is inside `monitoringJob`.
**How to avoid:** Launch heartbeat coroutines as children of the same coroutine scope as `monitoringJob`. When `monitoringJob.cancel()` is called, all child coroutines are cancelled. This is guaranteed by structured concurrency — no extra cleanup needed.

---

## Code Examples

### Signal REST — Minimal Send

```kotlin
// Source: github.com/bbernhard/signal-cli-rest-api EXAMPLES.md + issue #111
suspend fun send(event: TriggerEvent, attachment: ByteArray?): Result<Unit> =
    withContext(Dispatchers.IO) {
        runCatching {
            val bodyJson = buildString {
                append("""{"message":${jsonString(buildMessage(event))}""")
                append(""","number":${jsonString(senderNumber)}""")
                append(""","recipients":[${jsonString(recipientNumber)}]""")
                if (attachment != null) {
                    val b64 = Base64.encodeToString(attachment, Base64.NO_WRAP)
                    append(""","base64_attachments":["data:image/jpeg;filename=alert.jpg;base64,$b64"]""")
                }
                append("}")
            }
            val body = bodyJson.toRequestBody("application/json".toMediaType())
            val requestBuilder = Request.Builder()
                .url("$serverUrl/v2/send")
                .post(body)
                .header("Content-Type", "application/json")
            if (bearerToken.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $bearerToken")
            }
            val response = httpClient.newCall(requestBuilder.build()).execute()
            check(response.isSuccessful) { "Signal API error ${response.code}: ${response.body?.string()}" }
        }
    }
```

### Mattermost Webhook — Minimal Send

```kotlin
// Source: developers.mattermost.com/integrate/webhooks/incoming/
suspend fun send(event: TriggerEvent, attachment: ByteArray?): Result<Unit> =
    withContext(Dispatchers.IO) {
        runCatching {
            // attachment is intentionally ignored — Mattermost webhooks do not support file uploads
            val text = buildMarkdownMessage(event)
            val bodyJson = """{"text":${jsonString(text)},"username":"Haven","icon_emoji":":shield:"}"""
            val body = bodyJson.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(webhookUrl).post(body).build()
            val response = httpClient.newCall(request).execute()
            check(response.isSuccessful) { "Mattermost webhook error ${response.code}" }
        }
    }
```

### DataStore New Keys for Phase 4

```kotlin
// ~12 new keys in SettingsRepository companion object:
private val KEY_SIGNAL_ENABLED         = booleanPreferencesKey("signal_enabled")
private val KEY_SIGNAL_SERVER_URL      = stringPreferencesKey("signal_server_url")
private val KEY_SIGNAL_SENDER          = stringPreferencesKey("signal_sender_number")
private val KEY_SIGNAL_RECIPIENT       = stringPreferencesKey("signal_recipient_number")
private val KEY_SIGNAL_BEARER_TOKEN    = stringPreferencesKey("signal_bearer_token")
private val KEY_MATTERMOST_ENABLED     = booleanPreferencesKey("mattermost_enabled")
private val KEY_MATTERMOST_WEBHOOK_URL = stringPreferencesKey("mattermost_webhook_url")
private val KEY_MIN_SEVERITY           = stringPreferencesKey("notification_min_severity")
private val KEY_COOLDOWN_MS            = intPreferencesKey("notification_cooldown_ms")
private val KEY_TRIGGER_TYPE_WHITELIST = stringSetPreferencesKey("notification_trigger_types")
private val KEY_ATTACH_MEDIA           = booleanPreferencesKey("notification_attach_media")
private val KEY_HEARTBEAT_SIGNAL_MIN   = intPreferencesKey("heartbeat_signal_minutes")
private val KEY_HEARTBEAT_MATTERMOST_MIN = intPreferencesKey("heartbeat_mattermost_minutes")
private val KEY_LOG_LEVEL              = stringPreferencesKey("log_level")
```

### Hilt Injection of NotificationRouter into MonitorService

`NotificationRouter` is `@Singleton` — Hilt injects it into `MonitorService` via `@Inject lateinit var`:

```kotlin
// MonitorService.kt
@Inject lateinit var notificationRouter: NotificationRouter

// In startMonitoring(), after settings snapshot:
val rule = NotificationRule(
    minSeverity = settingsRepository.minSeverity.first(),
    cooldownMs = settingsRepository.cooldownMs.first(),
    triggerTypes = settingsRepository.notificationTriggerTypes.first(),
    attachMedia = settingsRepository.attachMedia.first(),
)
val channels = buildList<org.havenapp.main.notify.NotificationChannel> {
    if (settingsRepository.signalEnabled.first()) {
        add(SignalRestChannel(
            serverUrl = settingsRepository.signalServerUrl.first(),
            senderNumber = settingsRepository.signalSender.first(),
            recipientNumber = settingsRepository.signalRecipient.first(),
            bearerToken = settingsRepository.signalBearerToken.first(),
        ))
    }
    if (settingsRepository.mattermostEnabled.first()) {
        add(MattermostChannel(webhookUrl = settingsRepository.mattermostWebhookUrl.first()))
    }
}
notificationRouter.initialize(rule, channels)

// After eventRepository.recordTrigger():
val frame = cameraAnalyzer?.lastJpegFrame
lifecycleScope.launch { notificationRouter.route(trigger, frame) }
```

Note: `SignalRestChannel` and `MattermostChannel` are NOT Hilt-managed singletons — they are plain Kotlin objects instantiated per session with current config (configs can change between sessions). `NotificationRouter` IS a Hilt singleton because MonitorService needs it injected.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| `adb` / Android device | Build + deploy | ✓ | 1.0.41 (34.0.4) | — |
| signal-cli REST API server | SignalRestChannel | ✗ (external, user-provided) | user-configured | Graceful: `isEnabled=false` or send fails → logged |
| Mattermost instance | MattermostChannel | ✗ (external, user-provided) | user-configured | Graceful: webhook URL empty → channel disabled |
| OkHttp 4.12.0 | Both channels | ✗ (not yet in build.gradle) | — | Must add dependency |
| Internet permission | HTTP calls | ? (not verified in manifest) | — | Add `INTERNET` permission if missing |

**Missing dependencies with no fallback:**
- OkHttp: must be added to `app/build.gradle.kts` and `libs.versions.toml`

**Missing dependencies with fallback (external services):**
- Signal server, Mattermost instance: user-configured — channels gracefully disabled when URLs empty

**INTERNET permission check needed:** Phase 4 plan Wave 0 should verify `AndroidManifest.xml` includes `<uses-permission android:name="android.permission.INTERNET" />`. The current app is a local-only app; this permission may not be declared yet.

---

## Open Questions

1. **`android.permission.INTERNET` in Manifest**
   - What we know: Current app has no network calls. Manifest likely lacks INTERNET permission.
   - What's unclear: Whether it's already declared (camera streaming prep for Phase 5 may have added it).
   - Recommendation: Wave 0 task — check manifest and add if missing. Without INTERNET permission, all network calls silently fail on Android.

2. **NotificationChannel interface name collision**
   - What we know: `android.app.NotificationChannel` is used in `MonitorService.kt` line 330. A new `org.havenapp.main.notify.NotificationChannel` will cause import ambiguity.
   - What's unclear: Best rename strategy (rename Haven interface or alias Android class).
   - Recommendation: Rename the Haven interface to `HavenAlertChannel` at the package level to avoid any ambiguity. Simpler than aliases in every file that imports both.

3. **OkHttp shared instance vs. per-channel instance**
   - What we know: Creating one `OkHttpClient` per request is wasteful (no connection pooling).
   - What's unclear: Whether to share a single `OkHttpClient` across channels or inject it via Hilt.
   - Recommendation: Provide `OkHttpClient` as a `@Singleton` Hilt binding in a new `NetworkModule`. Both channels receive it via constructor injection.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `SharedPreferences` for string sets | `stringSetPreferencesKey` in DataStore | DataStore 1.0+ | Atomic, coroutine-safe, no ANR risk |
| WorkManager for periodic tasks in foreground services | Coroutine `delay` loop inside service scope | Android lifecycle guidelines 2022+ | Simpler, no 15-min minimum, auto-cancelled with service |
| Retrofit for all REST | OkHttp direct for simple POSTs | Retrofit overhead justified only for complex APIs | Less boilerplate, fewer dependencies for two endpoints |

---

## Sources

### Primary (HIGH confidence)
- `github.com/bbernhard/signal-cli-rest-api` EXAMPLES.md — `/v2/send` payload with `base64_attachments` array, field names verified
- `github.com/bbernhard/signal-cli-rest-api` issue #519 — confirmed no built-in auth; owner explanation; community workarounds
- `developers.mattermost.com/integrate/webhooks/incoming/` — payload format, character limits (16,383), file attachment limitation confirmed
- `kotlinlang.org/api/core/kotlin-stdlib/kotlin.jvm/-volatile/` — `@Volatile` semantics for cross-thread visibility
- Existing source files: `AppLogger.kt`, `SettingsRepository.kt`, `CameraAnalyzer.kt`, `MonitorService.kt`, `SettingsScreen.kt` — patterns verified by direct read

### Secondary (MEDIUM confidence)
- `developer.android.com` background work docs — WorkManager 15-min minimum period confirmed
- `android.com/develop/ui/compose/lists` — `Column + verticalScroll` appropriate for bounded settings lists; `LazyColumn` not suitable for nesting
- WebSearch: OkHttp 4.12.0 as latest stable 4.x (from mvnrepository.com search result)

### Tertiary (LOW confidence)
- WebSearch: signal-cli-rest-api auth via reverse proxy (reverse proxy pattern confirmed by owner but exact proxy config is user-dependent — no single correct answer)

---

## Metadata

**Confidence breakdown:**
- Signal REST API: HIGH — payload format verified against EXAMPLES.md + issue thread
- Mattermost webhook: HIGH — verified against official developer docs
- Auth handling: HIGH — explicitly documented as "no built-in auth" by owner
- Heartbeat strategy: HIGH — WorkManager 15-min minimum is a documented Android platform constraint
- DataStore Set<String>: HIGH — `stringSetPreferencesKey` is documented DataStore API
- AppLogger LogLevel: HIGH — `@Volatile` semantics verified from Kotlin stdlib docs + existing `@Synchronized` pattern in AppLogger
- Settings Card restructuring: HIGH — verified against existing code patterns + Compose list guidance
- OkHttp version: MEDIUM — confirmed 4.12.0 as latest stable 4.x from search results (Maven Central not directly queryable)

**Research date:** 2026-04-05
**Valid until:** 2026-07-05 (stable; signal-cli REST API changes slowly, Mattermost webhook spec is stable)
