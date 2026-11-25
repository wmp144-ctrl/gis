package ir.atlasgis.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import ir.atlasgis.app.core.BoundingBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Downloads offline basemaps without leaving the app.
 *
 * Two sources are supported:
 *  - a direct `.mbtiles` URL (Google Drive/Dropbox style links need the direct file
 *    URL; redirects are followed),
 *  - a standard `{z}/{x}/{y}` raster tile server for the current map view, packed
 *    into a freshly written MBTiles archive.
 *
 * Everything ends up in the same private folder [MapLibrary] owns, so the layer list,
 * styling and deletion logic are shared with file imports.
 */
class MapDownloader(private val context: Context) {

    private val library = MapLibrary(context)

    data class Progress(
        val downloaded: Long,
        val total: Long,
        val tilesDone: Int = 0,
        val tilesTotal: Int = 0,
        val phase: String = "",
    )

    suspend fun downloadMbTiles(
        url: String,
        name: String?,
        onProgress: (Progress) -> Unit = {},
    ): MapLayer = withContext(Dispatchers.IO) {
        val cleanUrl = normalizeUrl(url)
        val id = AppDatabase.newId()
        val directory = library.mapsDirectory
        val part = File(directory, "$id.part")
        val target = File(directory, "$id.mbtiles")
        val fallbackName = cleanUrl.substringAfterLast('/').substringBefore('?')
            .ifBlank { "map-${System.currentTimeMillis()}.mbtiles" }

        val connection = openConnection(cleanUrl)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("سرور کد $code برگرداند؛ لینک را بررسی کنید")
            if (connection.contentType?.contains("text/html") == true) {
                throw IOException("لینک به یک صفحهٔ وب اشاره می‌کند، نه فایل MBTiles")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
            if (total > 0) {
                val available = directory.usableSpace
                if (available in 1..(total + 8L * 1024 * 1024)) {
                    throw IOException("فضای کافی برای دانلود این نقشه وجود ندارد")
                }
            }
            var copied = 0L
            var lastReported = 0L
            connection.inputStream.use { source ->
                part.outputStream().use { sink ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        ensureActive()
                        val read = source.read(buffer)
                        if (read <= 0) break
                        sink.write(buffer, 0, read)
                        copied += read
                        if (copied - lastReported > 512 * 1024) {
                            lastReported = copied
                            onProgress(
                                Progress(
                                    downloaded = copied,
                                    total = total,
                                    phase = "در حال دریافت فایل نقشه",
                                ),
                            )
                        }
                    }
                    sink.flush()
                }
            }
            if (part.length() == 0L) throw IOException("فایل دانلودشده خالی است")
            onProgress(Progress(part.length(), part.length(), phase = "بررسی فایل نقشه"))
            // Read the metadata before publishing the file: a broken archive must not
            // leave a half-registered layer behind.
            MbtilesReader.read(part)
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            val displayName = name?.trim()?.takeIf { it.isNotEmpty() }
                ?: fallbackName.substringBeforeLast('.').substringBeforeLast(".mbtiles")
            library.adopt(target, displayName, id)
        } catch (error: Throwable) {
            part.delete()
            target.delete()
            if (error is kotlinx.coroutines.CancellationException) throw error
            throw when (error) {
                is IOException -> error
                is IllegalArgumentException -> IOException(error.message ?: "فایل MBTiles معتبر نیست")
                else -> IOException("دانلود نقشه ناموفق بود: ${error.message ?: "خطای ناشناخته"}")
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadTiles(
        template: String,
        name: String,
        bounds: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        onProgress: (Progress) -> Unit = {},
    ): MapLayer = withContext(Dispatchers.IO) {
        val cleanTemplate = normalizeUrl(template)
        if (!cleanTemplate.contains("{z}") || !cleanTemplate.contains("{x}") || !cleanTemplate.contains("{y}")) {
            throw IOException("آدرس کاشی باید شامل {z}، {x} و {y} باشد")
        }
        TileGrid.validate(bounds, minZoom, maxZoom)?.let { throw IOException(it) }

        val ranges = TileGrid.ranges(bounds, minZoom, maxZoom)
        val tiles = ArrayList<Triple<Int, Int, Int>>()
        ranges.forEach { range ->
            for (x in range.minX..range.maxX) {
                for (y in range.minY..range.maxY) {
                    tiles += Triple(range.zoom, x, y)
                }
            }
        }
        if (tiles.isEmpty()) throw IOException("هیچ کاشی‌ای در این محدوده پیدا نشد")

        val id = AppDatabase.newId()
        val directory = library.mapsDirectory
        val part = File(directory, "$id.part")
        val target = File(directory, "$id.mbtiles")
        var database: SQLiteDatabase? = null

        try {
            database = SQLiteDatabase.openOrCreateDatabase(part, null)
            database.execSQL("CREATE TABLE metadata (name TEXT, value TEXT)")
            database.execSQL(
                "CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)",
            )
            database.execSQL("CREATE UNIQUE INDEX tile_index ON tiles (zoom_level, tile_column, tile_row)")

            val total = tiles.size
            var done = 0
            var bytes = 0L
            var format: String? = null
            var firstFailure: String? = null

            // One writer owns the SQLite connection; workers only fetch bytes. This
            // keeps transactions single-threaded while downloads still overlap.
            coroutineScope {
                val work = Channel<Triple<Int, Int, Int>>(capacity = 256)
                val fetched = Channel<Pair<Triple<Int, Int, Int>, ByteArray>>(capacity = 128)
                val workers = List(WORKERS) {
                    launch(Dispatchers.IO) {
                        for (tile in work) {
                            ensureActive()
                            val url = cleanTemplate
                                .replace("{z}", tile.first.toString())
                                .replace("{x}", tile.second.toString())
                                .replace("{y}", tile.third.toString())
                            val data = try {
                                fetchTile(url)
                            } catch (error: Throwable) {
                                if (error is kotlinx.coroutines.CancellationException) throw error
                                if (firstFailure == null) firstFailure = error.message
                                null
                            }
                            if (data != null && data.isNotEmpty()) {
                                val detected = detectFormat(data)
                                if (detected == null) {
                                    if (firstFailure == null) {
                                        firstFailure = "پاسخ سرور تصویر کاشی نیست"
                                    }
                                } else {
                                    if (format == null) format = detected
                                    fetched.send(tile to data)
                                }
                            }
                            done++
                            onProgress(
                                Progress(
                                    downloaded = done.toLong(),
                                    total = total.toLong(),
                                    tilesDone = done,
                                    tilesTotal = total,
                                    phase = "در حال دریافت کاشی‌ها",
                                ),
                            )
                        }
                    }
                }
                val writer = launch(Dispatchers.IO) {
                    var batch = ArrayList<Pair<Triple<Int, Int, Int>, ByteArray>>(BATCH)
                    fun flush() {
                        if (batch.isEmpty()) return
                        val connection = database ?: return
                        connection.beginTransaction()
                        try {
                            batch.forEach { (tile, data) ->
                                val values = ContentValues().apply {
                                    put("zoom_level", tile.first)
                                    put("tile_column", tile.second)
                                    put("tile_row", TileGrid.tmsRow(tile.third, tile.first))
                                    put("tile_data", data)
                                }
                                connection.insertWithOnConflict(
                                    "tiles",
                                    null,
                                    values,
                                    SQLiteDatabase.CONFLICT_REPLACE,
                                )
                            }
                            connection.setTransactionSuccessful()
                        } finally {
                            connection.endTransaction()
                        }
                        bytes += batch.sumOf { it.second.size.toLong() }
                        batch = ArrayList(BATCH)
                    }
                    for (item in fetched) {
                        batch += item
                        if (batch.size >= BATCH) flush()
                    }
                    flush()
                }

                for (tile in tiles) {
                    ensureActive()
                    work.send(tile)
                }
                work.close()
                workers.forEach { it.join() }
                fetched.close()
                writer.join()
            }

            if (bytes == 0L) {
                throw IOException(firstFailure ?: "هیچ کاشی‌ای دانلود نشد؛ آدرس یا دسترسی شبکه را بررسی کنید")
            }
            writeMetadata(
                database = database,
                name = name.trim().ifBlank { "نقشهٔ دانلودشده" },
                format = format ?: "png",
                bounds = bounds,
                minZoom = minZoom,
                maxZoom = maxZoom,
                template = cleanTemplate,
            )
            database.close()
            database = null
            // SQLite journals are named after the temporary file and would be orphaned
            // by the rename below.
            File(part.path + "-journal").delete()
            File(part.path + "-wal").delete()
            File(part.path + "-shm").delete()

            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            onProgress(Progress(0, 0, tilesTotal = total, phase = "ثبت نقشه"))
            library.adopt(
                target,
                name.trim().ifBlank { "نقشهٔ دانلودشده" },
                id,
            )
        } catch (error: Throwable) {
            runCatching { database?.close() }
            part.delete()
            File(part.path + "-journal").delete()
            target.delete()
            if (error is kotlinx.coroutines.CancellationException) throw error
            throw when (error) {
                is IOException -> error
                else -> IOException("دانلود کاشی‌ها ناموفق بود: ${error.message ?: "خطای ناشناخته"}")
            }
        }
    }

    private fun writeMetadata(
        database: SQLiteDatabase,
        name: String,
        format: String,
        bounds: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        template: String,
    ) {
        val entries = listOf(
            "name" to name,
            "format" to format,
            "type" to "basemap",
            "version" to "1",
            "minzoom" to minZoom.toString(),
            "maxzoom" to maxZoom.toString(),
            "bounds" to String.format(
                Locale.US,
                "%.6f,%.6f,%.6f,%.6f",
                bounds.west,
                bounds.south,
                bounds.east,
                bounds.north,
            ),
            "attribution" to runCatching { URL(template).host }.getOrNull().orEmpty(),
        )
        database.beginTransaction()
        try {
            entries.forEach { (key, value) ->
                val values = ContentValues().apply {
                    put("name", key)
                    put("value", value)
                }
                database.insertWithOnConflict("metadata", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    private fun fetchTile(url: String): ByteArray? {
        val connection = openConnection(url)
        try {
            val code = connection.responseCode
            if (code == 204 || code == 404) return null
            if (code !in 200..299) throw IOException("سرور کاشی کد $code برگرداند")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "AtlasGIS/1.0 (offline map downloader)")
            setRequestProperty("Accept", "image/png,image/jpeg,image/webp,*/*")
        }

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw IOException("آدرس خالی است")
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return withScheme
    }

    private fun detectFormat(bytes: ByteArray): String? = when {
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "png"
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
        bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" -> "webp"
        else -> null
    }

    companion object {
        private const val WORKERS = 4
        private const val BATCH = 60

        /** Estimate used by the download sheet before anything is fetched. */
        fun estimate(bounds: BoundingBox, minZoom: Int, maxZoom: Int): Int =
            TileGrid.estimate(bounds, minZoom, maxZoom)
    }
}
