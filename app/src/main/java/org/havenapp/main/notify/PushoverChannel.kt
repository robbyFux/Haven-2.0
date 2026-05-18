package org.havenapp.main.notify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.havenapp.main.events.Severity
import org.havenapp.main.events.TriggerEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HavenAlertChannel implementation that sends alerts via the Pushover API.
 *
 * Pushover requires an app token (registered at pushover.net) and a user/group key.
 * Messages are posted as multipart/form-data to https://api.pushover.net/1/messages.json —
 * no self-hosted backend required.
 *
 * Priority mapping:
 * - LOW / MEDIUM → 0 (normal)
 * - HIGH → 1 (high priority, bypasses quiet hours)
 * - CRITICAL → 2 (emergency; requires retry + expire params, alerts until acknowledged)
 *
 * Attachment support: JPEG byte arrays are included as the "attachment" form field (image/jpeg).
 * Pushover limits attachments to 2.5 MB; callers should pass compressed JPEG frames only.
 * Only JPEG is supported — other formats are not sent.
 *
 * [isEnabled] is false if either [appToken] or [userKey] is blank; the NotificationRouter
 * will skip this channel automatically.
 *
 * @param httpClient Shared OkHttpClient singleton (provided via Hilt NetworkModule)
 * @param appToken   Pushover application token (registered at pushover.net/apps)
 * @param userKey    Pushover user or group key from the Pushover dashboard
 */
class PushoverChannel(
    private val httpClient: OkHttpClient,
    private val appToken: String,
    private val userKey: String,
) : HavenAlertChannel {

    override val id: String = "pushover"
    override val isEnabled: Boolean = userKey.isNotBlank() && appToken.isNotBlank()

    companion object {
        private const val API_URL = "https://api.pushover.net/1/messages.json"
    }

    /**
     * Sends a TriggerEvent alert to the Pushover API as a multipart/form-data POST.
     *
     * Priority is derived from [event].severity: LOW/MEDIUM → 0, HIGH → 1, CRITICAL → 2.
     * Emergency priority (CRITICAL) includes retry=60 and expire=3600 as required by the API.
     * If [attachment] is non-null, it is included as a JPEG image in the "attachment" field.
     *
     * @return [Result.success] on HTTP 2xx; [Result.failure] with the HTTP error message otherwise.
     */
    override suspend fun send(event: TriggerEvent, attachment: ByteArray?, attachmentMime: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val priority = priorityFor(event.severity)
                val bodyBuilder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("token", appToken)
                    .addFormDataPart("user", userKey)
                    .addFormDataPart("title", "[Haven] Alert")
                    .addFormDataPart("message", buildMessage(event))
                    .addFormDataPart("priority", priority)

                if (event.severity == Severity.CRITICAL) {
                    bodyBuilder
                        .addFormDataPart("retry", "60")
                        .addFormDataPart("expire", "3600")
                }

                if (attachment != null) {
                    bodyBuilder.addFormDataPart(
                        "attachment",
                        "alert.jpg",
                        attachment.toRequestBody("image/jpeg".toMediaType()),
                    )
                }

                val request = Request.Builder()
                    .url(API_URL)
                    .post(bodyBuilder.build())
                    .build()
                val response = httpClient.newCall(request).execute()
                check(response.isSuccessful) {
                    "Pushover API error ${response.code}: ${response.body?.string()}"
                }
            }
        }

    /**
     * Sends a heartbeat message to the Pushover API with normal priority and no attachment.
     *
     * @param message Heartbeat string including app version and monitor state.
     */
    override suspend fun sendHeartbeat(message: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("token", appToken)
                    .addFormDataPart("user", userKey)
                    .addFormDataPart("title", "[Haven] Heartbeat")
                    .addFormDataPart("message", message)
                    .addFormDataPart("priority", "0")
                    .build()

                val request = Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .build()
                val response = httpClient.newCall(request).execute()
                check(response.isSuccessful) {
                    "Pushover heartbeat error ${response.code}: ${response.body?.string()}"
                }
            }
        }

    private fun buildMessage(event: TriggerEvent): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(event.timestamp))
        return "[${event.type.name}] ${event.severity} – Motion detected\n" +
            "Timestamp: $time\n" +
            "Sensor value: ${event.sensorValue ?: "n/a"}"
    }

    /** Maps event severity to a Pushover priority string (-2..2). */
    private fun priorityFor(severity: Severity): String = when (severity) {
        Severity.LOW, Severity.MEDIUM -> "0"
        Severity.HIGH -> "1"
        Severity.CRITICAL -> "2"
    }
}
