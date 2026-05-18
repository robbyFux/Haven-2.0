package org.havenapp.main.notify

import android.util.Base64
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
 * HavenAlertChannel implementation that sends alerts via the signal-cli REST API
 * (bbernhard/signal-cli-rest-api Docker image).
 *
 * The signal-cli REST API has NO built-in authentication — the optional [bearerToken]
 * field is for a user-configured reverse proxy (e.g., nginx/Caddy). If [bearerToken]
 * is empty, the Authorization header is omitted entirely.
 *
 * Attachment support: JPEG frames are sent as base64-encoded strings in the
 * `base64_attachments` array (format: `data:image/jpeg;filename=alert.jpg;base64,...`).
 * This aligns with D-11 (JPEG still only) and D-13 (attachment for CAMERA types only —
 * enforced by the NotificationRouter caller, not this class).
 *
 * Known behavior: [isEnabled] is false if any of [serverUrl], [senderNumber], or
 * [recipientNumber] is blank. The NotificationRouter skips disabled channels.
 *
 * @param httpClient    Shared OkHttpClient singleton (provided via Hilt NetworkModule)
 * @param serverUrl     Base URL of the signal-cli REST API server (e.g. "http://192.168.1.2:8080")
 * @param senderNumber  Registered Signal sender number (E.164 format)
 * @param recipientNumber Signal recipient number (E.164 format)
 * @param bearerToken   Optional reverse-proxy auth token; empty string = no Authorization header
 */
class SignalRestChannel(
    private val httpClient: OkHttpClient,
    private val serverUrl: String,
    private val senderNumber: String,
    private val recipientNumber: String,
    private val bearerToken: String = "",
) : HavenAlertChannel {

    override val id: String = "signal"
    override val isEnabled: Boolean =
        serverUrl.isNotBlank() && senderNumber.isNotBlank() && recipientNumber.isNotBlank()

    /**
     * Sends a TriggerEvent alert to the Signal recipient via POST /v2/send.
     *
     * If [attachment] is non-null, it is included as a base64-encoded JPEG in the
     * `base64_attachments` array. Network call is dispatched on [Dispatchers.IO].
     *
     * @return [Result.success] on HTTP 2xx; [Result.failure] with the HTTP error message otherwise.
     */
    override suspend fun send(event: TriggerEvent, attachment: ByteArray?, attachmentMime: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bodyJson = buildString {
                    append("""{"message":${jsonStr(buildMessage(event))}""")
                    append(""","number":${jsonStr(senderNumber)}""")
                    append(""","recipients":[${jsonStr(recipientNumber)}]""")
                    if (attachment != null) {
                        val b64 = Base64.encodeToString(attachment, Base64.NO_WRAP)
                        append(""","base64_attachments":["data:image/jpeg;filename=haven_alert.jpg;base64,$b64"]""")
                    }
                    append("}")
                }
                val body = bodyJson.toRequestBody("application/json".toMediaType())
                val requestBuilder = Request.Builder()
                    .url("$serverUrl/v2/send")
                    .post(body)
                if (bearerToken.isNotBlank()) {
                    requestBuilder.header("Authorization", "Bearer $bearerToken")
                }
                val response = httpClient.newCall(requestBuilder.build()).execute()
                check(response.isSuccessful) {
                    "Signal API error ${response.code}: ${response.body?.string()}"
                }
            }
        }

    /**
     * Sends a heartbeat message to the Signal recipient (no attachment).
     *
     * @param message Heartbeat string including app version and monitor state (D-10).
     */
    override suspend fun sendHeartbeat(message: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bodyJson = buildString {
                    append("""{"message":${jsonStr(message)}""")
                    append(""","number":${jsonStr(senderNumber)}""")
                    append(""","recipients":[${jsonStr(recipientNumber)}]""")
                    append("}")
                }
                val body = bodyJson.toRequestBody("application/json".toMediaType())
                val requestBuilder = Request.Builder()
                    .url("$serverUrl/v2/send")
                    .post(body)
                if (bearerToken.isNotBlank()) {
                    requestBuilder.header("Authorization", "Bearer $bearerToken")
                }
                val response = httpClient.newCall(requestBuilder.build()).execute()
                check(response.isSuccessful) {
                    "Signal API heartbeat error ${response.code}: ${response.body?.string()}"
                }
            }
        }

    private fun buildMessage(event: TriggerEvent): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(event.timestamp))
        return "[${event.type.name}] ${event.severity} – Sensor value: ${event.sensorValue ?: "n/a"}\nTime: $time"
    }

    /**
     * Escapes a string value for inclusion in a hand-built JSON body.
     * Handles backslash and double-quote escaping only — sufficient for URLs,
     * phone numbers, and short human-readable messages.
     */
    private fun jsonStr(s: String): String =
        "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
