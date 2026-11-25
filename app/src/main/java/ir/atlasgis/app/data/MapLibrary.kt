package ir.atlasgis.app.data

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Owns the app-private MBTiles folder plus the layer rows in SQLite. */
class MapLibrary(private val context: Context) {

    private val db = AppDatabase(context)

    init {
        // If the process died during an import, the copied `.part` archive is left
        // behind and nothing will ever reference it again. Startup is the only moment
        // when no import can be running, so the space is reclaimed safely here.
        mapsDirectory.listFiles { file -> file.isFile && file.name.endsWith(PART_SUFFIX) }
            ?.forEach { runCatching { it.delete() } }
    }

    val mapsDirectory: File
        get() = File(context.filesDir, "maps").apply { if (!exists()) mkdirs() }

    /** Private folder holding the imported `style.json` templates. */
    val stylesDirectory: File
        get() = File(context.filesDir, "styles").apply { if (!exists()) mkdirs() }

    suspend fun layers(): List<MapLayer> = withContext(Dispatchers.IO) { db.readLayers() }

    suspend fun import(
        uri: Uri,
        onProgress: (Float) -> Unit = {},
    ): MapLayer = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri) ?: "map_${System.currentTimeMillis()}.mbtiles"
        val totalBytes = querySize(uri)
        val id = AppDatabase.newId()
        val directory = mapsDirectory
        val temp = File(directory, "$id$PART_SUFFIX")
        val target = File(directory, "$id.mbtiles")

        if (totalBytes > 0) {
            val available = StatFs(directory.absolutePath).availableBytes
            if (available < totalBytes + 8L * 1024 * 1024) {
                throw IOException("فضای کافی برای افزودن این نقشه وجود ندارد")
            }
        }

        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("دسترسی به فایل انتخاب‌شده ممکن نشد")
            input.use { source ->
                temp.outputStream().use { sink ->
                    val buffer = ByteArray(256 * 1024)
                    var copied = 0L
                    var lastReported = -1
                    while (true) {
                        val read = source.read(buffer)
                        if (read <= 0) break
                        sink.write(buffer, 0, read)
                        copied += read
                        if (totalBytes > 0) {
                            val percent = ((copied * 100) / totalBytes).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                onProgress((percent / 100f).coerceIn(0f, 1f))
                            }
                        }
                    }
                    sink.flush()
                }
            }
            if (temp.length() == 0L) throw IOException("فایل انتخاب‌شده خالی است")

            // Validate before publishing the file under its final name.
            MbtilesReader.read(temp)

            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            onProgress(1f)
            adopt(target, displayName.substringBeforeLast('.'), id)
        } catch (error: Throwable) {
            temp.delete()
            target.delete()
            throw when (error) {
                is IOException -> error
                is IllegalArgumentException -> IOException(error.message ?: "فایل MBTiles معتبر نیست")
                else -> IOException("افزودن نقشه ناموفق بود: ${error.message ?: "خطای ناشناخته"}")
            }
        }
    }

    /**
     * Registers an MBTiles file that already lives in [mapsDirectory]. File imports and
     * in-app downloads both end here, so a downloaded map behaves exactly like an
     * imported one (ordering, styling, deletion).
     */
    suspend fun adopt(file: File, displayName: String, id: String = AppDatabase.newId()): MapLayer =
        withContext(Dispatchers.IO) {
            val info = MbtilesReader.read(file)
            val existing = db.readLayers()
            val layer = MapLayer(
                id = id,
                name = (info.name ?: displayName).trim().ifBlank { "نقشه بدون نام" },
                filePath = file.absolutePath,
                format = info.format,
                minZoom = info.minZoom,
                maxZoom = info.maxZoom,
                bounds = info.bounds,
                tileSize = info.tileSize,
                visible = true,
                opacity = 1f,
                sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                attribution = info.attribution,
                vectorLayers = info.vectorLayers,
                fileSizeBytes = file.length(),
                addedAt = System.currentTimeMillis(),
            )
            db.upsertLayer(layer)
            layer
        }

    suspend fun setVisible(layer: MapLayer, visible: Boolean) = withContext(Dispatchers.IO) {
        db.setLayerVisible(layer.id, visible)
    }

    suspend fun setOpacity(layer: MapLayer, opacity: Float) = withContext(Dispatchers.IO) {
        db.setLayerOpacity(layer.id, opacity)
    }

    suspend fun setStylePreset(layer: MapLayer, preset: MapStylePreset) = withContext(Dispatchers.IO) {
        if (layer.stylePreset != preset) db.upsertLayer(layer.copy(stylePreset = preset))
    }

    suspend fun rename(layer: MapLayer, name: String) = withContext(Dispatchers.IO) {
        val trimmed = name.trim().take(80)
        if (trimmed.isNotEmpty()) db.upsertLayer(layer.copy(name = trimmed))
    }

    suspend fun move(layer: MapLayer, up: Boolean): List<MapLayer> = withContext(Dispatchers.IO) {
        val ordered = db.readLayers().sortedBy { it.sortOrder }.toMutableList()
        val index = ordered.indexOfFirst { it.id == layer.id }
        if (index < 0) return@withContext ordered
        val swapWith = if (up) index + 1 else index - 1
        if (swapWith !in ordered.indices) return@withContext ordered
        val moved = ordered.removeAt(index)
        ordered.add(swapWith, moved)
        val ids = ordered.map { it.id }
        db.reorderLayers(ids)
        db.readLayers()
    }

    /**
     * Copies a user supplied style.json next to the app private maps and attaches it
     * to [layer]. The template is validated first, so a broken file never reaches the
     * map (the previous style stays in place).
     */
    suspend fun importStyle(layer: MapLayer, uri: Uri): MapLayer = withContext(Dispatchers.IO) {
        if (layer.format != TileFormat.VECTOR) {
            throw IOException("استایل سفارشی فقط برای نقشه‌های برداری قابل استفاده است")
        }
        val raw = try {
            context.contentResolver.openInputStream(uri)
                ?.use { stream -> readLimited(stream) }
                ?: throw IOException("دسترسی به فایل استایل ممکن نشد")
        } catch (error: Throwable) {
            throw when (error) {
                is IOException -> error
                else -> IOException("خواندن فایل استایل ناموفق بود: ${error.message ?: "خطای ناشناخته"}")
            }
        }
        try {
            CustomStyleTemplate.validate(raw)
        } catch (error: IllegalArgumentException) {
            throw IOException(error.message ?: "فایل style.json معتبر نیست")
        }

        val target = File(stylesDirectory, "${layer.id}.json")
        val temp = File(stylesDirectory, "${layer.id}.part")
        try {
            temp.writeText(raw)
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } catch (error: IOException) {
            temp.delete()
            throw IOException("ذخیرهٔ استایل ناموفق بود: ${error.message ?: "خطای ناشناخته"}")
        }

        layer.stylePath?.let { previous -> if (previous != target.absolutePath) File(previous).delete() }
        val updated = layer.copy(stylePath = target.absolutePath)
        db.upsertLayer(updated)
        updated
    }

    /** Removes the custom template so the layer falls back to the automatic style. */
    suspend fun clearStyle(layer: MapLayer): MapLayer = withContext(Dispatchers.IO) {
        layer.stylePath?.let { path -> File(path).delete() }
        val updated = layer.copy(stylePath = null)
        db.upsertLayer(updated)
        updated
    }

    private fun readLimited(stream: java.io.InputStream): String {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(chunk)
            if (read <= 0) break
            total += read
            if (total > CustomStyleTemplate.MAX_BYTES) {
                throw IOException("فایل استایل بیش از حد بزرگ است (حداکثر ۲ مگابایت)")
            }
            buffer.write(chunk, 0, read)
        }
        val text = buffer.toString(Charsets.UTF_8.name())
        if (text.isBlank()) throw IOException("فایل استایل خالی است")
        return text
    }

    suspend fun delete(layer: MapLayer) = withContext(Dispatchers.IO) {
        db.deleteLayer(layer.id)
        layer.stylePath?.let { path -> File(path).delete() }
        val file = File(layer.filePath)
        file.delete()
        File(layer.filePath + "-journal").delete()
        File(layer.filePath + "-wal").delete()
        File(layer.filePath + "-shm").delete()
        Unit
    }

    suspend fun activeLayerFor(point: ir.atlasgis.app.core.LatLon): MapLayer? =
        withContext(Dispatchers.IO) {
            db.readLayers()
                .sortedByDescending { it.sortOrder }
                .firstOrNull { layer ->
                    layer.visible && (layer.bounds?.contains(point) ?: true)
                }
        }

    private companion object {
        const val PART_SUFFIX = ".part"
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    private fun querySize(uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                cursor.getLong(index)
            } else {
                -1L
            }
        } ?: -1L
    }.getOrDefault(-1L)
}
