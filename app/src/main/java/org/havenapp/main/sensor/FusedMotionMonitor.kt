package org.havenapp.main.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import org.havenapp.main.detection.SensorFusionEngine
import org.havenapp.main.events.Severity
import org.havenapp.main.events.TriggerEvent
import org.havenapp.main.events.TriggerType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Replaces the separate AccelerometerMonitor + GyroscopeMonitor with a single fused monitor.
 *
 * The SensorFusionEngine (Complementary Filter, alpha=0.7) combines:
 *   - Accelerometer delta magnitude (frame-to-frame change)
 *   - Gyroscope magnitude (angular velocity)
 *
 * The gyro listener runs passively and updates the latest gyro magnitude whenever a new
 * gyro sample arrives. The accel listener drives all decisions: on each accel sample it
 * computes the fused score and emits a TriggerEvent if the threshold is exceeded.
 *
 * Graceful degradation: if no gyroscope is present, the fused score equals the accel delta
 * (latestGyroMagnitude stays 0, so the 70% gyro term is always 0 → effectively 0.3× accel).
 * In that case the threshold is proportionally lower and still works correctly.
 */
@Singleton
class FusedMotionMonitor @Inject constructor(
    private val sensorManager: SensorManager,
) : SensorMonitor {

    /** Set to the computed noise floor once warmup is complete; null during warmup. */
    private val _noiseFloor = MutableStateFlow<Float?>(null)
    val noiseFloor: StateFlow<Float?> = _noiseFloor

    /**
     * Starts the fused accelerometer + gyroscope monitoring flow.
     *
     * @param sensitivity Sensitivity level controlling the threshold multiplier applied
     *   to the noise floor after warmup completes.
     * @param warmupMs Warmup duration in ms; motion events are suppressed until
     *   calibration completes and the noise floor is established.
     * @param expert Optional expert thresholds overriding the default sensitivity
     *   multipliers when [ExpertThresholds.DEFAULT] is not used.
     * @return Flow of [TriggerEvent]s emitted when fused motion score exceeds the threshold.
     *   Completes when the returned [Flow] collector is cancelled.
     */
    override fun observe(sensitivity: Sensitivity, warmupMs: Long, expert: ExpertThresholds): Flow<TriggerEvent> {
        if (sensitivity == Sensitivity.OFF) return emptyFlow()

        _noiseFloor.value = null

        return callbackFlow {
            val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

            if (accelSensor == null) {
                close()
                return@callbackFlow
            }

            val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            val fusionEngine = SensorFusionEngine()

            val warmupSamples = mutableListOf<Float>()
            var noiseFloor = 0f
            val startTime = System.currentTimeMillis()
            var warmupDone = false
            var lastEmitTime = 0L

            val accelListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val accelDelta = fusionEngine.processAccelerometer(
                        event.values[0], event.values[1], event.values[2],
                    )
                    val fused = fusionEngine.fuse(accelDelta)

                    val elapsed = System.currentTimeMillis() - startTime
                    if (!warmupDone) {
                        warmupSamples.add(fused)
                        if (elapsed >= warmupMs) {
                            // WHY 90th-percentile noise floor: more robust than the mean.
                            // During warmup, brief environmental spikes (footstep, door closing
                            // nearby) would inflate the mean and produce an unrealistically
                            // high baseline, making the threshold too lenient. The 90th
                            // percentile ignores the top 10 % of samples, giving a baseline
                            // that reflects sustained ambient motion rather than outliers.
                            noiseFloor = if (warmupSamples.isNotEmpty()) {
                                val sorted = warmupSamples.sorted()
                                val idx = (sorted.size * 0.90).toInt()
                                    .coerceIn(0, sorted.size - 1)
                                sorted[idx]
                            } else 0.5f
                            _noiseFloor.value = noiseFloor
                            warmupDone = true
                        }
                        return
                    }

                    val threshold = noiseFloor * sensitivity.effectiveAccelMultiplier(expert)
                    if (fused > threshold) {
                        val severity = when {
                            fused > threshold * 3f -> Severity.HIGH
                            fused > threshold * 1.5f -> Severity.MEDIUM
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
                                    type = TriggerType.ACCELEROMETER,
                                    sensorValue = fused,
                                    severity = severity,
                                )
                            )
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }

            val gyroListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    fusionEngine.processGyroscope(
                        event.values[0], event.values[1], event.values[2],
                    )
                }
                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }

            // WHY SENSOR_DELAY_GAME (~20 ms polling): ~10x faster than SENSOR_DELAY_NORMAL
            // (~200 ms). Faster sampling reduces the chance of missing short-duration
            // movements (door slam, quick hand-wave) between samples. Battery impact is
            // acceptable for a security monitoring use case where the app is deliberately
            // run in the foreground with a WakeLock.
            sensorManager.registerListener(accelListener, accelSensor, SensorManager.SENSOR_DELAY_GAME)
            if (gyroSensor != null) {
                sensorManager.registerListener(gyroListener, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
            }

            awaitClose {
                sensorManager.unregisterListener(accelListener)
                if (gyroSensor != null) sensorManager.unregisterListener(gyroListener)
                fusionEngine.reset()
            }
        }
    }
}
