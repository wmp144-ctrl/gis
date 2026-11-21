package ir.atlasgis.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ir.atlasgis.app.core.CoordinateFormat
import ir.atlasgis.app.core.formatCoordinate
import ir.atlasgis.app.data.Waypoint
import ir.atlasgis.app.data.WaypointCategoryStyle
import ir.atlasgis.app.ui.ImportUiState
import ir.atlasgis.app.ui.theme.AtlasColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun atlasTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AtlasColors.TextPrimary,
    unfocusedTextColor = AtlasColors.TextPrimary,
    focusedBorderColor = AtlasColors.Accent,
    unfocusedBorderColor = AtlasColors.Outline,
    cursorColor = AtlasColors.Accent,
    focusedContainerColor = AtlasColors.SurfaceElevated,
    unfocusedContainerColor = AtlasColors.SurfaceElevated,
)

/**
 * Creates a new waypoint or edits an existing one. Coordinates are taken from the
 * map long-press (or the map centre) and are shown read-only.
 */
@Composable
fun WaypointEditorDialog(
    existing: Waypoint?,
    lat: Double,
    lon: Double,
    styles: List<WaypointCategoryStyle>,
    onDismiss: () -> Unit,
    onCreateCategory: (WaypointCategoryStyle) -> Unit,
    onSave: (name: String, note: String, category: String) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var category by remember {
        mutableStateOf(existing?.category ?: styles.firstOrNull()?.key ?: "general")
    }
    var creatingCategory by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AtlasColors.SurfaceElevated,
        title = {
            Text(
                text = if (existing == null) "نقطه جدید" else "ویرایش نقطه",
                color = AtlasColors.TextPrimary,
            )
        },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = 420.dp),
            ) {
                // Coordinates of the long-pressed point: one tap copies them exactly
                // as shown, so they can be pasted into another app or message.
                val clipboard = LocalClipboardManager.current
                var copied by remember { mutableStateOf(false) }
                LaunchedEffect(copied) {
                    if (copied) {
                        delay(1600)
                        copied = false
                    }
                }
                val coordinate = formatCoordinate(lat, lon, CoordinateFormat.DECIMAL)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(AtlasColors.Surface)
                        .padding(start = 10.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PinDrop,
                        contentDescription = null,
                        tint = AtlasColors.Accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = if (copied) "$coordinate · کپی شد" else coordinate,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (copied) AtlasColors.Accent else AtlasColors.TextPrimary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(coordinate))
                            copied = true
                        },
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = "کپی مختصات",
                            tint = if (copied) AtlasColors.Accent else AtlasColors.TextMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("نام نقطه") },
                    placeholder = { Text("مثال: چشمه آب") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = atlasTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(400) },
                    label = { Text("یادداشت") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = atlasTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "دسته‌بندی",
                        style = MaterialTheme.typography.labelMedium,
                        color = AtlasColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "افزودن دستهٔ جدید",
                        style = MaterialTheme.typography.labelSmall,
                        color = AtlasColors.Accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { creatingCategory = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                FlowRowCategories(
                    styles = styles,
                    selected = category,
                    onSelect = { category = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, note, category) }) {
                Text(if (existing == null) "ذخیره نقطه" else "ذخیره تغییرات", color = AtlasColors.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف", color = AtlasColors.TextSecondary) }
        },
    )

    if (creatingCategory) {
        NewCategoryDialog(
            onDismiss = { creatingCategory = false },
            onCreate = { style ->
                onCreateCategory(style)
                category = style.key
                creatingCategory = false
            },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowRowCategories(
    styles: List<WaypointCategoryStyle>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    // Wrapping chips stay inside the dialog on narrow phones and at large font scales.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 4,
    ) {
        styles.forEach { style ->
            val isSelected = style.key == selected
            val color = androidx.compose.ui.graphics.Color(style.color)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) color.copy(alpha = 0.2f) else AtlasColors.Surface)
                    .clickable { onSelect(style.key) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = categoryIconFor(style.icon),
                    contentDescription = null,
                    tint = if (isSelected) color else AtlasColors.TextMuted,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = style.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) color else AtlasColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
fun GoToCoordinateDialog(
    onDismiss: () -> Unit,
    onGo: (String) -> Boolean,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AtlasColors.SurfaceElevated,
        icon = { Icon(Icons.Rounded.PinDrop, null, tint = AtlasColors.Accent) },
        title = { Text("پرش به مختصات", color = AtlasColors.TextPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("35.6892, 51.3890", color = AtlasColors.TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = atlasTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "نمونه‌ها: 35.6892, 51.3890  یا  35 41 21 N 51 23 20 E",
                    style = MaterialTheme.typography.labelSmall,
                    color = AtlasColors.TextMuted,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (onGo(text)) { onDismiss() } },
                enabled = text.isNotBlank(),
            ) { Text("نمایش", color = AtlasColors.Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف", color = AtlasColors.TextSecondary) }
        },
    )
}

@Composable
fun ImportProgressDialog(state: ImportUiState) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = AtlasColors.SurfaceElevated,
        title = { Text(state.label, color = AtlasColors.TextPrimary) },
        text = {
            Column {
                if (!state.indeterminate) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = AtlasColors.Accent,
                        trackColor = AtlasColors.Outline,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${(state.progress * 100).toInt()}٪",
                        style = MaterialTheme.typography.labelSmall,
                        color = AtlasColors.TextSecondary,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = AtlasColors.Accent,
                        trackColor = AtlasColors.Outline,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "فایل در حافظه برنامه کپی می‌شود؛ برای فایل‌های بزرگ چند لحظه صبر کنید.",
                    style = MaterialTheme.typography.labelSmall,
                    color = AtlasColors.TextMuted,
                )
            }
        },
        confirmButton = {},
    )
}

@Composable
fun DeleteConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "حذف",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AtlasColors.SurfaceElevated,
        title = { Text(title, color = AtlasColors.TextPrimary) },
        text = { Text(message, color = AtlasColors.TextSecondary, textAlign = TextAlign.Start) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = AtlasColors.Danger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف", color = AtlasColors.TextSecondary) }
        },
    )
}

@Composable
fun PermissionGate(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(AtlasColors.SurfaceGlass)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.LocationOff, null, tint = AtlasColors.Warning)
        Spacer(Modifier.height(6.dp))
        Text(
            "برای نمایش موقعیت و ثبت مسیر، دسترسی مکان لازم است.",
            style = MaterialTheme.typography.labelSmall,
            color = AtlasColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRequest) { Text("اجازه دسترسی", color = AtlasColors.Accent) }
    }
}
