package org.havenapp.main.detection

/**
 * Average Hash (aHash) based structural change detector.
 *
 * Downsamples the luma plane to 8×8, computes the per-block mean, builds a
 * 64-bit hash (bit = 1 if block average ≥ mean), and returns the Hamming
 * distance between consecutive frame hashes.
 *
 * Key advantage over raw luminance diff:
 *   - Uniform brightness shift (light flicker, cloud passing): spatial structure
 *     unchanged → near-zero hash distance → no false trigger.
 *   - Object moving through frame: blocks flip above/below mean → high hash distance.
 *
 * Typical distances (8×8 hash, 0–64 range):
 *   Static scene:       0–2
 *   Lighting flicker:   1–3
 *   Real motion:        4+
 */
class PerceptualHashDetector {

    companion object {
        private const val HASH_SIZE = 8              // 8×8 = 64-bit hash
        private const val TOTAL_BITS = HASH_SIZE * HASH_SIZE
    }

    private var previousHash = 0L
    private var hasHash = false

    /**
     * @param luma Width×height normalized luma array (one byte per pixel).
     * @return Hamming distance (0–64) to the previous frame, or null on the first call.
     */
    fun analyze(luma: ByteArray, width: Int, height: Int): Int? {
        val blockW = width / HASH_SIZE
        val blockH = height / HASH_SIZE

        // Average-pool luma into 8×8 grid
        val blockAvg = IntArray(TOTAL_BITS)
        for (gy in 0 until HASH_SIZE) {
            for (gx in 0 until HASH_SIZE) {
                var sum = 0L
                val rowStart = gy * blockH
                val colStart = gx * blockW
                for (row in rowStart until rowStart + blockH) {
                    val rowOffset = row * width
                    for (col in colStart until colStart + blockW) {
                        sum += luma[rowOffset + col].toInt() and 0xFF
                    }
                }
                val count = blockW * blockH
                blockAvg[gy * HASH_SIZE + gx] = (sum / count).toInt()
            }
        }

        // Threshold each block against the mean → 64-bit hash
        val mean = blockAvg.average()
        var hash = 0L
        for (i in 0 until TOTAL_BITS) {
            if (blockAvg[i] >= mean) {
                hash = hash or (1L shl i)
            }
        }

        if (!hasHash) {
            previousHash = hash
            hasHash = true
            return null
        }

        val dist = java.lang.Long.bitCount(hash xor previousHash)
        previousHash = hash
        return dist
    }

    fun reset() {
        hasHash = false
        previousHash = 0L
    }
}
