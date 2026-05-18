package org.havenapp.main.notify

import org.havenapp.main.events.TriggerEvent

/**
 * Abstraction for outbound alert channels (Signal, Mattermost, etc.).
 *
 * Named HavenAlertChannel (not NotificationChannel) to avoid import
 * collision with android.app.NotificationChannel used in MonitorService.
 */
interface HavenAlertChannel {
    val id: String
    val isEnabled: Boolean

    /**
     * If true, this channel defers its alert until a video clip is available.
     * [NotificationRouter.route] skips deferred channels; they are only called
     * from [NotificationRouter.uploadVideo] once the clip is ready.
     */
    val deferresToVideo: Boolean get() = false

    suspend fun send(event: TriggerEvent, attachment: ByteArray?, attachmentMime: String? = null): Result<Unit>
    suspend fun sendHeartbeat(message: String): Result<Unit>
}
