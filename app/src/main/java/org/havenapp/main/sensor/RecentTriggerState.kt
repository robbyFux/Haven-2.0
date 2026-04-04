package org.havenapp.main.sensor

import org.havenapp.main.events.TriggerType
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-global tracker of recent trigger timestamps per [TriggerType].
 *
 * [MonitorService] calls [record] for every trigger it receives.
 * [LightMonitor] calls [wasRecentlyTriggeredBy] to suppress light events
 * when motion or camera triggers occurred recently (cross-sensor priority gate).
 *
 * Thread-safe via [ConcurrentHashMap]. Not Hilt-injected — must be accessible
 * from both the service and sensor monitors without DI wiring.
 */
object RecentTriggerState {

    private val lastTriggerMs = ConcurrentHashMap<TriggerType, Long>()

    /** Records the current timestamp for the given trigger type. */
    fun record(type: TriggerType) {
        lastTriggerMs[type] = System.currentTimeMillis()
    }

    /**
     * Returns true if ANY of the given [types] was recorded within [windowMs]
     * of the current time.
     */
    fun wasRecentlyTriggeredBy(types: Set<TriggerType>, windowMs: Long): Boolean {
        if (windowMs <= 0L) return false
        val now = System.currentTimeMillis()
        return types.any { type ->
            val last = lastTriggerMs[type] ?: return@any false
            (now - last) < windowMs
        }
    }

    /** Clears all recorded timestamps. Called by MonitorService on session stop. */
    fun reset() {
        lastTriggerMs.clear()
    }
}
