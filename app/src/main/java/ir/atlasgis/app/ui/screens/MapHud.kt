package ir.atlasgis.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.atlasgis.app.core.CoordinateFormat
import ir.atlasgis.app.core.formatArea
import ir.atlasgis.app.core.formatCoordinate
import ir.atlasgis.app.core.formatDistance
import ir.atlasgis.app.core.formatDistanceShort
import ir.atlasgis.app.core.formatDuration
import ir.atlasgis.app.core.formatSpeed
import ir.atlasgis.app.core.niceScaleDistance
import ir.atlasgis.app.core.toPersianDigits
import ir.atlasgis.app.data.GpsFix
import ir.atlasgis.app.data.MeasureMode
import ir.atlasgis.app.data.RecordingState
import ir.atlasgis.app.data.Waypoint
import ir.atlasgis.app.ui.MeasureUiState
import ir.atlasgis.app.ui.SearchHit
import ir.atlasgis.app.ui.components.GlassPanel
import ir.atlasgis.app.ui.components.GradientBadge
import ir.atlasgis.app.ui.components.RoundIconButton
import ir.atlasgis.app.ui.components.StatBlock
import ir.atlasgis.app.ui.components.StatusChip
import ir.atlasgis.app.ui.theme.AtlasColors
import kotlin.math.roundToInt

/**
 * Compact bar pinned to the bottom of the map. It replaces the old top bar so the
 * upper edge stays free for the scale and the map controls.
 */
@Composable
fun MapBottomBar(
    layerSummary: String,
    layerCount: Int,
    onTools: () -> Unit,
    onSearch: () -> Unit,
    onLayers: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GradientBadge(Icons.Rounded.Map)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "AtlasGIS",
                    style = MaterialTheme.typography.labelLarge,
                    color = AtlasColors.TextPrimary,
                )
                Text(
                    text = if (layerCount > 0) {
                        "$layerSummary · ${layerCount.toString().toPersianDigits()} لایه"
                    } else {
                        "نقشه‌ای بارگذاری نشده"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = AtlasColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RoundIconButton(Icons.Rounded.GridView, "ابزارها", onTools)
            RoundIconButton(Icons.Rounded.Search, "جست‌وجوی مکان", onSearch)
            RoundIconButton(Icons.Rounded.Layers, "لایه‌ها", onLayers)
            RoundIconButton(Icons.Rounded.Settings, "تنظیمات", onSettings)
        }
    }
}

/** Zoom, compass and locate, stacked in the top-right corner. */
@Composable
fun MapSideControls(
    bearing: Float,
    following: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetBearing: () -> Unit,
    onLocate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier, shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier.padding(vertical = 3.dp, horizontal = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onResetBearing),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .rotate(-bearing),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Navigation,
                        contentDescription = "جهت شمال",
                        tint = if (bearing > 1f) AtlasColors.Accent else AtlasColors.TextSecondary,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            HorizontalDivider(color = AtlasColors.Outline, modifier = Modifier.width(20.dp))
            RoundIconButton(Icons.Rounded.Add, "بزرگ‌نمایی", onZoomIn)
            RoundIconButton(Icons.Rounded.Remove, "کوچک‌نمایی", onZoomOut)
            HorizontalDivider(color = AtlasColors.Outline, modifier = Modifier.width(20.dp))
            RoundIconButton(
                icon = Icons.Rounded.MyLocation,
                contentDescription = "موقعیت من",
                onClick = onLocate,
                tint = if (following) AtlasColors.Accent else AtlasColors.TextPrimary,
                background = if (following) AtlasColors.AccentSoft else Color.Transparent,
            )
        }
    }
}

@Composable
fun MeasureCard(
    state: MeasureUiState,
    onModeChange: (MeasureMode) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryValue = when (state.mode) {
        MeasureMode.DISTANCE -> formatDistance(state.lengthMeters)
        MeasureMode.AREA -> formatArea(state.areaSqMeters)
    }
    val secondaryLabel = when {
        state.mode == MeasureMode.AREA && state.points.size >= 3 ->
            "محیط: ${formatDistance(state.lengthMeters)}"
        state.points.size < 2 -> "برای شروع، روی نقشه ضربه بزنید"
        else -> "${state.points.size.toString().toPersianDigits()} نقطه · ${
            formatDistance(state.lengthMeters / (state.points.size - 1).coerceAtLeast(1))
        } میانگین هر ضلع"
    }
    GlassPanel(modifier = modifier.fillMaxWidth(), borderColor = AtlasColors.Measure.copy(alpha = 0.5f)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (state.mode == MeasureMode.DISTANCE) "اندازه‌گیری مسافت" else "اندازه‌گیری مساحت",
                    style = MaterialTheme.typography.labelMedium,
                    color = AtlasColors.Measure,
                )
                Spacer(Modifier.weight(1f))
                SegmentedChip(
                    options = listOf("مسافت" to MeasureMode.DISTANCE, "مساحت" to MeasureMode.AREA),
                    selected = state.mode,
                    onSelect = onModeChange,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = primaryValue,
                style = MaterialTheme.typography.titleLarge,
                color = AtlasColors.TextPrimary,
            )
            Text(
                text = secondaryLabel,
                style = MaterialTheme.typography.labelSmall,
                color = AtlasColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudActionButton(
                    "واگرد",
                    Icons.AutoMirrored.Rounded.Undo,
                    onUndo,
                    enabled = state.points.isNotEmpty(),
                )
                HudActionButton("پاک‌کردن", Icons.Rounded.Delete, onClear, enabled = state.points.isNotEmpty())
                Spacer(Modifier.weight(1f))
                HudActionButton("پایان", Icons.Rounded.Check, onClose, accent = AtlasColors.Measure)
            }
        }
    }
}

/** Shown before anything is recorded: the user decides when the GPS starts. */
@Composable
fun RecordingStartCard(
    onStart: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.fillMaxWidth(), borderColor = AtlasColors.Track.copy(alpha = 0.55f)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Route, contentDescription = null, tint = AtlasColors.Track, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "آمادهٔ ثبت مسیر",
                        style = MaterialTheme.typography.labelLarge,
                        color = AtlasColors.Track,
                    )
                    Text(
                        text = "با زدن شروع، ضبط GPS آغاز می‌شود",
                        style = MaterialTheme.typography.labelSmall,
                        color = AtlasColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RoundIconButton(Icons.Rounded.Close, "انصراف", onCancel)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudActionButton("شروع", Icons.Rounded.PlayArrow, onStart, accent = AtlasColors.Track)
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun RecordingCard(
    state: RecordingState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    GlassPanel(modifier = modifier.fillMaxWidth(), borderColor = AtlasColors.Track.copy(alpha = 0.55f)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.paused) AtlasColors.Warning else AtlasColors.Danger.copy(alpha = pulse),
                        ),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = if (state.paused) "ثبت مسیر — متوقف" else "در حال ثبت مسیر",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.paused) AtlasColors.Warning else AtlasColors.Track,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatDuration(state.movingSeconds),
                    style = MaterialTheme.typography.titleMedium,
                    color = AtlasColors.TextPrimary,
                )
                RoundIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "بستن کارت؛ ضبط ادامه پیدا می‌کند",
                    onClick = onCollapse,
                    tint = AtlasColors.TextSecondary,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row {
                StatBlock("مسافت", formatDistance(state.distanceMeters), Modifier.weight(1f))
                StatBlock("سرعت", formatSpeed(state.currentSpeedMps), Modifier.weight(1f))
                StatBlock("صعود", formatDistance(state.ascentMeters), Modifier.weight(1f))
                StatBlock("نقاط", state.pointCount.toString().toPersianDigits(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.paused) {
                    HudActionButton("ادامه", Icons.Rounded.PlayArrow, onResume, accent = AtlasColors.Accent)
                } else {
                    HudActionButton("توقف موقت", Icons.Rounded.Pause, onPause)
                }
                Spacer(Modifier.weight(1f))
                HudActionButton("پایان و ذخیره", Icons.Rounded.Check, onStop, accent = AtlasColors.Track)
            }
        }
    }
}

@Composable
fun RecordingMiniBar(
    state: RecordingState,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        borderColor = AtlasColors.Track.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onExpand)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (state.paused) AtlasColors.Warning else AtlasColors.Danger),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (state.paused) "ثبت مسیر متوقف است" else "در حال ثبت مسیر",
                style = MaterialTheme.typography.labelMedium,
                color = if (state.paused) AtlasColors.Warning else AtlasColors.Track,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${formatDistanceShort(state.distanceMeters)} · ${formatDuration(state.movingSeconds)}",
                style = MaterialTheme.typography.labelSmall,
                color = AtlasColors.TextSecondary,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Rounded.KeyboardArrowUp,
                contentDescription = "نمایش کارت ثبت مسیر",
                tint = AtlasColors.TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun WaypointCard(
    waypoint: Waypoint,
    distanceMeters: Double?,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(categoryColor(waypoint.category).copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = categoryIcon(waypoint.category),
                        contentDescription = null,
                        tint = categoryColor(waypoint.category),
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = waypoint.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = AtlasColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${categoryLabel(waypoint.category)}${
                            distanceMeters?.let { " · ${formatDistance(it)} از موقعیت شما" } ?: ""
                        }",
                        style = MaterialTheme.typography.labelSmall,
                        color = AtlasColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RoundIconButton(Icons.Rounded.Close, "بستن", onClose)
            }
            if (waypoint.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = waypoint.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = AtlasColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudActionButton("نمایش روی نقشه", Icons.Rounded.MyLocation, onCenter)
                Spacer(Modifier.weight(1f))
                HudActionButton("ویرایش", Icons.Rounded.Edit, onEdit)
                HudActionButton("حذف", Icons.Rounded.Delete, onDelete, accent = AtlasColors.Danger)
            }
        }
    }
}

/** The place picked from map search, pinned above the bottom bar. */
@Composable
fun SearchFocusCard(
    hit: SearchHit,
    distanceMeters: Double?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (hit.fromWaypoint) AtlasColors.Accent else poiColorFor(hit.kind)
    GlassPanel(modifier = modifier.fillMaxWidth(), borderColor = accent.copy(alpha = 0.5f)) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (hit.fromWaypoint) categoryIcon(hit.kind) else poiIconFor(hit.kind),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = hit.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = AtlasColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(hit.label.ifBlank { "مکان" })
                        if (distanceMeters != null) append(" · ${formatDistance(distanceMeters)}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = AtlasColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RoundIconButton(Icons.Rounded.Close, "بستن نتیجهٔ جست‌وجو", onClose)
        }
    }
}

@Composable
fun HudActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    accent: Color = AtlasColors.TextSecondary,
) {
    Row(
        modifier = Modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(accent.copy(alpha = if (enabled) 0.12f else 0.05f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Decorative: the label next to it is the accessible name.
        Icon(icon, contentDescription = null, tint = if (enabled) accent else AtlasColors.TextMuted, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) accent else AtlasColors.TextMuted,
            maxLines = 1,
        )
    }
}

@Composable
fun SegmentedChip(
    options: List<Pair<String, MeasureMode>>,
    selected: MeasureMode,
    onSelect: (MeasureMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(AtlasColors.SurfaceElevated)
            .padding(2.dp),
    ) {
        options.forEach { (label, mode) ->
            val isSelected = mode == selected
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) AtlasColors.OnAccent else AtlasColors.TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) AtlasColors.Measure else Color.Transparent)
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Read-only centre coordinate, formatted with the user's chosen coordinate style.
 * Tapping copies the full value; it is the visible half of the coordinate-format
 * setting, which previously only existed in the settings sheet.
 */
@Composable
fun MapCoordinateReadout(
    lat: Double,
    lon: Double,
    format: CoordinateFormat,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = remember(lat, lon, format) { formatCoordinate(lat, lon, format) }
    GlassPanel(modifier = modifier, shape = RoundedCornerShape(10.dp)) {
        Row(
            modifier = Modifier
                .widthIn(max = 228.dp)
                .clickable { onCopy(text) }
                .padding(start = 8.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.PinDrop,
                contentDescription = null,
                tint = AtlasColors.Accent,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                color = AtlasColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = "کپی مختصات",
                tint = AtlasColors.TextMuted,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
fun MapScaleBar(metersPerPixel: Double, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    if (!metersPerPixel.isFinite() || metersPerPixel <= 0.0) return
    val targetPx = with(density) { 76.dp.toPx() }.toDouble()
    val distance = niceScaleDistance(metersPerPixel * targetPx)
    val barPx = (distance / metersPerPixel).toFloat()
    val barDp = with(density) { barPx.toDp() }

    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = formatDistanceShort(distance),
            style = MaterialTheme.typography.labelSmall,
            color = AtlasColors.TextPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Canvas(
            Modifier
                .width(barDp.coerceIn(30.dp, 110.dp))
                .height(6.dp),
        ) {
            val stroke = 1.5.dp.toPx()
            drawLine(
                color = AtlasColors.TextPrimary,
                start = Offset(0f, size.height - stroke),
                end = Offset(size.width, size.height - stroke),
                strokeWidth = stroke,
                cap = StrokeCap.Square,
            )
            drawLine(
                color = AtlasColors.TextPrimary,
                start = Offset(0f, size.height - 6.dp.toPx()),
                end = Offset(0f, size.height),
                strokeWidth = stroke,
            )
            drawLine(
                color = AtlasColors.TextPrimary,
                start = Offset(size.width, size.height - 6.dp.toPx()),
                end = Offset(size.width, size.height),
                strokeWidth = stroke,
            )
        }
    }
}

@Composable
fun EmptyMapsCard(onImport: () -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AtlasColors.AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Map,
                    contentDescription = null,
                    tint = AtlasColors.Accent,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "نقشه آفلاین خود را اضافه کنید",
                style = MaterialTheme.typography.titleSmall,
                color = AtlasColors.TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "فایل MBTiles (رستری یا برداری) را از حافظه دستگاه انتخاب کنید؛ پردازش و نمایش کاملاً آفلاین است.",
                style = MaterialTheme.typography.labelSmall,
                color = AtlasColors.TextMuted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onImport,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AtlasColors.Accent,
                    contentColor = AtlasColors.OnAccent,
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("افزودن فایل MBTiles", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun FloatingHint(text: String, visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.95f),
        exit = fadeOut() + scaleOut(targetScale = 0.95f),
        modifier = modifier,
    ) {
        GlassPanel(shape = RoundedCornerShape(50)) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = AtlasColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
