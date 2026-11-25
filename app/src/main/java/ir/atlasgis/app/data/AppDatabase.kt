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
                moving_s INTEGER NOT NULL DEFAULT 0,
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
        createPlacesTable(db)
        db.execSQL(
            """
            CREATE TABLE waypoint_categories (
                key TEXT PRIMARY KEY NOT NULL,
                label TEXT NOT NULL,
                color INTEGER NOT NULL,
                icon TEXT NOT NULL,
                visible INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent(),
        )
        seedCategoryStyles(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE map_layers ADD COLUMN style_path TEXT")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE map_layers ADD COLUMN style_preset TEXT")
        }
        if (oldVersion < 4) {
            // Created with `visible` already: the seeding right below writes it, so
            // building the v4 table without the column made the next insert fail.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS waypoint_categories (
                    key TEXT PRIMARY KEY NOT NULL,
                    label TEXT NOT NULL,
                    color INTEGER NOT NULL,
                    icon TEXT NOT NULL,
                    visible INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent(),
            )
            seedCategoryStyles(db)
        }
        if (oldVersion == 4) {
            // Only databases created by v4 still need the column; newer tables above
            // already have it, and re-adding it would abort the upgrade.
            db.execSQL(
                "ALTER TABLE waypoint_categories ADD COLUMN visible INTEGER NOT NULL DEFAULT 1",
            )
        }
        if (oldVersion < 6) {
            createPlacesTable(db)
        }
        if (oldVersion in 6 until 7) {
            // The index now also collects the POIs around the map view, so the same
            // place can be inserted twice: drop the old duplicates and enforce one
            // row per place from now on.
            db.execSQL(
                "DELETE FROM map_places WHERE id NOT IN " +
                    "(SELECT min(id) FROM map_places GROUP BY layer_id, name_norm, lat, lon)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_places_unique " +
                    "ON map_places(layer_id, name_norm, lat, lon)",
            )
        }
        if (oldVersion < 9) {
            // Total time and moving time are now stored separately so a paused lunch
            // break is visible instead of disappearing into the duration.
            db.execSQL("ALTER TABLE tracks ADD COLUMN moving_s INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE tracks SET moving_s = duration_s WHERE moving_s = 0")
        }
        if (oldVersion < 8) {
            // Search folding now also unifies آ/أ/إ with ا. Rows indexed by an older
            // build keep the old spelling, so clear the index and let the next search
            // rebuild it with the new normalisation.
            db.execSQL("DELETE FROM map_places")
            db.execSQL("DELETE FROM map_place_index")
        }
    }

    /** Searchable place index for vector archives; see [PlaceIndexer]. */
    private fun createPlacesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS map_places (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                layer_id TEXT NOT NULL,
                name TEXT NOT NULL,
                name_norm TEXT NOT NULL,
                klass TEXT NOT NULL DEFAULT '',
                layer_name TEXT NOT NULL DEFAULT '',
                lat REAL NOT NULL,
                lon REAL NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_places_norm ON map_places(name_norm)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_places_layer ON map_places(layer_id)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_places_unique " +
                "ON map_places(layer_id, name_norm, lat, lon)",
        )
        // Marker so an archive with no naming features is not re-indexed on every
        // search: an empty result is a valid, complete result.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS map_place_index (
                layer_id TEXT PRIMARY KEY NOT NULL,
                built_at INTEGER NOT NULL,
                place_count INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }

    /** Missing rows only: an upgraded database keeps the user's own colours/icons. */
    private fun seedCategoryStyles(db: SQLiteDatabase) {
        WaypointCategoryStyle.defaults().forEach { style ->
            db.insertWithOnConflict(
                "waypoint_categories",
                null,
                style.toValues(),
                SQLiteDatabase.CONFLICT_IGNORE,
            )
        }
    }

    fun readCategoryStyles(): List<WaypointCategoryStyle> = synchronized(writeLock) {
        val rows = LinkedHashMap<String, WaypointCategoryStyle>()
        readableDatabase.query(
            "waypoint_categories", null, null, null, null, null, "rowid ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val visibleIndex = cursor.getColumnIndex("visible")
                val style = WaypointCategoryStyle(
                    key = cursor.getString(cursor.getColumnIndexOrThrow("key")),
                    label = cursor.getString(cursor.getColumnIndexOrThrow("label")),
                    color = cursor.getInt(cursor.getColumnIndexOrThrow("color")),
                    icon = cursor.getString(cursor.getColumnIndexOrThrow("icon")),
                    visible = visibleIndex < 0 || cursor.getInt(visibleIndex) == 1,
                )
                rows[style.key] = style
            }
        }
        // Include defaults a previous build never seeded, but never drop user categories.
        val ordered = ArrayList<WaypointCategoryStyle>(rows.size + WaypointCategoryStyle.defaults().size)
        WaypointCategoryStyle.defaults().forEach { default -> ordered.add(rows.remove(default.key) ?: default) }
        ordered.addAll(rows.values)
        ordered
    }

    fun upsertCategoryStyle(style: WaypointCategoryStyle) = synchronized(writeLock) {
        writableDatabase.insertWithOnConflict(
            "waypoint_categories", null, style.toValues(), SQLiteDatabase.CONFLICT_REPLACE,
        )
        Unit
    }

    /** Removes a custom category and moves its points to the general category. */
    fun deleteCategoryStyle(key: String) = synchronized(writeLock) {
        // Built-in categories are re-seeded by readCategoryStyles(), so a delete
        // would silently come back after the next launch. Keep their rows intact.
        if (WaypointCategoryStyle.isBuiltIn(key)) return@synchronized
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("waypoint_categories", "key = ?", arrayOf(key))
            val values = ContentValues().apply { put("category", WaypointCategory.GENERAL.key) }
            db.update("waypoints", values, "category = ?", arrayOf(key))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        Unit
    }

    private fun WaypointCategoryStyle.toValues(): ContentValues = ContentValues().apply {
        put("key", key)
        put("label", label)
        put("color", color)
        put("icon", icon)
        put("visible", if (visible) 1 else 0)
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
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.delete("map_places", "layer_id = ?", arrayOf(id))
                db.delete("map_place_index", "layer_id = ?", arrayOf(id))
                db.delete("map_layers", "id = ?", arrayOf(id))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        return layer
    }

    // ------------------------------------------------------------ place index

    fun isPlacesIndexed(layerId: String): Boolean = synchronized(writeLock) {
        readableDatabase.rawQuery(
            "SELECT 1 FROM map_place_index WHERE layer_id = ? LIMIT 1",
            arrayOf(layerId),
        ).use { it.moveToFirst() }
    }

    fun markPlacesIndexed(layerId: String, placeCount: Int) = synchronized(writeLock) {
        val values = ContentValues().apply {
            put("layer_id", layerId)
            put("built_at", System.currentTimeMillis())
            put("place_count", placeCount)
        }
        writableDatabase.insertWithOnConflict(
            "map_place_index", null, values, SQLiteDatabase.CONFLICT_REPLACE,
        )
        Unit
    }

    fun deletePlacesForLayer(layerId: String) = synchronized(writeLock) {
        writableDatabase.delete("map_places", "layer_id = ?", arrayOf(layerId))
        Unit
    }

    /** Inserts one batch inside its own transaction; returns how many rows were written. */
    fun insertPlaces(layerId: String, rows: List<MapPlace>): Int {
        if (rows.isEmpty()) return 0
        return synchronized(writeLock) {
            val db = writableDatabase
            var inserted = 0
            db.beginTransaction()
            try {
                val statement = db.compileStatement(
                    "INSERT OR IGNORE INTO map_places" +
                        "(layer_id, name, name_norm, klass, layer_name, lat, lon) " +
                        "VALUES(?, ?, ?, ?, ?, ?, ?)",
                )
                rows.forEach { row ->
                    statement.clearBindings()
                    statement.bindString(1, layerId)
                    statement.bindString(2, row.name)
                    statement.bindString(3, PlaceIndexer.normalize(row.name))
                    statement.bindString(4, row.klass)
                    statement.bindString(5, row.layerName)
                    statement.bindDouble(6, row.lat)
                    statement.bindDouble(7, row.lon)
                    if (statement.executeInsert() != -1L) inserted++
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            inserted
        }
    }

    /** Prefix matches first, then the shortest names: what a search field expects. */
    fun searchPlaces(query: String, limit: Int = 40): List<MapPlace> = synchronized(writeLock) {
        val needle = PlaceIndexer.normalize(query)
        if (needle.length < 2) return emptyList()
        val match = "%${PlaceIndexer.escapeLike(needle)}%"
        val prefix = "${PlaceIndexer.escapeLike(needle)}%"
        // The user may search for a place type ("بیمارستان", "نانوایی") instead of a
        // name. Those labels map to several raw tag values, which are matched against
        // the indexed `klass`; names still rank above type-only matches.
        val kinds = PlaceIndexer.kindValuesFor(needle)
        val kindClause = if (kinds.isEmpty()) {
            ""
        } else {
            " OR lower(klass) IN (${kinds.joinToString(",") { "?" }})"
        }
        val args = ArrayList<String>(kinds.size + 4)
        args += match
        args += match
        kinds.forEach { args += it.lowercase() }
        args += prefix
        args += limit.toString()
        val result = ArrayList<MapPlace>(limit)
        readableDatabase.rawQuery(
            "SELECT name, klass, layer_name, lat, lon, layer_id FROM map_places " +
                "WHERE name_norm LIKE ? ESCAPE '\\' OR lower(klass) LIKE ? ESCAPE '\\'$kindClause " +
                "ORDER BY CASE WHEN name_norm LIKE ? ESCAPE '\\' THEN 0 ELSE 1 END, " +
                "length(name_norm), name LIMIT ?",
            args.toTypedArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    MapPlace(
                        name = cursor.getString(0),
                        klass = cursor.getString(1),
                        layerName = cursor.getString(2),
                        lat = cursor.getDouble(3),
                        lon = cursor.getDouble(4),
                        layerId = cursor.getString(5),
                    ),
                )
            }
        }
        result
    }

    fun placeCount(): Long = synchronized(writeLock) {
        readableDatabase.rawQuery("SELECT count(*) FROM map_places", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
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
        put("category", category)
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
            category = getString(getColumnIndexOrThrow("category")),
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
        put("moving_s", movingSeconds)
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
        movingSeconds = getColumnIndex("moving_s")
            .takeIf { it >= 0 }
            ?.let { getLong(it) }
            ?: 0L,
    )

    companion object {
        private const val NAME = "atlasgis.db"
        private const val VERSION = 9

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
