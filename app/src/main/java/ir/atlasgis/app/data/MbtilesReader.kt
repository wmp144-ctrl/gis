package ir.atlasgis.app.data

import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import ir.atlasgis.app.core.BoundingBox
import org.json.JSONObject
import java.io.File

/**
 * Reads the `metadata` table of an MBTiles archive. Everything here is executed on
 * SQLite's own C code with read-only access, so it stays fast even for multi-GB files.
 */
object MbtilesReader {

    data class Info(
        val name: String?,
        val format: TileFormat,
        val minZoom: Int,
        val maxZoom: Int,
        val bounds: BoundingBox?,
        val tileSize: Int,
        val vectorLayers: List<String>,
        val attribution: String?,
    )

    fun openReadOnly(file: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    fun read(file: File): Info {
        require(file.exists()) { "فایل نقشه پیدا نشد" }
        require(file.length() > 0) { "فایل نقشه خالی است" }
        require(hasSqliteHeader(file)) { "فایل انتخاب‌شده یک پایگاه‌داده MBTiles معتبر نیست" }

        val db = openReadOnly(file)
        try {
            val tables = HashSet<String>()
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type IN ('table','view')",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) tables.add(cursor.getString(0))
            }
            require("tiles" in tables) { "این فایل جدول tiles ندارد و MBTiles معتبر نیست" }

            val metadata = HashMap<String, String>()
            if ("metadata" in tables) {
                db.rawQuery("SELECT name, value FROM metadata", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val key = cursor.getString(0) ?: continue
                        metadata[key.lowercase()] = cursor.getString(1) ?: ""
                    }
                }
            }

            val minZoom = metadata["minzoom"]?.toIntOrNull() ?: 0
            val maxZoom = metadata["maxzoom"]?.toIntOrNull() ?: 18
            val format = detectFormat(db, tables, metadata["format"])
            val vectorLayers = if (format == TileFormat.VECTOR) readVectorLayers(metadata) else emptyList()
            val bounds = parseBounds(metadata["bounds"]) ?: boundsFromTiles(db, minZoom)
            val tileSize = metadata["tile_size"]?.toIntOrNull()
                ?: metadata["tilesize"]?.toIntOrNull()
                ?: 256

            return Info(
                name = metadata["name"]?.takeIf { it.isNotBlank() },
                format = format,
                minZoom = minZoom.coerceIn(0, 24),
                maxZoom = maxZoom.coerceIn(minZoom, 24),
                bounds = bounds,
                tileSize = tileSize.coerceIn(128, 1024),
                vectorLayers = vectorLayers,
                attribution = metadata["attribution"]?.takeIf { it.isNotBlank() }
                    ?: metadata["description"]?.takeIf { it.isNotBlank() },
            )
        } finally {
            runCatching { db.close() }
        }
    }

    /** True when the tile index MapLibre relies on is present. */
    fun hasTileIndex(file: File): Boolean {
        return runCatching {
            openReadOnly(file).use { db ->
                db.rawQuery(
                    "SELECT name, sql FROM sqlite_master WHERE type = 'index' AND tbl_name = 'tiles'",
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val sql = cursor.getString(1) ?: continue
                        if (sql.contains("zoom_level") && sql.contains("tile_column") &&
                            sql.contains("tile_row")
                        ) {
                            return@use true
                        }
                    }
                    false
                }
            }
        }.getOrDefault(true)
    }

    fun tileCountApprox(file: File): Long = runCatching {
        openReadOnly(file).use { db ->
            DatabaseUtils.longForQuery(db, "SELECT count(*) FROM tiles", null)
        }
    }.getOrDefault(0L)

    private fun hasSqliteHeader(file: File): Boolean {
        return runCatching {
            file.inputStream().use { stream ->
                val header = ByteArray(16)
                if (stream.read(header) != 16) return@use false
                String(header, Charsets.US_ASCII) == "SQLite format 3\u0000"
            }
        }.getOrDefault(false)
    }

    private fun detectFormat(
        db: SQLiteDatabase,
        tables: Set<String>,
        declared: String?,
    ): TileFormat {
        val normalized = declared?.trim()?.lowercase()
        if (normalized != null) {
            if (normalized == "pbf" || normalized == "mvt" || normalized == "vector") {
                return TileFormat.VECTOR
            }
            if (normalized in setOf("png", "jpg", "jpeg", "webp")) return TileFormat.RASTER
        }
        // Fall back to the magic bytes of a single tile.
        if ("tiles" in tables) {
            runCatching {
                db.rawQuery("SELECT tile_data FROM tiles LIMIT 1", null).use { cursor ->
                    if (!cursor.moveToFirst()) return@use TileFormat.RASTER
                    val bytes = cursor.getBlob(0) ?: return@use TileFormat.RASTER
                    val rasterSignature = bytes.size >= 4 && (
                        (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) ||
                            (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) ||
                            (bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                                bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte())
                        )
                    if (rasterSignature) TileFormat.RASTER else TileFormat.VECTOR
                }
            }.getOrDefault(TileFormat.RASTER)
        }
        return TileFormat.RASTER
    }

    private fun readVectorLayers(metadata: Map<String, String>): List<String> {
        val json = metadata["json"]
        if (!json.isNullOrBlank()) {
            runCatching {
                val root = JSONObject(json)
                val layers = root.optJSONArray("vector_layers") ?: return emptyList()
                buildList {
                    for (i in 0 until layers.length()) {
                        val id = layers.optJSONObject(i)?.optString("id").orEmpty()
                        if (id.isNotBlank()) add(id)
                    }
                }
            }.getOrNull()?.let { if (it.isNotEmpty()) return it }
        }
        runCatching {
            val direct = metadata["vector_layers"] ?: return emptyList()
            val layers = org.json.JSONArray(direct)
            buildList {
                for (i in 0 until layers.length()) {
                    val id = layers.optJSONObject(i)?.optString("id").orEmpty()
                    if (id.isNotBlank()) add(id)
                }
            }
        }.getOrNull()?.let { if (it.isNotEmpty()) return it }
        return emptyList()
    }

    private fun parseBounds(raw: String?): BoundingBox? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(',')
        if (parts.size < 4) return null
        val west = parts[0].trim().toDoubleOrNull() ?: return null
        val south = parts[1].trim().toDoubleOrNull() ?: return null
        val east = parts[2].trim().toDoubleOrNull() ?: return null
        val north = parts[3].trim().toDoubleOrNull() ?: return null
        val box = BoundingBox(
            south = south.coerceIn(-85.0, 85.0),
            west = west.coerceIn(-180.0, 180.0),
            north = north.coerceIn(-85.0, 85.0),
            east = east.coerceIn(-180.0, 180.0),
        )
        return box.takeIf { it.isValid }
    }

    /** Derives coverage from the lowest zoom level when metadata lacks `bounds`. */
    private fun boundsFromTiles(db: SQLiteDatabase, minZoom: Int): BoundingBox? {
        return runCatching {
            db.rawQuery(
                "SELECT min(tile_column), max(tile_column), min(tile_row), max(tile_row) " +
                    "FROM tiles WHERE zoom_level = (SELECT min(zoom_level) FROM tiles)",
                null,
            ).use { cursor ->
                if (!cursor.moveToFirst() || cursor.isNull(0)) return@use null
                val minX = cursor.getInt(0)
                val maxX = cursor.getInt(1)
                val minY = cursor.getInt(2)
                val maxY = cursor.getInt(3)
                val zoom = minZoom.coerceIn(0, 24)
                val n = 1 shl zoom
                val west = minX.toDouble() / n * 360.0 - 180.0
                val east = (maxX + 1).toDouble() / n * 360.0 - 180.0
                // TMS rows grow northwards: the largest row is the northern edge.
                val north = tileRowToLat(maxY, n)
                val south = tileRowToLat(minY - 1, n)
                BoundingBox(south, west, north, east).takeIf { it.isValid }
            }
        }.getOrNull()
    }

    private fun tileRowToLat(tmsRow: Int, tilesAtZoom: Int): Double {
        // MBTiles stores TMS rows (origin bottom-left); convert to WGS84 latitude.
        val y = tilesAtZoom - 1 - tmsRow
        val n = Math.PI - 2.0 * Math.PI * y / tilesAtZoom
        val lat = Math.toDegrees(kotlin.math.atan(kotlin.math.sinh(n)))
        return lat.coerceIn(-85.0511, 85.0511)
    }
}
