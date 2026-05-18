package org.havenapp.main.notify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.havenapp.main.events.TriggerEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HavenAlertChannel implementation that sends alerts via a Mattermost Incoming Webhook.
 *
 * Mattermost "message attachments" in the API docs refer to Slack-style rich text
 * formatting objects (structured text), NOT binary file uploads. File upload via
 * Incoming Webhook is not supported — the [attachment] parameter is intentionally
 * ignored in this implementation (D-13, Pitfall 2 from research).
 *
 * Message format: Markdown with bold trigger type, severity, timestamp, and sensor value.
 *
 * @param httpClient Shared OkHttpClient singleton (provided via Hilt NetworkModule)
 * @param webhookUrl Full Mattermost Incoming Webhook URL
 */
class MattermostChannel(
    private val httpClient: OkHttpClient,
    private val webhookUrl: String,
) : HavenAlertChannel {

    override val id: String = "mattermost"
    override val isEnabled: Boolean = webhookUrl.isNotBlank()

    /**
     * Sends a TriggerEvent alert to the Mattermost webhook as a Markdown-formatted message.
     *
     * The [attachment] parameter is ignored — Mattermost Incoming Webhooks do not support
     * binary file uploads. Network call is dispatched on [Dispatchers.IO].
     *
     * @return [Result.success] on HTTP 2xx; [Result.failure] with the HTTP error message otherwise.
     */
    @Suppress("UNUSED_PARAMETER")
    override suspend fun send(event: TriggerEvent, attachment: ByteArray?, attachmentMime: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                // attachment is intentionally ignored — Mattermost webhooks do not support file uploads
                val text = buildMarkdownMessage(event)
                val bodyJson = """{"text":${jsonStr(text)},"username":"Haven","icon_emoji":":shield:"}"""
                val body = bodyJson.toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(webhookUrl).post(body).build()
                val response = httpClient.newCall(request).execute()
                check(response.isSuccessful) {
                    "Mattermost webhook error ${response.code}: ${response.body?.string()}"
                }
            }
        }

    /**
     * Sends a heartbeat message to the Mattermost webhook.
     *
     * @param message Heartbeat string including app version and monitor state (D-10).
     */
    override suspend fun sendHeartbeat(message: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bodyJson = """{"text":${jsonStr(message)},"username":"Haven","icon_emoji":":shield:"}"""
                val body = bodyJson.toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(webhookUrl).post(body).build()
                val response = httpClient.newCall(request).execute()
                check(response.isSuccessful) {
                    "Mattermost heartbeat error ${response.code}: ${response.body?.string()}"
                }
            }
        }

    private fun buildMarkdownMessage(event: TriggerEvent): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(event.timestamp))
        return "**[${event.type.name}]** ${event.severity} -- Motion detected\n" +
            "Timestamp: $time\n" +
            "Sensor value: ${event.sensorValue ?: "n/a"}"
    }

    /**
     * Escapes a string value for inclusion in a hand-built JSON body.
     * Handles backslash and double-quote escaping only — sufficient for URLs
     * and short human-readable messages.
     */
    private fun jsonStr(s: String): String =
        "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
