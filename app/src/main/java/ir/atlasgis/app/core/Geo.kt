package ir.atlasgis.app.core

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

/** WGS84 coordinate. */
data class LatLon(val lat: Double, val lon: Double) {
    fun isValid(): Boolean = lat in -90.0..90.0 && lon in -180.0..180.0
}

data class BoundingBox(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    val centerLat: Double get() = (south + north) / 2.0
    val centerLon: Double get() = (west + east) / 2.0
    val center: LatLon get() = LatLon(centerLat, centerLon)
    val isValid: Boolean
        get() = south <= north && west <= east && south.isFinite() && north.isFinite()

    fun contains(point: LatLon): Boolean =
        point.lat in south..north && point.lon in west..east

    fun union(other: BoundingBox): BoundingBox = BoundingBox(
        south = minOf(south, other.south),
        west = minOf(west, other.west),
        north = maxOf(north, other.north),
        east = maxOf(east, other.east),
    )

    companion object {
        val WORLD = BoundingBox(-85.05112878, -180.0, 85.05112878, 180.0)
    }
}

private const val EARTH_RADIUS_M = 6378137.0
private const val DEG_TO_RAD = PI / 180.0
private const val RAD_TO_DEG = 180.0 / PI

/** Great-circle distance in meters between two coordinates. */
fun distanceMeters(from: LatLon, to: LatLon): Double {
    val dLat = (to.lat - from.lat) * DEG_TO_RAD
    val dLon = (to.lon - from.lon) * DEG_TO_RAD
    val lat1 = from.lat * DEG_TO_RAD
    val lat2 = to.lat * DEG_TO_RAD
    val h = sin(dLat / 2).let { it * it } +
        cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
    return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

/** Total length of an open polyline in meters. */
fun pathLengthMeters(points: List<LatLon>): Double {
    if (points.size < 2) return 0.0
    var total = 0.0
    for (i in 1 until points.size) {
        total += distanceMeters(points[i - 1], points[i])
    }
    return total
}

/**
 * Geodesic area of a polygon on a sphere (the same family of formula QGIS uses for
 * ellipsoidal-area approximation). Coordinates may be in either winding order.
 */
fun polygonAreaSqMeters(points: List<LatLon>): Double {
    if (points.size < 3) return 0.0
    var total = 0.0
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        total += (b.lon - a.lon) * DEG_TO_RAD *
            (2 + sin(a.lat * DEG_TO_RAD) + sin(b.lat * DEG_TO_RAD))
    }
    return abs(total) * EARTH_RADIUS_M * EARTH_RADIUS_M / 2.0
}

/** Initial bearing from [from] to [to] in degrees, clockwise from north. */
fun bearingDegrees(from: LatLon, to: LatLon): Double {
    val lat1 = from.lat * DEG_TO_RAD
    val lat2 = to.lat * DEG_TO_RAD
    val dLon = (to.lon - from.lon) * DEG_TO_RAD
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return (atan2(y, x) * RAD_TO_DEG + 360.0) % 360.0
}

/** Point [distance] meters away from [origin] along [bearing] degrees. */
fun destinationPoint(origin: LatLon, bearing: Double, distance: Double): LatLon {
    val angular = distance / EARTH_RADIUS_M
    val brng = bearing * DEG_TO_RAD
    val lat1 = origin.lat * DEG_TO_RAD
    val lon1 = origin.lon * DEG_TO_RAD
    val lat2 = asin(
        sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(brng),
    )
    val lon2 = lon1 + atan2(
        sin(brng) * sin(angular) * cos(lat1),
        cos(angular) - sin(lat1) * sin(lat2),
    )
    return LatLon(lat2 * RAD_TO_DEG, ((lon2 * RAD_TO_DEG + 540) % 360) - 180)
}

/** Bounding box of a point list, or null when empty. */
fun boundingBoxOf(points: List<LatLon>): BoundingBox? {
    if (points.isEmpty()) return null
    var south = points[0].lat
    var north = points[0].lat
    var west = points[0].lon
    var east = points[0].lon
    for (p in points) {
        if (p.lat < south) south = p.lat
        if (p.lat > north) north = p.lat
        if (p.lon < west) west = p.lon
        if (p.lon > east) east = p.lon
    }
    return BoundingBox(south, west, north, east)
}

/**
 * Ground resolution for the Web-Mercator projection MapLibre uses (512 px world at z0).
 */
fun metersPerPixel(latitude: Double, zoom: Double): Double {
    val latRad = latitude.coerceIn(-85.05112878, 85.05112878) * DEG_TO_RAD
    return 40075016.6855785 * cos(latRad) / (512.0 * Math.pow(2.0, zoom))
}

/** Shortest distance in meters from [point] to the polyline [line]. */
fun distanceToSegmentMeters(point: LatLon, a: LatLon, b: LatLon): Double {
    // Local planar approximation is accurate enough at tap distances.
    val latRef = point.lat * DEG_TO_RAD
    val mPerDegLat = 111132.0
    val mPerDegLon = 111320.0 * cos(latRef)
    val px = (point.lon - a.lon) * mPerDegLon
    val py = (point.lat - a.lat) * mPerDegLat
    val bx = (b.lon - a.lon) * mPerDegLon
    val by = (b.lat - a.lat) * mPerDegLat
    val lenSq = bx * bx + by * by
    if (lenSq <= 1e-9) return sqrt(px * px + py * py)
    val t = ((px * bx + py * by) / lenSq).coerceIn(0.0, 1.0)
    val dx = px - t * bx
    val dy = py - t * by
    return sqrt(dx * dx + dy * dy)
}

/** True when [point] lies within [toleranceMeters] of the polyline. */
fun isNearLine(point: LatLon, line: List<LatLon>, toleranceMeters: Double): Boolean {
    if (line.isEmpty()) return false
    if (line.size == 1) return distanceMeters(point, line[0]) <= toleranceMeters
    for (i in 1 until line.size) {
        if (distanceToSegmentMeters(point, line[i - 1], line[i]) <= toleranceMeters) return true
    }
    return false
}
