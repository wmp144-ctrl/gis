package ir.atlasgis.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.atlasgis.app.core.BoundingBox
import ir.atlasgis.app.core.toPersianDigits
import ir.atlasgis.app.data.TileGrid
import ir.atlasgis.app.ui.DownloadUiState
import ir.atlasgis.app.ui.components.GlassPanel
import ir.atlasgis.app.ui.components.SectionHeader
import ir.atlasgis.app.ui.theme.AtlasColors

/**
 * In-app map acquisition. Two honest paths are offered: a direct MBTiles link (what
 * most offline-map sites hand out) and a `{z}/{x}/{y}` raster tile server for the
 * region currently on screen. Both end up as a normal layer, identical to a file
 * import.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadMapSheet(
    currentBounds: BoundingBox?,
    progress: DownloadUiState?,
    onDismiss: () -> Unit,
    onDownloadMbTiles: (url: String, name: String) -> Unit,
    onDownloadRegion: (template: String, name: String, minZoom: Int, maxZoom: Int) -> Unit,
    onCancelDownload: () -> Unit,
) {
    var mode by remember { mutableStateOf(0) }
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var minZoom by remember { mutableIntStateOf(10) }
    var maxZoom by remember { mutableIntStateOf(14) }

    val tileEstimate = remember(currentBounds, minZoom, maxZoom) {
        if (currentBounds == null) null else TileGrid.estimate(currentBounds, minZoom, maxZoom)
    }
    val validation = remember(currentBounds, minZoom, maxZoom) {
        if (currentBounds == null) "محدودهٔ نقشه آماده نیست" else TileGrid.validate(currentBounds, minZoom, maxZoom)
    }

    ModalBottomSheet(
        onDismissRequest = { if (progress == null) onDismiss() },
        containerColor = AtlasColors.Surface,
        contentColor = AtlasColors.TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AtlasColors.Outline) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 22.dp),
        ) {
            SectionHeader(
                title = "دریافت نقشهٔ آفلاین",
                subtitle = "بدون رایانه؛ دانلود در همین برنامه و استفادهٔ کامل آفلاین",
            )
            Spacer(Modifier.height(10.dp))

            if (progress != null) {
                GlassPanel(Modifier.fillMaxWidth(), borderColor = AtlasColors.Accent.copy(alpha = 0.45f)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.CloudDownload,
                                contentDescription = null,
                                tint = AtlasColors.Accent,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = progress.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = AtlasColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        if (progress.indeterminate) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = AtlasColors.Accent,
                                trackColor = AtlasColors.Outline,
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { progress.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = AtlasColors.Accent,
                                trackColor = AtlasColors.Outline,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = buildString {
                                if (progress.detail.isNotBlank()) append(progress.detail)
                                if (!progress.indeterminate) {
                                    if (isNotEmpty()) append(" · ")
                                    append("${(progress.progress * 100).toInt().toString().toPersianDigits()}٪")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = AtlasColors.TextMuted,
                        )
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = onCancelDownload) {
                            Text("لغو دانلود", color = AtlasColors.Danger)
                        }
                    }
                }
                return@Column
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(AtlasColors.SurfaceElevated)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                listOf("لینک فایل MBTiles", "سرور کاشی (XYZ)").forEachIndexed { index, label ->
                    val selected = mode == index
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) AtlasColors.OnAccent else AtlasColors.TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) AtlasColors.Accent else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { mode = index }
                            .padding(vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it.trim() },
                label = { Text(if (mode == 0) "آدرس فایل MBTiles" else "آدرس کاشی") },
                placeholder = {
                    Text(
                        if (mode == 0) "https://example.com/iran.mbtiles" else "https://tile.example.com/{z}/{x}/{y}.png",
                        color = AtlasColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = atlasTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text("نام نقشه (اختیاری)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = atlasTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
            )

            if (mode == 1) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "محدودهٔ دانلود، همان کادری است که الان روی نقشه می‌بینید. " +
                        "برای محدودهٔ دیگر، ابتدا نقشه را جابه‌جا و بزرگ‌نمایی کنید.",
                    style = MaterialTheme.typography.labelSmall,
                    color = AtlasColors.TextMuted,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "زوم شروع",
                        style = MaterialTheme.typography.labelSmall,
                        color = AtlasColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    ZoomStepper(
                        value = minZoom,
                        onChange = { minZoom = it.coerceIn(0, maxZoom) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "زوم پایان",
                        style = MaterialTheme.typography.labelSmall,
                        color = AtlasColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    ZoomStepper(
                        value = maxZoom,
                        onChange = { maxZoom = it.coerceIn(minZoom, 19) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                val estimateText = tileEstimate?.let { count ->
                    "${count.toString().toPersianDigits()} کاشی · حدود " +
                        formatBytes(TileGrid.estimateBytes(count))
                } ?: "—"
                Text(
                    text = estimateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = AtlasColors.Accent,
                )
                validation?.let { message ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = AtlasColors.Warning,
                    )
                }
            } else {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "لینک باید مستقیم به فایل .mbtiles اشاره کند (لینک اشتراک‌گذاری گوگل‌درایو " +
                        "کافی نیست؛ از لینک دانلود مستقیم استفاده کنید).",
                    style = MaterialTheme.typography.labelSmall,
                    color = AtlasColors.TextMuted,
                )
            }

            Spacer(Modifier.height(14.dp))
            val canDownload = url.isNotBlank() && when (mode) {
                1 -> validation == null
                else -> true
            }
            Button(
                onClick = {
                    if (mode == 0) onDownloadMbTiles(url, name)
                    else onDownloadRegion(url, name.ifBlank { "نقشهٔ ${maxZoom}" }, minZoom, maxZoom)
                },
                enabled = canDownload,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AtlasColors.Accent,
                    contentColor = AtlasColors.OnAccent,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (mode == 0) "دانلود فایل نقشه" else "دانلود این محدوده")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "فایل در حافظهٔ خصوصی برنامه ذخیره می‌شود؛ می‌توانید بعداً از بخش لایه‌ها حذفش کنید.",
                style = MaterialTheme.typography.labelSmall,
                color = AtlasColors.TextMuted,
            )
        }
    }
}

@Composable
private fun ZoomStepper(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(value - 1) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.Remove, "کاهش زوم", tint = AtlasColors.TextSecondary, modifier = Modifier.size(16.dp))
        }
        Text(
            text = value.toString().toPersianDigits(),
            style = MaterialTheme.typography.titleSmall,
            color = AtlasColors.TextPrimary,
        )
        IconButton(onClick = { onChange(value + 1) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.Add, "افزایش زوم", tint = AtlasColors.TextSecondary, modifier = Modifier.size(16.dp))
        }
    }
}
