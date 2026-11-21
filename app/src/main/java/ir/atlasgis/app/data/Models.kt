package ir.atlasgis.app.data

import ir.atlasgis.app.core.BoundingBox
import ir.atlasgis.app.core.LatLon

enum class TileFormat { RASTER, VECTOR }

/** One imported MBTiles archive. */
data class MapLayer(
    val id: String,
    val name: String,
    val filePath: String,
    val format: TileFormat,
    val minZoom: Int,
    val maxZoom: Int,
    val bounds: BoundingBox?,
    val tileSize: Int,
    val visible: Boolean,
    val opacity: Float,
    val sortOrder: Int,
    val attribution: String?,
    val vectorLayers: List<String>,
    val fileSizeBytes: Long,
    val addedAt: Long,
    /** Absolute path of a user supplied style.json, applied instead of the auto style. */
    val stylePath: String? = null,
    /** Palette used by the automatic style for vector archives. */
    val stylePreset: MapStylePreset = MapStylePreset.DEFAULT,
) {
    val zoomLabel: String get() = "z$minZoom–$maxZoom"
    val hasCustomStyle: Boolean get() = stylePath != null
}

/** One searchable place read from a vector map archive. */
data class MapPlace(
    val name: String,
    val klass: String,
    val layerName: String,
    val lat: Double,
    val lon: Double,
    val layerId: String,
) {
    val position: LatLon get() = LatLon(lat, lon)
}

enum class WaypointCategory(val key: String) {
    GENERAL("general"),
    WATER("water"),
    FOOD("food"),
    CAMP("camp"),
    HAZARD("hazard"),
    PARKING("parking"),
    VIEW("view"),
    ;

    companion object {
        fun fromKey(key: String?): WaypointCategory =
            entries.firstOrNull { it.key == key } ?: GENERAL
    }
}

data class Waypoint(
    val id: String,
    val name: String,
    val note: String,
    /** Key of a [WaypointCategoryStyle]; categories can be added or removed at runtime. */
    val category: String,
    val lat: Double,
    val lon: Double,
    val elevation: Double?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val position: LatLon get() = LatLon(lat, lon)
}

data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val elevation: Double? = null,
    val timestamp: Long = 0L,
)

data class Track(
    val id: String,
    val name: String,
    val points: List<TrackPoint>,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val startedAt: Long,
    val endedAt: Long,
    val ascentMeters: Double,
    val descentMeters: Double,
    val maxSpeedMps: Double,
    val color: String,
    val isRecording: Boolean,
)

/** Snapshot of a recording session, used for the live HUD. */
data class RecordingState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val trackId: String? = null,
    val name: String = "",
    val startedAt: Long = 0L,
    val distanceMeters: Double = 0.0,
    val movingSeconds: Long = 0L,
    val ascentMeters: Double = 0.0,
    val descentMeters: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val currentSpeedMps: Double = 0.0,
    val pointCount: Int = 0,
)

data class GpsFix(
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float,
    val altitude: Double?,
    val speedMps: Float?,
    val bearing: Double?,
    val satellites: Int,
    val timestamp: Long,
)

enum class MeasureMode { DISTANCE, AREA }
