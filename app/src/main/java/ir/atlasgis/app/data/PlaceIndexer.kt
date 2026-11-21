package ir.atlasgis.app.data

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sinh

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

        private val indexableLayers = setOf(
            "poi", "pois", "poi_label", "poi_labels", "pois_points",
            "place", "places", "place_labels", "place_label", "place_label_points", "localities",
            "water_name", "water_names", "water_label", "water_labels",
            "mountain_peak", "mountain_peaks", "peak", "peaks",
            "aerodrome_label", "airport_label", "airports", "aerodrome",
        )

        fun isIndexableLayer(name: String): Boolean {
            val key = name.lowercase()
            return key in indexableLayers || key.contains("poi")
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
