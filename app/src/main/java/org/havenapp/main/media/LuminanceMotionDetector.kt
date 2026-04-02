package org.havenapp.main.media

/**
 * Vergleicht zwei aufeinanderfolgende Luminanz-Frames pixelweise.
 * Gibt den Anteil veränderter Pixel (0.0–1.0) zurück.
 *
 * Erwartet ein bereits normalisiertes Luma-Array der Größe width×height
 * (ein Byte pro Pixel, kein rowStride/pixelStride-Overhead).
 */
class LuminanceMotionDetector(private val pixelThreshold: Int = 25) {

    private var previousLuma: ByteArray? = null

    /**
     * @param luma Normalisiertes Luma-Array (width×height Bytes).
     * @return Anteil veränderter Pixel (0.0–1.0), oder null beim ersten Frame.
     */
    fun analyze(luma: ByteArray, width: Int, height: Int): Float? {
        val prev = previousLuma
        previousLuma = luma.copyOf()

        if (prev == null || prev.size != luma.size) return null

        var changedPixels = 0
        for (i in luma.indices) {
            if (Math.abs(luma[i].toInt() - prev[i].toInt()) > pixelThreshold) {
                changedPixels++
            }
        }

        return changedPixels.toFloat() / (width * height)
    }

    fun reset() {
        previousLuma = null
    }
}
