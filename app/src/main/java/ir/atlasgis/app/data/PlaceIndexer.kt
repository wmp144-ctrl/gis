package ir.atlasgis.app.data

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Builds the offline search index for one vector archive.
 *
 * Vector tiles cannot be searched with SQL, so the naming features of a single,
 * well-chosen zoom level are read once and copied into a small SQLite table. That
 * keeps search instant on a phone while staying independent of the map view, and
 * the chosen zoom is capped so even a country-sized archive is indexed in seconds
 * rather than minutes.
 */
class PlaceIndexer(private val db: AppDatabase) {

    suspend fun build(layer: MapLayer, onProgress: (Float) -> Unit = {}): Int =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val file = File(layer.filePath)
            if (layer.format != TileFormat.VECTOR || !file.exists()) return@withContext 0
            val sqlite = runCatching { MbtilesReader.openReadOnly(file) }.getOrNull()
                ?: return@withContext 0
            try {
                val zoom = chooseZoom(sqlite, layer)
                if (zoom == null) {
                    // Archive without POI/place layers: remember that search is empty
                    // instead of re-scanning it on every open.
                    db.markPlacesIndexed(layer.id, 0)
                    return@withContext 0
                }
                val total = countTiles(sqlite, zoom)
                if (total <= 0) {
                    db.markPlacesIndexed(layer.id, 0)
                    return@withContext 0
                }

                db.deletePlacesForLayer(layer.id)
                val buffer = ArrayList<MapPlace>(2048)
                var inserted = 0
                var seen = 0
                val world = 1 shl zoom

                sqlite.rawQuery(
                    "SELECT tile_column, tile_row, tile_data FROM tiles WHERE zoom_level = ?",
                    arrayOf(zoom.toString()),
                ).use { cursor ->
                    while (cursor.moveToNext() && inserted < MAX_ROWS) {
                        currentCoroutineContext().ensureActive()
                        val column = cursor.getInt(0)
                        val tmsRow = cursor.getInt(1)
                        val data = cursor.getBlob(2)
                        seen++
                        if (data != null && data.isNotEmpty()) {
                            val row = world - 1 - tmsRow
                            MvtDecoder.decode(data).forEach { point ->
                                if (!isIndexableLayer(point.layer)) return@forEach
                                val name = displayName(point.tags) ?: return@forEach
                                buffer.add(
                                    MapPlace(
                                        name = name,
                                        klass = kindOf(point.tags),
                                        layerName = point.layer,
                                        lat = latitude(row + point.y, world),
                                        lon = (column + point.x) / world * 360.0 - 180.0,
                                        layerId = layer.id,
                                    ),
                                )
                            }
                        }
                        if (buffer.size >= 2048) {
                            inserted += db.insertPlaces(layer.id, buffer)
                            buffer.clear()
                        }
                        if ((seen and 0x1F) == 0) onProgress((seen.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
                if (buffer.isNotEmpty()) inserted += db.insertPlaces(layer.id, buffer)
                db.markPlacesIndexed(layer.id, inserted)
                onProgress(1f)
                inserted
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (ignored: Throwable) {
                // A damaged tile must never break the map; search simply stays partial.
                0
            } finally {
                runCatching { sqlite.close() }
            }
        }

    /**
     * Indexes the named points of the tiles around one position at street zoom.
     *
     * A country-sized archive has far too many z15 tiles to scan once, and the POI
     * layer only exists at those zooms, so the searchable POIs are collected for the
     * area the user is looking at. Calling it again as the map moves costs a handful
     * of tile reads, and the unique index drops the points that are already stored.
     */
    suspend fun buildNear(
        layer: MapLayer,
        lat: Double,
        lon: Double,
        radius: Int = 3,
    ): Int = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val file = File(layer.filePath)
        if (layer.format != TileFormat.VECTOR || !file.exists()) return@withContext 0
        val zoom = minOf(layer.maxZoom, 16)
        if (zoom < layer.minZoom) return@withContext 0
        val sqlite = runCatching { MbtilesReader.openReadOnly(file) }.getOrNull()
            ?: return@withContext 0
        try {
            val world = 1 shl zoom
            val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
            val column = ((lon + 180.0) / 360.0 * world).toInt().coerceIn(0, world - 1)
            val row = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * world)
                .toInt().coerceIn(0, world - 1)
            val buffer = ArrayList<MapPlace>(256)
            for (dx in -radius..radius) {
                for (dy in -radius..radius) {
                    val x = ((column + dx) % world + world) % world
                    val y = row + dy
                    if (y !in 0 until world) continue
                    val tmsRow = world - 1 - y
                    sqlite.rawQuery(
                        "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? " +
                            "AND tile_row = ? LIMIT 1",
                        arrayOf(zoom.toString(), x.toString(), tmsRow.toString()),
                    ).use { cursor ->
                        if (!cursor.moveToFirst()) return@use
                        val data = cursor.getBlob(0)
                        if (data == null || data.isEmpty()) return@use
                        MvtDecoder.decode(data).forEach { point ->
                            if (!isIndexableLayer(point.layer)) return@forEach
                            val name = displayName(point.tags) ?: return@forEach
                            buffer.add(
                                MapPlace(
                                    name = name,
                                    klass = kindOf(point.tags),
                                    layerName = point.layer,
                                    lat = latitude(y + point.y, world),
                                    lon = (x + point.x) / world * 360.0 - 180.0,
                                    layerId = layer.id,
                                ),
                            )
                        }
                    }
                }
            }
            if (buffer.isEmpty()) 0 else db.insertPlaces(layer.id, buffer)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (ignored: Throwable) {
            0
        } finally {
            runCatching { sqlite.close() }
        }
    }

    private fun chooseZoom(sqlite: SQLiteDatabase, layer: MapLayer): Int? {
        val highest = minOf(layer.maxZoom, 16)
        val lowest = maxOf(layer.minZoom, 9)
        if (highest < lowest) return null
        var fallback: Pair<Int, Long>? = null
        for (zoom in highest downTo lowest) {
            val tiles = countTiles(sqlite, zoom)
            if (tiles <= 0L) continue
            if (tiles <= MAX_TILES) return zoom
            if (fallback == null || tiles < fallback.second) fallback = zoom to tiles
        }
        return fallback?.first
    }

    private fun countTiles(sqlite: SQLiteDatabase, zoom: Int): Long = runCatching {
        sqlite.rawQuery(
            "SELECT count(*) FROM tiles WHERE zoom_level = ?",
            arrayOf(zoom.toString()),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
    }.getOrDefault(0L)

    private fun latitude(tileY: Double, world: Int): Double {
        val y = tileY / world
        val degrees = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * y))))
        return degrees.coerceIn(-85.0511, 85.0511)
    }

    companion object {

        /** Upper bound on the tiles read for one archive: ~3k tiles is a few seconds. */
        private const val MAX_TILES = 3000L
        private const val MAX_ROWS = 200_000

        /**
         * Only geometry and address layers are skipped. Every other layer may carry
         * named points — `poi`, but also `amenities`, `shops` or a bespoke name —
         * and a place the map shows must also be findable.
         */
        private val nonPlaceLayers = setOf(
            "water", "waterway", "waterways", "landuse", "landcover", "land",
            "transportation", "transportation_name", "transportation_names",
            "road", "roads", "street", "streets", "rail", "railway",
            "boundary", "boundaries", "admin", "aeroway", "route", "routes",
        )

        fun isIndexableLayer(name: String): Boolean {
            val key = name.lowercase()
            if (key in nonPlaceLayers) return false
            return !key.contains("building") && !key.contains("housenumber") &&
                !key.contains("address")
        }

        private fun firstOf(tags: Map<String, String>, vararg keys: String): String? {
            keys.forEach { key ->
                val value = tags[key]?.trim()
                if (!value.isNullOrEmpty()) return value
            }
            return null
        }

        fun displayName(tags: Map<String, String>): String? {
            val raw = firstOf(
                tags,
                "name:fa", "name_fa", "name", "name:en", "name_en", "name:latin", "name_int",
                "ref",
            ) ?: return null
            val cleaned = raw.replace(Regex("\\s+"), " ").trim()
            if (cleaned.length < 2 || cleaned.length > 120) return null
            return cleaned
        }

        fun kindOf(tags: Map<String, String>): String =
            firstOf(
                tags,
                "subclass", "class", "amenity", "shop", "tourism", "leisure", "office", "maki",
                "type", "kind", "category",
            ) ?: ""

        /**
         * Persian-aware folding so "ك" (Arabic kaf) and "ک" (Persian kaf) match, and
         * so zero-width non-joiners and diacritics never make a search fail.
         */
        fun normalize(raw: String): String = raw
            .trim()
            .lowercase()
            .replace('\u064A', '\u06CC')
            .replace('\u0649', '\u06CC')
            .replace('\u0643', '\u06A9')
            .replace('\u0629', '\u0647')
            .replace('\u200C', ' ')
            .replace('\u200D', ' ')
            .replace(Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]"), "")
            .replace(Regex("[\\u0640]"), "")
            .replace(Regex("\\s+"), " ")
    }
}
