package ir.atlasgis.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Reads and writes [BackupCodec] snapshots through a SAF document URI. */
class BackupManager(private val context: Context) {

    private val db = AppDatabase(context)

    suspend fun writeBackup(uri: Uri, snapshot: BackupCodec.Snapshot): Int =
        withContext(Dispatchers.IO) {
            val text = BackupCodec.encode(snapshot)
            val stream = context.contentResolver.openOutputStream(uri)
                ?: throw IOException("نوشتن فایل پشتیبان ممکن نشد")
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            text.toByteArray(Charsets.UTF_8).size
        }

    suspend fun readBackup(uri: Uri): BackupCodec.Snapshot = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("خواندن فایل پشتیبان ممکن نشد")
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        var total = 0
        stream.use { input ->
            while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                total += read
                if (total > MAX_BYTES) {
                    throw IOException("فایل پشتیبان بیش از حد بزرگ است (حداکثر ۳۲ مگابایت)")
                }
                buffer.write(chunk, 0, read)
            }
        }
        BackupCodec.decode(buffer.toByteArray().toString(Charsets.UTF_8))
    }

    /** Merges a snapshot: rows are matched by id, so restoring twice is harmless. */
    suspend fun apply(snapshot: BackupCodec.Snapshot): BackupCodec.Snapshot = withContext(Dispatchers.IO) {
        snapshot.categories.forEach { db.upsertCategoryStyle(it) }
        snapshot.waypoints.forEach { db.upsertWaypoint(it) }
        snapshot.tracks.forEach { db.upsertTrack(it.copy(isRecording = false)) }
        snapshot
    }

    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "atlasgis-backup-$stamp.json"
    }

    /** Current state of this device, ready to be written to a file. */
    suspend fun snapshot(): BackupCodec.Snapshot = withContext(Dispatchers.IO) {
        BackupCodec.Snapshot(
            waypoints = db.readWaypoints(),
            tracks = db.readTracks().filter { !it.isRecording },
            categories = db.readCategoryStyles(),
            exportedAt = System.currentTimeMillis(),
        )
    }


    private companion object {
        const val MAX_BYTES = 32 * 1024 * 1024
    }
}
