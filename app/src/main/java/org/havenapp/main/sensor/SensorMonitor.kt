package org.havenapp.main.sensor

import kotlinx.coroutines.flow.Flow
import org.havenapp.main.events.TriggerEvent

interface SensorMonitor {
    fun observe(sensitivity: Sensitivity, warmupMs: Long = 10_000L): Flow<TriggerEvent>
}
