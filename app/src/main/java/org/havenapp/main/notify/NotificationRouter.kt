package org.havenapp.main.notify

import org.havenapp.main.events.TriggerEvent
import org.havenapp.main.events.TriggerType
import org.havenapp.main.storage.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes sensor trigger events to active notification channels,
 * applying the global [NotificationRule] filters:
 * - minSeverity gate
 * - triggerType whitelist
 * - anti-flood cooldown
 *
 * Initialized per monitoring session in MonitorService with current settings.
 * Channels are plain objects (not Hilt-managed) instantiated with session config.
 */
@Singleton
class NotificationRouter @Inject constructor(
    private val appLogger: AppLogger,
) {
    private var rule: NotificationRule = NotificationRule()
    private var channels: List<HavenAlertChannel> = emptyList()
    private var lastNotifiedMs: Long = 0L

    companion object {
        private const val TAG = "NotificationRouter"
    }

    /**
     * Initialize with current session config. Called once per monitoring session
     * from MonitorService.startMonitoring() with settings snapshots.
     */
    fun initialize(rule: NotificationRule, channels: List<HavenAlertChannel>) {
        this.rule = rule
        this.channels = channels.filter { it.isEnabled }
        this.lastNotifiedMs = 0L
        appLogger.i(TAG, "Initialized: ${this.channels.size} channels, minSeverity=${rule.minSeverity}, cooldown=${rule.cooldownMs}ms")
    }

    /**
     * Route an event to all active channels if it passes rule filters.
     *
     * @param event The trigger event to evaluate and potentially send.
     * @param lastFrame Optional JPEG attachment (from CameraAnalyzer.lastJpegFrame).
     *                  Only included if rule.attachMedia is true AND event is a camera type.
     */
    suspend fun route(event: TriggerEvent, lastFrame: ByteArray?) {
        if (channels.isEmpty()) return

        // Filter 1: severity gate
        if (event.severity.ordinal < rule.minSeverity.ordinal) return

        // Filter 2: trigger type whitelist
        if (event.type !in rule.triggerTypes) return

        // Filter 3: anti-flood cooldown
        val now = System.currentTimeMillis()
        if (rule.cooldownMs > 0 && now - lastNotifiedMs < rule.cooldownMs) return
        lastNotifiedMs = now

        // Determine attachment: only for camera-type triggers when attachMedia enabled (D-13)
        val attachment = if (rule.attachMedia && event.type.isCameraType()) lastFrame else null

        // Deferred channels (e.g. CloudChannel) wait for the video clip via uploadVideo() — skip here.
        val channelsToNotify = channels.filter { !it.deferresToVideo }
        if (channelsToNotify.isEmpty()) return
        appLogger.d(TAG, "Routing ${event.type} (${event.severity}) to ${channelsToNotify.size} channels")

        channelsToNotify.forEach { ch ->
            ch.send(event, attachment)
                .onFailure { appLogger.e(TAG, "Channel ${ch.id} failed: ${it.message}") }
                .onSuccess { appLogger.d(TAG, "Channel ${ch.id} sent successfully") }
        }
    }

    /**
     * Upload a recorded video clip to all active channels as a CAMERA_VIDEO event.
     *
     * Called after a clip finishes recording and before local encryption, so channels
     * receive the raw (unencrypted) bytes. Bypasses the cooldown and trigger-type
     * whitelist — video upload is always attempted if the channel is enabled and
     * attachMedia is set.
     *
     * @param event     The original trigger event that started the clip (used for metadata).
     * @param videoBytes Raw MP4 bytes of the completed clip.
     */
    suspend fun uploadVideo(event: TriggerEvent, videoBytes: ByteArray) {
        if (!rule.attachMedia) return
        val deferredChannels = channels.filter { it.deferresToVideo }
        if (deferredChannels.isEmpty()) return
        appLogger.d(TAG, "Uploading video clip (${videoBytes.size} bytes) to ${deferredChannels.size} channels")
        deferredChannels.forEach { ch ->
            ch.send(event, videoBytes, "video/mp4")
                .onFailure { appLogger.e(TAG, "Channel ${ch.id} video upload failed: ${it.message}") }
                .onSuccess { appLogger.d(TAG, "Channel ${ch.id} video uploaded") }
        }
    }

    /** Reset state when monitoring stops. */
    fun reset() {
        channels = emptyList()
        lastNotifiedMs = 0L
    }
}

/** Camera-based trigger types that can carry JPEG attachments (D-13). */
private fun TriggerType.isCameraType(): Boolean = this in setOf(
    TriggerType.CAMERA, TriggerType.CAMERA_PERSON,
    TriggerType.CAMERA_PET, TriggerType.CAMERA_VEHICLE,
)
