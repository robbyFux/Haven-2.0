package org.havenapp.main.detection

/**
 * Rechteckige Erkennungszone im Kamerabild.
 * Koordinaten sind normalisiert (0.0–1.0), (0,0) = oben links, (1,1) = unten rechts.
 *
 * Nur Pixel innerhalb der Zone werden für Luminanz-Diff und pHash ausgewertet.
 * Das reduziert False Positives durch irrelevante Bildbereiche (z.B. belebte Straße
 * im Hintergrund, schwankende Bäume am Fensterrand).
 */
data class DetectionZone(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left < right) { "left ($left) must be < right ($right)" }
        require(top < bottom) { "top ($top) must be < bottom ($bottom)" }
    }

    companion object {
        /** Serialisierung für DataStore: "left,top,right,bottom" */
        fun fromString(s: String): DetectionZone? = runCatching {
            val p = s.split(",")
            check(p.size == 4)
            DetectionZone(p[0].toFloat(), p[1].toFloat(), p[2].toFloat(), p[3].toFloat())
        }.getOrNull()
    }

    fun serialize(): String = "$left,$top,$right,$bottom"

    /** Pixelgenaue Grenzen bei gegebener Bildgröße. */
    fun toPixelBounds(width: Int, height: Int): PixelBounds = PixelBounds(
        colStart = (left * width).toInt().coerceIn(0, width - 1),
        colEnd = (right * width).toInt().coerceIn(1, width),
        rowStart = (top * height).toInt().coerceIn(0, height - 1),
        rowEnd = (bottom * height).toInt().coerceIn(1, height),
    )

    data class PixelBounds(val colStart: Int, val colEnd: Int, val rowStart: Int, val rowEnd: Int) {
        val zoneWidth: Int get() = colEnd - colStart
        val zoneHeight: Int get() = rowEnd - rowStart
    }
}
