package ir.atlasgis.app.data

import ir.atlasgis.app.core.BoundingBox
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.tan
import kotlin.math.cos

/**
 * Slippy-map tile arithmetic for downloading a region. Pure Kotlin so the estimate
 * shown in the download sheet and the actual download use exactly the same grid.
 */
object TileGrid {

    /** Beyond this a phone-side download is a bad idea on mobile data anyway. */
    const val MAX_TILES = 20_000

    data class ZoomRange(
        val zoom: Int,
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int,
    ) {
        val count: Int get() = (maxX - minX + 1) * (maxY - minY + 1)
    }

    fun tileCountX(zoom: Int): Int = 1 shl zoom

    fun lonToTileX(lon: Double, zoom: Int): Int {
        val n = tileCountX(zoom)
        val normalized = ((lon + 180.0) % 360.0 + 360.0) % 360.0
        return ((normalized / 360.0) * n).toInt().coerceIn(0, n - 1)
    }

    fun latToTileY(lat: Double, zoom: Int): Int {
        val n = tileCountX(zoom)
        val clamped = lat.coerceIn(-85.05112878, 85.05112878)
        val rad = clamped * PI / 180.0
        val y = ((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0) * n
        return y.toInt().coerceIn(0, n - 1)
    }

    /** Inclusive tile ranges per zoom, or an empty list for an invalid request. */
    fun ranges(bounds: BoundingBox, minZoom: Int, maxZoom: Int): List<ZoomRange> {
        if (!bounds.isValid || minZoom > maxZoom) return emptyList()
        val result = ArrayList<ZoomRange>(maxZoom - minZoom + 1)
        for (zoom in minZoom.coerceIn(0, 22)..maxZoom.coerceIn(0, 22)) {
            val minX = lonToTileX(bounds.west, zoom)
            val maxX = lonToTileX(bounds.east, zoom)
            val minY = latToTileY(bounds.north, zoom)
            val maxY = latToTileY(bounds.south, zoom)
            result += ZoomRange(
                zoom = zoom,
                minX = minOf(minX, maxX),
                maxX = maxOf(minX, maxX),
                minY = minOf(minY, maxY),
                maxY = maxOf(minY, maxY),
            )
        }
        return result
    }

    fun estimate(bounds: BoundingBox, minZoom: Int, maxZoom: Int): Int =
        ranges(bounds, minZoom, maxZoom).sumOf { it.count }

    /** TMS row for an XYZ tile: MBTiles stores rows bottom-up. */
    fun tmsRow(y: Int, zoom: Int): Int = tileCountX(zoom) - 1 - y

    /** Rough download size used by the UI estimate (typical 256px raster tile). */
    fun estimateBytes(tiles: Int): Long = tiles.toLong() * 14_000L

    /** Rough printed tile count for a zoom span; null when the span is unusable. */
    fun validate(bounds: BoundingBox, minZoom: Int, maxZoom: Int): String? {
        if (!bounds.isValid) return "محدودهٔ انتخابی معتبر نیست"
        if (minZoom < 0 || maxZoom > 19) return "بازهٔ زوم باید بین ۰ تا ۱۹ باشد"
        if (minZoom > maxZoom) return "زوم شروع نباید از زوم پایان بزرگ‌تر باشد"
        if (abs(bounds.north - bounds.south) < 0.0005 || abs(bounds.east - bounds.west) < 0.0005) {
            return "محدودهٔ انتخابی بسیار کوچک است؛ کمی نقشه را بزرگ‌تر کنید"
        }
        val count = estimate(bounds, minZoom, maxZoom)
        if (count > MAX_TILES) {
            return "این محدوده $count کاشی می‌خواهد؛ حداکثر $MAX_TILES کاشی مجاز است. " +
                "زوم پایان را کم یا محدوده را کوچک‌تر کنید."
        }
        if (count <= 0) return "هیچ کاشی‌ای در این محدوده نیست"
        return null
    }
}
