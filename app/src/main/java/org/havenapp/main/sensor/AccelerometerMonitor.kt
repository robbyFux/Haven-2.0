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
class AccelerometerMonitor @Inject constructor(
    private val sensorManager: SensorManager,
) : SensorMonitor {

    override fun observe(sensitivity: Sensitivity, warmupMs: Long): Flow<TriggerEvent> {
        if (sensitivity == Sensitivity.OFF) return kotlinx.coroutines.flow.emptyFlow()

        return callbackFlow {
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

            if (sensor == null) {
                close()
                return@callbackFlow
            }

            var lastX = 0f
            var lastY = 0f
            var lastZ = 0f
            var initialized = false

            val warmupSamples = mutableListOf<Float>()
            var noiseFloor = 0f
            val startTime = System.currentTimeMillis()
            var warmupDone = false
            var lastEmitTime = 0L

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]

                    if (!initialized) {
                        lastX = x; lastY = y; lastZ = z
                        initialized = true
                        return
                    }

                    val delta = sqrt(
                        (x - lastX) * (x - lastX) +
                        (y - lastY) * (y - lastY) +
                        (z - lastZ) * (z - lastZ)
                    )
                    lastX = x; lastY = y; lastZ = z

                    val elapsed = System.currentTimeMillis() - startTime
                    if (!warmupDone) {
                        warmupSamples.add(delta)
                        if (elapsed >= warmupMs) {
                            // 90. Perzentil als Noise-Floor: resistenter gegen Ausreißer
                            // als der Durchschnitt, der Peaks zu sehr dämpft.
                            noiseFloor = if (warmupSamples.isNotEmpty()) {
                                val sorted = warmupSamples.sorted()
                                val idx = (sorted.size * 0.90).toInt()
                                    .coerceIn(0, sorted.size - 1)
                                sorted[idx]
                            } else 0.5f
                            warmupDone = true
                        }
                        return
                    }

                    val threshold = noiseFloor * sensitivity.accelerometerMultiplier
                    if (delta > threshold) {
                        val severity = when {
                            delta > threshold * 3f -> Severity.HIGH
                            delta > threshold * 1.5f -> Severity.MEDIUM
                            else -> Severity.LOW
                        }
                        // Cooldown: verhindert Rapid-Fire alle 40ms
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
                                    type = TriggerType.ACCELEROMETER,
                                    sensorValue = delta,
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
