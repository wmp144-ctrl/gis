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
import ir.atlasgis.app.core.formatOpacity
import ir.atlasgis.app.core.toPersianDigits
import ir.atlasgis.app.data.MapLayer
import ir.atlasgis.app.data.MapStylePreset
import ir.atlasgis.app.data.TileFormat
import ir.atlasgis.app.ui.components.GlassPanel
import ir.atlasgis.app.ui.components.RoundIconButton
import ir.atlasgis.app.ui.components.SectionHeader
import ir.atlasgis.app.ui.components.StatusChip
import ir.atlasgis.app.ui.theme.AtlasColors
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LayersSheet(
    layers: List<MapLayer>,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    onToggle: (MapLayer) -> Unit,
    onOpacity: (MapLayer, Float) -> Unit,
    onMove: (MapLayer, Boolean) -> Unit,
    onRename: (MapLayer, String) -> Unit,
    onDelete: (MapLayer) -> Unit,
    onImportStyle: (MapLayer) -> Unit,
    onClearStyle: (MapLayer) -> Unit,
    onStylePreset: (MapLayer, MapStylePreset) -> Unit,
) {
    var renameTarget by remember { mutableStateOf<MapLayer?>(null) }
    var deleteTarget by remember { mutableStateOf<MapLayer?>(null) }
    var styleTarget by remember { mutableStateOf<MapLayer?>(null) }
    val ordered = remember(layers) { layers.sortedByDescending { it.sortOrder } }
    val totalBytes = remember(layers) { layers.sumOf { it.fileSizeBytes } }

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
                title = "نقشه‌های آفلاین",
                subtitle = "${layers.size.toString().toPersianDigits()} فایل · ${formatBytes(totalBytes)}",
                action = {
                    IconButton(onClick = onImport) {
                        Icon(Icons.Rounded.Add, contentDescription = "افزودن نقشه", tint = AtlasColors.Accent)
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            if (layers.isEmpty()) {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "هنوز فایلی اضافه نشده است",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AtlasColors.TextSecondary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onImport,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AtlasColors.Accent,
                                contentColor = AtlasColors.OnAccent,
                            ),
                        ) { Text("انتخاب فایل MBTiles") }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 26.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(ordered, key = { it.id }) { layer ->
                        LayerRow(
                            layer = layer,
                            isTop = layer.id == ordered.firstOrNull()?.id,
                            isBottom = layer.id == ordered.lastOrNull()?.id,
                            onToggle = { onToggle(layer) },
                            onOpacity = { onOpacity(layer, it) },
                            onMoveUp = { onMove(layer, true) },
                            onMoveDown = { onMove(layer, false) },
                            onRename = { renameTarget = layer },
                            onDelete = { deleteTarget = layer },
                            onImportStyle = { onImportStyle(layer) },
                            onClearStyle = { onClearStyle(layer) },
                            onPickStyle = { styleTarget = layer },
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { layer ->
        RenameLayerDialog(
            initialName = layer.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                onRename(layer, name)
                renameTarget = null
            },
        )
    }

    styleTarget?.let { layer ->
        MapStylePresetDialog(
            layer = layer,
            onDismiss = { styleTarget = null },
            onSelect = { preset ->
                onStylePreset(layer, preset)
                styleTarget = null
            },
        )
    }

    deleteTarget?.let { layer ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = AtlasColors.SurfaceElevated,
            title = { Text("حذف نقشه", color = AtlasColors.TextPrimary) },
            text = {
                Text(
                    "فایل «${layer.name}» (${formatBytes(layer.fileSizeBytes)}) از حافظه برنامه حذف می‌شود. این کار قابل بازگشت نیست.",
                    color = AtlasColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(layer)
                    deleteTarget = null
                }) { Text("حذف", color = AtlasColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("انصراف", color = AtlasColors.TextSecondary) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayerRow(
    layer: MapLayer,
    isTop: Boolean,
    isBottom: Boolean,
    onToggle: () -> Unit,
    onOpacity: (Float) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onImportStyle: () -> Unit,
    onClearStyle: () -> Unit,
    onPickStyle: () -> Unit,
) {
    var sliderValue by remember(layer.id, layer.opacity) { mutableStateOf(layer.opacity) }
    var menuOpen by remember { mutableStateOf(false) }

    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (layer.format == TileFormat.VECTOR) {
                                AtlasColors.Info.copy(alpha = 0.15f)
                            } else {
                                AtlasColors.Accent.copy(alpha = 0.15f)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (layer.format == TileFormat.VECTOR) {
                            Icons.Rounded.Layers
                        } else {
                            Icons.Rounded.Map
                        },
                        contentDescription = null,
                        tint = if (layer.format == TileFormat.VECTOR) AtlasColors.Info else AtlasColors.Accent,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = layer.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = AtlasColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    // Wraps instead of pushing the switch off-screen on narrow phones.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        StatusChip(
                            text = if (layer.format == TileFormat.VECTOR) "برداری" else "رستری",
                            color = if (layer.format == TileFormat.VECTOR) AtlasColors.Info else AtlasColors.Accent,
                            background = Color.Transparent,
                        )
                        StatusChip(text = layer.zoomLabel.toPersianDigits(), background = Color.Transparent)
                        StatusChip(text = formatBytes(layer.fileSizeBytes), background = Color.Transparent)
                        if (layer.hasCustomStyle) {
                            StatusChip(
                                text = "استایل سفارشی",
                                color = AtlasColors.Warning,
                                background = Color.Transparent,
                            )
                        } else if (layer.format == TileFormat.VECTOR) {
                            StatusChip(
                                text = "استایل ${layer.stylePreset.label}",
                                color = AtlasColors.TextSecondary,
                                background = Color.Transparent,
                            )
                        }
                    }
                }
                Switch(
                    checked = layer.visible,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AtlasColors.OnAccent,
                        checkedTrackColor = AtlasColors.Accent,
                        uncheckedThumbColor = AtlasColors.TextMuted,
                        uncheckedTrackColor = AtlasColors.SurfaceElevated,
                    ),
                )
            }
            if (layer.visible) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Opacity,
                        contentDescription = null,
                        tint = AtlasColors.TextMuted,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { onOpacity(sliderValue) },
                        valueRange = 0.1f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = AtlasColors.Accent,
                            activeTrackColor = AtlasColors.Accent,
                            inactiveTrackColor = AtlasColors.Outline,
                        ),
                        modifier = Modifier.weight(1f).height(30.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = formatOpacity(sliderValue),
                        style = MaterialTheme.typography.labelSmall,
                        color = AtlasColors.TextSecondary,
                        modifier = Modifier.width(36.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (layer.attribution != null) {
                    Text(
                        text = layer.attribution.take(64),
                        style = MaterialTheme.typography.labelSmall,
                        color = AtlasColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Box {
                    RoundIconButton(
                        icon = Icons.Rounded.MoreVert,
                        contentDescription = "گزینه‌های بیشتر",
                        onClick = { menuOpen = true },
                    )
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = AtlasColors.SurfaceElevated,
                    ) {
                        DropdownMenuItem(
                            text = { Text("تغییر نام", color = AtlasColors.TextPrimary) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = AtlasColors.TextSecondary) },
                            onClick = { menuOpen = false; onRename() },
                        )
                        if (layer.format == TileFormat.VECTOR) {
                            DropdownMenuItem(
                                text = { Text("استایل نقشه", color = AtlasColors.TextPrimary) },
                                leadingIcon = { Icon(Icons.Rounded.Palette, null, tint = AtlasColors.TextSecondary) },
                                onClick = { menuOpen = false; onPickStyle() },
                            )
                            DropdownMenuItem(
                                text = { Text("بارگذاری استایل style.json", color = AtlasColors.TextPrimary) },
                                leadingIcon = { Icon(Icons.Rounded.Style, null, tint = AtlasColors.TextSecondary) },
                                onClick = { menuOpen = false; onImportStyle() },
                            )
                            if (layer.hasCustomStyle) {
                                DropdownMenuItem(
                                    text = { Text("حذف استایل سفارشی", color = AtlasColors.TextPrimary) },
                                    leadingIcon = { Icon(Icons.Rounded.LayersClear, null, tint = AtlasColors.TextSecondary) },
                                    onClick = { menuOpen = false; onClearStyle() },
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text("انتقال به بالا", color = AtlasColors.TextPrimary) },
                            enabled = !isTop,
                            leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null, tint = AtlasColors.TextSecondary) },
                            onClick = { menuOpen = false; onMoveUp() },
                        )
                        DropdownMenuItem(
                            text = { Text("انتقال به پایین", color = AtlasColors.TextPrimary) },
                            enabled = !isBottom,
                            leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null, tint = AtlasColors.TextSecondary) },
                            onClick = { menuOpen = false; onMoveDown() },
                        )
                        DropdownMenuItem(
                            text = { Text("حذف", color = AtlasColors.Danger) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = AtlasColors.Danger) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

/** Picks one of the built-in palettes for a vector archive. */
@Composable
private fun MapStylePresetDialog(
    layer: MapLayer,
    onDismiss: () -> Unit,
    onSelect: (MapStylePreset) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AtlasColors.SurfaceElevated,
        title = { Text("استایل نقشه", color = AtlasColors.TextPrimary) },
        text = {
            Column(
                Modifier
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (layer.hasCustomStyle) {
                        "پالت رنگ برای «${layer.name}». با انتخاب هر استایل، استایل سفارشی فعلی حذف می‌شود."
                    } else {
                        "پالت رنگ برای «${layer.name}». لایه‌ها و داده‌های خود فایل تغییر نمی‌کند."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = AtlasColors.TextMuted,
                )
                MapStylePreset.entries.forEach { preset ->
                    val selected = layer.stylePreset == preset && !layer.hasCustomStyle
                    StylePresetOption(
                        preset = preset,
                        selected = selected,
                        onClick = { onSelect(preset) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("بستن", color = AtlasColors.TextSecondary) }
        },
    )
}

@Composable
private fun StylePresetOption(
    preset: MapStylePreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) AtlasColors.AccentSoft else AtlasColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Three-colour swatch previews the palette before it is applied.
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .width(46.dp)
                .height(30.dp),
        ) {
            preset.preview.forEach { argb ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(argb)),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = preset.label,
                style = MaterialTheme.typography.titleSmall,
                color = AtlasColors.TextPrimary,
            )
            Text(
                text = preset.description,
                style = MaterialTheme.typography.labelSmall,
                color = AtlasColors.TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.CheckCircle, contentDescription = "انتخاب‌شده", tint = AtlasColors.Accent, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun RenameLayerDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AtlasColors.SurfaceElevated,
        title = { Text("نام نقشه", color = AtlasColors.TextPrimary) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AtlasColors.TextPrimary,
                    unfocusedTextColor = AtlasColors.TextPrimary,
                    focusedBorderColor = AtlasColors.Accent,
                    unfocusedBorderColor = AtlasColors.Outline,
                ),
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

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "۰ بایت"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    fun number(value: Double, decimals: Int): String =
        String.format(Locale.US, "%.${decimals}f", value)
            .toPersianDigits()
            .replace('.', '٫')
    return when {
        bytes < 1024 -> "${bytes.toString().toPersianDigits()} بایت"
        gb >= 1.0 -> number(gb, 1) + " گیگابایت"
        mb >= 1.0 -> number(mb, 1) + " مگابایت"
        else -> number(kb, 0) + " کیلوبایت"
    }
}
