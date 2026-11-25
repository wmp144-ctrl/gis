package ir.atlasgis.app.core

/** Where the user currently is relative to a followed route. */
data class RouteProgress(
    /** Index of the route point the user is heading to (always >= 1 when possible). */
    val nextIndex: Int,
    /** Perpendicular distance from the route, in metres. */
    val offRouteMeters: Double,
    /** Distance still to walk along the route to its last point. */
    val remainingMeters: Double,
    /** 0..1 fraction of the route already covered. */
    val fraction: Float,
) {
    val isOffRoute: Boolean get() = offRouteMeters > 40.0
}

/**
 * Projects [point] onto the polyline [route]. Distances use the same planar
 * approximation as [distanceToSegmentMeters], which is accurate enough at the scale
 * of a hike and cheap enough to run on every GPS fix.
 */
fun locateOnRoute(point: LatLon, route: List<LatLon>): RouteProgress? {
    if (route.isEmpty()) return null
    if (route.size == 1) {
        return RouteProgress(
            nextIndex = 0,
            offRouteMeters = distanceMeters(point, route[0]),
            remainingMeters = 0.0,
            fraction = 1f,
        )
    }
    var bestDistance = Double.MAX_VALUE
    var bestSegmentEnd = 1
    for (i in 1 until route.size) {
        val distance = distanceToSegmentMeters(point, route[i - 1], route[i])
        if (distance < bestDistance) {
            bestDistance = distance
            bestSegmentEnd = i
        }
    }
    val total = pathLengthMeters(route)
    // Remaining distance starts at the user's own position: walking to the next route
    // point first, then along the rest of the line. Measuring from the segment start
    // would silently drop the metres between the user and the route.
    val startIndex = bestSegmentEnd.coerceAtMost(route.size - 1)
    val remaining = distanceMeters(point, route[startIndex]) +
        pathLengthMeters(route.subList(startIndex, route.size))
    return RouteProgress(
        nextIndex = bestSegmentEnd,
        offRouteMeters = bestDistance,
        remainingMeters = remaining,
        fraction = if (total > 0) ((total - remaining) / total).toFloat().coerceIn(0f, 1f) else 0f,
    )
}

/**
 * Average speed estimate for an arrival time. Prefers the live GPS speed and falls
 * back to the average moving speed of the current recording, then to a walking pace
 * so an ETA can still be shown while standing still.
 */
fun estimateSpeedMps(gpsSpeed: Double?, movingDistance: Double, movingSeconds: Long): Double? {
    val live = gpsSpeed?.takeIf { it.isFinite() && it > 0.6 }
    if (live != null) return live
    if (movingSeconds > 10 && movingDistance > 10) return movingDistance / movingSeconds
    return null
}
