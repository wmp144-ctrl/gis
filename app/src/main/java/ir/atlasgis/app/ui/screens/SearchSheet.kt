package ir.atlasgis.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.atlasgis.app.core.LatLon
import ir.atlasgis.app.core.distanceMeters
import ir.atlasgis.app.core.formatDistance
import ir.atlasgis.app.core.toPersianDigits
import ir.atlasgis.app.data.GpsFix
import ir.atlasgis.app.data.PoiClassifier
import ir.atlasgis.app.ui.SearchHit
import ir.atlasgis.app.ui.components.GlassPanel
import ir.atlasgis.app.ui.components.SectionHeader
import ir.atlasgis.app.ui.theme.AtlasColors

/**
 * Offline map search: saved waypoints first, then every named place the imported
 * vector archive contains. Nothing leaves the device; results come from the place
 * index built from the map file itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(
    query: String,
    hits: List<SearchHit>,
    indexProgress: Float?,
    gps: GpsFix?,
    onQueryChange: (String) -> Unit,
    onSelect: (SearchHit) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val origin = gps?.let { LatLon(it.lat, it.lon) }
    val typing = query.trim().length >= 2

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AtlasColors.Surface,
        contentColor = AtlasColors.TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AtlasColors.Outline) },
    ) {
        Column(
            Modifier
                .fillMaxHeight(0.92f)
                .padding(horizontal = 12.dp),
        ) {
            SectionHeader(
                title = "جست‌وجوی مکان",
                subtitle = "اماکن نقشه و نقاط ذخیره‌شده",
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("نام خیابان، فروشگاه، مسجد، بیمارستان…", color = AtlasColors.TextMuted) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = AtlasColors.TextMuted) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, "پاک‌کردن", tint = AtlasColors.TextMuted)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AtlasColors.Accent,
                    unfocusedBorderColor = AtlasColors.Outline,
                    focusedContainerColor = AtlasColors.SurfaceElevated,
                    unfocusedContainerColor = AtlasColors.SurfaceElevated,
                    cursorColor = AtlasColors.Accent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )

            if (indexProgress != null) {
                Spacer(Modifier.height(10.dp))
                Column {
                    Text(
                        text = "ساخت نمایهٔ جست‌وجو از فایل نقشه… ${
                            (indexProgress * 100).toInt().coerceIn(0, 100).toString().toPersianDigits()
                        }٪",
                        style = MaterialTheme.typography.labelSmall,
                        color = AtlasColors.TextMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { indexProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(50)),
                        color = AtlasColors.Accent,
                        trackColor = AtlasColors.SurfaceElevated,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            when {
                !typing -> {
                    Box(Modifier.weight(1f)) {
                        Text(
                            text = "حداقل دو حرف از نام مکان یا خیابان را بنویسید. " +
                                "جست‌وجو کاملاً آفلاین و روی همان نقشه‌ای است که باز کرده‌اید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AtlasColors.TextMuted,
                            modifier = Modifier.padding(vertical = 22.dp),
                        )
                    }
                }

                hits.isEmpty() && indexProgress == null -> {
                    Box(Modifier.weight(1f)) {
                        Text(
                            text = "مکانی با این نام پیدا نشد. املای دیگری را امتحان کنید یا " +
                                "نقشهٔ برداری دیگری با لایهٔ اماکن اضافه کنید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AtlasColors.TextMuted,
                            modifier = Modifier.padding(vertical = 22.dp),
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        contentPadding = PaddingValues(bottom = 14.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        // No item keys: two identical POIs can share a name and a
                        // position, and duplicate keys would crash the list.
                        itemsIndexed(hits) { _, hit ->
                            SearchHitRow(
                                hit = hit,
                                distanceMeters = origin?.let { distanceMeters(it, hit.position) },
                                onClick = {
                                    focusManager.clearFocus()
                                    onSelect(hit)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // The search field is the whole point of the sheet: focus it as it opens.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
}

@Composable
private fun SearchHitRow(
    hit: SearchHit,
    distanceMeters: Double?,
    onClick: () -> Unit,
) {
    val accent = if (hit.fromWaypoint) AtlasColors.Accent else poiColorFor(hit.kind)
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
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
                        append(if (hit.fromWaypoint) "نقطهٔ من" else hit.label.ifBlank {
                            PoiClassifier.labels.getValue(PoiClassifier.GENERIC)
                        })
                        if (distanceMeters != null) append(" · ${formatDistance(distanceMeters)}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = AtlasColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
