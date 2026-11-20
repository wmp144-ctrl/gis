package ir.atlasgis.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.atlasgis.app.core.BoundingBox
import ir.atlasgis.app.core.LatLon
import ir.atlasgis.app.core.boundingBoxOf
import ir.atlasgis.app.core.pathLengthMeters
import ir.atlasgis.app.core.polygonAreaSqMeters
import ir.atlasgis.app.data.AppDatabase
import ir.atlasgis.app.data.AppSettings
import ir.atlasgis.app.data.GeoFileCodec
import ir.atlasgis.app.data.GpsFix
import ir.atlasgis.app.data.MapLayer
import ir.atlasgis.app.data.MapLibrary
import ir.atlasgis.app.data.MapStyleBuilder
import ir.atlasgis.app.data.MeasureMode
import ir.atlasgis.app.data.OverlayGeoJson
import ir.atlasgis.app.data.RecordingState
import ir.atlasgis.app.data.SettingsStore
import ir.atlasgis.app.data.Track
import ir.atlasgis.app.data.Waypoint
import ir.atlasgis.app.data.WaypointCategory
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

enum class Tool { BROWSE, MEASURE }

enum class Sheet { NONE, LAYERS, WAYPOINTS, TRACKS, SETTINGS }

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

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val library = MapLibrary(application)
    private val db = AppDatabase(application)
    private val settingsStore = SettingsStore(application)
    private val locationProvider = ir.atlasgis.app.data.LocationProvider(application)

    val controller = MapController(application)

    private val _layers = MutableStateFlow<List<MapLayer>>(emptyList())
    val layers: StateFlow<List<MapLayer>> = _layers.asStateFlow()

    private val _waypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    val waypoints: StateFlow<List<Waypoint>> = _waypoints.asStateFlow()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _selectedWaypointId = MutableStateFlow<String?>(null)
    val selectedWaypointId: StateFlow<String?> = _selectedWaypointId.asStateFlow()

    private val _tool = MutableStateFlow(Tool.BROWSE)
    val tool: StateFlow<Tool> = _tool.asStateFlow()

    private val _measure = MutableStateFlow(MeasureUiState())
    val measure: StateFlow<MeasureUiState> = _measure.asStateFlow()

    private val _recording = MutableStateFlow(RecordingState())
    val recording: StateFlow<RecordingState> = _recording.asStateFlow()

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

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private val _gps = MutableStateFlow<GpsFix?>(null)
    val gps: StateFlow<GpsFix?> = _gps.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsStore.settings

    private var activeSession: TrackSession? = null
    private var tickerJob: Job? = null
    private var lastPersistedAt = 0L
    private var lastLivePushAt = 0L
    private var lastStyleKey: String? = null
    private var hadSavedCamera = false

    private val _liveTrackPoints = MutableStateFlow<List<LatLon>>(emptyList())

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
            if (!_measure.value.active) _pendingWaypoint.value = point
        }
        controller.onCameraIdle = { info ->
            settingsStore.saveCamera(
                info.center.lat, info.center.lon, info.zoom, info.bearing.toDouble(),
            )
        }

        viewModelScope.launch(Dispatchers.IO) { db.closeDanglingRecordings() }
        loadAll()
        observeGps()
    }

    // ------------------------------------------------------------------ loading

    private fun loadAll() {
        viewModelScope.launch {
            val loadedLayers = withContext(Dispatchers.IO) { library.layers() }
            _layers.value = loadedLayers
            _waypoints.value = withContext(Dispatchers.IO) { db.readWaypoints() }
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
                    .append(layer.opacity).append(':').append(layer.sortOrder).append(';')
            }
        }
        if (!force && key == lastStyleKey) return
        lastStyleKey = key
        controller.loadStyle(MapStyleBuilder.build(current))
    }

    private fun pushOverlays() {
        val measureState = _measure.value
        controller.pushOverlays(
            waypointsJson = OverlayGeoJson.waypoints(_waypoints.value, _selectedWaypointId.value),
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
            activeTrackJson = OverlayGeoJson.line(_liveTrackPoints.value),
            gpsJson = OverlayGeoJson.gpsPoint(_gps.value),
            accuracyJson = OverlayGeoJson.accuracyRing(_gps.value),
        )
    }

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
        val screen = controller.screenPosition(point)
        val waypointId = screen?.let { controller.waypointIdAt(it) }
        if (waypointId != null) {
            _selectedWaypointId.value = waypointId
            pushOverlays()
        } else {
            _selectedWaypointId.value = null
            pushOverlays()
        }
    }

    // ----------------------------------------------------------------- waypoints

    fun requestWaypointAt(point: LatLon) {
        _pendingWaypoint.value = point
    }

    fun cancelPendingWaypoint() {
        _pendingWaypoint.value = null
    }

    fun createWaypoint(
        point: LatLon,
        name: String,
        note: String,
        category: WaypointCategory,
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
        _selectedWaypointId.value = waypoint.id
        controller.flyTo(waypoint.position, max(controller.zoom(), 15.0))
        pushOverlays()
    }

    // ------------------------------------------------------------------ measuring

    fun startMeasure(mode: MeasureMode) {
        _selectedWaypointId.value = null
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

    // ---------------------------------------------------------------- navigation

    fun zoomIn() = controller.zoomBy(1.0)

    fun zoomOut() = controller.zoomBy(-1.0)

    fun resetBearing() = controller.resetBearing()

    fun toggleFollowMode() {
        val next = !_followMode.value
        _followMode.value = next
        if (next) {
            val fix = _gps.value
            if (fix != null) {
                controller.flyTo(LatLon(fix.lat, fix.lon), max(controller.zoom(), 15.0))
            } else {
                _messages.tryEmit("در انتظار دریافت موقعیت GPS…")
            }
        }
    }

    fun centerOnLocation() {
        val fix = _gps.value
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

    fun cycleCoordinateFormat() = settingsStore.cycleCoordinateFormat()

    fun setCoordinateFormat(format: ir.atlasgis.app.core.CoordinateFormat) =
        settingsStore.updateCoordinateFormat(format)

    fun setKeepScreenOn(enabled: Boolean) = settingsStore.setKeepScreenOn(enabled)

    fun setShowScaleBar(enabled: Boolean) = settingsStore.setShowScaleBar(enabled)

    fun setShowCoordinateBar(enabled: Boolean) = settingsStore.setShowCoordinateBar(enabled)

    fun setShowTracks(enabled: Boolean) {
        _showTracks.value = enabled
        pushOverlays()
    }

    // ----------------------------------------------------------------- recording

    fun startRecording() {
        if (activeSession != null) return
        if (!locationProvider.hasPermission()) {
            _messages.tryEmit("برای ثبت مسیر، دسترسی موقعیت مکانی لازم است")
            return
        }
        if (!locationProvider.locationServicesEnabled()) {
            _messages.tryEmit("GPS دستگاه خاموش است؛ پس از روشن شدن، ثبت ادامه می‌یابد")
        }
        val session = TrackSession(
            id = AppDatabase.newId(),
            name = TrackSession.newName(),
            startedAt = System.currentTimeMillis(),
        )
        activeSession = session
        _liveTrackPoints.value = emptyList()
        _recording.value = session.snapshot()
        _tool.value = Tool.BROWSE
        _measure.value = MeasureUiState()
        lastPersistedAt = System.currentTimeMillis()
        lastLivePushAt = 0L
        locationProvider.start()
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                activeSession?.let { _recording.value = it.snapshot() }
            }
        }
        pushOverlays()
        _messages.tryEmit("ثبت مسیر آغاز شد")
    }

    fun pauseRecording() {
        val session = activeSession ?: return
        session.pause()
        _recording.value = session.snapshot()
        persistSession()
        _messages.tryEmit("ثبت مسیر موقتاً متوقف شد")
    }

    fun resumeRecording() {
        val session = activeSession ?: return
        session.resume()
        _recording.value = session.snapshot()
    }

    fun stopRecording() {
        val session = activeSession ?: return
        activeSession = null
        tickerJob?.cancel()
        tickerJob = null
        if (session.pointCount < 2) {
            _recording.value = RecordingState()
            _liveTrackPoints.value = emptyList()
            pushOverlays()
            _messages.tryEmit("برای ذخیره مسیر، حداقل دو نقطه لازم است")
            return
        }
        val track = session.toTrack(recording = false)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.upsertTrack(track) }
            _tracks.value = withContext(Dispatchers.IO) { db.readTracks() }
            _recording.value = RecordingState()
            _liveTrackPoints.value = emptyList()
            pushOverlays()
            _messages.tryEmit("مسیر با ${track.points.size} نقطه ذخیره شد")
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

    private fun persistSession() {
        val session = activeSession ?: return
        val track = session.toTrack(recording = true)
        viewModelScope.launch(Dispatchers.IO) { db.upsertTrack(track) }
    }

    // ------------------------------------------------------------------------ gps

    private fun observeGps() {
        viewModelScope.launch {
            locationProvider.fix.collect { fix ->
                _gps.value = fix
                if (fix == null) return@collect
                if (_followMode.value) {
                    controller.followTo(
                        LatLon(fix.lat, fix.lon),
                        controller.zoom().coerceAtLeast(14.0),
                    )
                }
                val session = activeSession
                if (session != null && !session.paused) {
                    if (session.addFix(fix)) {
                        val now = System.currentTimeMillis()
                        _recording.value = session.snapshot()
                        if (now - lastLivePushAt >= 900L) {
                            lastLivePushAt = now
                            _liveTrackPoints.value = session.pointsForLivePreview()
                        }
                        if (now - lastPersistedAt >= 15_000L) {
                            lastPersistedAt = now
                            persistSession()
                        }
                    }
                }
                updateGpsOverlays(fix)
            }
        }
    }

    private fun updateGpsOverlays(fix: GpsFix) {
        controller.updateSource(MapStyleBuilder.SRC_GPS, OverlayGeoJson.gpsPoint(fix))
        controller.updateSource(MapStyleBuilder.SRC_GPS_ACCURACY, OverlayGeoJson.accuracyRing(fix))
        if (_liveTrackPoints.value.isNotEmpty()) {
            controller.updateSource(
                MapStyleBuilder.SRC_ACTIVE_TRACK,
                OverlayGeoJson.line(_liveTrackPoints.value),
            )
        }
    }

    fun onLocationPermissionGranted() {
        locationProvider.start()
    }

    fun onScreenResumed() {
        if (locationProvider.hasPermission()) locationProvider.start()
    }

    fun onScreenPaused() {
        if (activeSession == null) locationProvider.stop()
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

    fun writeExport(uri: Uri, kind: ExportKind) {
        viewModelScope.launch {
            runCatching {
                val content = exportContent(kind)
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
                val text = withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver
                        .openInputStream(uri) ?: error("خواندن فایل ممکن نشد")
                    stream.bufferedReader().use { it.readText() }
                }
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

    // -------------------------------------------------------------------- go to

    fun goToCoordinate(input: String): Boolean {
        val parsed = parseLatLon(input)
        if (parsed == null) {
            _messages.tryEmit("مختصات معتبر نیست؛ نمونه: 35.6892, 51.3890")
            return false
        }
        controller.flyTo(parsed, max(controller.zoom(), 14.0))
        return true
    }

    fun parseLatLon(input: String): LatLon? {
        val latin = input.map { ch ->
            if (ch in '۰'..'۹') ('0' + (ch - '۰')) else ch
        }.joinToString("")
            .replace('٫', '.')
            .replace('،', ' ')
            .replace('°', ' ')
            .replace('′', ' ')
            .replace('″', ' ')
            .replace(Regex("[NSEWnsew]"), " ")
        val tokens = Regex("[-+]?\\d+(?:\\.\\d+)?").findAll(latin)
            .mapNotNull { it.value.toDoubleOrNull() }
            .toList()
        return when {
            tokens.size >= 6 -> {
                val lat = dmsToDecimal(tokens[0], tokens[1], tokens[2])
                val lon = dmsToDecimal(tokens[3], tokens[4], tokens[5])
                LatLon(lat, lon).takeIf { it.isValid() }
            }
            tokens.size >= 2 -> LatLon(tokens[0], tokens[1]).takeIf { it.isValid() }
            else -> null
        }
    }

    private fun dmsToDecimal(degrees: Double, minutes: Double, seconds: Double): Double {
        val sign = if (degrees < 0) -1.0 else 1.0
        return sign * (kotlin.math.abs(degrees) + minutes / 60.0 + seconds / 3600.0)
    }

    fun notify(message: String) {
        _messages.tryEmit(message)
    }

    override fun onCleared() {
        tickerJob?.cancel()
        locationProvider.stop()
        val session = activeSession
        activeSession = null
        if (session != null && session.pointCount >= 2) {
            val track = session.toTrack(recording = false)
            Thread { runCatching { db.upsertTrack(track) } }.start()
        }
        super.onCleared()
    }
}
