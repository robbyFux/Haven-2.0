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

@Singleton
class LightMonitor @Inject constructor(
    private val sensorManager: SensorManager,
) : SensorMonitor {

    /** EMA-Baseline in Lux – wird gesetzt, sobald der Warmup abgeschlossen ist. */
    private val _emaBaseline = MutableStateFlow<Float?>(null)
    val emaBaseline: StateFlow<Float?> = _emaBaseline

    override fun observe(sensitivity: Sensitivity, warmupMs: Long): Flow<TriggerEvent> {
        if (sensitivity == Sensitivity.OFF) return kotlinx.coroutines.flow.emptyFlow()

        _emaBaseline.value = null

        return callbackFlow {
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
            if (sensor == null) {
                close()
                return@callbackFlow
            }

            // EMA-Baseline: passt sich langsam an graduelle Lichtveränderungen an.
            // alpha=0.02 → Zeitkonstante ~50 Samples (~10s bei SENSOR_DELAY_NORMAL).
            // Sonnenauf/-untergang (~0.5 lux/s → 0.1 lux/Sample) erzeugt nur ~5 lux
            // Abweichung von der EMA – weit unter den Thresholds (10–50 lux).
            // Eine Lampe (plötzlich +200 lux) erzeugt sofort 200 lux Abweichung.
            val emaAlpha = 0.02f
            var ema = Float.NaN
            val startTime = System.currentTimeMillis()
            var lastEmitTime = 0L
            val cooldownMs = 30_000L  // 30s Cooldown – verhindert Mehrfach-Trigger bei langsamen Tageslichtänderungen

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val lux = event.values[0]

                    if (ema.isNaN()) {
                        ema = lux
                        return
                    }

                    // Abweichung vor EMA-Update messen (sonst wird Spike sofort absorbiert)
                    val deviation = abs(lux - ema)
                    ema = emaAlpha * lux + (1f - emaAlpha) * ema

                    // Während Warmup nur EMA aufbauen, keine Events
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed < warmupMs) return
                    if (_emaBaseline.value == null) _emaBaseline.value = ema

                    val threshold = sensitivity.lightDeltaLux
                    if (deviation >= threshold) {
                        val now = System.currentTimeMillis()
                        if (now - lastEmitTime >= cooldownMs) {
                            lastEmitTime = now
                            ema = lux  // EMA sofort auf neuen Wert snappen – verhindert Kaskaden-Trigger
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
