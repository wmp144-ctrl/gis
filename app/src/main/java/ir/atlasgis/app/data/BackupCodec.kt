package ir.atlasgis.app.data

import ir.atlasgis.app.core.formatDistance
import ir.atlasgis.app.core.formatSpeed
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup of everything the user created: waypoints, tracks, categories. Map
 * archives are intentionally left out — an MBTiles file can be several gigabytes and
 * is re-importable, while these rows are irreplaceable.
 *
 * The format is plain JSON so a backup can be opened, inspected or edited on a PC.
 */
object BackupCodec {

    const val FORMAT = "atlasgis-backup"
    const val VERSION = 1

    data class Snapshot(
        val waypoints: List<Waypoint>,
        val tracks: List<Track>,
        val categories: List<WaypointCategoryStyle>,
        val exportedAt: Long = System.currentTimeMillis(),
    )

    fun encode(snapshot: Snapshot): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", snapshot.exportedAt)

        val waypoints = JSONArray()
        snapshot.waypoints.forEach { waypoint ->
            waypoints.put(
                JSONObject()
                    .put("id", waypoint.id)
                    .put("name", waypoint.name)
                    .put("note", waypoint.note)
                    .put("category", waypoint.category)
                    .put("lat", waypoint.lat)
                    .put("lon", waypoint.lon)
                    .put("elevation", waypoint.elevation ?: JSONObject.NULL)
                    .put("createdAt", waypoint.createdAt)
                    .put("updatedAt", waypoint.updatedAt),
            )
        }

        val tracks = JSONArray()
        snapshot.tracks.forEach { track ->
            tracks.put(
                JSONObject()
                    .put("id", track.id)
                    .put("name", track.name)
                    .put("points", TrackCodec.encode(track.points))
                    .put("distanceMeters", track.distanceMeters)
                    .put("durationSeconds", track.durationSeconds)
                    .put("movingSeconds", track.movingSeconds)
                    .put("startedAt", track.startedAt)
                    .put("endedAt", track.endedAt)
                    .put("ascentMeters", track.ascentMeters)
                    .put("descentMeters", track.descentMeters)
                    .put("maxSpeedMps", track.maxSpeedMps)
                    .put("color", track.color),
            )
        }

        val categories = JSONArray()
        snapshot.categories.forEach { style ->
            categories.put(
                JSONObject()
                    .put("key", style.key)
                    .put("label", style.label)
                    .put("color", style.color)
                    .put("icon", style.icon)
                    .put("visible", style.visible),
            )
        }

        root.put("waypoints", waypoints)
        root.put("tracks", tracks)
        root.put("categories", categories)
        return root.toString(2)
    }

    fun decode(raw: String): Snapshot {
        val root = runCatching { JSONObject(raw) }.getOrElse {
            throw IllegalArgumentException("فایل پشتیبان JSON معتبر نیست")
        }
        if (root.optString("format") != FORMAT) {
            throw IllegalArgumentException("این فایل پشتیبان AtlasGIS نیست")
        }
        val version = root.optInt("version", 0)
        if (version <= 0 || version > VERSION) {
            throw IllegalArgumentException("نسخهٔ این پشتیبان ($version) پشتیبانی نمی‌شود")
        }

        val waypoints = ArrayList<Waypoint>()
        val waypointArray = root.optJSONArray("waypoints") ?: JSONArray()
        for (i in 0 until waypointArray.length()) {
            val item = waypointArray.optJSONObject(i) ?: continue
            val lat = item.optDouble("lat", Double.NaN)
            val lon = item.optDouble("lon", Double.NaN)
            if (!lat.isFinite() || !lon.isFinite()) continue
            waypoints += Waypoint(
                id = item.optString("id").ifBlank { AppDatabase.newId() },
                name = item.optString("name"),
                note = item.optString("note"),
                category = item.optString("category", WaypointCategory.GENERAL.key),
                lat = lat,
                lon = lon,
                elevation = if (item.isNull("elevation")) null else item.optDouble("elevation"),
                createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
            )
        }

        val tracks = ArrayList<Track>()
        val trackArray = root.optJSONArray("tracks") ?: JSONArray()
        for (i in 0 until trackArray.length()) {
            val item = trackArray.optJSONObject(i) ?: continue
            val points = TrackCodec.decode(item.optString("points"))
            if (points.size < 2) continue
            tracks += Track(
                id = item.optString("id").ifBlank { AppDatabase.newId() },
                name = item.optString("name"),
                points = points,
                distanceMeters = item.optDouble("distanceMeters", 0.0),
                durationSeconds = item.optLong("durationSeconds", 0),
                startedAt = item.optLong("startedAt", 0),
                endedAt = item.optLong("endedAt", 0),
                ascentMeters = item.optDouble("ascentMeters", 0.0),
                descentMeters = item.optDouble("descentMeters", 0.0),
                maxSpeedMps = item.optDouble("maxSpeedMps", 0.0),
                color = item.optString("color", "#FF9F45"),
                isRecording = false,
                movingSeconds = item.optLong("movingSeconds", 0),
            )
        }

        val categories = ArrayList<WaypointCategoryStyle>()
        val categoryArray = root.optJSONArray("categories") ?: JSONArray()
        for (i in 0 until categoryArray.length()) {
            val item = categoryArray.optJSONObject(i) ?: continue
            val key = item.optString("key").trim()
            if (key.isEmpty()) continue
            categories += WaypointCategoryStyle(
                key = key,
                label = item.optString("label", key),
                color = item.optInt("color"),
                icon = item.optString("icon", "place"),
                visible = item.optBoolean("visible", true),
            )
        }

        return Snapshot(
            waypoints = waypoints,
            tracks = tracks,
            categories = categories,
            exportedAt = root.optLong("exportedAt", 0),
        )
    }

    /** Human-readable one-line summary used in the restore confirmation. */
    fun summary(snapshot: Snapshot): String = buildString {
        append("${snapshot.waypoints.size} نقطه و ${snapshot.tracks.size} مسیر")
        if (snapshot.categories.isNotEmpty()) append(" و ${snapshot.categories.size} دسته‌بندی")
        snapshot.tracks.firstOrNull()?.let { track ->
            append(" · مسیر «${track.name}» ${formatDistance(track.distanceMeters)}")
            if (track.maxSpeedMps > 0) append(" · بیشینه ${formatSpeed(track.maxSpeedMps)}")
        }
    }

}
