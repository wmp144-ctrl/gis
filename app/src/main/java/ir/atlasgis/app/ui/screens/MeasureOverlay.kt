package ir.atlasgis.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.atlasgis.app.core.LatLon
import ir.atlasgis.app.core.distanceMeters
import ir.atlasgis.app.core.formatArea
import ir.atlasgis.app.core.formatDistanceShort
import ir.atlasgis.app.core.polygonAreaSqMeters
import ir.atlasgis.app.data.MeasureMode
import ir.atlasgis.app.ui.MeasureUiState
import ir.atlasgis.app.ui.map.CameraInfo
import ir.atlasgis.app.ui.map.MapController
import ir.atlasgis.app.ui.theme.AtlasColors

/**
 * Draws per-segment distance chips and the total area directly on a transparent
 * Canvas above the map. It consumes no touches, so map gestures keep working.
 */
@Composable
fun MeasureLabelsOverlay(
    controller: MapController,
    state: MeasureUiState,
    camera: CameraInfo?,
) {
    if (!state.active || state.points.size < 2) return
    val measurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelSmall.copy(
        color = AtlasColors.TextPrimary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
    )
    Canvas(Modifier.fillMaxSize()) {
        // Camera is only read to trigger recomposition; geometry comes from MapLibre.
        if (camera == null) return@Canvas
        val points = state.points
        for (index in 1 until points.size) {
            val from = points[index - 1]
            val to = points[index]
            val screenFrom = controller.screenPosition(from) ?: continue
            val screenTo = controller.screenPosition(to) ?: continue
            if (kotlin.math.abs(screenFrom.x - screenTo.x) +
                kotlin.math.abs(screenFrom.y - screenTo.y) < 70f
            ) {
                continue
            }
            val midpoint = Offset(
                (screenFrom.x + screenTo.x) / 2f,
                (screenFrom.y + screenTo.y) / 2f,
            )
            drawChip(measurer, textStyle, formatDistanceShort(distanceMeters(from, to)), midpoint)
        }
        if (state.mode == MeasureMode.AREA && points.size >= 3) {
            val centroid = LatLon(
                points.map { it.lat }.average(),
                points.map { it.lon }.average(),
            )
            val screen = controller.screenPosition(centroid)
            if (screen != null) {
                drawChip(
                    measurer,
                    textStyle.copy(color = AtlasColors.Measure),
                    formatArea(polygonAreaSqMeters(points)),
                    Offset(screen.x, screen.y),
                )
            }
        }
    }
}

private fun DrawScope.drawChip(
    measurer: TextMeasurer,
    style: TextStyle,
    text: String,
    center: Offset,
) {
    val layout = measurer.measure(text, style)
    val padH = 6.dp.toPx()
    val padV = 3.dp.toPx()
    val width = layout.size.width + padH * 2
    val height = layout.size.height + padV * 2
    val topLeft = Offset(center.x - width / 2f, center.y - height / 2f)
    val corner = CornerRadius(7.dp.toPx(), 7.dp.toPx())
    drawRoundRect(
        color = Color(0xF20B1220),
        topLeft = topLeft,
        size = Size(width, height),
        cornerRadius = corner,
    )
    drawRoundRect(
        color = AtlasColors.Measure.copy(alpha = 0.55f),
        topLeft = topLeft,
        size = Size(width, height),
        cornerRadius = corner,
        style = Stroke(width = 1.dp.toPx()),
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(topLeft.x + padH, topLeft.y + padV),
    )
}
