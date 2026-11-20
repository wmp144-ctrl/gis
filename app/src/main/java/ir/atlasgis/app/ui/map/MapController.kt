package ir.atlasgis.app.ui.map

import android.content.Context
import android.graphics.PointF
import ir.atlasgis.app.core.BoundingBox
import ir.atlasgis.app.core.LatLon
import ir.atlasgis.app.data.MapStyleBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource

data class CameraInfo(
    val center: LatLon,
    val zoom: Double,
    val bearing: Float,
    val tilt: Float,
    val metersPerPixel: Double,
    val bounds: BoundingBox?,
)

/**
 * Thin, defensively-guarded facade over MapLibre. All geometry updates go through
 * `setGeoJson` on sources that already exist in the style, so no layer is ever
 * re-created while the user pans or records.
 */
class MapController(@Suppress("unused") private val appContext: Context) {

    private var mapRef: MapLibreMap? = null

    private val _camera = MutableStateFlow<CameraInfo?>(null)
    val camera: StateFlow<CameraInfo?> = _camera.asStateFlow()

    var onTap: ((LatLon) -> Unit)? = null
    var onLongPress: ((LatLon) -> Unit)? = null
    var onMapReady: (() -> Unit)? = null
    var onStyleReady: (() -> Unit)? = null
    var onCameraIdle: ((CameraInfo) -> Unit)? = null

    private var lastCameraEmit = 0L

    /** Camera used for the very first frame, filled in from saved settings by the ViewModel. */
    var initialCenter: LatLon = LatLon(35.6892, 51.3890)
    var initialZoom: Double = 10.0
    var initialBearing: Float = 0f

    val isReady: Boolean get() = mapRef != null

    fun attachCurrent(map: MapLibreMap) = attach(map, initialCenter, initialZoom, initialBearing)

    fun attach(map: MapLibreMap, initialCenter: LatLon, initialZoom: Double, initialBearing: Float) {
        mapRef = map
        map.setTileCacheEnabled(true)
        map.setPrefetchesTiles(true)
        map.setMinZoomPreference(1.0)
        map.setMaxZoomPreference(22.0)
        map.uiSettings.apply {
            isCompassEnabled = false
            isLogoEnabled = false
            isAttributionEnabled = false
            isRotateGesturesEnabled = true
            isTiltGesturesEnabled = true
            isZoomGesturesEnabled = true
            isScrollGesturesEnabled = true
            isDoubleTapGesturesEnabled = true
            isQuickZoomGesturesEnabled = true
            setFlingVelocityAnimationEnabled(true)
        }
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(initialCenter.lat, initialCenter.lon))
                    .zoom(initialZoom)
                    .bearing(initialBearing.toDouble())
                    .tilt(0.0)
                    .build(),
            ),
        )

        map.addOnMapClickListener { latLng ->
            onTap?.invoke(LatLon(latLng.latitude, latLng.longitude))
            true
        }
        map.addOnMapLongClickListener { latLng ->
            onLongPress?.invoke(LatLon(latLng.latitude, latLng.longitude))
            true
        }
        map.addOnCameraMoveListener { emitCamera(false) }
        map.addOnCameraIdleListener {
            emitCamera(true)
            _camera.value?.let { onCameraIdle?.invoke(it) }
        }
        onMapReady?.invoke()
        emitCamera(true)
    }

    fun detach() {
        mapRef = null
    }

    // ------------------------------------------------------------------ style

    fun loadStyle(styleJson: String) {
        val map = mapRef ?: return
        val builder = Style.Builder().fromJson(styleJson)
        map.setStyle(builder) { _: Style ->
            emitCamera(true)
            onStyleReady?.invoke()
        }
    }

    fun updateSource(sourceId: String, geoJson: String) {
        val style = mapRef?.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(sourceId) ?: return
        runCatching { source.setGeoJson(geoJson) }
    }

    fun pushOverlays(
        waypointsJson: String,
        measureJson: String,
        tracksJson: String,
        activeTrackJson: String,
        gpsJson: String,
        accuracyJson: String,
    ) {
        updateSource(MapStyleBuilder.SRC_WAYPOINTS, waypointsJson)
        updateSource(MapStyleBuilder.SRC_MEASURE, measureJson)
        updateSource(MapStyleBuilder.SRC_TRACKS, tracksJson)
        updateSource(MapStyleBuilder.SRC_ACTIVE_TRACK, activeTrackJson)
        updateSource(MapStyleBuilder.SRC_GPS, gpsJson)
        updateSource(MapStyleBuilder.SRC_GPS_ACCURACY, accuracyJson)
    }

    // ----------------------------------------------------------------- camera

    fun flyTo(point: LatLon, zoom: Double? = null, animate: Boolean = true) {
        val map = mapRef ?: return
        val targetZoom = (zoom ?: map.cameraPosition.zoom).coerceIn(1.0, 22.0)
        val update = CameraUpdateFactory.newLatLngZoom(LatLng(point.lat, point.lon), targetZoom)
        if (animate) map.animateCamera(update, 650) else map.moveCamera(update)
    }

    /** Immediate re-centre used by follow mode (no animation queue). */
    fun followTo(point: LatLon, zoom: Double) {
        val map = mapRef ?: return
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(point.lat, point.lon),
                zoom.coerceIn(1.0, 22.0),
            ),
        )
    }

    fun zoomBy(delta: Double) {
        val map = mapRef ?: return
        runCatching { map.animateCamera(CameraUpdateFactory.zoomBy(delta), 220) }
    }

    fun resetBearing() {
        val map = mapRef ?: return
        val position = map.cameraPosition
        val update = CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder(position).bearing(0.0).tilt(0.0).build(),
        )
        map.animateCamera(update, 350)
    }

    fun fitBounds(bounds: BoundingBox, paddingPx: Int = 96) {
        val map = mapRef ?: return
        if (!bounds.isValid) return
        val latLngBounds = LatLngBounds.Builder()
            .include(LatLng(bounds.south, bounds.west))
            .include(LatLng(bounds.north, bounds.east))
            .build()
        runCatching { map.animateCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds, paddingPx), 650) }
    }

    fun center(): LatLon? = mapRef?.cameraPosition?.target?.let { LatLon(it.latitude, it.longitude) }

    fun zoom(): Double = mapRef?.cameraPosition?.zoom ?: 0.0

    fun screenPosition(point: LatLon): PointF? {
        val map = mapRef ?: return null
        return runCatching {
            map.projection.toScreenLocation(LatLng(point.lat, point.lon))
        }.getOrNull()
    }

    fun waypointIdAt(screen: PointF): String? {
        val map = mapRef ?: return null
        return runCatching {
            val features = map.queryRenderedFeatures(screen, MapStyleBuilder.LAYER_WAYPOINT)
            features.firstOrNull()?.getStringProperty("id")
        }.getOrNull()
    }

    fun emitCamera(force: Boolean) {
        val map = mapRef ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastCameraEmit < 32L) return
        lastCameraEmit = now
        val position = map.cameraPosition
        val target = position.target ?: return
        val visible = runCatching { map.projection.visibleRegion }.getOrNull()
        val bounds = if (visible != null) {
            val latLngBounds = visible.latLngBounds
            BoundingBox(
                south = latLngBounds.latitudeSouth,
                west = latLngBounds.longitudeWest,
                north = latLngBounds.latitudeNorth,
                east = latLngBounds.longitudeEast,
            )
        } else {
            null
        }
        val metersPerPixel = runCatching {
            map.projection.getMetersPerPixelAtLatitude(target.latitude)
        }.getOrDefault(0.0)

        _camera.value = CameraInfo(
            center = LatLon(target.latitude, target.longitude),
            zoom = position.zoom,
            bearing = position.bearing.toFloat(),
            tilt = position.tilt.toFloat(),
            metersPerPixel = metersPerPixel,
            bounds = bounds,
        )
    }
}
