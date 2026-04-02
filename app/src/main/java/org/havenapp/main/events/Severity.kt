package org.havenapp.main.events

/**
 * Schwereklasse eines ausgelösten Events.
 * Wird vom NotificationRouter (Phase 3) verwendet um zu entscheiden,
 * welche Benachrichtigungskanäle aktiviert werden.
 */
enum class Severity {
    LOW,        // Hintergrundrauschen, Lichtveränderung
    MEDIUM,     // Bewegung ohne ML-Bestätigung
    HIGH,       // ML-bestätigte Detektion (Person, Fahrzeug)
    CRITICAL    // Gerätemanipulation, Mehrfach-Trigger gleichzeitig
}
