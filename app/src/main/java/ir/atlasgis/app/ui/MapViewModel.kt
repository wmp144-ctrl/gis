package ir.atlasgis.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.atlasgis.app.core.BoundingBox
import ir.atlasgis.app.core.LatLon
import ir.atlasgis.app.core.bearingDegrees
import ir.atlasgis.app.core.boundingBoxOf
import ir.atlasgis.app.core.distanceMeters
import ir.atlasgis.app.core.estimateSpeedMps
import ir.atlasgis.app.core.locateOnRoute
import ir.atlasgis.app.core.parseCoordinateInput
import ir.atlasgis.app.core.pathLengthMeters
import ir.atlasgis.app.core.polygonAreaSqMeters
import ir.atlasgis.app.data.AppDatabase
import ir.atlasgis.app.data.AppSettings
import ir.atlasgis.app.data.BackupCodec
import ir.atlasgis.app.data.BackupManager
import ir.atlasgis.app.data.CompassProvider
import ir.atlasgis.app.data.GeoFileCodec
import ir.atlasgis.app.data.GpsFix
import ir.atlasgis.app.data.MapLayer
import ir.atlasgis.app.data.MapDownloader
import ir.atlasgis.app.data.MapLibrary
import ir.atlasgis.app.data.MapPlace
import ir.atlasgis.app.data.MapStyleBuilder
import ir.atlasgis.app.data.MapStylePreset
import ir.atlasgis.app.data.MapSymbolIcons
import ir.atlasgis.app.data.MeasureMode
import ir.atlasgis.app.data.OverlayGeoJson
import ir.atlasgis.app.data.PlaceIndexer
import ir.atlasgis.app.data.PoiClassifier
import ir.atlasgis.app.data.TileFormat
import ir.atlasgis.app.data.RecordingState
import ir.atlasgis.app.data.SettingsStore
import ir.atlasgis.app.data.ShareHelper
import ir.atlasgis.app.data.Track
import ir.atlasgis.app.data.Waypoint
import ir.atlasgis.app.data.WaypointCategoryStyle
import ir.atlasgis.app.domain.RecordingEngine
import ir.atlasgis.app.domain.RecordingService
import ir.atlasgis.app.domain.TrackSession
import ir.atlasgis.app.ui.map.MapController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

private const val MAX_GEO_IMPORT_BYTES = 32 * 1024 * 1024

enum class Tool { BROWSE, MEASURE }

enum class Sheet { NONE, TOOLS, LAYERS, WAYPOINTS, TRACKS, SETTINGS, SEARCH }

/** One row in the map search sheet: a saved waypoint or a place from the map file. */
data class SearchHit(
    val name: String,
    val kind: String,
    val label: String,
    val position: LatLon,
    val waypointId: String? = null,
    /** Archive a map place came from; null for a saved waypoint. */
    val layerId: String? = null,
) {
    val fromWaypoint: Boolean get() = waypointId != null
}

data class MeasureUiState(
    val active: Boolean = false,
    val mode: MeasureMode = MeasureMode.DISTANCE,
    val points: List<LatLon> = emptyList(),
) {
    val lengthMeters: Double get() = pathLengthMeters(points)
    val areaSqMeters: Double
        get() = if (mode == MeasureMode.AREA) polygonAreaSqMeters(points) else 0.0
    val closed: Boolean get() = mode == MeasureMode.AREA && points.size >= 3
}

data class ImportUiState(val label: String, val progress: Float, val indeterminate: Boolean)

enum class NavKind { POINT, TRACK }

/** Live guidance for a target point or a followed track. */
data class NavigationState(
    val label: String,
    val target: LatLon,
    val kind: NavKind,
    val distanceMeters: Double? = null,
    val bearingDegrees: Double? = null,
    /** Bearing relative to where the phone is facing (null without a compass). */
    val relativeBearing: Float? = null,
    val etaSeconds: Long? = null,
    val arrived: Boolean = false,
    val offRouteMeters: Double? = null,
    val remainingMeters: Double? = null,
    val progress: Float? = null,
)

data class DownloadUiState(
    val label: String,
    val progress: Float,
    val indeterminate: Boolean = false,
    val detail: String = "",
)

/** A backup file that has been read and is waiting for the user's confirmation. */
data class RestorePreview(
    val snapshot: BackupCodec.Snapshot,
    val summary: String,
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val library = MapLibrary(application)
    private val db = AppDatabase(application)
    private val settingsStore = SettingsStore(application)
    private val engine = RecordingEngine.get(application)
    private val compass = CompassProvider(application)
    private val mapDownloader = MapDownloader(application)
    private val backupManager = BackupManager(application)

    val controller = MapController(application)

    private val _layers = MutableStateFlow<List<MapLayer>>(emptyList())
    val layers: StateFlow<List<MapLayer>> = _layers.asStateFlow()

    private val _waypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    val waypoints: StateFlow<List<Waypoint>> = _waypoints.asStateFlow()

    private val _categoryStyles = MutableStateFlow(WaypointCategoryStyle.defaults())
    val categoryStyles: StateFlow<List<WaypointCategoryStyle>> = _categoryStyles.asStateFlow()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _selectedWaypointId = MutableStateFlow<String?>(null)
    val selectedWaypointId: StateFlow<String?> = _selectedWaypointId.asStateFlow()

    private val _tool = MutableStateFlow(Tool.BROWSE)
    val tool: StateFlow<Tool> = _tool.asStateFlow()

    private val _measure = MutableStateFlow(MeasureUiState())
    val measure: StateFlow<MeasureUiState> = _measure.asStateFlow()

    val recording: StateFlow<RecordingState> = engine.recording

    private val _sheet = MutableStateFlow(Sheet.NONE)
    val sheet: StateFlow<Sheet> = _sheet.asStateFlow()

    private val _pendingWaypoint = MutableStateFlow<LatLon?>(null)
    val pendingWaypoint: StateFlow<LatLon?> = _pendingWaypoint.asStateFlow()

    private val _editingWaypoint = MutableStateFlow<Waypoint?>(null)
    val editingWaypoint: StateFlow<Waypoint?> = _editingWaypoint.asStateFlow()

    private val _importing = MutableStateFlow<ImportUiState?>(null)
    val importing: StateFlow<ImportUiState?> = _importing.asStateFlow()

    private val _followMode = MutableStateFlow(false)
    val followMode: StateFlow<Boolean> = _followMode.asStateFlow()

    private val _showTracks = MutableStateFlow(true)
    val showTracks: StateFlow<Boolean> = _showTracks.asStateFlow()

    /** False while the chrome is hidden by a short tap on the map. */
    private val _chromeVisible = MutableStateFlow(true)
    val chromeVisible: StateFlow<Boolean> = _chromeVisible.asStateFlow()

    /** A record action is prepared but the GPS has not started yet. */
    private val _recordArmed = MutableStateFlow(false)
    val recordArmed: StateFlow<Boolean> = _recordArmed.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    val gps: StateFlow<GpsFix?> = engine.gps

    private val _nav = MutableStateFlow<NavigationState?>(null)
    val nav: StateFlow<NavigationState?> = _nav.asStateFlow()

    private val _deviceHeading = MutableStateFlow<Float?>(null)
    val deviceHeading: StateFlow<Float?> = _deviceHeading.asStateFlow()

    private val _download = MutableStateFlow<DownloadUiState?>(null)
    val download: StateFlow<DownloadUiState?> = _download.asStateFlow()

    private val _restorePreview = MutableStateFlow<RestorePreview?>(null)
    val restorePreview: StateFlow<RestorePreview?> = _restorePreview.asStateFlow()

    private var restoreSnapshot: BackupCodec.Snapshot? = null
    private var downloadJob: Job? = null
    private var navTrack: Track? = null
    private var arrivalNotified = false

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchHits = MutableStateFlow<List<SearchHit>>(emptyList())
    val searchHits: StateFlow<List<SearchHit>> = _searchHits.asStateFlow()

    /** 0..1 while the place index is being built for the first time, null otherwise. */
    private val _searchIndexProgress = MutableStateFlow<Float?>(null)
    val searchIndexProgress: StateFlow<Float?> = _searchIndexProgress.asStateFlow()

    /** The place the user picked from search; drawn as a highlighted pin. */
    private val _searchFocus = MutableStateFlow<SearchHit?>(null)
    val searchFocus: StateFlow<SearchHit?> = _searchFocus.asStateFlow()

    private val placeIndexer = PlaceIndexer(db)
    private var searchJob: Job? = null
    private var indexJob: Job? = null
    private var nearbyIndexJob: Job? = null
    private var nearbyIndexKey: String? = null

    /** Material icon plates for POIs, rendered once and handed to every style load. */
    private val symbolImages: Map<String, Bitmap> by lazy {
        runCatching { MapSymbolIcons.bitmaps(getApplication()) }.getOrDefault(emptyMap())
    }

    val settings: StateFlow<AppSettings> = settingsStore.settings

    private var lastStyleKey: String? = null
    private var hadSavedCamera = false

    init {
        settingsStore.loadCamera()?.let { saved ->
            hadSavedCamera = true
            controller.initialCenter = LatLon(saved.lat, saved.lon)
            controller.initialZoom = saved.zoom
            controller.initialBearing = saved.bearing.toFloat()
        }
        controller.onMapReady = {
            controller.initialCenter = controller.center() ?: controller.initialCenter
            ensureStyle(force = true)
            pushOverlays()
        }
        controller.onStyleReady = { pushOverlays() }
        controller.onTap = { point -> handleMapTap(point) }
        controller.onLongPress = { point ->
            if (!_measure.value.active) {
                _searchFocus.value = null
                _pendingWaypoint.value = point
                pushOverlays()
            }
        }
        controller.onCameraIdle = { info ->
            settingsStore.saveCamera(
                info.center.lat, info.center.lon, info.zoom, info.bearing.toDouble(),
            )
        }
        controller.onUserGesture = {
            // Panning while following used to be undone by the next GPS fix.
            if (_followMode.value) {
                _followMode.value = false
                _messages.tryEmit("حالت تعقیب خاموش شد؛ برای روشن‌شدن دوباره، دکمهٔ موقعیت را بزنید")
            }
        }

        loadAll()
        observeGps()
    }

    // ------------------------------------------------------------------ loading

    private fun loadAll() {
        viewModelScope.launch {
            // Finish recordings left behind by a killed process before the list is
            // read, otherwise the first frame can show a track that is not recording.
            withContext(Dispatchers.IO) { db.closeDanglingRecordings() }
            val loadedLayers = withContext(Dispatchers.IO) { library.layers() }
            _layers.value = loadedLayers
            _waypoints.value = withContext(Dispatchers.IO) { db.readWaypoints() }
            _categoryStyles.value = withContext(Dispatchers.IO) { db.readCategoryStyles() }
            _tracks.value = withContext(Dispatchers.IO) { db.readTracks() }
            ensureStyle(force = true)
            pushOverlays()
            if (!hadSavedCamera) {
                val firstBounds = loadedLayers.filter { it.visible }.maxByOrNull { it.sortOrder }?.bounds
                if (firstBounds != null) controller.fitBounds(firstBounds, 24)
            }
        }
    }

    private fun ensureStyle(force: Boolean = false) {
        if (!controller.isReady) return
        val current = _layers.value
        val key = buildString {
            current.forEach { layer ->
                append(layer.id).append(':').append(layer.visible).append(':')
                    .append(layer.opacity).append(':').append(layer.sortOrder).append(':')
                    .append(layer.stylePath ?: "-").append(':')
                    .append(layer.stylePreset.key).append(';')
            }
        }
        if (!force && key == lastStyleKey) return
        lastStyleKey = key
        controller.loadStyle(MapStyleBuilder.build(current), symbolImages)
    }

    private fun pushOverlays() {
        val measureState = _measure.value
        // Per-category map filter: hidden categories are left out of the GeoJSON.
        val visibleCategories = _categoryStyles.value.filter { it.visible }.map { it.key }.toSet()
        controller.pushOverlays(
            waypointsJson = OverlayGeoJson.waypoints(
                _waypoints.value.filter { it.category in visibleCategories },
                _selectedWaypointId.value,
                _categoryStyles.value.associate { it.key to it.hexColor },
            ),
            measureJson = if (measureState.active) {
                OverlayGeoJson.measurement(measureState.points, measureState.closed)
            } else {
                OverlayGeoJson.empty()
            },
            tracksJson = if (_showTracks.value) {
                OverlayGeoJson.tracks(_tracks.value.filter { !it.isRecording })
            } else {
                OverlayGeoJson.empty()
            },
            activeTrackJson = OverlayGeoJson.line(engine.livePoints.value),
            gpsJson = OverlayGeoJson.gpsPoint(gps.value),
            accuracyJson = OverlayGeoJson.accuracyRing(gps.value),
            searchJson = OverlayGeoJson.searchFocus(_searchFocus.value?.position),
            navJson = navOverlayJson(),
        )
    }

    // -------------------------------------------------------------------- search

    fun openSearch() {
        _sheet.value = Sheet.SEARCH
        ensureSearchIndex()
        ensureNearbyPlaces()
    }

    fun closeSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchHits.value = emptyList()
        _sheet.value = Sheet.NONE
    }

    fun setSearchQuery(text: String) {
        _searchQuery.value = text
        searchJob?.cancel()
        val needle = text.trim()
        if (needle.length < 2) {
            _searchHits.value = emptyList()
            return
        }
        ensureNearbyPlaces()
        val normalizedNeedle = PlaceIndexer.normalize(needle)
        if (normalizedNeedle.isEmpty()) {
            _searchHits.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(140)
            val waypointHits = _waypoints.value
                .filter {
                    PlaceIndexer.normalize(it.name).contains(normalizedNeedle) ||
                        PlaceIndexer.normalize(it.note).contains(normalizedNeedle)
                }
                .take(12)
                .map { waypoint ->
                    SearchHit(
                        name = waypoint.name,
                        kind = waypoint.category,
                        label = _categoryStyles.value.firstOrNull { it.key == waypoint.category }?.label
                            ?: PoiClassifier.labelFor(waypoint.category),
                        position = waypoint.position,
                        waypointId = waypoint.id,
                    )
                }
            // A hidden archive is not drawn, so it must not answer a map search.
            val visibleLayerIds = _layers.value
                .filter { it.visible && it.format == TileFormat.VECTOR }
                .map { it.id }
                .toSet()
            val placeHits = withContext(Dispatchers.IO) { db.searchPlaces(needle, 40) }
                .filter { it.layerId in visibleLayerIds }
                .map { place -> place.toHit() }
            _searchHits.value = waypointHits + placeHits
        }
    }

    /** Builds the place index once per vector archive, in the background. */
    private fun ensureSearchIndex() {
        if (indexJob?.isActive == true) return
        val candidates = _layers.value.filter { it.visible && it.format == TileFormat.VECTOR }
        if (candidates.isEmpty()) return
        indexJob = viewModelScope.launch {
            val pending = withContext(Dispatchers.IO) {
                candidates.filter { !db.isPlacesIndexed(it.id) }
            }
            if (pending.isEmpty()) return@launch
            _searchIndexProgress.value = 0f
            pending.forEach { layer ->
                runCatching {
                    placeIndexer.build(layer) { progress -> _searchIndexProgress.value = progress }
                }
            }
            _searchIndexProgress.value = null
            // Results typed while indexing finished are refreshed now.
            if (_searchQuery.value.trim().length >= 2) setSearchQuery(_searchQuery.value)
        }
    }

    /**
     * Adds the named POIs of the tiles around the camera to the index.
     *
     * The one-shot index of [ensureSearchIndex] has to stay at a low zoom for a
     * country-sized archive, and POI layers only exist at street zoom, so without
     * this pass a shop, mosque or pharmacy could be drawn on the map and still never
     * be found by search. Re-indexing only happens when the map has moved a real
     * distance, and duplicate rows are ignored by the database.
     */
    private fun ensureNearbyPlaces() {
        val center = controller.camera.value?.center ?: return
        val candidates = _layers.value.filter { it.visible && it.format == TileFormat.VECTOR }
        if (candidates.isEmpty()) return
        val key = candidates.joinToString(",") { it.id } + "|" +
            (center.lat * 25).toInt() + ":" + (center.lon * 25).toInt()
        if (key == nearbyIndexKey || nearbyIndexJob?.isActive == true) return
        nearbyIndexKey = key
        nearbyIndexJob = viewModelScope.launch {
            candidates.forEach { layer ->
                runCatching { placeIndexer.buildNear(layer, center.lat, center.lon) }
            }
            if (_searchQuery.value.trim().length >= 2) setSearchQuery(_searchQuery.value)
        }
    }

    fun selectSearchHit(hit: SearchHit) {
        _sheet.value = Sheet.NONE
        // A saved waypoint has its own richer card (edit/delete), so showing the
        // search-focus card as well used to stack two cards for the same point.
        _searchFocus.value = if (hit.waypointId == null) hit else null
        _selectedWaypointId.value = hit.waypointId
        _chromeVisible.value = true
        controller.flyTo(hit.position, max(controller.zoom(), 16.0))
        pushOverlays()
    }

    fun clearSearchFocus() {
        if (_searchFocus.value == null) return
        _searchFocus.value = null
        pushOverlays()
    }

    private fun MapPlace.toHit(): SearchHit = SearchHit(
        name = name,
        kind = klass,
        label = PoiClassifier.labelFor(klass),
        position = position,
        layerId = layerId,
    )

    // -------------------------------------------------------------------- layers

    fun importMap(uri: Uri) {
        viewModelScope.launch {
            _importing.value = ImportUiState("در حال افزودن نقشه…", 0f, false)
            runCatching {
                val layer = library.import(uri) { progress ->
                    _importing.value = ImportUiState("در حال کپی نقشه…", progress, progress <= 0f)
                }
                _importing.value = null
                _layers.value = library.layers()
                ensureStyle(force = true)
                layer.bounds?.let { controller.fitBounds(it, 40) }
                if (layer.format == ir.atlasgis.app.data.TileFormat.VECTOR && layer.vectorLayers.isEmpty()) {
                    _messages.tryEmit(
                        "نقشه افزوده شد؛ این فایل برداری فهرست لایه‌ها ندارد، ممکن است برخی عوارض نمایش داده نشوند",
                    )
                } else {
                    _messages.tryEmit("نقشه «${layer.name}» افزوده شد")
                }
            }.onFailure { error ->
                _importing.value = null
                _messages.tryEmit(error.message ?: "افزودن نقشه ناموفق بود")
            }
        }
    }

    fun toggleLayer(layer: MapLayer) {
        viewModelScope.launch {
            library.setVisible(layer, !layer.visible)
            _layers.value = library.layers()
            ensureStyle()
        }
    }

    fun setLayerOpacity(layer: MapLayer, opacity: Float) {
        viewModelScope.launch {
            library.setOpacity(layer, opacity)
            _layers.value = library.layers()
            ensureStyle()
        }
    }

    fun moveLayer(layer: MapLayer, up: Boolean) {
        viewModelScope.launch {
            _layers.value = library.move(layer, up)
            ensureStyle(force = true)
        }
    }

    fun renameLayer(layer: MapLayer, name: String) {
        viewModelScope.launch {
            library.rename(layer, name)
            _layers.value = library.layers()
        }
    }

    fun importLayerStyle(layer: MapLayer, uri: Uri) {
        viewModelScope.launch {
            _importing.value = ImportUiState("در حال خواندن استایل…", 0f, true)
            runCatching { withContext(Dispatchers.IO) { library.importStyle(layer, uri) } }
                .onSuccess { updated ->
                    _importing.value = null
                    _layers.value = library.layers()
                    ensureStyle(force = true)
                    _messages.tryEmit("استایل سفارشی برای «${updated.name}» اعمال شد")
                }
                .onFailure { error ->
                    _importing.value = null
                    _messages.tryEmit(error.message ?: "بارگذاری استایل ناموفق بود")
                }
        }
    }

    fun setLayerStylePreset(layer: MapLayer, preset: MapStylePreset) {
        viewModelScope.launch {
            // Picking a built-in palette means the user wants the automatic style,
            // so a custom style.json that would hide it is detached in the same step.
            if (layer.hasCustomStyle) library.clearStyle(layer)
            library.setStylePreset(layer, preset)
            _layers.value = library.layers()
            ensureStyle(force = true)
            _messages.tryEmit("استایل «${preset.label}» اعمال شد")
        }
    }

    fun clearLayerStyle(layer: MapLayer) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { library.clearStyle(layer) } }
                .onSuccess {
                    _layers.value = library.layers()
                    ensureStyle(force = true)
                    _messages.tryEmit("استایل سفارشی حذف شد؛ استایل خودکار جای آن را گرفت")
                }
                .onFailure { error ->
                    _messages.tryEmit(error.message ?: "حذف استایل ناموفق بود")
                }
        }
    }

    fun deleteLayer(layer: MapLayer) {
        viewModelScope.launch {
            library.delete(layer)
            _layers.value = library.layers()
            ensureStyle(force = true)
            _messages.tryEmit("نقشه «${layer.name}» حذف شد")
        }
    }

    // ---------------------------------------------------------------- interaction

    private fun handleMapTap(point: LatLon) {
        if (_measure.value.active) {
            addMeasurePoint(point)
            return
        }
        // Exploring the map dismisses the last search result pin.
        if (_searchFocus.value != null) clearSearchFocus()
        if (!_chromeVisible.value) {
            // The first tap only brings the chrome back, as requested.
            _chromeVisible.value = true
            return
        }
        val screen = controller.screenPosition(point)
        val waypointId = screen?.let { controller.waypointIdAt(it) }
        if (waypointId != null) {
            _selectedWaypointId.value = waypointId
            pushOverlays()
            return
        }
        _selectedWaypointId.value = null
        pushOverlays()
        // A short tap on empty map hides every panel and button.
        _chromeVisible.value = false
    }

    fun setChromeVisible(visible: Boolean) {
        _chromeVisible.value = visible
    }

    fun toggleChrome() {
        _chromeVisible.value = !_chromeVisible.value
    }

    // ----------------------------------------------------------------- waypoints

    fun requestWaypointAt(point: LatLon) {
        _searchFocus.value = null
        _pendingWaypoint.value = point
        pushOverlays()
    }

    fun cancelPendingWaypoint() {
        _pendingWaypoint.value = null
    }

    fun createWaypoint(
        point: LatLon,
        name: String,
        note: String,
        category: String,
        elevation: Double? = null,
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val waypoint = Waypoint(
                id = AppDatabase.newId(),
                name = name.trim().ifBlank { "نقطه ${_waypoints.value.size + 1}" },
                note = note.trim(),
                category = category,
                lat = point.lat,
                lon = point.lon,
                elevation = elevation,
                createdAt = now,
                updatedAt = now,
            )
            withContext(Dispatchers.IO) { db.upsertWaypoint(waypoint) }
            _waypoints.value = withContext(Dispatchers.IO) { db.readWaypoints() }
            _selectedWaypointId.value = waypoint.id
            _pendingWaypoint.value = null
            pushOverlays()
            _messages.tryEmit("نقطه ذخیره شد")
        }
    }

    fun updateWaypoint(waypoint: Waypoint) {
        viewModelScope.launch {
            val updated = waypoint.copy(updatedAt = System.currentTimeMillis())
            withContext(Dispatchers.IO) { db.upsertWaypoint(updated) }
            _waypoints.value = withContext(Dispatchers.IO) { db.readWaypoints() }
            _editingWaypoint.value = null
            pushOverlays()
            _messages.tryEmit("تغییرات ذخیره شد")
        }
    }

    fun deleteWaypoint(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.deleteWaypoint(id) }
            _waypoints.value = withContext(Dispatchers.IO) { db.readWaypoints() }
            if (_selectedWaypointId.value == id) _selectedWaypointId.value = null
            pushOverlays()
            _messages.tryEmit("نقطه حذف شد")
        }
    }

    fun updateCategoryStyle(style: WaypointCategoryStyle) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.upsertCategoryStyle(style) }
            _categoryStyles.value = withContext(Dispatchers.IO) { db.readCategoryStyles() }
            // Marker colours on the map come from the category style too.
            pushOverlays()
            _messages.tryEmit("دستهٔ «${style.label}» به‌روزرسانی شد")
        }
    }

    /** Hides or shows every point of one category on the map. */
    fun setCategoryVisible(style: WaypointCategoryStyle, visible: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.upsertCategoryStyle(style.copy(visible = visible)) }
            _categoryStyles.value = withContext(Dispatchers.IO) { db.readCategoryStyles() }
            pushOverlays()
        }
    }

    /** Deletes a category; its waypoints fall back to the general category. */
    fun deleteCategoryStyle(style: WaypointCategoryStyle) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.deleteCategoryStyle(style.key) }
            _categoryStyles.value = withContext(Dispatchers.IO) { db.readCategoryStyles() }
            _waypoints.value = withContext(Dispatchers.IO) { db.readWaypoints() }
            pushOverlays()
            _messages.tryEmit("دستهٔ «${style.label}» حذف شد؛ نقاط آن به دستهٔ عمومی منتقل شدند")
        }
    }

    fun selectWaypoint(id: String?) {
        _selectedWaypointId.value = id
        pushOverlays()
    }

    fun startEditingWaypoint(waypoint: Waypoint) {
        _editingWaypoint.value = waypoint
    }

    fun cancelEditingWaypoint() {
        _editingWaypoint.value = null
    }

    fun showWaypointOnMap(waypoint: Waypoint) {
        _sheet.value = Sheet.NONE
        _searchFocus.value = null
        _selectedWaypointId.value = waypoint.id
        controller.flyTo(waypoint.position, max(controller.zoom(), 15.0))
        pushOverlays()
    }

    // ------------------------------------------------------------------ measuring

    fun startMeasure(mode: MeasureMode) {
        _selectedWaypointId.value = null
        _searchFocus.value = null
        _recordArmed.value = false
        _tool.value = Tool.MEASURE
        _measure.value = MeasureUiState(active = true, mode = mode, points = emptyList())
        pushOverlays()
    }

    fun setMeasureMode(mode: MeasureMode) {
        val current = _measure.value
        if (!current.active || current.mode == mode) return
        _measure.value = current.copy(mode = mode)
        pushOverlays()
    }

    fun addMeasurePoint(point: LatLon) {
        val current = _measure.value
        if (!current.active) return
        _measure.value = current.copy(points = current.points + point)
        pushOverlays()
    }

    fun undoMeasurePoint() {
        val current = _measure.value
        if (!current.active || current.points.isEmpty()) return
        _measure.value = current.copy(points = current.points.dropLast(1))
        pushOverlays()
    }

    fun clearMeasure() {
        val current = _measure.value
        _measure.value = current.copy(points = emptyList())
        pushOverlays()
    }

    fun stopMeasure() {
        _measure.value = MeasureUiState()
        _tool.value = Tool.BROWSE
        pushOverlays()
    }

    fun closeMeasureAndReturn() = stopMeasure()

    // ------------------------------------------------------------------ guidance

    private fun navOverlayJson(): String {
        val state = _nav.value ?: return OverlayGeoJson.empty()
        val position = gps.value?.let { LatLon(it.lat, it.lon) }
        val route = if (state.kind == NavKind.TRACK) {
            navTrack?.points?.map { LatLon(it.lat, it.lon) }.orEmpty()
        } else {
            emptyList()
        }
        return OverlayGeoJson.navigation(position, state.target, route)
    }

    /** Starts guidance to a single point: bearing arrow, distance and ETA. */
    fun navigateTo(label: String, position: LatLon) {
        val name = label.ifBlank { "مقصد" }
        navTrack = null
        arrivalNotified = false
        _nav.value = NavigationState(label = name, target = position, kind = NavKind.POINT)
        _searchFocus.value = null
        _sheet.value = Sheet.NONE
        pushOverlays()
        refreshNavigation(gps.value)
        controller.flyTo(position, max(controller.zoom(), 15.0))
        _messages.tryEmit("ناوبری به «$name» آغاز شد")
    }

    fun navigateToWaypoint(waypoint: Waypoint) =
        navigateTo(waypoint.name, waypoint.position)

    fun navigateToSearchHit(hit: SearchHit) =
        navigateTo(hit.name, hit.position)

    /**
     * Track-back: keeps the saved line on screen and reports how far the user is from
     * it, how much is left and which way to walk. The line itself is the guide — the
     * app never invents a route it cannot verify against the map.
     */
    fun followTrack(track: Track) {
        val points = track.points.map { LatLon(it.lat, it.lon) }
        if (points.size < 2) {
            _messages.tryEmit("این مسیر برای دنبال‌کردن نقطهٔ کافی ندارد")
            return
        }
        navTrack = track
        arrivalNotified = false
        val position = gps.value?.let { LatLon(it.lat, it.lon) }
        val progress = position?.let { locateOnRoute(it, points) }
        _nav.value = NavigationState(
            label = track.name,
            target = points.getOrElse(progress?.nextIndex ?: 0) { points.last() },
            kind = NavKind.TRACK,
            offRouteMeters = progress?.offRouteMeters,
            remainingMeters = progress?.remainingMeters ?: pathLengthMeters(points),
            progress = progress?.fraction ?: 0f,
        )
        _sheet.value = Sheet.NONE
        _searchFocus.value = null
        _selectedWaypointId.value = null
        pushOverlays()
        refreshNavigation(gps.value)
        controller.fitBounds(boundingBoxOf(points) ?: return, 96)
        _messages.tryEmit("دنبال‌کردن مسیر «${track.name}» آغاز شد")
    }

    fun centerOnNavigationTarget() {
        val state = _nav.value ?: return
        controller.flyTo(state.target, max(controller.zoom(), 15.0))
    }

    fun stopNavigation() {
        if (_nav.value == null) return
        _nav.value = null
        navTrack = null
        arrivalNotified = false
        controller.updateSource(MapStyleBuilder.SRC_NAV, OverlayGeoJson.empty())
        _messages.tryEmit("ناوبری پایان یافت")
    }

    private fun refreshNavigation(fix: GpsFix?) {
        val state = _nav.value ?: return
        val position = fix?.let { LatLon(it.lat, it.lon) } ?: return
        val heading = _deviceHeading.value
        val recordingState = engine.recording.value
        val speed = estimateSpeedMps(
            gpsSpeed = fix.speedMps?.toDouble(),
            movingDistance = recordingState.distanceMeters,
            movingSeconds = recordingState.movingSeconds,
        )

        val updated = if (state.kind == NavKind.TRACK) {
            val route = navTrack?.points?.map { LatLon(it.lat, it.lon) }.orEmpty()
            val progress = locateOnRoute(position, route)
            val target = route.getOrNull(progress?.nextIndex ?: 0) ?: route.lastOrNull() ?: state.target
            val remaining = progress?.remainingMeters ?: state.remainingMeters
            val bearing = if (route.size >= 2) bearingDegrees(position, target) else state.bearingDegrees
            state.copy(
                target = target,
                distanceMeters = remaining,
                remainingMeters = remaining,
                offRouteMeters = progress?.offRouteMeters,
                progress = progress?.fraction,
                bearingDegrees = bearing,
                relativeBearing = relativeToHeading(bearing, heading),
                etaSeconds = estimateEta(remaining, speed),
                arrived = (remaining ?: Double.MAX_VALUE) <= 20.0,
            )
        } else {
            val direct = distanceMeters(position, state.target)
            val bearing = bearingDegrees(position, state.target)
            state.copy(
                distanceMeters = direct,
                bearingDegrees = bearing,
                relativeBearing = relativeToHeading(bearing, heading),
                etaSeconds = estimateEta(direct, speed),
                arrived = direct <= 15.0,
            )
        }
        _nav.value = updated
        controller.updateSource(MapStyleBuilder.SRC_NAV, navOverlayJson())
        if (updated.arrived && !arrivalNotified) {
            arrivalNotified = true
            _messages.tryEmit("به مقصد رسیدید: ${updated.label}")
        } else if (!updated.arrived) {
            arrivalNotified = false
        }
    }

    private fun relativeToHeading(bearing: Double?, heading: Float?): Float? {
        if (bearing == null) return null
        if (heading == null) return bearing.toFloat()
        var delta = bearing.toFloat() - heading
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        return delta
    }

    private fun estimateEta(distance: Double?, speed: Double?): Long? {
        if (distance == null || speed == null || speed <= 0.05) return null
        return (distance / speed).toLong().coerceIn(0, 7L * 24 * 3600)
    }

    // ------------------------------------------------------------ map downloads

    fun downloadMbTiles(url: String, name: String) {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            _download.value = DownloadUiState("در حال دانلود نقشه", 0f)
            runCatching {
                val layer = withContext(Dispatchers.IO) {
                    mapDownloader.downloadMbTiles(url, name.ifBlank { null }) { progress ->
                        val fraction = if (progress.total > 0) {
                            (progress.downloaded.toFloat() / progress.total).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        _download.value = DownloadUiState(
                            label = progress.phase.ifBlank { "در حال دانلود نقشه" },
                            progress = fraction,
                            indeterminate = progress.total <= 0,
                            detail = if (progress.total > 0) {
                                "${progress.downloaded / (1024 * 1024)} / ${progress.total / (1024 * 1024)} مگابایت"
                            } else {
                                "${progress.downloaded / (1024 * 1024)} مگابایت"
                            },
                        )
                    }
                }
                _download.value = null
                _layers.value = library.layers()
                ensureStyle(force = true)
                layer.bounds?.let { controller.fitBounds(it, 40) }
                _messages.tryEmit("نقشه «${layer.name}» دانلود و افزوده شد")
            }.onFailure { error ->
                _download.value = null
                _messages.tryEmit(error.message ?: "دانلود نقشه ناموفق بود")
            }
        }
    }

    fun downloadRegion(template: String, name: String, minZoom: Int, maxZoom: Int) {
        if (downloadJob?.isActive == true) return
        val bounds = controller.camera.value?.bounds
        if (bounds == null) {
            _messages.tryEmit("محدودهٔ دید نقشه هنوز آماده نیست")
            return
        }
        downloadJob = viewModelScope.launch {
            _download.value = DownloadUiState("در حال دانلود کاشی‌ها", 0f, true)
            runCatching {
                val layer = withContext(Dispatchers.IO) {
                    mapDownloader.downloadTiles(template, name, bounds, minZoom, maxZoom) { progress ->
                        val fraction = if (progress.tilesTotal > 0) {
                            (progress.tilesDone.toFloat() / progress.tilesTotal).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        _download.value = DownloadUiState(
                            label = progress.phase.ifBlank { "در حال دانلود کاشی‌ها" },
                            progress = fraction,
                            indeterminate = progress.tilesTotal <= 0,
                            detail = "${progress.tilesDone} از ${progress.tilesTotal} کاشی",
                        )
                    }
                }
                _download.value = null
                _layers.value = library.layers()
                ensureStyle(force = true)
                layer.bounds?.let { controller.fitBounds(it, 40) }
                _messages.tryEmit("نقشه «${layer.name}» ساخته و افزوده شد")
            }.onFailure { error ->
                _download.value = null
                _messages.tryEmit(error.message ?: "دانلود کاشی‌ها ناموفق بود")
            }
        }
    }

    fun cancelDownload() {
        val job = downloadJob ?: return
        job.cancel()
        downloadJob = null
        _download.value = null
        _messages.tryEmit("دانلود لغو شد")
    }

    // -------------------------------------------------------- backup and sharing

    fun backupSuggestedName(): String = backupManager.suggestedFileName()

    fun createBackup(uri: Uri) {
        viewModelScope.launch {
            _importing.value = ImportUiState("در حال ساخت پشتیبان…", 0f, true)
            runCatching {
                val snapshot = backupManager.snapshot(settingsStore.settings.value)
                withContext(Dispatchers.IO) { backupManager.writeBackup(uri, snapshot) }
                _importing.value = null
                _messages.tryEmit(
                    "پشتیبان ذخیره شد: ${snapshot.waypoints.size} نقطه، ${snapshot.tracks.size} مسیر و تنظیمات برنامه",
                )
            }.onFailure { error ->
                _importing.value = null
                _messages.tryEmit(error.message ?: "ساخت پشتیبان ناموفق بود")
            }
        }
    }

    /** Reads the file first and only merges it after the user confirms. */
    fun previewRestore(uri: Uri) {
        viewModelScope.launch {
            _importing.value = ImportUiState("در حال خواندن پشتیبان…", 0f, true)
            runCatching {
                val snapshot = withContext(Dispatchers.IO) { backupManager.readBackup(uri) }
                _importing.value = null
                if (snapshot.waypoints.isEmpty() && snapshot.tracks.isEmpty() && snapshot.settings == null) {
                    error("هیچ نقطه، مسیر یا تنظیمی در این پشتیبان نیست")
                }
                restoreSnapshot = snapshot
                _restorePreview.value = RestorePreview(snapshot, BackupCodec.summary(snapshot))
            }.onFailure { error ->
                _importing.value = null
                _messages.tryEmit(error.message ?: "خواندن پشتیبان ناموفق بود")
            }
        }
    }

    fun confirmRestore() {
        val snapshot = restoreSnapshot ?: return
        _restorePreview.value = null
        restoreSnapshot = null
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { backupManager.apply(snapshot) }
                snapshot.settings?.let(settingsStore::applyBackup)
                _waypoints.value = withContext(Dispatchers.IO) { db.readWaypoints() }
                _tracks.value = withContext(Dispatchers.IO) { db.readTracks() }
                _categoryStyles.value = withContext(Dispatchers.IO) { db.readCategoryStyles() }
                pushOverlays()
                _messages.tryEmit(
                    "بازگردانی شد: ${snapshot.waypoints.size} نقطه، ${snapshot.tracks.size} مسیر" +
                        if (snapshot.settings != null) " و تنظیمات برنامه" else "",
                )
            }.onFailure { error ->
                _messages.tryEmit(error.message ?: "بازگردانی پشتیبان ناموفق بود")
            }
        }
    }

    fun cancelRestore() {
        _restorePreview.value = null
        restoreSnapshot = null
    }

    fun shareWaypoint(waypoint: Waypoint) {
        ShareHelper.shareWaypoint(getApplication(), waypoint)
    }

    fun shareTrack(track: Track, gpx: Boolean) {
        runCatching {
            val content = exportTrackContent(track, gpx)
            val stem = track.name.trim()
                .replace(Regex("[\\\\/:*?\"<>|]"), "-")
                .replace(Regex("\\s+"), "-")
                .take(48)
                .ifBlank { "atlasgis-track" }
            val extension = if (gpx) "gpx" else "geojson"
            ShareHelper.shareFile(
                getApplication(),
                "$stem.$extension",
                if (gpx) "application/gpx+xml" else "application/geo+json",
                content,
            )
        }.onFailure { _messages.tryEmit(it.message ?: "اشتراک‌گذاری ناموفق بود") }
    }

    // ---------------------------------------------------------------- navigation

    fun zoomIn() = controller.zoomBy(1.0)

    fun zoomOut() = controller.zoomBy(-1.0)

    fun resetBearing() = controller.resetBearing()

    fun toggleFollowMode() {
        val next = !_followMode.value
        _followMode.value = next
        if (next) {
            val fix = gps.value
            if (fix != null) {
                controller.flyTo(LatLon(fix.lat, fix.lon), max(controller.zoom(), 15.0))
            } else {
                _messages.tryEmit("در انتظار دریافت موقعیت GPS…")
            }
        }
    }

    fun centerOnLocation() {
        val fix = gps.value
        if (fix == null) {
            _messages.tryEmit("موقعیت یافت نشد؛ در فضای باز کمی صبر کنید")
            return
        }
        controller.flyTo(LatLon(fix.lat, fix.lon), max(controller.zoom(), 15.0))
    }

    fun fitAllWaypoints() {
        val box = boundingBoxOf(_waypoints.value.map { it.position })
        if (box == null) {
            _messages.tryEmit("هنوز نقطه‌ای ثبت نشده است")
            return
        }
        controller.fitBounds(box, 140)
    }

    fun showTrack(track: Track) {
        val box = boundingBoxOf(track.points.map { LatLon(it.lat, it.lon) })
        _sheet.value = Sheet.NONE
        _searchFocus.value = null
        _selectedWaypointId.value = null
        pushOverlays()
        if (box != null) controller.fitBounds(box, 96)
    }

    fun fitBounds(bounds: BoundingBox) = controller.fitBounds(bounds, 96)

    fun openSheet(sheet: Sheet) {
        _sheet.value = sheet
    }

    fun closeSheet() {
        _sheet.value = Sheet.NONE
    }

    // ---------------------------------------------------------------- settings ops

    fun setCoordinateFormat(format: ir.atlasgis.app.core.CoordinateFormat) =
        settingsStore.updateCoordinateFormat(format)

    fun setKeepScreenOn(enabled: Boolean) = settingsStore.setKeepScreenOn(enabled)

    fun setShowScaleBar(enabled: Boolean) = settingsStore.setShowScaleBar(enabled)

    fun setShowCoordinates(enabled: Boolean) = settingsStore.setShowCoordinates(enabled)

    fun setShowTracks(enabled: Boolean) {
        _showTracks.value = enabled
        pushOverlays()
    }

    // ----------------------------------------------------------------- recording

    /**
     * Recording lives in an application-scoped engine kept alive by
     * [RecordingService]; this ViewModel only mirrors it, so leaving the screen no
     * longer ends the track.
     */
    private fun recordingPermissionError(): String? = when {
        !engine.hasPermission() -> "برای ثبت مسیر، دسترسی موقعیت مکانی لازم است"
        !engine.hasPrecisePermission() ->
            "برای ثبت مسیر، دسترسی «دقیق» موقعیت مکانی لازم است؛ آن را از تنظیمات برنامه فعال کنید"
        else -> null
    }

    /** Prepares a recording; the GPS starts only when the user presses شروع. */
    fun armRecording() {
        if (engine.isActive) {
            _recordArmed.value = false
            return
        }
        recordingPermissionError()?.let { message ->
            _messages.tryEmit(message)
            return
        }
        _measure.value = MeasureUiState()
        _selectedWaypointId.value = null
        _searchFocus.value = null
        _recordArmed.value = true
        pushOverlays()
    }

    fun cancelArmRecording() {
        _recordArmed.value = false
    }

    fun startRecording() {
        if (engine.isActive) return
        _recordArmed.value = false
        recordingPermissionError()?.let { message ->
            _messages.tryEmit(message)
            return
        }
        if (!engine.locationServicesEnabled()) {
            _messages.tryEmit("GPS دستگاه خاموش است؛ پس از روشن شدن، ثبت ادامه می‌یابد")
        }
        if (!engine.start()) return
        _tool.value = Tool.BROWSE
        _measure.value = MeasureUiState()
        _searchFocus.value = null
        // The service keeps the GPS alive with the screen off; without it Android
        // freezes location updates as soon as the app leaves the foreground.
        RecordingService.start(getApplication())
        pushOverlays()
    }

    fun pauseRecording() {
        engine.pause()
    }

    fun resumeRecording() {
        engine.resume()
    }

    fun stopRecording() {
        engine.stop()
    }

    fun renameTrack(track: Track, name: String) {
        val trimmed = name.trim().take(80)
        if (trimmed.isEmpty() || trimmed == track.name) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.upsertTrack(track.copy(name = trimmed)) }
            _tracks.value = withContext(Dispatchers.IO) { db.readTracks() }
            _messages.tryEmit("نام مسیر ذخیره شد")
        }
    }

    fun deleteTrack(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.deleteTrack(id) }
            _tracks.value = withContext(Dispatchers.IO) { db.readTracks() }
            pushOverlays()
            _messages.tryEmit("مسیر حذف شد")
        }
    }


    // ------------------------------------------------------------------------ gps

    private fun observeGps() {
        viewModelScope.launch {
            engine.gps.collect { fix ->
                if (fix == null) return@collect
                if (_followMode.value) {
                    controller.followTo(
                        LatLon(fix.lat, fix.lon),
                        controller.zoom().coerceAtLeast(14.0),
                    )
                }
                updateGpsOverlays(fix)
                refreshNavigation(fix)
            }
        }
        // The engine owns the live polyline; pushing it here keeps the drawn line in
        // step with the notification even while the activity is paused.
        viewModelScope.launch {
            engine.livePoints.collect { points ->
                controller.updateSource(
                    MapStyleBuilder.SRC_ACTIVE_TRACK,
                    OverlayGeoJson.line(points),
                )
            }
        }
        viewModelScope.launch {
            engine.messages.collect { message -> _messages.tryEmit(message) }
        }
        viewModelScope.launch {
            engine.savedTracks.collect {
                _tracks.value = withContext(Dispatchers.IO) { db.readTracks() }
                pushOverlays()
            }
        }
        viewModelScope.launch {
            compass.heading.collect { heading ->
                _deviceHeading.value = heading
                // Keep the arrow pointing at the target as the phone turns.
                refreshNavigation(gps.value)
            }
        }
    }

    private fun updateGpsOverlays(fix: GpsFix) {
        controller.updateSource(MapStyleBuilder.SRC_GPS, OverlayGeoJson.gpsPoint(fix))
        controller.updateSource(MapStyleBuilder.SRC_GPS_ACCURACY, OverlayGeoJson.accuracyRing(fix))
    }

    fun onLocationPermissionGranted() {
        engine.startLocation()
    }

    fun onScreenResumed() {
        if (engine.hasPermission()) engine.startLocation()
        compass.start()
    }

    fun onScreenPaused() {
        engine.stopLocationIfIdle()
        compass.stop()
    }

    // ------------------------------------------------------------ import / export

    enum class ExportKind(val fileStem: String, val mimeType: String, val gpx: Boolean) {
        WAYPOINTS_GPX("atlasgis-waypoints", "application/gpx+xml", true),
        WAYPOINTS_GEOJSON("atlasgis-waypoints", "application/geo+json", false),
        TRACKS_GPX("atlasgis-tracks", "application/gpx+xml", true),
        TRACKS_GEOJSON("atlasgis-tracks", "application/geo+json", false),
    }

    fun exportContent(kind: ExportKind): String = when (kind) {
        ExportKind.WAYPOINTS_GPX -> GeoFileCodec.toGpx(_waypoints.value, emptyList())
        ExportKind.WAYPOINTS_GEOJSON -> GeoFileCodec.waypointsToGeoJson(_waypoints.value)
        ExportKind.TRACKS_GPX -> GeoFileCodec.toGpx(emptyList(), _tracks.value)
        ExportKind.TRACKS_GEOJSON -> GeoFileCodec.tracksToGeoJson(_tracks.value)
    }

    /** Content of a single saved track, used by the per-track export actions. */
    fun exportTrackContent(track: Track, gpx: Boolean): String =
        if (gpx) GeoFileCodec.toGpx(emptyList(), listOf(track))
        else GeoFileCodec.tracksToGeoJson(listOf(track))

    fun writeExport(uri: Uri, kind: ExportKind, trackId: String? = null) {
        viewModelScope.launch {
            runCatching {
                val single = trackId?.let { id -> _tracks.value.firstOrNull { it.id == id } }
                // Serialising a long track must not block the UI thread.
                val content = withContext(Dispatchers.IO) {
                    if (single != null) exportTrackContent(single, kind.gpx) else exportContent(kind)
                }
                withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver
                        .openOutputStream(uri) ?: error("نوشتن فایل ممکن نشد")
                    stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                }
                _messages.tryEmit("فایل با موفقیت ذخیره شد")
            }.onFailure { _messages.tryEmit(it.message ?: "ذخیره فایل ناموفق بود") }
        }
    }

    fun importGeoFile(uri: Uri) {
        viewModelScope.launch {
            _importing.value = ImportUiState("در حال خواندن فایل…", 0f, true)
            runCatching {
                val text = withContext(Dispatchers.IO) { readTextLimited(uri) }
                val result = withContext(Dispatchers.Default) { GeoFileCodec.parse(text) }
                if (result.waypoints.isEmpty() && result.tracks.isEmpty()) {
                    error("هیچ نقطه یا مسیری در فایل پیدا نشد")
                }
                withContext(Dispatchers.IO) {
                    result.waypoints.forEach { db.upsertWaypoint(it) }
                    result.tracks.forEach { db.upsertTrack(it) }
                }
                _waypoints.value = withContext(Dispatchers.IO) { db.readWaypoints() }
                _tracks.value = withContext(Dispatchers.IO) { db.readTracks() }
                pushOverlays()
                _importing.value = null
                _messages.tryEmit(
                    "${result.waypoints.size} نقطه و ${result.tracks.size} مسیر وارد شد",
                )
            }.onFailure { error ->
                _importing.value = null
                _messages.tryEmit(error.message ?: "خواندن فایل ناموفق بود")
            }
        }
    }

    /** Reads a GPX/GeoJSON file, refusing an oversized file before it can exhaust memory. */
    private fun readTextLimited(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        val input = resolver.openInputStream(uri) ?: error("خواندن فایل ممکن نشد")
        val output = java.io.ByteArrayOutputStream()
        input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                total += read
                if (total > MAX_GEO_IMPORT_BYTES) {
                    error("فایل ورودی بیش از حد بزرگ است (حداکثر ۳۲ مگابایت)")
                }
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray().toString(Charsets.UTF_8)
    }

    // -------------------------------------------------------------------- go to

    fun goToCoordinate(input: String): Boolean {
        val parsed = parseLatLon(input)
        if (parsed == null) {
            _messages.tryEmit("مختصات معتبر نیست؛ نمونه: 35.6892, 51.3890")
            return false
        }
        _searchFocus.value = null
        pushOverlays()
        controller.flyTo(parsed, max(controller.zoom(), 14.0))
        return true
    }

    fun parseLatLon(input: String): LatLon? = parseCoordinateInput(input)

    fun notify(message: String) {
        _messages.tryEmit(message)
    }

    override fun onCleared() {
        compass.stop()
        engine.stopLocationIfIdle()
        super.onCleared()
    }
}
