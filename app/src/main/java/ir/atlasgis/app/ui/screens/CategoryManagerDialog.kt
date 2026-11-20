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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
 * Lets the user restyle the built-in waypoint categories (colour + icon). The
 * chosen style is used everywhere: map popup, list rows and the editor chips.
 */
@Composable
fun CategoryManagerDialog(
    styles: List<WaypointCategoryStyle>,
    onDismiss: () -> Unit,
    onSave: (WaypointCategoryStyle) -> Unit,
) {
    var editingKey by remember { mutableStateOf<String?>(null) }
    val editing = styles.firstOrNull { it.key == editingKey }

    if (editing == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = AtlasColors.SurfaceElevated,
            title = { Text("دسته‌بندی نقاط", color = AtlasColors.TextPrimary) },
            text = {
                Column(
                    Modifier
                        .widthIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "برای تغییر رنگ و آیکون هر دسته، روی آن بزنید.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AtlasColors.TextMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    styles.forEach { style ->
                        CategoryStyleRow(style = style, onClick = { editingKey = style.key })
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
}

@Composable
private fun CategoryStyleRow(style: WaypointCategoryStyle, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AtlasColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryPreview(style = style, size = 34)
        Spacer(Modifier.width(10.dp))
        Text(
            text = style.label,
            style = MaterialTheme.typography.titleSmall,
            color = AtlasColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Rounded.Edit,
            contentDescription = "ویرایش ${style.label}",
            tint = AtlasColors.TextMuted,
            modifier = Modifier.size(17.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryStyleEditor(
    style: WaypointCategoryStyle,
    onBack: () -> Unit,
    onSave: (WaypointCategoryStyle) -> Unit,
) {
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
                    CategoryPreview(style.copy(color = color, icon = icon), size = 42)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = style.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = AtlasColors.TextPrimary,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("رنگ", style = MaterialTheme.typography.labelMedium, color = AtlasColors.TextSecondary)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryColorChoices.forEach { choice ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
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
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("آیکون", style = MaterialTheme.typography.labelMedium, color = AtlasColors.TextSecondary)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryIconCatalog.forEach { (key, vector) ->
                        val selected = key == icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) Color(color).copy(alpha = 0.2f) else AtlasColors.Surface,
                                )
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) Color(color) else AtlasColors.Outline,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = vector,
                                contentDescription = null,
                                tint = if (selected) Color(color) else AtlasColors.TextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(style.copy(color = color, icon = icon)) }) {
                Text("ذخیره", color = AtlasColors.Accent)
            }
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
