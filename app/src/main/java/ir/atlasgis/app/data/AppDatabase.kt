package ir.atlasgis.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import ir.atlasgis.app.core.BoundingBox
import org.json.JSONArray
import java.util.UUID

/**
 * Small hand-written SQLite layer. Writes are serialised on one lock so that the
 * track auto-save (every few seconds) can never race a user edit.
 */
class AppDatabase(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    private val writeLock = Any()

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE map_layers (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                path TEXT NOT NULL,
                format TEXT NOT NULL,
                min_zoom INTEGER NOT NULL DEFAULT 0,
                max_zoom INTEGER NOT NULL DEFAULT 18,
                bounds_w REAL, bounds_s REAL, bounds_e REAL, bounds_n REAL,
                tile_size INTEGER NOT NULL DEFAULT 256,
                visible INTEGER NOT NULL DEFAULT 1,
                opacity REAL NOT NULL DEFAULT 1.0,
                sort_order INTEGER NOT NULL DEFAULT 0,
                attribution TEXT,
                vector_layers TEXT,
                file_size INTEGER NOT NULL DEFAULT 0,
                added_at INTEGER NOT NULL,
                style_path TEXT,
                style_preset TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE waypoints (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                category TEXT NOT NULL,
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                elevation REAL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_waypoints_created ON waypoints(created_at DESC)")
        db.execSQL(
            """
            CREATE TABLE tracks (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                coordinates TEXT NOT NULL,
                point_count INTEGER NOT NULL DEFAULT 0,
                distance_m REAL NOT NULL DEFAULT 0,
                duration_s INTEGER NOT NULL DEFAULT 0,
                started_at INTEGER NOT NULL,
                ended_at INTEGER NOT NULL,
                ascent_m REAL NOT NULL DEFAULT 0,
                descent_m REAL NOT NULL DEFAULT 0,
                max_speed REAL NOT NULL DEFAULT 0,
                color TEXT NOT NULL,
                recording INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_tracks_started ON tracks(started_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE map_layers ADD COLUMN style_path TEXT")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE map_layers ADD COLUMN style_preset TEXT")
        }
    }

    // ---------------------------------------------------------------- layers

    fun readLayers(): List<MapLayer> = synchronized(writeLock) {
        val result = ArrayList<MapLayer>()
        readableDatabase.query(
            "map_layers", null, null, null, null, null, "sort_order ASC, added_at ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) result.add(cursor.toLayer())
        }
        result
    }

    fun upsertLayer(layer: MapLayer) = synchronized(writeLock) {
        writableDatabase.insertWithOnConflict(
            "map_layers", null, layer.toValues(), SQLiteDatabase.CONFLICT_REPLACE,
        )
        Unit
    }

    fun setLayerVisible(id: String, visible: Boolean) = updateLayerValue(id, "visible", if (visible) 1 else 0)

    fun setLayerOpacity(id: String, opacity: Float) =
        updateLayerValue(id, "opacity", opacity.coerceIn(0.1f, 1.0f).toDouble())

    fun reorderLayers(idsInOrder: List<String>) = synchronized(writeLock) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            idsInOrder.forEachIndexed { index, id ->
                val values = ContentValues().apply { put("sort_order", index) }
                db.update("map_layers", values, "id = ?", arrayOf(id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        Unit
    }

    fun deleteLayer(id: String): MapLayer? {
        val layer = synchronized(writeLock) { layerById(id) }
        synchronized(writeLock) {
            writableDatabase.delete("map_layers", "id = ?", arrayOf(id))
        }
        return layer
    }

    private fun layerById(id: String): MapLayer? =
        readableDatabase.query("map_layers", null, "id = ?", arrayOf(id), null, null, null)
            .use { if (it.moveToFirst()) it.toLayer() else null }

    private fun updateLayerValue(id: String, column: String, value: Any) = synchronized(writeLock) {
        val values = ContentValues().apply {
            when (value) {
                is Int -> put(column, value)
                is Double -> put(column, value)
                is String -> put(column, value)
            }
        }
        writableDatabase.update("map_layers", values, "id = ?", arrayOf(id))
        Unit
    }

    // -------------------------------------------------------------- waypoints

    fun readWaypoints(): List<Waypoint> = synchronized(writeLock) {
        val result = ArrayList<Waypoint>()
        readableDatabase.query(
            "waypoints", null, null, null, null, null, "created_at DESC",
        ).use { cursor ->
            while (cursor.moveToNext()) result.add(cursor.toWaypoint())
        }
        result
    }

    fun upsertWaypoint(waypoint: Waypoint) = synchronized(writeLock) {
        writableDatabase.insertWithOnConflict(
            "waypoints", null, waypoint.toValues(), SQLiteDatabase.CONFLICT_REPLACE,
        )
        Unit
    }

    fun deleteWaypoint(id: String) = synchronized(writeLock) {
        writableDatabase.delete("waypoints", "id = ?", arrayOf(id))
        Unit
    }

    // ----------------------------------------------------------------- track

    fun readTracks(): List<Track> = synchronized(writeLock) {
        val result = ArrayList<Track>()
        readableDatabase.query(
            "tracks", null, null, null, null, null, "started_at DESC",
        ).use { cursor ->
            while (cursor.moveToNext()) result.add(cursor.toTrack())
        }
        result
    }

    fun trackById(id: String): Track? = synchronized(writeLock) {
        readableDatabase.query("tracks", null, "id = ?", arrayOf(id), null, null, null)
            .use { if (it.moveToFirst()) it.toTrack() else null }
    }

    fun upsertTrack(track: Track) = synchronized(writeLock) {
        writableDatabase.insertWithOnConflict(
            "tracks", null, track.toValues(), SQLiteDatabase.CONFLICT_REPLACE,
        )
        Unit
    }

    fun deleteTrack(id: String) = synchronized(writeLock) {
        writableDatabase.delete("tracks", "id = ?", arrayOf(id))
        Unit
    }

    /** Marks tracks left in "recording" state by a crash/kill as finished. */
    fun closeDanglingRecordings() = synchronized(writeLock) {
        val values = ContentValues().apply { put("recording", 0) }
        writableDatabase.update("tracks", values, "recording = 1", null)
        Unit
    }

    // --------------------------------------------------------------- mapping

    private fun MapLayer.toValues(): ContentValues = ContentValues().apply {
        put("id", id)
        put("name", name)
        put("path", filePath)
        put("format", format.name)
        put("min_zoom", minZoom)
        put("max_zoom", maxZoom)
        put("bounds_w", bounds?.west)
        put("bounds_s", bounds?.south)
        put("bounds_e", bounds?.east)
        put("bounds_n", bounds?.north)
        put("tile_size", tileSize)
        put("visible", if (visible) 1 else 0)
        put("opacity", opacity.toDouble())
        put("sort_order", sortOrder)
        put("attribution", attribution)
        put("vector_layers", JSONArray(vectorLayers).toString())
        put("file_size", fileSizeBytes)
        put("added_at", addedAt)
        put("style_path", stylePath)
        put("style_preset", stylePreset.key)
    }

    private fun Cursor.toLayer(): MapLayer {
        val west = getColumnIndexOrThrow("bounds_w")
        val bounds = if (isNull(west)) {
            null
        } else {
            BoundingBox(
                getDouble(getColumnIndexOrThrow("bounds_s")),
                getDouble(west),
                getDouble(getColumnIndexOrThrow("bounds_n")),
                getDouble(getColumnIndexOrThrow("bounds_e")),
            )
        }
        return MapLayer(
            id = getString(getColumnIndexOrThrow("id")),
            name = getString(getColumnIndexOrThrow("name")),
            filePath = getString(getColumnIndexOrThrow("path")),
            format = runCatching {
                TileFormat.valueOf(getString(getColumnIndexOrThrow("format")))
            }.getOrDefault(TileFormat.RASTER),
            minZoom = getInt(getColumnIndexOrThrow("min_zoom")),
            maxZoom = getInt(getColumnIndexOrThrow("max_zoom")),
            bounds = bounds,
            tileSize = getInt(getColumnIndexOrThrow("tile_size")),
            visible = getInt(getColumnIndexOrThrow("visible")) == 1,
            opacity = getFloat(getColumnIndexOrThrow("opacity")),
            sortOrder = getInt(getColumnIndexOrThrow("sort_order")),
            attribution = getString(getColumnIndexOrThrow("attribution")),
            vectorLayers = parseStringArray(getString(getColumnIndexOrThrow("vector_layers"))),
            fileSizeBytes = getLong(getColumnIndexOrThrow("file_size")),
            addedAt = getLong(getColumnIndexOrThrow("added_at")),
            stylePath = getColumnIndex("style_path")
                .takeIf { it >= 0 && !isNull(it) }
                ?.let { getString(it) },
            stylePreset = getColumnIndex("style_preset")
                .takeIf { it >= 0 && !isNull(it) }
                ?.let { MapStylePreset.fromKey(getString(it)) }
                ?: MapStylePreset.DEFAULT,
        )
    }

    private fun Waypoint.toValues(): ContentValues = ContentValues().apply {
        put("id", id)
        put("name", name)
        put("note", note)
        put("category", category.key)
        put("lat", lat)
        put("lon", lon)
        put("elevation", elevation)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    private fun Cursor.toWaypoint(): Waypoint {
        val elevationIndex = getColumnIndexOrThrow("elevation")
        return Waypoint(
            id = getString(getColumnIndexOrThrow("id")),
            name = getString(getColumnIndexOrThrow("name")),
            note = getString(getColumnIndexOrThrow("note")),
            category = WaypointCategory.fromKey(getString(getColumnIndexOrThrow("category"))),
            lat = getDouble(getColumnIndexOrThrow("lat")),
            lon = getDouble(getColumnIndexOrThrow("lon")),
            elevation = if (isNull(elevationIndex)) null else getDouble(elevationIndex),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
        )
    }

    private fun Track.toValues(): ContentValues = ContentValues().apply {
        put("id", id)
        put("name", name)
        put("coordinates", TrackCodec.encode(points))
        put("point_count", points.size)
        put("distance_m", distanceMeters)
        put("duration_s", durationSeconds)
        put("started_at", startedAt)
        put("ended_at", endedAt)
        put("ascent_m", ascentMeters)
        put("descent_m", descentMeters)
        put("max_speed", maxSpeedMps)
        put("color", color)
        put("recording", if (isRecording) 1 else 0)
    }

    private fun Cursor.toTrack(): Track = Track(
        id = getString(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        points = TrackCodec.decode(getString(getColumnIndexOrThrow("coordinates"))),
        distanceMeters = getDouble(getColumnIndexOrThrow("distance_m")),
        durationSeconds = getLong(getColumnIndexOrThrow("duration_s")),
        startedAt = getLong(getColumnIndexOrThrow("started_at")),
        endedAt = getLong(getColumnIndexOrThrow("ended_at")),
        ascentMeters = getDouble(getColumnIndexOrThrow("ascent_m")),
        descentMeters = getDouble(getColumnIndexOrThrow("descent_m")),
        maxSpeedMps = getDouble(getColumnIndexOrThrow("max_speed")),
        color = getString(getColumnIndexOrThrow("color")),
        isRecording = getInt(getColumnIndexOrThrow("recording")) == 1,
    )

    companion object {
        private const val NAME = "atlasgis.db"
        private const val VERSION = 3

        fun newId(): String = UUID.randomUUID().toString()

        private fun parseStringArray(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (i in 0 until array.length()) add(array.optString(i))
                }
            }.getOrDefault(emptyList())
        }
    }
}

/** Compact, allocation-friendly track point serialisation: `lat,lon,ele,t;...`. */
object TrackCodec {

    fun encode(points: List<TrackPoint>): String {
        if (points.isEmpty()) return ""
        val sb = StringBuilder(points.size * 24)
        points.forEachIndexed { index, point ->
            if (index > 0) sb.append(';')
            sb.append(point.lat).append(',')
                .append(point.lon).append(',')
                .append(point.elevation ?: "").append(',')
                .append(point.timestamp)
        }
        return sb.toString()
    }

    fun decode(raw: String): List<TrackPoint> {
        if (raw.isBlank()) return emptyList()
        val parts = raw.split(';')
        val points = ArrayList<TrackPoint>(parts.size)
        for (part in parts) {
            val fields = part.split(',')
            if (fields.size < 2) continue
            val lat = fields[0].toDoubleOrNull() ?: continue
            val lon = fields[1].toDoubleOrNull() ?: continue
            val elevation = fields.getOrNull(2)?.toDoubleOrNull()
            val timestamp = fields.getOrNull(3)?.toLongOrNull() ?: 0L
            points.add(TrackPoint(lat, lon, elevation, timestamp))
        }
        return points
    }
}
