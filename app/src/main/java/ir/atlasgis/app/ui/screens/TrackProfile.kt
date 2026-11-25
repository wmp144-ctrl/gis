package ir.atlasgis.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.atlasgis.app.core.TrackAnalysis
import ir.atlasgis.app.core.TrackSplit
import ir.atlasgis.app.core.downsample
import ir.atlasgis.app.core.formatDistance
import ir.atlasgis.app.core.formatDuration
import ir.atlasgis.app.core.formatElevation
import ir.atlasgis.app.core.formatSpeed
import ir.atlasgis.app.core.formatDistanceShort
import ir.atlasgis.app.core.toPersianDigits
import ir.atlasgis.app.ui.theme.AtlasColors

/**
 * Elevation (or, for tracks without altitude, speed) profile of a saved track.
 *
 * Drawn with a plain Canvas: no charting dependency is added for one screen, and the
 * samples are downsampled so a 20 000-point hike still scrolls smoothly.
 */
@Composable
fun TrackProfileChart(
    analysis: TrackAnalysis,
    modifier: Modifier = Modifier,
) {
    val useElevation = analysis.hasElevation
    val samples = remember(analysis, useElevation) { downsample(analysis.samples, 260) }
    val values = remember(samples, useElevation) {
        samples.mapNotNull { sample ->
            val value = if (useElevation) sample.elevation else sample.speedMps?.times(3.6)
            value?.takeIf { it.isFinite() }?.let { sample.distanceMeters to it.toFloat() }
        }
    }
    if (values.size < 2) {
        Text(
            text = "برای این مسیر دادهٔ ارتفاع یا سرعت ثبت نشده است.",
            style = MaterialTheme.typography.labelSmall,
            color = AtlasColors.TextMuted,
            modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
        )
        return
    }

    val minValue = remember(values) { values.minOf { it.second } }
    val maxValue = remember(values) { values.maxOf { it.second } }
    val span = (maxValue - minValue).coerceAtLeast(1f)
    val totalDistance = remember(values) { values.last().first.coerceAtLeast(1.0) }
    val accent = if (useElevation) AtlasColors.Track else AtlasColors.Info

    var selected by remember(values) { mutableStateOf<Int?>(null) }

    fun pick(x: Float, width: Int) {
        if (width <= 0) return
        val distance = (x / width).coerceIn(0f, 1f) * totalDistance
        var bestIndex = 0
        var bestDelta = Double.MAX_VALUE
        values.forEachIndexed { index, pair ->
            val delta = kotlin.math.abs(pair.first - distance)
            if (delta < bestDelta) {
                bestDelta = delta
                bestIndex = index
            }
        }
        selected = bestIndex
    }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (useElevation) "پروفایل ارتفاع" else "پروفایل سرعت",
                style = MaterialTheme.typography.labelMedium,
                color = AtlasColors.TextSecondary,
            )
            Spacer(Modifier.width(8.dp))
            val index = selected
            if (index != null && index in values.indices) {
                val (distance, value) = values[index]
                Text(
                    text = formatDistanceShort(distance) + " · " +
                        if (useElevation) formatElevation(value.toDouble()) else formatSpeed(value / 3.6),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1,
                )
            } else {
                Text(
                    text = if (useElevation) {
                        "${formatElevation(minValue.toDouble())} تا ${formatElevation(maxValue.toDouble())}"
                    } else {
                        "۰ تا ${formatSpeed(maxValue / 3.6)}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = AtlasColors.TextMuted,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = formatDistanceShort(totalDistance),
                style = MaterialTheme.typography.labelSmall,
                color = AtlasColors.TextMuted,
                textAlign = TextAlign.End,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AtlasColors.Surface)
                .pointerInput(values) {
                    detectDragGestures(
                        onDragStart = { offset -> pick(offset.x, size.width) },
                        onDragEnd = { selected = null },
                        onDragCancel = { selected = null },
                    ) { change, _ ->
                        change.consume()
                        pick(change.position.x, size.width)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val horizontalPadding = 4.dp.toPx()
                val verticalPadding = 8.dp.toPx()
                val width = (size.width - horizontalPadding * 2).coerceAtLeast(1f)
                val height = (size.height - verticalPadding * 2).coerceAtLeast(1f)

                for (i in 0..3) {
                    val y = verticalPadding + height * i / 3f
                    drawLine(
                        color = AtlasColors.Outline.copy(alpha = 0.35f),
                        start = Offset(horizontalPadding, y),
                        end = Offset(horizontalPadding + width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                val line = Path()
                values.forEachIndexed { index, (distance, value) ->
                    val x = horizontalPadding + (distance / totalDistance).toFloat() * width
                    val normalized = ((value - minValue) / span).coerceIn(0f, 1f)
                    val y = verticalPadding + height - normalized * height
                    if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
                }

                val fill = Path().apply {
                    addPath(line)
                    lineTo(horizontalPadding + width, verticalPadding + height)
                    lineTo(horizontalPadding, verticalPadding + height)
                    close()
                }
                drawPath(
                    path = fill,
                    brush = Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.32f), accent.copy(alpha = 0.02f)),
                    ),
                )
                drawPath(
                    path = line,
                    color = accent,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )

                selected?.let { index ->
                    if (index in values.indices) {
                        val (distance, value) = values[index]
                        val x = horizontalPadding + (distance / totalDistance).toFloat() * width
                        val normalized = ((value - minValue) / span).coerceIn(0f, 1f)
                        val y = verticalPadding + height - normalized * height
                        drawLine(
                            color = accent.copy(alpha = 0.6f),
                            start = Offset(x, verticalPadding),
                            end = Offset(x, verticalPadding + height),
                            strokeWidth = 1.dp.toPx(),
                        )
                        drawCircle(color = accent, radius = 4.dp.toPx(), center = Offset(x, y))
                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(x, y))
                    }
                }
            }
        }
    }
}

/** Per-kilometre breakdown; long tracks are capped so the dialog stays scrollable. */
@Composable
fun TrackSplitsList(
    analysis: TrackAnalysis,
    modifier: Modifier = Modifier,
    maxRows: Int = 12,
) {
    if (analysis.splits.isEmpty()) return
    val shown = analysis.splits.take(maxRows)
    Column(modifier.fillMaxWidth()) {
        Text(
            text = "کیلومتر‌ها",
            style = MaterialTheme.typography.labelMedium,
            color = AtlasColors.TextSecondary,
        )
        Spacer(Modifier.height(4.dp))
        shown.forEach { split ->
            SplitRow(split)
        }
        val hidden = analysis.splits.size - shown.size
        if (hidden > 0) {
            Text(
                text = "و ${hidden.toString().toPersianDigits()} کیلومتر دیگر",
                style = MaterialTheme.typography.labelSmall,
                color = AtlasColors.TextMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SplitRow(split: TrackSplit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = split.index.toString().toPersianDigits(),
            style = MaterialTheme.typography.labelSmall,
            color = AtlasColors.Track,
            modifier = Modifier.width(18.dp),
        )
        Text(
            text = formatDistance(split.distanceMeters),
            style = MaterialTheme.typography.labelSmall,
            color = AtlasColors.TextPrimary,
            modifier = Modifier.width(58.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatDuration(split.durationSeconds),
            style = MaterialTheme.typography.labelSmall,
            color = AtlasColors.TextSecondary,
            modifier = Modifier.width(56.dp),
            maxLines = 1,
        )
        Text(
            text = formatSpeed(split.speedMps),
            style = MaterialTheme.typography.labelSmall,
            color = AtlasColors.TextSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (split.ascentMeters > 0) {
            Text(
                text = "↑ ${formatDistanceShort(split.ascentMeters)}",
                style = MaterialTheme.typography.labelSmall,
                color = AtlasColors.TextMuted,
                maxLines = 1,
            )
        }
    }
}
