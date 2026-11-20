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

    val mapsDirectory: File
        get() = File(context.filesDir, "maps").apply { if (!exists()) mkdirs() }

    suspend fun layers(): List<MapLayer> = withContext(Dispatchers.IO) { db.readLayers() }

    suspend fun import(
        uri: Uri,
        onProgress: (Float) -> Unit = {},
    ): MapLayer = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri) ?: "map_${System.currentTimeMillis()}.mbtiles"
        val totalBytes = querySize(uri)
        val id = AppDatabase.newId()
        val directory = mapsDirectory
        val temp = File(directory, "$id.part")
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

            val info = MbtilesReader.read(temp)

            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }

            val existing = db.readLayers()
            val layer = MapLayer(
                id = id,
                name = (info.name ?: displayName.substringBeforeLast('.')).trim()
                    .ifBlank { "نقشه بدون نام" },
                filePath = target.absolutePath,
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
                fileSizeBytes = target.length(),
                addedAt = System.currentTimeMillis(),
            )
            db.upsertLayer(layer)
            onProgress(1f)
            layer
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

    suspend fun setVisible(layer: MapLayer, visible: Boolean) = withContext(Dispatchers.IO) {
        db.setLayerVisible(layer.id, visible)
    }

    suspend fun setOpacity(layer: MapLayer, opacity: Float) = withContext(Dispatchers.IO) {
        db.setLayerOpacity(layer.id, opacity)
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

    suspend fun delete(layer: MapLayer) = withContext(Dispatchers.IO) {
        db.deleteLayer(layer.id)
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
