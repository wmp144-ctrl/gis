package ir.atlasgis.app.data

import ir.atlasgis.app.core.LatLon
import ir.atlasgis.app.core.destinationPoint
import java.util.Locale

/**
 * Hand-rolled GeoJSON writers. These run on every GPS tick and every measure tap, so
 * they build strings directly (no JSON object graph) to stay allocation-light.
 */
object OverlayGeoJson {

    fun empty(): String = """{"type":"FeatureCollection","features":[]}"""

    fun waypoints(
        waypoints: List<Waypoint>,
        selectedId: String?,
        categoryColors: Map<String, String> = emptyMap(),
    ): String {
        if (waypoints.isEmpty()) return empty()
        val sb = StringBuilder(waypoints.size * 110 + 48)
        sb.append("""{"type":"FeatureCollection","features":[""")
        waypoints.forEachIndexed { index, waypoint ->
            if (index > 0) sb.append(',')
            sb.append("""{"type":"Feature","properties":{"id":""")
            appendQuoted(sb, waypoint.id)
            sb.append(""","color":""")
            appendQuoted(
                sb,
                categoryColors[waypoint.category] ?: colorFor(waypoint.category),
            )
            sb.append(""","name":""")
            appendQuoted(sb, waypoint.name)
            sb.append(""","selected":""")
            sb.append(waypoint.id == selectedId)
            sb.append("""},"geometry":{"type":"Point","coordinates":[""")
            appendCoordinate(sb, waypoint.lon, waypoint.lat)
            sb.append("]}}")
        }
        sb.append("]}")
        return sb.toString()
    }

    fun measurement(points: List<LatLon>, closed: Boolean): String {
        if (points.isEmpty()) return empty()
        val sb = StringBuilder(points.size * 40 + 160)
        sb.append("""{"type":"FeatureCollection","features":[""")

        if (points.size >= 2) {
            sb.append("""{"type":"Feature","properties":{"role":"line"},"geometry":{"type":""")
            if (closed && points.size >= 3) {
                sb.append("\"Polygon\",\"coordinates\":[[")
                points.forEach { appendPosition(sb, it.lon, it.lat).append(',') }
                appendPosition(sb, points[0].lon, points[0].lat)
                sb.append("]]}},")
            } else {
                sb.append("\"LineString\",\"coordinates\":[")
                points.forEachIndexed { index, point ->
                    if (index > 0) sb.append(',')
                    appendPosition(sb, point.lon, point.lat)
                }
                sb.append("]}},")
            }
        }

        points.forEachIndexed { index, point ->
            if (index > 0) sb.append(',')
            sb.append("""{"type":"Feature","properties":{"index":""").append(index)
            sb.append("""},"geometry":{"type":"Point","coordinates":[""")
            appendCoordinate(sb, point.lon, point.lat)
            sb.append("]}}")
        }
        sb.append("]}")
        return sb.toString()
    }

    fun tracks(tracks: List<Track>): String {
        val visible = tracks.filter { it.points.size >= 2 }
        if (visible.isEmpty()) return empty()
        val sb = StringBuilder(1024)
        sb.append("""{"type":"FeatureCollection","features":[""")
        visible.forEachIndexed { index, track ->
            if (index > 0) sb.append(',')
            sb.append("""{"type":"Feature","properties":{"id":""")
            appendQuoted(sb, track.id)
            sb.append(""","color":""")
            appendQuoted(sb, track.color)
            sb.append("""},"geometry":{"type":"LineString","coordinates":[""")
            track.points.forEachIndexed { pointIndex, point ->
                if (pointIndex > 0) sb.append(',')
                appendPosition(sb, point.lon, point.lat)
            }
            sb.append("]}}")
        }
        sb.append("]}")
        return sb.toString()
    }

    fun line(points: List<LatLon>): String {
        if (points.size < 2) return empty()
        val sb = StringBuilder(points.size * 26 + 120)
        sb.append("""{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[""")
        points.forEachIndexed { index, point ->
            if (index > 0) sb.append(',')
            appendPosition(sb, point.lon, point.lat)
        }
        sb.append("]}}]}")
        return sb.toString()
    }

    fun gpsPoint(fix: GpsFix?): String {
        if (fix == null) return empty()
        return """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[${fix.lon},${fix.lat}]}}]}"""
    }

    /** Approximate accuracy circle as a geodesic polygon (48 vertices). */
    fun accuracyRing(fix: GpsFix?): String {
        if (fix == null || fix.accuracyMeters <= 0f) return empty()
        val center = LatLon(fix.lat, fix.lon)
        val sb = StringBuilder(1200)
        sb.append("""{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":[[ """)
        val steps = 48
        for (i in 0..steps) {
            val point = destinationPoint(center, i * 360.0 / steps, fix.accuracyMeters.toDouble())
            if (i > 0) sb.append(',')
            appendPosition(sb, point.lon, point.lat)
        }
        sb.append("]]}}]}")
        return sb.toString()
    }

    fun colorFor(categoryKey: String): String = when (categoryKey) {
        WaypointCategory.WATER.key -> "#5FB8FF"
        WaypointCategory.FOOD.key -> "#FFC15E"
        WaypointCategory.CAMP.key -> "#B98CFF"
        WaypointCategory.HAZARD.key -> "#FF6B6B"
        WaypointCategory.PARKING.key -> "#7C8CFF"
        WaypointCategory.VIEW.key -> "#FF9F45"
        else -> "#3ED598"
    }

    /** Writes a GeoJSON position: [lon,lat] */
    private fun appendPosition(sb: StringBuilder, lon: Double, lat: Double): StringBuilder {
        sb.append('[')
        appendCoordinate(sb, lon, lat)
        sb.append(']')
        return sb
    }

    private fun appendCoordinate(sb: StringBuilder, lon: Double, lat: Double): StringBuilder {
        sb.append(String.format(Locale.US, "%.6f", lon))
        sb.append(',')
        sb.append(String.format(Locale.US, "%.6f", lat))
        return sb
    }

    private fun appendQuoted(sb: StringBuilder, value: String) {
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch.code < 0x20) sb.append(' ') else sb.append(ch)
            }
        }
        sb.append('"')
    }
}
