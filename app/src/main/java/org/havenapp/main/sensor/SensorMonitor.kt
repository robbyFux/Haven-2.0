package org.havenapp.main.sensor

import kotlinx.coroutines.flow.Flow
import org.havenapp.main.events.TriggerEvent

interface SensorMonitor {
    // expert parameter added in SENSOR-11: allows per-sensor Medium threshold overrides.
    // All active monitors (FusedMotionMonitor, MicrophoneMonitor) and legacy dead-code monitors
    // (AccelerometerMonitor, GyroscopeMonitor) implement this signature.
    fun observe(sensitivity: Sensitivity, warmupMs: Long = 10_000L, expert: ExpertThresholds = ExpertThresholds.DEFAULT): Flow<TriggerEvent>
}
