package org.havenapp.main.detection

import kotlin.math.sqrt

/**
 * Complementary filter combining accelerometer delta and gyroscope magnitude.
 *
 * alpha=0.7 → 70% gyroscope contribution, 30% accelerometer contribution.
 *
 * Key insight: table vibrations produce a high accel delta but very low gyro magnitude
 * (the table doesn't rotate). The fused score is therefore pulled down by the gyro term,
 * reducing false positives. Intentional movement produces both a large accel delta AND
 * a large gyro magnitude, so the fused score remains high and triggers correctly.
 */
class SensorFusionEngine(
    private val alpha: Float = 0.7f,
) {
    private var lastAccelX = 0f
    private var lastAccelY = 0f
    private var lastAccelZ = 0f
    private var accelInitialized = false

    var latestGyroMagnitude = 0f
        private set

    /**
     * Computes the Euclidean delta magnitude since the last accelerometer sample.
     *
     * @param x Accelerometer x-axis value in m/s².
     * @param y Accelerometer y-axis value in m/s².
     * @param z Accelerometer z-axis value in m/s².
     * @return Euclidean distance between consecutive accelerometer vectors (delta magnitude).
     *   Returns 0 on the first call (no previous sample to compare against).
     */
    fun processAccelerometer(x: Float, y: Float, z: Float): Float {
        if (!accelInitialized) {
            lastAccelX = x; lastAccelY = y; lastAccelZ = z
            accelInitialized = true
            return 0f
        }
        val dx = x - lastAccelX
        val dy = y - lastAccelY
        val dz = z - lastAccelZ
        lastAccelX = x; lastAccelY = y; lastAccelZ = z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * Stores the latest angular velocity magnitude for the next [fuse] call.
     *
     * @param x Gyroscope x-axis angular velocity in rad/s.
     * @param y Gyroscope y-axis angular velocity in rad/s.
     * @param z Gyroscope z-axis angular velocity in rad/s.
     */
    fun processGyroscope(x: Float, y: Float, z: Float) {
        latestGyroMagnitude = sqrt(x * x + y * y + z * z)
    }

    /** Fused score: alpha × gyroMag + (1-alpha) × accelDelta. */
    fun fuse(accelDelta: Float): Float =
        alpha * latestGyroMagnitude + (1f - alpha) * accelDelta

    fun reset() {
        accelInitialized = false
        latestGyroMagnitude = 0f
    }
}
