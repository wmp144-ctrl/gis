package ir.atlasgis.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import ir.atlasgis.app.core.formatCoordinate
import ir.atlasgis.app.core.CoordinateFormat
import java.io.File

/**
 * Shares waypoints and tracks through the system share sheet.
 *
 * Text goes straight into chat apps (a `geo:` link opens other map apps); files are
 * written to a FileProvider-backed cache folder so no storage permission is needed.
 */
object ShareHelper {

    fun shareWaypoint(context: Context, waypoint: Waypoint) {
        val coordinate = formatCoordinate(waypoint.lat, waypoint.lon, CoordinateFormat.DECIMAL)
        val text = buildString {
            append(waypoint.name.ifBlank { "نقطه" })
            append("\n$coordinate")
            append("\ngeo:${waypoint.lat},${waypoint.lon}?q=${waypoint.lat},${waypoint.lon}")
            if (waypoint.note.isNotBlank()) append("\n${waypoint.note}")
            append("\n— AtlasGIS")
        }
        shareText(context, waypoint.name.ifBlank { "نقطه AtlasGIS" }, text)
    }

    fun shareText(context: Context, subject: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        launchChooser(context, intent, "اشتراک‌گذاری")
    }

    fun shareFile(context: Context, fileName: String, mimeType: String, content: String) {
        val directory = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(directory, fileName)
        file.writeText(content, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launchChooser(context, intent, "اشتراک‌گذاری فایل")
    }

    private fun launchChooser(context: Context, intent: Intent, title: String) {
        val chooser = Intent.createChooser(intent, title).apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }
}
