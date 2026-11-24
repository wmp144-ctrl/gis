package ir.atlasgis.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ir.atlasgis.app.data.WaypointCategoryStyle
import ir.atlasgis.app.ui.theme.AtlasColors

/** Colours offered for a category; all sit comfortably on the dark map chrome. */
private val CategoryColorChoices = listOf(
    0xFF3ED598, 0xFF5FB8FF, 0xFFFFC15E, 0xFFB98CFF,
    0xFFFF6B6B, 0xFF7C8CFF, 0xFFFF9F45, 0xFF4DD0E1,
    0xFFF06292, 0xFFAED581, 0xFFE0E0E0, 0xFFFFD54F,
).map { it.toInt() }

/**
 * Manages waypoint categories: create, rename, restyle, delete them and choose which
 * ones are drawn on the map. Everything is persisted, so lists, chips and the map
 * markers always agree.
 */
@Composable
fun CategoryManagerDialog(
    styles: List<WaypointCategoryStyle>,
    onDismiss: () -> Unit,
    onSave: (WaypointCategoryStyle) -> Unit,
    onDelete: (WaypointCategoryStyle) -> Unit,
    onVisibility: (WaypointCategoryStyle, Boolean) -> Unit,
) {
    var editingKey by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<WaypointCategoryStyle?>(null) }
    val editing = styles.firstOrNull { it.key == editingKey }

    if (editing == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = AtlasColors.SurfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("دسته‌بندی نقاط", color = AtlasColors.TextPrimary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { creating = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = AtlasColors.Accent, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("دستهٔ جدید", color = AtlasColors.Accent, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            text = {
                Column(
                    Modifier
                        .widthIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "برای تغییر نام، رنگ و آیکون روی دسته بزنید. با آیکون چشم، نمایش نقاط آن دسته روی نقشه را روشن یا خاموش کنید. دسته‌های پیش‌فرض با قفل مشخص شده‌اند و حذف نمی‌شوند.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AtlasColors.TextMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    styles.forEach { style ->
                        CategoryStyleRow(
                            style = style,
                            canDelete = !WaypointCategoryStyle.isBuiltIn(style.key),
                            onClick = { editingKey = style.key },
                            onVisibility = { onVisibility(style, !style.visible) },
                            onDelete = { deleteTarget = style },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("بستن", color = AtlasColors.TextSecondary) }
            },
        )
    } else {
        CategoryStyleEditor(
            style = editing,
            onBack = { editingKey = null },
            onSave = { updated -> onSave(updated); editingKey = null },
        )
    }

    if (creating) {
        NewCategoryDialog(
            onDismiss = { creating = false },
            onCreate = { style ->
                onSave(style)
                creating = false
            },
        )
    }

    deleteTarget?.let { style ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = AtlasColors.SurfaceElevated,
            title = { Text("حذف دسته", color = AtlasColors.TextPrimary) },
            text = {
                Text(
                    "دستهٔ «${style.label}» حذف شود؟ نقاط این دسته حذف نمی‌شوند و به دستهٔ عمومی منتقل می‌شوند.",
                    color = AtlasColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(style); deleteTarget = null }) {
                    Text("حذف", color = AtlasColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("انصراف", color = AtlasColors.TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun CategoryStyleRow(
    style: WaypointCategoryStyle,
    canDelete: Boolean,
    onClick: () -> Unit,
    onVisibility: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AtlasColors.Surface)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryPreview(style = style, size = 32)
        Spacer(Modifier.width(10.dp))
        Text(
            text = style.label,
            style = MaterialTheme.typography.titleSmall,
            color = if (style.visible) AtlasColors.TextPrimary else AtlasColors.TextMuted,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        IconButton(onClick = onVisibility) {
            Icon(
                imageVector = if (style.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                contentDescription = if (style.visible) "پنهان‌کردن روی نقشه" else "نمایش روی نقشه",
                tint = if (style.visible) AtlasColors.Accent else AtlasColors.TextMuted,
                modifier = Modifier.size(19.dp),
            )
        }
        if (canDelete) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "حذف ${style.label}",
                    tint = AtlasColors.TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            // Built-in keys are restored on every launch, so deleting them would not
            // stick; a lock makes that visible instead of silently ignoring the tap.
            Icon(
                Icons.Rounded.Lock,
                contentDescription = "دستهٔ پیش‌فرض؛ قابل حذف نیست",
                tint = AtlasColors.TextMuted,
                modifier = Modifier.padding(horizontal = 12.dp).size(15.dp),
            )
        }
        Icon(
            Icons.Rounded.Edit,
            contentDescription = "ویرایش ${style.label}",
            tint = AtlasColors.TextMuted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
    }
}

/** Dialog used both for a brand-new category and for editing an existing one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewCategoryDialog(
    onDismiss: () -> Unit,
    onCreate: (WaypointCategoryStyle) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(CategoryColorChoices.first()) }
    var icon by remember { mutableStateOf(WaypointCategoryStyle.ICON_PLACE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AtlasColors.SurfaceElevated,
        title = { Text("دستهٔ جدید", color = AtlasColors.TextPrimary) },
        text = {
            Column(
                Modifier
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(24) },
                    label = { Text("نام دسته") },
                    placeholder = { Text("مثال: دکل مخابراتی") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = atlasTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text("رنگ", style = MaterialTheme.typography.labelMedium, color = AtlasColors.TextSecondary)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryColorChoices.forEach { choice ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(choice))
                                .border(
                                    width = if (choice == color) 3.dp else 1.dp,
                                    color = if (choice == color) AtlasColors.TextPrimary else AtlasColors.Outline,
                                    shape = CircleShape,
                                )
                                .clickable { color = choice },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (choice == color) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "رنگ انتخاب‌شده",
                                    tint = AtlasColors.Background,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("آیکون", style = MaterialTheme.typography.labelMedium, color = AtlasColors.TextSecondary)
                Spacer(Modifier.height(6.dp))
                IconPicker(selected = icon, color = color, onSelect = { icon = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(WaypointCategoryStyle.custom(label, color, icon)) },
                enabled = label.isNotBlank(),
            ) { Text("ساخت دسته", color = AtlasColors.Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف", color = AtlasColors.TextSecondary) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPicker(selected: String, color: Int, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CategoryIconCatalog.forEach { (key, vector) ->
            val isSelected = key == selected
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) Color(color).copy(alpha = 0.2f) else AtlasColors.Surface)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) Color(color) else AtlasColors.Outline,
                        shape = RoundedCornerShape(11.dp),
                    )
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = vector,
                    contentDescription = null,
                    tint = if (isSelected) Color(color) else AtlasColors.TextSecondary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryStyleEditor(
    style: WaypointCategoryStyle,
    onBack: () -> Unit,
    onSave: (WaypointCategoryStyle) -> Unit,
) {
    var label by remember(style.key) { mutableStateOf(style.label) }
    var color by remember(style.key) { mutableStateOf(style.color) }
    var icon by remember(style.key) { mutableStateOf(style.icon) }

    AlertDialog(
        onDismissRequest = onBack,
        containerColor = AtlasColors.SurfaceElevated,
        title = { Text("دستهٔ «${style.label}»", color = AtlasColors.TextPrimary) },
        text = {
            Column(
                Modifier
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryPreview(style.copy(color = color, icon = icon), size = 38)
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it.take(24) },
                        label = { Text("نام دسته") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = atlasTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("رنگ", style = MaterialTheme.typography.labelMedium, color = AtlasColors.TextSecondary)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryColorChoices.forEach { choice ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(choice))
                                .border(
                                    width = if (choice == color) 3.dp else 1.dp,
                                    color = if (choice == color) AtlasColors.TextPrimary else AtlasColors.Outline,
                                    shape = CircleShape,
                                )
                                .clickable { color = choice },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (choice == color) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "رنگ انتخاب‌شده",
                                    tint = AtlasColors.Background,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("آیکون", style = MaterialTheme.typography.labelMedium, color = AtlasColors.TextSecondary)
                Spacer(Modifier.height(8.dp))
                IconPicker(selected = icon, color = color, onSelect = { icon = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(style.copy(label = label.trim().ifBlank { style.label }, color = color, icon = icon))
                },
            ) { Text("ذخیره", color = AtlasColors.Accent) }
        },
        dismissButton = {
            TextButton(onClick = onBack) { Text("بازگشت", color = AtlasColors.TextSecondary) }
        },
    )
}

@Composable
private fun CategoryPreview(style: WaypointCategoryStyle, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3).dp))
            .background(Color(style.color).copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = categoryIconFor(style.icon),
            contentDescription = null,
            tint = Color(style.color),
            modifier = Modifier.size((size * 0.52f).dp),
        )
    }
}
