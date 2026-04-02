package org.havenapp.main.events

/**
 * Ein einzelner Sensor- oder Kamera-Auslöser.
 *
 * Wird von Sensor-Monitoren und dem CameraAnalyzer emittiert und
 * vom MonitorService via EventRepository persistiert.
 *
 * @param type          Art des Auslösers
 * @param timestamp     Zeitstempel in Unix-Millisekunden
 * @param sensorValue   Rohwert des Sensors zum Zeitpunkt des Auslösens
 *                      (z.B. Beschleunigung in m/s², dB-Wert, Lux)
 * @param mediaPath     Optionaler Pfad zu Foto oder Audiodatei
 * @param severity      Schwereklasse – wird vom EventEngine berechnet
 */
data class TriggerEvent(
    val type: TriggerType,
    val timestamp: Long = System.currentTimeMillis(),
    val sensorValue: Float? = null,
    val mediaPath: String? = null,
    val severity: Severity = Severity.MEDIUM,
)
