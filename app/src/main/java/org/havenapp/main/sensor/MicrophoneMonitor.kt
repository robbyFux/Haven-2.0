package org.havenapp.main.sensor

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.havenapp.main.events.Severity
import org.havenapp.main.events.TriggerEvent
import org.havenapp.main.events.TriggerType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.sqrt

@Singleton
class MicrophoneMonitor @Inject constructor() : SensorMonitor {

    private val sampleRate = 44_100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        .coerceAtLeast(4096)

    override fun observe(sensitivity: Sensitivity, warmupMs: Long, expert: ExpertThresholds): Flow<TriggerEvent> {
        if (sensitivity == Sensitivity.OFF) return kotlinx.coroutines.flow.emptyFlow()

        return callbackFlow<TriggerEvent> {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize,
            )

            recorder.startRecording()

            val job = launch(Dispatchers.IO) {
                val buffer = ShortArray(bufferSize)
                val thresholdDb = sensitivity.effectiveMicDb(expert)
                val cooldownMs = 1_000L
                var lastEmitTime = 0L

                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read <= 0) continue

                    val rms = sqrt(buffer.take(read).sumOf { it.toLong() * it }.toDouble() / read)
                    if (rms < 1.0) continue

                    val db = (20.0 * log10(rms)).toFloat()
                    if (db >= thresholdDb) {
                        val now = System.currentTimeMillis()
                        if (now - lastEmitTime < cooldownMs) continue
                        lastEmitTime = now
                        val severity = when {
                            db >= thresholdDb + 15f -> Severity.CRITICAL
                            db >= thresholdDb + 8f -> Severity.HIGH
                            db >= thresholdDb + 3f -> Severity.MEDIUM
                            else -> Severity.LOW
                        }
                        trySend(
                            TriggerEvent(
                                type = TriggerType.MICROPHONE,
                                sensorValue = db,
                                severity = severity,
                            )
                        )
                    }
                }
            }

            awaitClose {
                job.cancel()
                recorder.stop()
                recorder.release()
            }
        }.flowOn(Dispatchers.IO)
    }
}
