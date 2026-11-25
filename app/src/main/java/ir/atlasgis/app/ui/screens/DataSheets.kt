package ir.atlasgis.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.atlasgis.app.core.CoordinateFormat
import ir.atlasgis.app.core.analyzeTrack
import ir.atlasgis.app.core.distanceMeters
import ir.atlasgis.app.core.formatArea
import ir.atlasgis.app.core.formatCoordinate
import ir.atlasgis.app.core.formatDistance
import ir.atlasgis.app.core.formatDuration
import ir.atlasgis.app.core.formatElevation
import ir.atlasgis.app.core.formatSpeed
import ir.atlasgis.app.core.toPersianDigits
import ir.atlasgis.app.data.AppSettings
import ir.atlasgis.app.data.GpsFix
import ir.atlasgis.app.data.PlaceIndexer
import ir.atlasgis.app.data.Track
import ir.atlasgis.app.data.Waypoint
import ir.atlasgis.app.data.WaypointCategoryStyle
import ir.atlasgis.app.ui.components.GlassPanel
import ir.atlasgis.app.ui.components.RoundIconButton
import ir.atlasgis.app.ui.components.SectionHeader
import ir.atlasgis.app.ui.components.SwitchRow
import ir.atlasgis.app.ui.theme.AtlasColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaypointsSheet(
    waypoints: List<Waypoint>,
    gps: GpsFix?,
    categoryStyles: List<WaypointCategoryStyle>,
    coordinateFormat: CoordinateFormat = CoordinateFormat.DECIMAL,
    onDismiss: () -> Unit,
    onAddAtCenter: () -> Unit,
    onShow: (Waypoint) -> Unit,
    onEdit: (Waypoint) -> Unit,
    onDelete: (Waypoint) -> Unit,
    onShare: (Waypoint) -> Unit,
    onFitAll: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onCategoryStyleSave: (WaypointCategoryStyle) -> Unit,
    onCategoryDelete: (WaypointCategoryStyle) -> Unit,
    onCategoryVisibility: (WaypointCategoryStyle, Boolean) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var manageCategories by remember { mutableStateOf(false) }
    val filtered = remember(waypoints, query) {
        val needle = PlaceIndexer.normalize(query)
        if (needle.isEmpty()) {
            waypoints
        } else {
            waypoints.filter {
                PlaceIndexer.normalize(it.name).contains(needle) ||
                    PlaceIndexer.normalize(it.note).contains(needle)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AtlasColors.Surface,
        contentColor = AtlasColors.TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AtlasColors.Outline) },
    ) {
        Column(
            Modifier
                .fillMaxHeight(0.96f)
                .padding(horizontal = 12.dp),
        ) {
            SectionHeader(
                title = "نقاط ذخیره‌شده",
                subtitle = "${waypoints.size.toString().toPersianDigits()} نقطه",
                action = {
                    Row {
                        IconButton(onClick = { manageCategories = true }) {
                            Icon(Icons.Rounded.Category, "دسته‌بندی‌ها", tint = AtlasColors.TextSecondary)
                        }
                        IconButton(onClick = onImport) {
                            Icon(Icons.Rounded.FileDownload, "ورود فایل", tint = AtlasColors.TextSecondary)
                        }
                        IconButton(onClick = onExport) {
                            Icon(Icons.Rounded.FileUpload, "خروجی GPX", tint = AtlasColors.TextSecondary)
                        }
                        IconButton(onClick = onAddAtCenter) {
                            Icon(Icons.Rounded.AddLocationAlt, "افزودن نقطه", tint = AtlasColors.Accent)
                        }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("جست‌وجو در نام و یادداشت", color = AtlasColors.TextMuted) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = AtlasColors.TextMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = atlasTextFieldColors(),
            )
            Spacer(Modifier.height(8.dp))
            // One-tap filter: only the selected categories are drawn on the map.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                categoryStyles.forEach { style ->
                    val color = Color(style.color)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (style.visible) color.copy(alpha = 0.18f) else AtlasColors.SurfaceElevated,
                            )
                            .clickable { onCategoryVisibility(style, !style.visible) }
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = categoryIconFor(style.icon),
                            contentDescription = null,
                            tint = if (style.visible) color else AtlasColors.TextMuted,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = style.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (style.visible) color else AtlasColors.TextMuted,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Rounded.Place,
                        contentDescription = null,
                        tint = if (waypoints.isEmpty()) AtlasColors.Accent else AtlasColors.TextMuted,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (waypoints.isEmpty()) {
                            "با نگه‌داشتن انگشت روی نقشه یا دکمه + یک نقطه بسازید."
                        } else {
                            "نقطه‌ای با این جست‌وجو پیدا نشد."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = AtlasColors.TextMuted,
                        textAlign = TextAlign.Center,
                    )
                    if (waypoints.isEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onAddAtCenter,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AtlasColors.Accent,
                                contentColor = AtlasColors.OnAccent,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Rounded.AddLocationAlt, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("افزودن نقطه در مرکز نقشه", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(filtered, key = { it.id }) { waypoint ->
                        WaypointRow(
                            waypoint = waypoint,
                            distanceMeters = gps?.let {
                                distanceMeters(
                                    ir.atlasgis.app.core.LatLon(it.lat, it.lon),
                                    waypoint.position,
                                )
                            },
                            coordinateFormat = coordinateFormat,
                            onClick = { onShow(waypoint) },
                            onEdit = { onEdit(waypoint) },
                            onDelete = { onDelete(waypoint) },
                            onShare = { onShare(waypoint) },
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onFitAll) {
                    Icon(Icons.Rounded.CenterFocusStrong, null, tint = AtlasColors.TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("نمایش همه نقاط", color = AtlasColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    if (manageCategories) {
        CategoryManagerDialog(
            styles = categoryStyles,
            onDismiss = { manageCategories = false },
            onSave = onCategoryStyleSave,
            onDelete = onCategoryDelete,
            onVisibility = onCategoryVisibility,
        )
    }
}

@Composable
private fun WaypointRow(
    waypoint: Waypoint,
    distanceMeters: Double?,
    coordinateFormat: CoordinateFormat,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(categoryColor(waypoint.category).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    categoryIcon(waypoint.category),
                    contentDescription = null,
                    tint = categoryColor(waypoint.category),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    waypoint.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = AtlasColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(categoryLabel(waypoint.category))
                        if (distanceMeters != null) append(" · ${formatDistance(distanceMeters)}")
                        else append(" · ${formatCoordinate(waypoint.lat, waypoint.lon, coordinateFormat)}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = AtlasColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RoundIconButton(Icons.Rounded.Share, "اشتراک‌گذاری", onShare, tint = AtlasColors.TextSecondary)
            RoundIconButton(Icons.Rounded.Edit, "ویرایش", onEdit, tint = AtlasColors.TextSecondary)
            RoundIconButton(Icons.Rounded.Delete, "حذف", onDelete, tint = AtlasColors.TextMuted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksSheet(
    tracks: List<Track>,
    showTracks: Boolean,
    onDismiss: () -> Unit,
    onShowTracksChange: (Boolean) -> Unit,
    onShow: (Track) -> Unit,
    onDelete: (Track) -> Unit,
    onRename: (Track, String) -> Unit,
    onExportAll: (Boolean) -> Unit,
    onExportTrack: (Track, Boolean) -> Unit,
    onFollow: (Track) -> Unit,
    onShare: (Track, Boolean) -> Unit,
    onImport: () -> Unit,
) {
    var detail by remember { mutableStateOf<Track?>(null) }
    var renameTarget by remember { mutableStateOf<Track?>(null) }
    var deleteTarget by remember { mutableStateOf<Track?>(null) }
    var exportMenuOpen by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AtlasColors.Surface,
        contentColor = AtlasColors.TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AtlasColors.Outline) },
    ) {
        Column(
            Modifier
                .fillMaxHeight(0.96f)
                .padding(horizontal = 12.dp),
        ) {
            SectionHeader(
                title = "مسیرهای ثبت‌شده",
                subtitle = "${tracks.size.toString().toPersianDigits()} مسیر · ${formatDistance(tracks.sumOf { it.distanceMeters })}",
                action = {
                    Row {
                        IconButton(onClick = onImport) {
                            Icon(Icons.Rounded.FileDownload, "ورود فایل", tint = AtlasColors.TextSecondary)
                        }
                        Box {
                            IconButton(onClick = { exportMenuOpen = true }) {
                                Icon(Icons.Rounded.FileUpload, "خروجی فایل", tint = AtlasColors.TextSecondary)
                            }
                            DropdownMenu(
                                expanded = exportMenuOpen,
                                onDismissRequest = { exportMenuOpen = false },
                                containerColor = AtlasColors.SurfaceElevated,
                            ) {
                                DropdownMenuItem(
                                    text = { Text("خروجی GPX همه مسیرها", color = AtlasColors.TextPrimary) },
                                    onClick = { exportMenuOpen = false; onExportAll(true) },
                                )
                                DropdownMenuItem(
                                    text = { Text("خروجی GeoJSON همه مسیرها", color = AtlasColors.TextPrimary) },
                                    onClick = { exportMenuOpen = false; onExportAll(false) },
                                )
                            }
                        }
                    }
                },
            )
            Spacer(Modifier.height(6.dp))
            SwitchRow(
                title = "نمایش مسیرها روی نقشه",
                description = "همه مسیرهای ذخیره‌شده به‌صورت خط نارنجی نمایش داده می‌شوند",
                checked = showTracks,
                onCheckedChange = onShowTracksChange,
            )
            Spacer(Modifier.height(4.dp))
            if (tracks.isEmpty()) {
                Box(Modifier.weight(1f)) {
                    Text(
                        "هنوز مسیری ثبت نشده است. با دکمه «ثبت مسیر» شروع کنید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AtlasColors.TextMuted,
                        modifier = Modifier.padding(vertical = 22.dp),
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 26.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(tracks, key = { it.id }) { track ->
                        TrackRow(
                            track = track,
                            onClick = { detail = track },
                        )
                    }
                }
            }
        }
    }

    detail?.let { track ->
        TrackDetailDialog(
            track = track,
            onDismiss = { detail = null },
            onShow = {
                detail = null
                onShow(track)
            },
            onRename = {
                detail = null
                renameTarget = track
            },
            onDelete = {
                detail = null
                deleteTarget = track
            },
            onExport = { gpx ->
                detail = null
                onExportTrack(track, gpx)
            },
            onFollow = {
                detail = null
                onFollow(track)
            },
            onShare = { gpx -> onShare(track, gpx) },
        )
    }

    renameTarget?.let { track ->
        RenameTrackDialog(
            initialName = track.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                onRename(track, name)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { track ->
        DeleteConfirmDialog(
            title = "حذف مسیر",
            message = "مسیر «${track.name}» با ${track.points.size.toString().toPersianDigits()} نقطه حذف شود؟ این کار قابل بازگشت نیست.",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                onDelete(track)
                deleteTarget = null
            },
        )
    }
}

@Composable
private fun TrackRow(track: Track, onClick: () -> Unit) {
    val date = remember(track.startedAt) {
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
            .format(Date(track.startedAt))
            .toPersianDigits()
    }
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AtlasColors.Track.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Route, null, tint = AtlasColors.Track, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = AtlasColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatDistance(track.distanceMeters)} · ${formatDuration(track.durationSeconds)} · " +
                        "${track.points.size.toString().toPersianDigits()} نقطه · $date",
                    style = MaterialTheme.typography.labelSmall,
                    color = AtlasColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (track.maxSpeedMps > 0) {
                    Text(
                        text = "بیشینه سرعت ${formatSpeed(track.maxSpeedMps)}" +
                            if (track.ascentMeters > 0) " · صعود ${formatDistance(track.ascentMeters)}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = AtlasColors.TextMuted,
                    )
                }
            }
            Icon(
                Icons.Rounded.ChevronLeft,
                contentDescription = null,
                tint = AtlasColors.TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Full details for one saved track: stats, map preview and file operations. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TrackDetailDialog(
    track: Track,
    onDismiss: () -> Unit,
    onShow: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: (Boolean) -> Unit,
    onFollow: () -> Unit,
    onShare: (Boolean) -> Unit,
) {
    val date = remember(track.startedAt) {
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
            .format(Date(track.startedAt))
            .toPersianDigits()
    }
    val analysis = remember(track) { analyzeTrack(track) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AtlasColors.SurfaceElevated,
        title = { Text(track.name, color = AtlasColors.TextPrimary) },
        text = {
            Column(
                Modifier
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "شروع: $date · ${track.points.size.toString().toPersianDigits()} نقطه ثبت‌شده",
                    style = MaterialTheme.typography.labelSmall,
                    color = AtlasColors.TextMuted,
                )
                Spacer(Modifier.height(12.dp))
                TrackStatRow("مسافت", formatDistance(analysis.totalDistanceMeters))
                TrackStatRow("مدت کل", formatDuration(analysis.totalSeconds))
                TrackStatRow("زمان حرکت", formatDuration(analysis.movingSeconds))
                TrackStatRow("زمان توقف", formatDuration(analysis.stoppedSeconds))
                TrackStatRow("میانگین سرعت", formatSpeed(analysis.averageMovingSpeedMps))
                TrackStatRow("سرعت بیشینه", formatSpeed(track.maxSpeedMps))
                TrackStatRow("صعود", formatDistance(analysis.ascentMeters))
                TrackStatRow("فرود", formatDistance(analysis.descentMeters))
                if (analysis.hasElevation) {
                    TrackStatRow(
                        "ارتفاع",
                        "${formatElevation(analysis.minElevation)} تا ${formatElevation(analysis.maxElevation)}",
                    )
                }
                Spacer(Modifier.height(12.dp))
                TrackProfileChart(analysis, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                TrackSplitsList(analysis)
                Spacer(Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HudActionButton(
                        "دنبال‌کردن مسیر",
                        Icons.Rounded.Navigation,
                        onFollow,
                        accent = AtlasColors.Info,
                    )
                    HudActionButton("اشتراک GPX", Icons.Rounded.Share, { onShare(true) })
                    HudActionButton("خروجی GPX", Icons.Rounded.FileUpload, { onExport(true) })
                    HudActionButton("خروجی GeoJSON", Icons.Rounded.DataObject, { onExport(false) })
                    HudActionButton("تغییر نام", Icons.Rounded.Edit, onRename)
                    HudActionButton("حذف", Icons.Rounded.Delete, onDelete, accent = AtlasColors.Danger)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onShow) { Text("نمایش روی نقشه", color = AtlasColors.Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("بستن", color = AtlasColors.TextSecondary) }
        },
    )
}

@Composable
private fun TrackStatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = AtlasColors.TextMuted,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.titleSmall, color = AtlasColors.TextPrimary)
    }
}

@Composable
private fun RenameTrackDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AtlasColors.SurfaceElevated,
        title = { Text("نام مسیر", color = AtlasColors.TextPrimary) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(80) },
                singleLine = true,
                colors = atlasTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text("ذخیره", color = AtlasColors.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف", color = AtlasColors.TextSecondary) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    layerCount: Int,
    storageBytes: Long,
    waypointCount: Int,
    trackCount: Int,
    onDismiss: () -> Unit,
    onCoordinateFormat: (CoordinateFormat) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onScaleBar: (Boolean) -> Unit,
    onShowCoordinates: (Boolean) -> Unit,
    onGoTo: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AtlasColors.Surface,
        contentColor = AtlasColors.TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AtlasColors.Outline) },
    ) {
        Column(
            Modifier
                .fillMaxHeight(0.96f)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
        ) {
            SectionHeader(title = "تنظیمات", subtitle = "نمایش مختصات و رفتار نقشه")
            Spacer(Modifier.height(14.dp))

            Text("قالب نمایش مختصات", style = MaterialTheme.typography.labelMedium, color = AtlasColors.TextSecondary)
            Spacer(Modifier.height(8.dp))
            Text(
                "این قالب روی نوار مختصات نقشه و کارت نقطه اثر می‌گذارد.",
                style = MaterialTheme.typography.labelSmall,
                color = AtlasColors.TextMuted,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CoordinateFormat.entries.forEach { format ->
                    val selected = settings.coordinateFormat == format
                    Text(
                        text = format.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) AtlasColors.OnAccent else AtlasColors.TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) AtlasColors.Accent else AtlasColors.SurfaceElevated)
                            .clickable { onCoordinateFormat(format) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            SwitchRow(
                title = "روشن نگه‌داشتن صفحه",
                description = "در هنگام کار میدانی از خاموش‌شدن نمایشگر جلوگیری می‌کند",
                checked = settings.keepScreenOn,
                onCheckedChange = onKeepScreenOn,
            )
            SwitchRow(
                title = "نمایش مقیاس",
                description = "نوار مقیاس در بالای نقشه",
                checked = settings.showScaleBar,
                onCheckedChange = onScaleBar,
            )
            SwitchRow(
                title = "نمایش مختصات مرکز",
                description = "مختصات مرکز نقشه را با قالب انتخابی نشان می‌دهد؛ برای کپی روی آن بزنید",
                checked = settings.showCoordinates,
                onCheckedChange = onShowCoordinates,
            )
            Spacer(Modifier.height(10.dp))

            GlassPanel(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .clickable(onClick = onGoTo)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.PinDrop, null, tint = AtlasColors.Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("پرش به مختصات", style = MaterialTheme.typography.titleSmall, color = AtlasColors.TextPrimary)
                        Text(
                            "ورود مختصات اعشاری یا درجه/دقیقه/ثانیه",
                            style = MaterialTheme.typography.labelSmall,
                            color = AtlasColors.TextMuted,
                        )
                    }
                    Icon(Icons.Rounded.ChevronLeft, null, tint = AtlasColors.TextMuted)
                }
            }
            Spacer(Modifier.height(14.dp))

            Text("وضعیت ذخیره‌سازی", style = MaterialTheme.typography.labelMedium, color = AtlasColors.TextSecondary)
            Spacer(Modifier.height(8.dp))
            GlassPanel(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp)) {
                    MiniStat("نقشه‌ها", layerCount.toString().toPersianDigits(), Modifier.weight(1f))
                    MiniStat("حجم نقشه", formatBytes(storageBytes), Modifier.weight(1f))
                    MiniStat("نقاط", waypointCount.toString().toPersianDigits(), Modifier.weight(1f))
                    MiniStat("مسیرها", trackCount.toString().toPersianDigits(), Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(14.dp))

            Text(
                "پشتیبان‌گیری و بازیابی",
                style = MaterialTheme.typography.labelMedium,
                color = AtlasColors.TextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            GlassPanel(Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier
                            .clickable(onClick = onBackup)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.SettingsBackupRestore,
                            null,
                            tint = AtlasColors.Accent,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "ساخت پشتیبان کامل",
                                style = MaterialTheme.typography.titleSmall,
                                color = AtlasColors.TextPrimary,
                            )
                            Text(
                                "همهٔ نقاط، مسیرها و دسته‌بندی‌ها در یک فایل JSON",
                                style = MaterialTheme.typography.labelSmall,
                                color = AtlasColors.TextMuted,
                            )
                        }
                        Icon(Icons.Rounded.ChevronLeft, null, tint = AtlasColors.TextMuted)
                    }
                    HorizontalDivider(color = AtlasColors.OutlineSoft)
                    Row(
                        modifier = Modifier
                            .clickable(onClick = onRestore)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Restore,
                            null,
                            tint = AtlasColors.Info,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "بازگردانی از پشتیبان",
                                style = MaterialTheme.typography.titleSmall,
                                color = AtlasColors.TextPrimary,
                            )
                            Text(
                                "نقاط هم‌شناسه به‌روزرسانی می‌شوند؛ دادهٔ فعلی پاک نمی‌شود",
                                style = MaterialTheme.typography.labelSmall,
                                color = AtlasColors.TextMuted,
                            )
                        }
                        Icon(Icons.Rounded.ChevronLeft, null, tint = AtlasColors.TextMuted)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            Text(
                text = "AtlasGIS 1.1.0 — نمایش آفلاین MBTiles با موتور رندر MapLibre Native (OpenGL ES). " +
                    "همه داده‌ها روی همین دستگاه ذخیره می‌شوند و برنامه بدون اینترنت کار می‌کند.",
                style = MaterialTheme.typography.labelSmall,
                color = AtlasColors.TextMuted,
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleSmall, color = AtlasColors.TextPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = AtlasColors.TextMuted)
    }
}
