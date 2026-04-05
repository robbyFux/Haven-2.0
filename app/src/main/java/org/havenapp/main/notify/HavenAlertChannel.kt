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
    suspend fun send(event: TriggerEvent, attachment: ByteArray?): Result<Unit>
    suspend fun sendHeartbeat(message: String): Result<Unit>
}
