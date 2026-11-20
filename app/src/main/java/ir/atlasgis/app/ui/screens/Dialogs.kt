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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.atlasgis.app.core.CoordinateFormat
import ir.atlasgis.app.core.formatCoordinate
import ir.atlasgis.app.data.Waypoint
import ir.atlasgis.app.data.WaypointCategory
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
    onDismiss: () -> Unit,
    onSave: (name: String, note: String, category: WaypointCategory) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: WaypointCategory.GENERAL) }

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
                Text(
                    text = formatCoordinate(lat, lon, CoordinateFormat.DECIMAL),
                    style = MaterialTheme.typography.labelSmall,
                    color = AtlasColors.Accent,
                )
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
                Spacer(Modifier.height(14.dp))
                Text("دسته‌بندی", style = MaterialTheme.typography.labelMedium, color = AtlasColors.TextSecondary)
                Spacer(Modifier.height(8.dp))
                FlowRowCategories(selected = category, onSelect = { category = it })
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
}

@Composable
private fun FlowRowCategories(
    selected: WaypointCategory,
    onSelect: (WaypointCategory) -> Unit,
) {
    // Two rows of chips keeps it compact inside the dialog on small screens.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WaypointCategory.entries.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { category ->
                    val isSelected = category == selected
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isSelected) {
                                    categoryColor(category).copy(alpha = 0.2f)
                                } else {
                                    AtlasColors.Surface
                                },
                            )
                            .clickable { onSelect(category) }
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = categoryIcon(category),
                            contentDescription = null,
                            tint = if (isSelected) categoryColor(category) else AtlasColors.TextMuted,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = categoryLabel(category),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) categoryColor(category) else AtlasColors.TextSecondary,
                        )
                    }
                }
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
