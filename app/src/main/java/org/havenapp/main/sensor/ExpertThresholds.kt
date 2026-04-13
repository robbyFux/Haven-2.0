package org.havenapp.main.sensor

/**
 * Per-sensor custom Medium threshold overrides for the Expert Settings feature (SENSOR-11).
 *
 * A null field means "use the Sensitivity enum default for that sensor's Medium value".
 * All four fields null (the default) means no customization — all sensors use enum defaults.
 *
 * Only Medium-tier thresholds are customizable. LOW and HIGH tiers are derived automatically
 * in the UI (Plan 03) as fixed offsets from the custom Medium value, preserving the
 * three-tier spread without requiring separate storage.
 *
 * Instances are created by SettingsRepository and passed by value into sensor monitors at
 * session start — not injected via Hilt.
 */
data class ExpertThresholds(
    /** Custom accelerometer noise-floor multiplier for Medium sensitivity. Null = enum default (2.0×). */
    val accelMediumMultiplier: Float? = null,
    /** Custom microphone threshold in dB for Medium sensitivity. Null = enum default (55 dB). */
    val micMediumDb: Float? = null,
    /** Custom light delta threshold in lux for Medium sensitivity. Null = enum default (40 lux). */
    val lightMediumLux: Float? = null,
    /** Custom camera luma-diff fraction for Medium sensitivity. Null = enum default (0.08). */
    val cameraMediumFraction: Float? = null,
) {
    companion object {
        /** All fields null — sensors use Sensitivity enum defaults. */
        val DEFAULT = ExpertThresholds()
    }
}

/** Identifies which sensor's expert threshold to set or reset individually. */
enum class ExpertThresholdKind { ACCEL, MIC, LIGHT, CAMERA }
