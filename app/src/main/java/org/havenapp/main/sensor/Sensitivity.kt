package org.havenapp.main.sensor

/**
 * Einheitliche Empfindlichkeitsstufe für alle Sensor-Monitore.
 *
 * Statt roher Zahlenwerte wie in Haven 0.2.1 (Integer.parseInt("Off") → NumberFormatException)
 * verwenden wir ein typsicheres Enum.
 *
 * Default-Spreizung (SENSOR-10, revidiert 2026-04):
 *   LOW    = (4.0×, 65 dB, 80 lux, 0.18) — weniger sensitiv als Medium
 *   MEDIUM = (2.0×, 55 dB, 40 lux, 0.08) — neuer Default; triggert zuverlässig bei normaler Indoor-Bewegung
 *   HIGH   = (1.2×, 45 dB, 20 lux, 0.04) — sensitiver als Medium
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
    LOW(4.0f, 65f, 80f, 0.18f),
    MEDIUM(2.0f, 55f, 40f, 0.08f),
    HIGH(1.2f, 45f, 20f, 0.04f),
}

// ── Expert-Threshold-Helfer (SENSOR-11) ──────────────────────────────────────
// Gibt den effektiven Schwellwert zurück: ExpertThresholds-Override (wenn gesetzt),
// sonst den enum-Default des jeweiligen Sensors.

/**
 * Effective accelerometer multiplier: expert override if set, otherwise the enum default.
 * Only applies when sensitivity is MEDIUM — other tiers use the enum value directly.
 */
fun Sensitivity.effectiveAccelMultiplier(expert: ExpertThresholds): Float =
    expert.accelMediumMultiplier ?: accelerometerMultiplier

/**
 * Effective microphone threshold dB: expert override if set, otherwise the enum default.
 */
fun Sensitivity.effectiveMicDb(expert: ExpertThresholds): Float =
    expert.micMediumDb ?: microphoneThresholdDb

/**
 * Effective light delta lux: expert override if set, otherwise the enum default.
 */
fun Sensitivity.effectiveLightLux(expert: ExpertThresholds): Float =
    expert.lightMediumLux ?: lightDeltaLux

/**
 * Effective camera motion fraction: expert override if set, otherwise the enum default.
 */
fun Sensitivity.effectiveCameraFraction(expert: ExpertThresholds): Float =
    expert.cameraMediumFraction ?: cameraMotionThreshold
