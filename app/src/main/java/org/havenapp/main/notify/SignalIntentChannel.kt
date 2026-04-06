package org.havenapp.main.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import org.havenapp.main.events.TriggerEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HavenAlertChannel that sends alerts by posting a local Android notification
 * whose content intent opens the Signal conversation with the configured recipient.
 *
 * Tapping the notification is a user gesture, which bypasses Android 10+
 * background activity start restrictions. The previous approach of calling
 * [Context.startActivity] directly from a background service is silently
 * dropped by Android 10+ and is no longer used.
 *
 * The notification uses [Intent.ACTION_VIEW] with Signal's phone-number deep
 * link (`https://signal.me/#p/$recipientNumber`) so the correct conversation
 * opens immediately. No [Intent.EXTRA_EMAIL] or [Intent.ACTION_SEND] is used.
 *
 * **Heartbeat disabled:** Heartbeats would fire repeated notifications on an
 * unattended device, which is disruptive. [sendHeartbeat] returns
 * [Result.success] immediately without posting a notification.
 *
 * **Attachment ignored:** The notification approach cannot carry binary JPEG
 * data alongside the alert text. The attachment parameter is silently dropped.
 *
 * @param context         Application context — must outlive the channel; never use Activity context here.
 * @param recipientNumber E.164 phone number of the Signal recipient (e.g. "+491701234567").
 *                        If blank, [isEnabled] is false and no notification is posted.
 */
class SignalIntentChannel(
    private val context: Context,
    private val recipientNumber: String,
) : HavenAlertChannel {

    override val id: String = "signal_intent"
    override val isEnabled: Boolean = recipientNumber.isNotBlank()

    /**
     * Posts a local notification whose content intent opens the Signal
     * conversation with [recipientNumber] when tapped. This bypasses Android 10+
     * background activity start restrictions.
     *
     * Uses [NotificationCompat] from `androidx.core.app` for the notification
     * builder and [NotificationManager] directly (API 23+) for posting.
     * Attachment is ignored.
     */
    override suspend fun send(event: TriggerEvent, attachment: ByteArray?): Result<Unit> =
        runCatching {
            val message = buildMessage(event)

            // Deep-link to Signal conversation with the recipient
            val signalIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://signal.me/#p/$recipientNumber")).apply {
                setPackage("org.thoughtcrime.securesms")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                event.timestamp.toInt(),
                signalIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val nm = context.getSystemService(NotificationManager::class.java)

            // Ensure notification channel exists (no-op on re-call)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
                )
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("[Haven] ${event.type.name} – ${event.severity}")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            nm.notify(event.timestamp.toInt(), notification)
        }

    /**
     * No-op: firing repeated notifications on an unattended device would be
     * disruptive. Heartbeat is logged at the caller (MonitorService) level.
     * The notification approach is not used for heartbeats.
     */
    override suspend fun sendHeartbeat(message: String): Result<Unit> = Result.success(Unit)

    private fun buildMessage(event: TriggerEvent): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(event.timestamp))
        return "[Haven] ${event.type.name} – ${event.severity}\n$time"
    }

    companion object {
        private const val CHANNEL_ID = "haven_signal_intent_alerts"
        private const val CHANNEL_NAME = "Signal (App) Alerts"
    }
}
