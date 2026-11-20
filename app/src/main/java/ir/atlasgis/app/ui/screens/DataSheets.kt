package ir.atlasgis.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.atlasgis.app.core.CoordinateFormat
import ir.atlasgis.app.core.distanceMeters
import ir.atlasgis.app.core.formatArea
import ir.atlasgis.app.core.formatCoordinate
import ir.atlasgis.app.core.formatDistance
import ir.atlasgis.app.core.formatDuration
import ir.atlasgis.app.core.formatSpeed
import ir.atlasgis.app.core.toPersianDigits
import ir.atlasgis.app.data.AppSettings
import ir.atlasgis.app.data.GpsFix
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
    onDismiss: () -> Unit,
    onAddAtCenter: () -> Unit,
    onShow: (Waypoint) -> Unit,
    onEdit: (Waypoint) -> Unit,
    onDelete: (Waypoint) -> Unit,
    onFitAll: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onCategoryStyleSave: (WaypointCategoryStyle) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var manageCategories by remember { mutableStateOf(false) }
    val filtered = remember(waypoints, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            waypoints
        } else {
            waypoints.filter {
                it.name.contains(needle, ignoreCase = true) ||
                    it.note.contains(needle, ignoreCase = true)
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
                .fillMaxHeight(0.9f)
                .padding(horizontal = 18.dp),
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
            Spacer(Modifier.height(10.dp))
            if (filtered.isEmpty()) {
                Box(Modifier.weight(1f)) {
                    Text(
                        text = if (waypoints.isEmpty()) {
                            "با نگه‌داشتن انگشت روی نقشه یا دکمه + یک نقطه بسازید."
                        } else {
                            "نقطه‌ای با این جست‌وجو پیدا نشد."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = AtlasColors.TextMuted,
                        modifier = Modifier.padding(vertical = 22.dp),
                    )
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
                            onClick = { onShow(waypoint) },
                            onEdit = { onEdit(waypoint) },
                            onDelete = { onDelete(waypoint) },
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
        )
    }
}

@Composable
private fun WaypointRow(
    waypoint: Waypoint,
    distanceMeters: Double?,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
                        else append(" · ${formatCoordinate(waypoint.lat, waypoint.lon, CoordinateFormat.DECIMAL)}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = AtlasColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AtlasColors.Surface,
        contentColor = AtlasColors.TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AtlasColors.Outline) },
    ) {
        Column(
            Modifier
                .fillMaxHeight(0.9f)
                .padding(horizontal = 18.dp),
        ) {
            SectionHeader(
                title = "مسیرهای ثبت‌شده",
                subtitle = "${tracks.size.toString().toPersianDigits()} مسیر · ${formatDistance(tracks.sumOf { it.distanceMeters })}",
                action = {
                    Row {
                        IconButton(onClick = onImport) {
                            Icon(Icons.Rounded.FileDownload, "ورود فایل", tint = AtlasColors.TextSecondary)
                        }
                        IconButton(onClick = onExport) {
                            Icon(Icons.Rounded.FileUpload, "خروجی GPX", tint = AtlasColors.TextSecondary)
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
                            onClick = { onShow(track) },
                            onDelete = { onDelete(track) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, onClick: () -> Unit, onDelete: () -> Unit) {
    val date = remember(track.startedAt) {
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(track.startedAt))
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
            RoundIconButton(Icons.Rounded.Delete, "حذف مسیر", onDelete, tint = AtlasColors.TextMuted)
        }
    }
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
    onCoordinateBar: (Boolean) -> Unit,
    onGoTo: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AtlasColors.Surface,
        contentColor = AtlasColors.TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AtlasColors.Outline) },
    ) {
        Column(
            Modifier
                .fillMaxHeight(0.92f)
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
        ) {
            SectionHeader(title = "تنظیمات", subtitle = "نمایش مختصات و رفتار نقشه")
            Spacer(Modifier.height(14.dp))

            Text("قالب نمایش مختصات", style = MaterialTheme.typography.labelMedium, color = AtlasColors.TextSecondary)
            Spacer(Modifier.height(8.dp))
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
                description = "نوار مقیاس در پایین نقشه",
                checked = settings.showScaleBar,
                onCheckedChange = onScaleBar,
            )
            SwitchRow(
                title = "نمایش مختصات مرکز نقشه",
                description = "مختصات مرکز نمایش با ضربه قابل تغییر قالب است",
                checked = settings.showCoordinateBar,
                onCheckedChange = onCoordinateBar,
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
                text = "AtlasGIS 1.0 — نمایش آفلاین MBTiles با موتور رندر MapLibre Native (OpenGL ES). " +
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
