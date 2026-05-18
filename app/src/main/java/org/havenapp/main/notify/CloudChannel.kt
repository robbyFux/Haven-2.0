package org.havenapp.main.notify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.havenapp.main.events.TriggerEvent
import java.time.Instant

/**
 * HavenAlertChannel implementation that uploads events to the self-hosted Haven cloud server.
 *
 * Uploads are sent as multipart/form-data to:
 *   POST {serverUrl}/api/v1/devices/{appKey}/events
 *
 * Authentication uses the X-App-Key header with the device's app key. This matches the
 * server API defined in plan 05-04 (devices router).
 *
 * Heartbeats are sent as a JSON POST to:
 *   POST {serverUrl}/api/v1/devices/{appKey}/heartbeat
 *
 * [isEnabled] is false if either [serverUrl] or [appKey] is blank; the NotificationRouter
 * will skip this channel automatically.
 *
 * @param httpClient Shared OkHttpClient singleton (provided via Hilt NetworkModule)
 * @param serverUrl  Base URL of the self-hosted Haven cloud server (e.g. "https://haven.example.com")
 * @param appKey     Device app key registered on the cloud server
 */
class CloudChannel(
    private val httpClient: OkHttpClient,
    private val serverUrl: String,
    private val appKey: String,
) : HavenAlertChannel {

    override val id: String = "cloud"
    override val isEnabled: Boolean = serverUrl.isNotBlank() && appKey.isNotBlank()

    /** Cloud channel waits for the video clip before sending — no immediate JPEG alert. */
    override val deferresToVideo: Boolean = true

    /**
     * Uploads a TriggerEvent to the cloud server as a multipart/form-data POST.
     *
     * Fields: event_type, severity, timestamp (ISO-8601), sensor_value.
     * If [attachment] is non-null, it is included as a JPEG image in the "media" field.
     *
     * @return [Result.success] on HTTP 201; [Result.failure] with the HTTP error message otherwise.
     */
    override suspend fun send(event: TriggerEvent, attachment: ByteArray?, attachmentMime: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "${serverUrl.trimEnd('/')}/api/v1/devices/$appKey/events"
                // Server expects a single JSON-encoded "metadata" form field
                val sensorVal = event.sensorValue ?: 0f
                val ts = Instant.ofEpochMilli(event.timestamp).toString()
                val metadataJson = """{"event_type":"${event.type.name}","severity":"${event.severity.name}","timestamp":"$ts","sensor_value":$sensorVal}"""

                val bodyBuilder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("metadata", metadataJson)

                if (attachment != null) {
                    val mime = attachmentMime ?: "application/octet-stream"
                    val filename = if (mime.startsWith("video/")) "clip.mp4" else "attachment"
                    bodyBuilder.addFormDataPart(
                        "video",
                        filename,
                        attachment.toRequestBody(mime.toMediaType()),
                    )
                }

                val request = Request.Builder()
                    .url(url)
                    .header("X-App-Key", appKey)
                    .post(bodyBuilder.build())
                    .build()
                val response = httpClient.newCall(request).execute()
                check(response.isSuccessful) {
                    "Cloud server error ${response.code}: ${response.body?.string()}"
                }
            }
        }

    /**
     * Sends a heartbeat message to the cloud server as a JSON POST.
     *
     * @param message Heartbeat string including app version and monitor state.
     */
    override suspend fun sendHeartbeat(message: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "${serverUrl.trimEnd('/')}/api/v1/devices/$appKey/heartbeat"
                // Escape double quotes in message to produce valid JSON
                val escaped = message.replace("\\", "\\\\").replace("\"", "\\\"")
                val jsonBody = """{"message":"$escaped"}"""
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(url)
                    .header("X-App-Key", appKey)
                    .post(jsonBody)
                    .build()
                val response = httpClient.newCall(request).execute()
                check(response.isSuccessful) {
                    "Cloud heartbeat error ${response.code}: ${response.body?.string()}"
                }
            }
        }
}
