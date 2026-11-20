package ir.atlasgis.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.atlasgis.app.core.LatLon
import ir.atlasgis.app.core.distanceMeters
import ir.atlasgis.app.core.formatCoordinate
import ir.atlasgis.app.data.GpsFix
import ir.atlasgis.app.data.MapLayer
import ir.atlasgis.app.data.MeasureMode
import ir.atlasgis.app.data.RecordingState
import ir.atlasgis.app.data.Waypoint
import ir.atlasgis.app.ui.components.GlassPanel
import ir.atlasgis.app.ui.map.MapCanvas
import ir.atlasgis.app.ui.map.MapController
import ir.atlasgis.app.ui.screens.*
import ir.atlasgis.app.ui.theme.AtlasColors
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private data class RecordingBadge(val active: Boolean, val paused: Boolean)

/** A pending "save as" export, optionally scoped to one saved track. */
private data class PendingExport(
    val kind: MapViewModel.ExportKind,
    val trackId: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MapViewModel) {
    val context = LocalContext.current
    val controller = viewModel.controller

    val layers by viewModel.layers.collectAsStateWithLifecycle()
    val waypoints by viewModel.waypoints.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val selectedWaypointId by viewModel.selectedWaypointId.collectAsStateWithLifecycle()
    val measure by viewModel.measure.collectAsStateWithLifecycle()
    val tool by viewModel.tool.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val followMode by viewModel.followMode.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val pendingWaypoint by viewModel.pendingWaypoint.collectAsStateWithLifecycle()
    val editingWaypoint by viewModel.editingWaypoint.collectAsStateWithLifecycle()
    val showTracks by viewModel.showTracks.collectAsStateWithLifecycle()
    val gps by viewModel.gps.collectAsStateWithLifecycle()
    val categoryStyles by viewModel.categoryStyles.collectAsStateWithLifecycle()
    val cardsScroll = rememberScrollState()

    val recordingBadge by remember(viewModel) {
        viewModel.recording.map { RecordingBadge(it.active, it.paused) }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = RecordingBadge(false, false))

    val snackbarHostState = remember { SnackbarHostState() }
    var deleteWaypointTarget by remember { mutableStateOf<Waypoint?>(null) }
    var pendingExport by remember { mutableStateOf<PendingExport?>(null) }
    var showGoToDialog by remember { mutableStateOf(false) }
    var recordingCardExpanded by remember { mutableStateOf(true) }

    // A fresh recording always starts with the full card visible.
    LaunchedEffect(recordingBadge.active) {
        if (recordingBadge.active) recordingCardExpanded = true
    }

    val mapPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importMap)
    }
    val geoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importGeoFile)
    }
    var styleTarget by remember { mutableStateOf<MapLayer?>(null) }
    val stylePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = styleTarget
        if (uri != null && target != null) viewModel.importLayerStyle(target, uri)
        styleTarget = null
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val request = pendingExport
        if (uri != null && request != null) {
            viewModel.writeExport(uri, request.kind, request.trackId)
        }
        pendingExport = null
    }

    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.values.any { it }
        locationGranted = granted
        if (granted) viewModel.onLocationPermissionGranted() else viewModel.notify("دسترسی مکان داده نشد")
    }

    LaunchedEffect(Unit) {
        if (locationGranted) {
            viewModel.onLocationPermissionGranted()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onScreenResumed()
                Lifecycle.Event.ON_PAUSE -> viewModel.onScreenPaused()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val view = LocalView.current
    LaunchedEffect(settings.keepScreenOn) { view.keepScreenOn = settings.keepScreenOn }

    val selectedWaypoint = remember(selectedWaypointId, waypoints) {
        waypoints.firstOrNull { it.id == selectedWaypointId }
    }
    val activeLayerName = remember(layers) {
        layers.filter { it.visible }.maxByOrNull { it.sortOrder }?.name ?: "بدون لایه فعال"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AtlasColors.Background),
    ) {
        MapCanvas(controller, Modifier.fillMaxSize())

        MeasureLabelsThroughCamera(controller, measure)

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            MapTopBar(
                layerSummary = activeLayerName,
                layerCount = layers.size,
                onLayers = { viewModel.openSheet(Sheet.LAYERS) },
                onSettings = { viewModel.openSheet(Sheet.SETTINGS) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Cards stack up (waypoint + recording + measure). On short screens or
                // in landscape the fixed part must not push the dock off-screen, so the
                // card area scrolls inside a bounded height.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(cardsScroll),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AnimatedVisibility(
                        visible = selectedWaypoint != null,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        selectedWaypoint?.let { waypoint ->
                            WaypointCardLive(
                                viewModel = viewModel,
                                waypoint = waypoint,
                                gpsFlow = viewModel.gps,
                                onClose = { viewModel.selectWaypoint(null) },
                                onEdit = { viewModel.startEditingWaypoint(waypoint) },
                                onDelete = { deleteWaypointTarget = waypoint },
                                onCenter = { viewModel.showWaypointOnMap(waypoint) },
                            )
                        }
                    }

                    RecordingHud(
                        stateFlow = viewModel.recording,
                        expanded = recordingCardExpanded,
                        onCollapse = { recordingCardExpanded = false },
                        onPause = viewModel::pauseRecording,
                        onResume = viewModel::resumeRecording,
                        onStop = viewModel::stopRecording,
                    )

                    AnimatedVisibility(
                        visible = measure.active,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        MeasureCard(
                            state = measure,
                            onModeChange = viewModel::setMeasureMode,
                            onUndo = viewModel::undoMeasurePoint,
                            onClear = viewModel::clearMeasure,
                            onClose = viewModel::stopMeasure,
                        )
                    }
                }

                // When the recording card is closed the compact bar keeps the state
                // visible and opens the card again, while the track keeps recording.
                RecordingMiniHud(
                    stateFlow = viewModel.recording,
                    visible = !recordingCardExpanded,
                    onExpand = { recordingCardExpanded = true },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column {
                        if (settings.showScaleBar) {
                            ControllerScaleBar(controller)
                        }
                        val attribution = remember(layers) {
                            layers.filter { it.visible }.maxByOrNull { it.sortOrder }?.attribution
                        }
                        if (attribution != null) {
                            Text(
                                text = attribution,
                                style = MaterialTheme.typography.labelSmall,
                                color = AtlasColors.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    if (settings.showCoordinateBar) {
                        CoordinateReadout(
                            controller = controller,
                            gpsFlow = viewModel.gps,
                            coordinateFormat = settings.coordinateFormat,
                            onCycleFormat = viewModel::cycleCoordinateFormat,
                        )
                    }
                }

                // Snackbars live in the sheet stack so they never cover the dock.
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = AtlasColors.SurfaceElevated,
                        contentColor = AtlasColors.TextPrimary,
                        actionColor = AtlasColors.Accent,
                        shape = RoundedCornerShape(14.dp),
                    )
                }

                BottomDock(
                    tool = tool,
                    recordingActive = recordingBadge.active,
                    onBrowse = { viewModel.stopMeasure() },
                    onTools = { viewModel.openSheet(Sheet.TOOLS) },
                )
            }
        }

        SideControlsHud(
            controller = controller,
            following = followMode,
            onZoomIn = viewModel::zoomIn,
            onZoomOut = viewModel::zoomOut,
            onResetBearing = viewModel::resetBearing,
            onLocate = viewModel::toggleFollowMode,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .statusBarsPadding()
                .padding(end = 8.dp, top = 76.dp),
        )

        if (layers.isEmpty()) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 70.dp),
            ) {
                EmptyMapsCard(
                    onImport = { mapPicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.padding(bottom = if (locationGranted) 0.dp else 160.dp),
                )
            }
        }

        if (!locationGranted) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 150.dp, start = 20.dp, end = 20.dp),
            ) {
                PermissionGate(onRequest = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                })
            }
        }

    }

    // ------------------------------------------------------------------ sheets

    when (sheet) {
        Sheet.TOOLS -> ToolsSheet(
            waypointCount = waypoints.size,
            trackCount = tracks.size,
            recordingActive = recordingBadge.active,
            recordingPaused = recordingBadge.paused,
            onDismiss = viewModel::closeSheet,
            onMeasure = {
                viewModel.closeSheet()
                if (!measure.active) viewModel.startMeasure(MeasureMode.DISTANCE)
            },
            onRecord = {
                viewModel.closeSheet()
                recordingCardExpanded = true
                if (!recordingBadge.active) viewModel.startRecording()
            },
            onWaypoints = { viewModel.openSheet(Sheet.WAYPOINTS) },
            onTracks = { viewModel.openSheet(Sheet.TRACKS) },
        )

        Sheet.LAYERS -> LayersSheet(
            layers = layers,
            onDismiss = viewModel::closeSheet,
            onImport = { mapPicker.launch(arrayOf("*/*")) },
            onToggle = viewModel::toggleLayer,
            onOpacity = viewModel::setLayerOpacity,
            onMove = viewModel::moveLayer,
            onRename = viewModel::renameLayer,
            onDelete = viewModel::deleteLayer,
            onImportStyle = { layer ->
                styleTarget = layer
                stylePicker.launch(arrayOf("*/*"))
            },
            onClearStyle = viewModel::clearLayerStyle,
            onStylePreset = viewModel::setLayerStylePreset,
        )

        Sheet.WAYPOINTS -> WaypointsSheet(
            waypoints = waypoints,
            gps = gps,
            categoryStyles = categoryStyles,
            onDismiss = viewModel::closeSheet,
            onAddAtCenter = {
                val center = controller.center()
                if (center != null) {
                    viewModel.closeSheet()
                    viewModel.requestWaypointAt(center)
                }
            },
            onShow = viewModel::showWaypointOnMap,
            onEdit = { viewModel.closeSheet(); viewModel.startEditingWaypoint(it) },
            onDelete = { deleteWaypointTarget = it },
            onFitAll = { viewModel.fitAllWaypoints(); viewModel.closeSheet() },
            onExport = {
                pendingExport = PendingExport(MapViewModel.ExportKind.WAYPOINTS_GPX)
                exporter.launch("atlasgis-waypoints.gpx")
            },
            onImport = { geoPicker.launch(arrayOf("*/*")) },
            onCategoryStyleSave = viewModel::updateCategoryStyle,
        )

        Sheet.TRACKS -> TracksSheet(
            tracks = tracks,
            showTracks = showTracks,
            onDismiss = viewModel::closeSheet,
            onShowTracksChange = viewModel::setShowTracks,
            onShow = viewModel::showTrack,
            onDelete = { track -> viewModel.deleteTrack(track.id) },
            onRename = viewModel::renameTrack,
            onExportAll = { gpx ->
                pendingExport = PendingExport(
                    if (gpx) MapViewModel.ExportKind.TRACKS_GPX else MapViewModel.ExportKind.TRACKS_GEOJSON,
                )
                exporter.launch(if (gpx) "atlasgis-tracks.gpx" else "atlasgis-tracks.geojson")
            },
            onExportTrack = { track, gpx ->
                pendingExport = PendingExport(
                    if (gpx) MapViewModel.ExportKind.TRACKS_GPX else MapViewModel.ExportKind.TRACKS_GEOJSON,
                    track.id,
                )
                exporter.launch(safeFileName(track.name, if (gpx) "gpx" else "geojson"))
            },
            onImport = { geoPicker.launch(arrayOf("*/*")) },
        )

        Sheet.SETTINGS -> SettingsSheet(
            settings = settings,
            layerCount = layers.size,
            storageBytes = layers.sumOf { it.fileSizeBytes },
            waypointCount = waypoints.size,
            trackCount = tracks.size,
            onDismiss = viewModel::closeSheet,
            onCoordinateFormat = viewModel::setCoordinateFormat,
            onKeepScreenOn = viewModel::setKeepScreenOn,
            onScaleBar = viewModel::setShowScaleBar,
            onCoordinateBar = viewModel::setShowCoordinateBar,
            onGoTo = {
                viewModel.closeSheet()
                showGoToDialog = true
            },
        )

        Sheet.NONE -> Unit
    }

    // ----------------------------------------------------------------- dialogs

    pendingWaypoint?.let { point ->
        WaypointEditorDialog(
            existing = null,
            lat = point.lat,
            lon = point.lon,
            onDismiss = viewModel::cancelPendingWaypoint,
            onSave = { name, note, category ->
                viewModel.createWaypoint(point, name, note, category)
            },
        )
    }

    editingWaypoint?.let { waypoint ->
        WaypointEditorDialog(
            existing = waypoint,
            lat = waypoint.lat,
            lon = waypoint.lon,
            onDismiss = viewModel::cancelEditingWaypoint,
            onSave = { name, note, category ->
                viewModel.updateWaypoint(waypoint.copy(name = name, note = note, category = category))
            },
        )
    }

    deleteWaypointTarget?.let { waypoint ->
        DeleteConfirmDialog(
            title = "حذف نقطه",
            message = "نقطه «${waypoint.name}» حذف شود؟ این کار قابل بازگشت نیست.",
            onDismiss = { deleteWaypointTarget = null },
            onConfirm = {
                viewModel.deleteWaypoint(waypoint.id)
                deleteWaypointTarget = null
            },
        )
    }

    if (showGoToDialog) {
        GoToCoordinateDialog(
            onDismiss = { showGoToDialog = false },
            onGo = { input -> viewModel.goToCoordinate(input) },
        )
    }

    importing?.let { state -> ImportProgressDialog(state) }
}

// --------------------------------------------------------------------- leaf HUD

@Composable
private fun SideControlsHud(
    controller: MapController,
    following: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetBearing: () -> Unit,
    onLocate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val camera by controller.camera.collectAsStateWithLifecycle()
    MapSideControls(
        bearing = camera?.bearing ?: 0f,
        following = following,
        onZoomIn = onZoomIn,
        onZoomOut = onZoomOut,
        onResetBearing = onResetBearing,
        onLocate = onLocate,
        modifier = modifier,
    )
}

@Composable
private fun ControllerScaleBar(controller: MapController) {
    val camera by controller.camera.collectAsStateWithLifecycle()
    val metersPerPixel = camera?.metersPerPixel ?: 0.0
    GlassPanel(shape = RoundedCornerShape(10.dp)) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            MapScaleBar(metersPerPixel)
        }
    }
}

@Composable
private fun CoordinateReadout(
    controller: MapController,
    gpsFlow: StateFlow<GpsFix?>,
    coordinateFormat: ir.atlasgis.app.core.CoordinateFormat,
    onCycleFormat: () -> Unit,
) {
    val camera by controller.camera.collectAsStateWithLifecycle()
    val gps by gpsFlow.collectAsStateWithLifecycle()
    camera?.let { info ->
        CoordinateBar(
            coordinateText = formatCoordinate(info.center.lat, info.center.lon, coordinateFormat),
            gps = gps,
            onCycleFormat = onCycleFormat,
        )
    }
}

@Composable
private fun MeasureLabelsThroughCamera(controller: MapController, measure: MeasureUiState) {
    val camera by controller.camera.collectAsStateWithLifecycle()
    MeasureLabelsOverlay(controller, measure, camera)
}

@Composable
private fun RecordingHud(
    stateFlow: StateFlow<RecordingState>,
    expanded: Boolean,
    onCollapse: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val state by stateFlow.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = state.active && expanded,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        RecordingCard(
            state = state,
            onPause = onPause,
            onResume = onResume,
            onStop = onStop,
            onCollapse = onCollapse,
        )
    }
}

@Composable
private fun RecordingMiniHud(
    stateFlow: StateFlow<RecordingState>,
    visible: Boolean,
    onExpand: () -> Unit,
) {
    val state by stateFlow.collectAsStateWithLifecycle()
    androidx.compose.animation.AnimatedVisibility(
        visible = visible && state.active,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut(),
    ) {
        RecordingMiniBar(state = state, onExpand = onExpand)
    }
}

/** Keeps track names usable as file names on every document provider. */
private fun safeFileName(raw: String, extension: String): String {
    val cleaned = raw.trim()
        .replace(Regex("[\\\\/:*?\"<>|]"), "-")
        .replace(Regex("\\s+"), "-")
        .take(48)
        .ifBlank { "atlasgis-track" }
    return "$cleaned.$extension"
}

@Composable
private fun WaypointCardLive(
    viewModel: MapViewModel,
    waypoint: Waypoint,
    gpsFlow: StateFlow<GpsFix?>,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCenter: () -> Unit,
) {
    val gps by gpsFlow.collectAsStateWithLifecycle()
    val distance = gps?.let {
        distanceMeters(LatLon(it.lat, it.lon), waypoint.position)
    }
    WaypointCard(
        waypoint = waypoint,
        distanceMeters = distance,
        onClose = onClose,
        onEdit = onEdit,
        onDelete = onDelete,
        onCenter = onCenter,
    )
}
