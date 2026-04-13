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
import org.havenapp.main.events.Severity
import org.havenapp.main.events.TriggerEvent
import org.havenapp.main.events.TriggerType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Light sensor monitor with dual-rate EMA baseline tracking.
 *
 * Two EMAs run in parallel:
 * - **emaFast** (alpha=0.1): adapts quickly to gradual daylight changes (~10 samples / ~2s).
 *   Trigger deviation is measured against this baseline. On trigger, emaFast snaps to the
 *   current lux value to prevent cascade triggers.
 * - **emaSlow** (alpha=0.02): slow reference (~50 samples / ~10s). Reserved for diagnostics
 *   and future use; not used for triggering.
 *
 * Cross-sensor suppression (per D-04): before emitting a trigger, checks
 * [RecentTriggerState.wasRecentlyTriggeredBy] for ACCELEROMETER and CAMERA within
 * [suppressionWindowMs]. If another high-priority sensor fired recently, the light
 * trigger is suppressed (the user likely caused the light change by moving).
 *
 * NOTE: This class does NOT implement [SensorMonitor]. The 3-parameter [observe] method
 * does not match the 2-parameter interface contract. [MonitorService] injects this class
 * by its concrete type, so the interface is not needed.
 */
@Singleton
class LightMonitor @Inject constructor(
    private val sensorManager: SensorManager,
) {

    /** EMA-Baseline (emaFast) in Lux -- null until warmup completes. */
    private val _emaBaseline = MutableStateFlow<Float?>(null)
    val emaBaseline: StateFlow<Float?> = _emaBaseline

    /**
     * Observes the light sensor and emits [TriggerEvent]s when deviation exceeds threshold.
     *
     * @param suppressionWindowMs Duration in ms during which a recent ACCELEROMETER or CAMERA
     *   trigger suppresses light triggers. 0 = suppression disabled. Default: 10000 (10s).
     */
    fun observe(
        sensitivity: Sensitivity,
        warmupMs: Long,
        expert: ExpertThresholds = ExpertThresholds.DEFAULT,
        suppressionWindowMs: Long = 10_000L,
    ): Flow<TriggerEvent> {
        if (sensitivity == Sensitivity.OFF) return kotlinx.coroutines.flow.emptyFlow()

        _emaBaseline.value = null

        return callbackFlow {
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
            if (sensor == null) {
                close()
                return@callbackFlow
            }

            val emaFastAlpha = 0.1f
            val emaSlowAlpha = 0.02f
            var emaFast = Float.NaN
            var emaSlow = Float.NaN
            val startTime = System.currentTimeMillis()
            var lastEmitTime = 0L
            val cooldownMs = 30_000L

            val suppressTypes = setOf(TriggerType.ACCELEROMETER, TriggerType.CAMERA)

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val lux = event.values[0]

                    if (emaFast.isNaN()) {
                        emaFast = lux
                        emaSlow = lux
                        return
                    }

                    // Measure deviation from emaFast BEFORE updating (spike must not be absorbed)
                    val deviation = abs(lux - emaFast)

                    // Update both EMAs
                    emaFast = emaFastAlpha * lux + (1f - emaFastAlpha) * emaFast
                    emaSlow = emaSlowAlpha * lux + (1f - emaSlowAlpha) * emaSlow

                    // During warmup: build baselines only, no events
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed < warmupMs) return
                    if (_emaBaseline.value == null) _emaBaseline.value = emaFast

                    val threshold = sensitivity.effectiveLightLux(expert)
                    if (deviation >= threshold) {
                        val now = System.currentTimeMillis()
                        if (now - lastEmitTime >= cooldownMs) {
                            // Cross-sensor suppression gate (D-04)
                            if (RecentTriggerState.wasRecentlyTriggeredBy(suppressTypes, suppressionWindowMs)) {
                                return  // suppress -- do NOT reset cooldown
                            }

                            lastEmitTime = now
                            emaFast = lux  // snap fast baseline to avoid cascade triggers
                            val severity = when {
                                deviation >= threshold * 3f -> Severity.HIGH
                                deviation >= threshold * 1.5f -> Severity.MEDIUM
                                else -> Severity.LOW
                            }
                            trySend(
                                TriggerEvent(
                                    type = TriggerType.LIGHT,
                                    sensorValue = deviation,
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
