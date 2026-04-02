package org.havenapp.main.detection

/**
 * Steuert, welche Erkennungslogik die Kamera-Pipeline nutzt.
 *
 * MOTION_ONLY: Nur Luminanz-Diff + pHash – kein TFLite, geringster Akkuverbrauch.
 * PERSON/PET/VEHICLE: TFLite läuft nur, wenn zuvor Bewegung erkannt wurde (2-Stufen-Gate).
 * ALL: Alle drei Klassen werden geprüft.
 */
enum class DetectionMode {
    MOTION_ONLY,
    PERSON,
    PET,
    VEHICLE,
    ALL;

    val requiresML: Boolean get() = this != MOTION_ONLY
    val detectsPerson: Boolean get() = this == PERSON || this == ALL
    val detectsPet: Boolean get() = this == PET || this == ALL
    val detectsVehicle: Boolean get() = this == VEHICLE || this == ALL
}
