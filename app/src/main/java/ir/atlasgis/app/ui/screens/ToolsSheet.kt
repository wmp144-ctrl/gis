package ir.atlasgis.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.atlasgis.app.core.toPersianDigits
import ir.atlasgis.app.ui.components.GlassPanel
import ir.atlasgis.app.ui.components.SectionHeader
import ir.atlasgis.app.ui.theme.AtlasColors

/**
 * Single entry point for everything that is not plain map browsing. Keeping these
 * three actions here leaves the main screen with only pan/zoom/locate controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsSheet(
    waypointCount: Int,
    trackCount: Int,
    recordingActive: Boolean,
    recordingPaused: Boolean,
    onDismiss: () -> Unit,
    onMeasure: () -> Unit,
    onRecord: () -> Unit,
    onWaypoints: () -> Unit,
    onTracks: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AtlasColors.Surface,
        contentColor = AtlasColors.TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AtlasColors.Outline) },
    ) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 26.dp)) {
            SectionHeader(
                title = "ابزارها",
                subtitle = "اندازه‌گیری، ثبت مسیر، نقاط و مسیرهای ذخیره‌شده",
            )
            Spacer(Modifier.height(12.dp))

            ToolRow(
                icon = Icons.Rounded.Straighten,
                title = "اندازه‌گیری",
                subtitle = "مسافت و مساحت را روی نقشه اندازه بگیرید",
                accent = AtlasColors.Measure,
                onClick = onMeasure,
            )
            Spacer(Modifier.height(8.dp))
            ToolRow(
                icon = Icons.Rounded.Route,
                title = "ثبت مسیر",
                subtitle = when {
                    recordingPaused -> "ثبت متوقف شده — برای ادامه کارت پایین نقشه را ببینید"
                    recordingActive -> "در حال ثبت — کنترل توقف در پایین نقشه"
                    else -> "مسیر حرکت را با GPS ضبط و ذخیره کنید"
                },
                accent = AtlasColors.Track,
                highlight = recordingActive,
                onClick = onRecord,
            )
            Spacer(Modifier.height(8.dp))
            ToolRow(
                icon = Icons.Rounded.Place,
                title = "نقاط",
                subtitle = "${waypointCount.toString().toPersianDigits()} نقطه ذخیره‌شده · افزودن، ویرایش و خروجی GPX",
                accent = AtlasColors.Accent,
                onClick = onWaypoints,
            )
            Spacer(Modifier.height(8.dp))
            ToolRow(
                icon = Icons.Rounded.Timeline,
                title = "مسیرهای ثبت‌شده",
                subtitle = "${trackCount.toString().toPersianDigits()} مسیر · نمایش روی نقشه و خروجی GPX",
                accent = AtlasColors.Info,
                onClick = onTracks,
            )
        }
    }
}

@Composable
private fun ToolRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    highlight: Boolean = false,
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (highlight) accent.copy(alpha = 0.55f) else AtlasColors.OutlineSoft,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = AtlasColors.TextPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = AtlasColors.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
