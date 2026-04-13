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

        appLogger.d(TAG, "Routing ${event.type} (${event.severity}) to ${channels.size} channels")

        channels.forEach { ch ->
            runCatching { ch.send(event, attachment) }
                .onFailure { appLogger.e(TAG, "Channel ${ch.id} failed: ${it.message}") }
                .onSuccess { appLogger.d(TAG, "Channel ${ch.id} sent successfully") }
        }
    }

    /**
     * Upload a video clip to channels that support deferred video delivery (e.g. CloudChannel).
     * Called after the raw clip is available and before local encryption.
     * Delegates to channels that implement [CloudChannel] by calling send() with the video bytes.
     */
    suspend fun uploadVideo(event: TriggerEvent, videoBytes: ByteArray) {
        channels.filterIsInstance<CloudChannel>().forEach { ch ->
            runCatching { ch.send(event, videoBytes) }
                .onFailure { appLogger.e(TAG, "CloudChannel uploadVideo failed: ${it.message}") }
                .onSuccess { appLogger.d(TAG, "CloudChannel video uploaded successfully") }
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
