package org.havenapp.main.sensor

/**
 * Einheitliche Empfindlichkeitsstufe für alle Sensor-Monitore.
 *
 * Statt roher Zahlenwerte wie in Haven 0.2.1 (Integer.parseInt("Off") → NumberFormatException)
 * verwenden wir ein typsicheres Enum.
 *
 * @param accelerometerMultiplier  Faktor auf den gemessenen Noise-Floor (Phase 2)
 * @param microphoneThresholdDb    Absoluter dB-Schwellwert für Mikrofon-Detektion
 * @param lightDeltaLux            Mindestveränderung in Lux für Licht-Detektion
 */
enum class Sensitivity(
    val accelerometerMultiplier: Float,
    val microphoneThresholdDb: Float,
    val lightDeltaLux: Float,
    /** Fraction of pixels that must change (Luma-Diff) to confirm camera motion. */
    val cameraMotionThreshold: Float,
) {
    OFF(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE),
    LOW(6.5f, 70f, 100f, 0.22f),
    MEDIUM(4.5f, 60f, 60f, 0.12f),
    HIGH(2.5f, 50f, 30f, 0.06f),
}
