package org.havenapp.main.notify

import android.content.Context
import android.content.Intent
import org.havenapp.main.events.TriggerEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HavenAlertChannel that sends alerts by launching an Android Intent to the
 * locally installed Signal app (org.thoughtcrime.securesms).
 *
 * This is the P2 fallback channel for users who don't run a self-hosted
 * signal-cli REST API server. The intent is fired via [Intent.ACTION_SEND]
 * with [Intent.FLAG_ACTIVITY_NEW_TASK] so it can be launched from a background
 * service (MonitorService).
 *
 * **Heartbeat disabled:** Heartbeats would open the Signal compose screen
 * repeatedly on an unattended device, which is disruptive. [sendHeartbeat]
 * returns [Result.success] immediately without dispatching an intent.
 *
 * **Attachment ignored:** Android share intents for text/plain cannot carry
 * binary JPEG data alongside a message in a single action. The attachment
 * parameter is silently dropped.
 *
 * @param context         Application context — must outlive the channel; never use Activity context here.
 * @param recipientNumber E.164 phone number of the Signal recipient (e.g. "+491701234567").
 *                        If blank, [isEnabled] is false and no intent is fired.
 */
class SignalIntentChannel(
    private val context: Context,
    private val recipientNumber: String,
) : HavenAlertChannel {

    override val id: String = "signal_intent"
    override val isEnabled: Boolean = recipientNumber.isNotBlank()

    /**
     * Fires an ACTION_SEND intent to Signal with the alert message as plain text.
     * Uses FLAG_ACTIVITY_NEW_TASK for service-context launch. Attachment is ignored.
     */
    override suspend fun send(event: TriggerEvent, attachment: ByteArray?): Result<Unit> =
        runCatching {
            val message = buildMessage(event)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                // Pre-fill the recipient if Signal supports it via EXTRA_EMAIL convention.
                // Not all Signal versions honour this; message delivery is best-effort.
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientNumber))
                setPackage("org.thoughtcrime.securesms")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

    /**
     * No-op: firing repeated intents to open Signal on an unattended device
     * would be disruptive. Heartbeat is logged at the caller (MonitorService) level.
     */
    override suspend fun sendHeartbeat(message: String): Result<Unit> = Result.success(Unit)

    private fun buildMessage(event: TriggerEvent): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(event.timestamp))
        return "[Haven] ${event.type.name} – ${event.severity}\n$time"
    }
}
