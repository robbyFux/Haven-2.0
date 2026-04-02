package org.havenapp.main.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.havenapp.main.events.Severity
import org.havenapp.main.events.TriggerEvent
import org.havenapp.main.events.TriggerType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class GyroscopeMonitor @Inject constructor(
    private val sensorManager: SensorManager,
) : SensorMonitor {

    override fun observe(sensitivity: Sensitivity, warmupMs: Long): Flow<TriggerEvent> {
        if (sensitivity == Sensitivity.OFF) return kotlinx.coroutines.flow.emptyFlow()

        return callbackFlow {
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            if (sensor == null) {
                close()
                return@callbackFlow
            }

            val warmupSamples = mutableListOf<Float>()
            var noiseFloor = 0f
            val startTime = System.currentTimeMillis()
            var warmupDone = false
            var lastEmitTime = 0L

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val magnitude = sqrt(
                        event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2]
                    )

                    val elapsed = System.currentTimeMillis() - startTime
                    if (!warmupDone) {
                        warmupSamples.add(magnitude)
                        if (elapsed >= warmupMs) {
                            noiseFloor = if (warmupSamples.isNotEmpty()) {
                                val sorted = warmupSamples.sorted()
                                val idx = (sorted.size * 0.90).toInt()
                                    .coerceIn(0, sorted.size - 1)
                                sorted[idx]
                            } else 0.05f
                            warmupDone = true
                        }
                        return
                    }

                    val threshold = noiseFloor * sensitivity.accelerometerMultiplier
                    if (magnitude > threshold) {
                        val severity = when {
                            magnitude > threshold * 3f -> Severity.HIGH
                            magnitude > threshold * 1.5f -> Severity.MEDIUM
                            else -> Severity.LOW
                        }
                        val cooldownMs = when (severity) {
                            Severity.HIGH -> 200L
                            Severity.MEDIUM -> 500L
                            else -> 800L
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastEmitTime >= cooldownMs) {
                            lastEmitTime = now
                            trySend(
                                TriggerEvent(
                                    type = TriggerType.GYROSCOPE,
                                    sensorValue = magnitude,
                                    severity = severity,
                                )
                            )
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }

            sensorManager.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL,
            )

            awaitClose { sensorManager.unregisterListener(listener) }
        }
    }
}
