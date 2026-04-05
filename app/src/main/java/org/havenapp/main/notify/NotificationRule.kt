package org.havenapp.main.notify

import org.havenapp.main.events.Severity
import org.havenapp.main.events.TriggerType

/**
 * Global notification rule controlling which events trigger outbound alerts.
 * Persisted as individual DataStore fields (not serialized object) per D-06.
 */
data class NotificationRule(
    val minSeverity: Severity = Severity.MEDIUM,
    val cooldownMs: Long = 60_000L,
    val triggerTypes: Set<TriggerType> = setOf(
        TriggerType.CAMERA, TriggerType.CAMERA_PERSON,
        TriggerType.CAMERA_PET, TriggerType.CAMERA_VEHICLE,
        TriggerType.MICROPHONE,
    ),
    val attachMedia: Boolean = true,
)
