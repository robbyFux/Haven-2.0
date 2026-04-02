package org.havenapp.main.events

/**
 * Alle möglichen Auslöser eines Haven-Events.
 *
 * IDs 0–8: kompatibel mit Haven 0.2.1.
 * IDs 9–14: neu in Haven 2.0 (ML + erweitertes Audio).
 */
enum class TriggerType(val id: Int) {
    // --- Sensor-basiert (Haven-Baseline) ---
    ACCELEROMETER(0),       // Beschleunigungssensor-Ausschlag
    CAMERA(1),              // Kamera-Bewegung (Luminanz-Diff)
    MICROPHONE(2),          // Mikrofon-Pegelüberschreitung (allgemein)
    PRESSURE(3),            // Barometer-Änderung
    LIGHT(4),               // Lichtsensor-Änderung
    POWER(5),               // Netzteil angeschlossen / getrennt
    BUMP(6),                // Significant Motion (Gerät aufgenommen)
    CAMERA_VIDEO(7),        // Videoclip durch Kamera-Bewegung ausgelöst
    HEART(8),               // Heartbeat-Ping (Lebenszeichen)

    // --- ML-basiert (Haven 2.0, Phase 2) ---
    CAMERA_PERSON(9),       // TFLite: Person erkannt
    CAMERA_PET(10),         // TFLite: Tier erkannt
    CAMERA_VEHICLE(11),     // TFLite: Fahrzeug erkannt
    CAMERA_LINGER(12),      // Person verweilt länger im Bild
    CAMERA_ABSENT(13),      // Person nicht mehr im Bild

    // --- Erweitertes Audio (Haven 2.0) ---
    SOUND_DECIBEL(14),      // Absoluter Dezibel-Schwellwert überschritten

    // --- Weitere Sensoren (Haven 2.0) ---
    GYROSCOPE(15);          // Gyroskop-Ausschlag

    companion object {
        fun fromId(id: Int): TriggerType? = entries.find { it.id == id }
    }
}
