package ir.atlasgis.app.data

import android.util.Xml
import ir.atlasgis.app.core.LatLon
import ir.atlasgis.app.core.distanceMeters
import ir.atlasgis.app.core.newId
import ir.atlasgis.app.core.pathLengthMeters
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Reads and writes waypoints/tracks as GPX 1.1 or GeoJSON. */
object GeoFileCodec {

    data class ImportResult(
        val waypoints: List<Waypoint> = emptyList(),
        val tracks: List<Track> = emptyList(),
    )

    // ------------------------------------------------------------------ export

    fun waypointsToGeoJson(waypoints: List<Waypoint>): String {
        val features = JSONArray()
        waypoints.forEach { waypoint ->
            features.put(
                JSONObject()
                    .put("type", "Feature")
                    .put(
                        "properties",
                        JSONObject()
                            .put("name", waypoint.name)
                            .put("description", waypoint.note)
                            .put("category", waypoint.category),
                    )
                    .put(
                        "geometry",
                        JSONObject()
                            .put("type", "Point")
                            .put(
                                "coordinates",
                                JSONArray().put(waypoint.lon).put(waypoint.lat).apply {
                                    waypoint.elevation?.let { put(it) }
                                },
                            ),
                    ),
            )
        }
        return JSONObject()
            .put("type", "FeatureCollection")
            .put("features", features)
            .toString(2)
    }

    fun tracksToGeoJson(tracks: List<Track>): String {
        val features = JSONArray()
        tracks.forEach { track ->
            val coordinates = JSONArray()
            track.points.forEach { point ->
                coordinates.put(JSONArray().put(point.lon).put(point.lat))
            }
            features.put(
                JSONObject()
                    .put("type", "Feature")
                    .put("properties", JSONObject().put("name", track.name))
                    .put(
                        "geometry",
                        JSONObject().put("type", "LineString").put("coordinates", coordinates),
                    ),
            )
        }
        return JSONObject()
            .put("type", "FeatureCollection")
            .put("features", features)
            .toString(2)
    }

    fun toGpx(waypoints: List<Waypoint>, tracks: List<Track>): String {
        val sb = StringBuilder(4096)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"AtlasGIS\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        waypoints.forEach { waypoint ->
            sb.append("  <wpt lat=\"").append(formatDouble(waypoint.lat))
                .append("\" lon=\"").append(formatDouble(waypoint.lon)).append("\">\n")
            waypoint.elevation?.let {
                sb.append("    <ele>").append(formatDouble(it)).append("</ele>\n")
            }
            sb.append("    <time>").append(formatTime(waypoint.createdAt)).append("</time>\n")
            sb.append("    <name>").append(escapeXml(waypoint.name)).append("</name>\n")
            if (waypoint.note.isNotBlank()) {
                sb.append("    <desc>").append(escapeXml(waypoint.note)).append("</desc>\n")
            }
            sb.append("    <type>").append(waypoint.category).append("</type>\n")
            sb.append("  </wpt>\n")
        }
        tracks.forEach { track ->
            sb.append("  <trk>\n    <name>").append(escapeXml(track.name)).append("</name>\n")
            sb.append("    <trkseg>\n")
            track.points.forEach { point ->
                sb.append("      <trkpt lat=\"").append(formatDouble(point.lat))
                    .append("\" lon=\"").append(formatDouble(point.lon)).append("\">\n")
                point.elevation?.let {
                    sb.append("        <ele>").append(formatDouble(it)).append("</ele>\n")
                }
                if (point.timestamp > 0) {
                    sb.append("        <time>").append(formatTime(point.timestamp)).append("</time>\n")
                }
                sb.append("      </trkpt>\n")
            }
            sb.append("    </trkseg>\n  </trk>\n")
        }
        sb.append("</gpx>\n")
        return sb.toString()
    }

    // ------------------------------------------------------------------ import

    fun parse(content: String): ImportResult {
        val trimmed = content.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        return try {
            when {
                trimmed.startsWith("{") -> parseGeoJson(trimmed)
                trimmed.startsWith("<") -> parseGpx(trimmed)
                else -> ImportResult()
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException("فایل ورودی یک GPX یا GeoJSON معتبر نیست", error)
        }
    }

    /** Coordinates outside WGS84 are rejected instead of entering the database. */
    private fun isValidPosition(lat: Double, lon: Double): Boolean =
        lat.isFinite() && lon.isFinite() && LatLon(lat, lon).isValid()

    private fun parseGeoJson(text: String): ImportResult {
        val root = JSONObject(text)
        val waypoints = ArrayList<Waypoint>()
        val tracks = ArrayList<Track>()
        val now = System.currentTimeMillis()

        fun handleGeometry(geometry: JSONObject?, properties: JSONObject) {
            if (geometry == null) return
            val type = geometry.optString("type")
            val name = properties.optString("name")
                .ifBlank { properties.optString("title") }
                .ifBlank { "نقطه واردشده" }
            val note = properties.optString("description")
                .ifBlank { properties.optString("desc") }
            // A collection has no `coordinates` of its own, so it must be handled
            // before the coordinate lookup below would return early.
            if (type == "GeometryCollection") {
                val geometries = geometry.optJSONArray("geometries") ?: return
                for (i in 0 until geometries.length()) {
                    handleGeometry(geometries.optJSONObject(i), properties)
                }
                return
            }
            val coordinates = geometry.optJSONArray("coordinates") ?: return
            fun addPoint(position: JSONArray) {
                val lon = position.optDouble(0, Double.NaN)
                val lat = position.optDouble(1, Double.NaN)
                if (!isValidPosition(lat, lon)) return
                waypoints.add(
                    Waypoint(
                        id = newId(),
                        name = name,
                        note = note,
                        category = categoryKey(properties.optString("category")),
                        lat = lat,
                        lon = lon,
                        elevation = if (position.length() > 2) position.optDouble(2) else null,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }

            fun addRing(ring: JSONArray) {
                val points = parsePath(ring).toMutableList()
                if (points.size >= 4 && points.first() == points.last()) points.removeAt(points.lastIndex)
                if (points.size >= 2) tracks.add(trackFrom(points, name, now))
            }

            when (type) {
                "Point" -> addPoint(coordinates)
                "MultiPoint" -> {
                    for (i in 0 until coordinates.length()) {
                        coordinates.optJSONArray(i)?.let(::addPoint)
                    }
                }
                "LineString", "MultiLineString" -> {
                    val lines = if (type == "LineString") JSONArray().put(coordinates) else coordinates
                    for (i in 0 until lines.length()) {
                        val points = parsePath(lines.optJSONArray(i) ?: continue)
                        if (points.size >= 2) tracks.add(trackFrom(points, name, now))
                    }
                }
                "Polygon" -> {
                    for (i in 0 until coordinates.length()) {
                        coordinates.optJSONArray(i)?.let(::addRing)
                    }
                }
                "MultiPolygon" -> {
                    for (p in 0 until coordinates.length()) {
                        val polygon = coordinates.optJSONArray(p) ?: continue
                        for (r in 0 until polygon.length()) {
                            polygon.optJSONArray(r)?.let(::addRing)
                        }
                    }
                }
            }
        }

        fun walk(feature: JSONObject) {
            val properties = feature.optJSONObject("properties") ?: JSONObject()
            when (feature.optString("type")) {
                "FeatureCollection" -> {
                    val features = feature.optJSONArray("features") ?: return
                    for (i in 0 until features.length()) {
                        features.optJSONObject(i)?.let(::walk)
                    }
                }
                "Feature" -> handleGeometry(feature.optJSONObject("geometry"), properties)
                else -> handleGeometry(feature, properties)
            }
        }

        walk(root)
        return ImportResult(waypoints, tracks)
    }

    private fun parsePath(array: JSONArray): List<LatLon> {
        val points = ArrayList<LatLon>(array.length())
        for (i in 0 until array.length()) {
            val position = array.optJSONArray(i) ?: continue
            val lon = position.optDouble(0, Double.NaN)
            val lat = position.optDouble(1, Double.NaN)
            if (isValidPosition(lat, lon)) points.add(LatLon(lat, lon))
        }
        return points
    }

    private fun trackFrom(points: List<LatLon>, name: String, now: Long): Track = Track(
        id = newId(),
        name = name.ifBlank { "مسیر واردشده" },
        points = points.map { TrackPoint(it.lat, it.lon) },
        distanceMeters = pathLengthMeters(points),
        durationSeconds = 0,
        startedAt = now,
        endedAt = now,
        ascentMeters = 0.0,
        descentMeters = 0.0,
        maxSpeedMps = 0.0,
        color = "#FF9F45",
        isRecording = false,
    )

    private fun parseGpx(text: String): ImportResult {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(text))

        val waypoints = ArrayList<Waypoint>()
        val tracks = ArrayList<Track>()
        val now = System.currentTimeMillis()

        var event = parser.eventType
        var currentWpt: JSONObject? = null
        var trackName: String? = null
        var currentTrack = ArrayList<TrackPoint>()
        var inTrack = false
        var pendingName: String? = null
        var currentText = StringBuilder()

        fun flushWaypoint() {
            val data = currentWpt ?: return
            val lat = data.optDouble("lat", Double.NaN)
            val lon = data.optDouble("lon", Double.NaN)
            if (isValidPosition(lat, lon)) {
                waypoints.add(
                    Waypoint(
                        id = newId(),
                        name = data.optString("name").ifBlank { "نقطه GPX" },
                        note = data.optString("desc"),
                        category = categoryKey(data.optString("type")),
                        lat = lat,
                        lon = lon,
                        elevation = data.optDouble("ele").takeIf { it.isFinite() },
                        createdAt = data.optLong("time", now).takeIf { it > 0 } ?: now,
                        updatedAt = now,
                    ),
                )
            }
            currentWpt = null
        }

        fun flushTrack() {
            if (currentTrack.size >= 2) {
                val points = currentTrack.toList()
                val gains = elevationGains(points)
                tracks.add(
                    Track(
                        id = newId(),
                        name = trackName?.ifBlank { "مسیر GPX" } ?: "مسیر GPX",
                        points = points,
                        distanceMeters = pathLengthMeters(points.map { LatLon(it.lat, it.lon) }),
                        durationSeconds = if (points.last().timestamp > points.first().timestamp) {
                            (points.last().timestamp - points.first().timestamp) / 1000
                        } else {
                            0
                        },
                        startedAt = points.first().timestamp.takeIf { it > 0 } ?: now,
                        endedAt = points.last().timestamp.takeIf { it > 0 } ?: now,
                        ascentMeters = gains.first,
                        descentMeters = gains.second,
                        maxSpeedMps = peakSpeedMps(points),
                        color = "#FF9F45",
                        isRecording = false,
                    ),
                )
            }
            currentTrack = ArrayList()
            trackName = null
            inTrack = false
        }

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.substringAfter(':')
                    currentText = StringBuilder()
                    when (tag) {
                        "wpt" -> currentWpt = JSONObject()
                        "trk" -> {
                            inTrack = true
                            trackName = null
                            currentTrack = ArrayList()
                        }
                        "rte" -> {
                            inTrack = true
                            trackName = null
                            currentTrack = ArrayList()
                        }
                        "trkpt", "rtept" -> {
                            val point = TrackPoint(
                                lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: Double.NaN,
                                lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: Double.NaN,
                            )
                            if (isValidPosition(point.lat, point.lon)) currentTrack.add(point)
                        }
                    }
                }
                XmlPullParser.TEXT -> currentText.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.substringAfter(':')
                    val textValue = currentText.toString().trim()
                    when (tag) {
                        "wpt" -> flushWaypoint()
                        "trk", "rte" -> flushTrack()
                        "name" -> {
                            if (inTrack) trackName = textValue else pendingName = textValue
                            currentWpt?.put("name", textValue)
                        }
                        "desc", "cmt" -> currentWpt?.put("desc", textValue)
                        "ele" -> {
                            val value = textValue.toDoubleOrNull()
                            if (value != null) {
                                currentWpt?.put("ele", value)
                                val index = currentTrack.lastIndex
                                if (inTrack && index >= 0) {
                                    currentTrack[index] = currentTrack[index].copy(elevation = value)
                                }
                            }
                        }
                        "time" -> {
                            val millis = parseTime(textValue)
                            if (millis > 0) {
                                currentWpt?.put("time", millis)
                                val index = currentTrack.lastIndex
                                if (inTrack && index >= 0) {
                                    currentTrack[index] = currentTrack[index].copy(timestamp = millis)
                                }
                            }
                        }
                        "type" -> currentWpt?.put("type", textValue)
                    }
                    if (tag == "wpt") pendingName = null
                }
            }
            event = parser.next()
        }
        flushWaypoint()

        return ImportResult(waypoints, tracks)
    }

    // ------------------------------------------------------------------ helpers

    /** Keeps built-in and user (`custom-…`) category keys across an export/import. */
    private fun categoryKey(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return WaypointCategory.GENERAL.key
        return WaypointCategory.entries.firstOrNull { it.key == value }?.key
            ?: value.takeIf { it.startsWith("custom-") }
            ?: WaypointCategory.GENERAL.key
    }

    private fun peakSpeedMps(points: List<TrackPoint>): Double {
        var maximum = 0.0
        for (i in 1 until points.size) {
            val seconds = (points[i].timestamp - points[i - 1].timestamp) / 1000.0
            if (seconds <= 0.0 || seconds > 60.0) continue
            val from = LatLon(points[i - 1].lat, points[i - 1].lon)
            val to = LatLon(points[i].lat, points[i].lon)
            val speed = distanceMeters(from, to) / seconds
            if (speed.isFinite() && speed > maximum) maximum = speed
        }
        return maximum
    }

    /** Sums uphill/downhill changes, ignoring sub-metre elevation noise. */
    internal fun elevationGains(points: List<TrackPoint>): Pair<Double, Double> {
        var last: Double? = null
        var ascent = 0.0
        var descent = 0.0
        points.forEach { point ->
            val elevation = point.elevation
            if (elevation != null && elevation.isFinite()) {
                val previous = last
                if (previous == null) {
                    last = elevation
                } else {
                    val delta = elevation - previous
                    if (delta >= 1.0) {
                        ascent += delta
                        last = elevation
                    } else if (delta <= -1.0) {
                        descent += -delta
                        last = elevation
                    }
                }
            }
        }
        return ascent to descent
    }

    private fun parseTime(value: String): Long {
        if (value.isBlank()) return 0L
        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss",
        )
        for (pattern in patterns) {
            runCatching {
                val format = SimpleDateFormat(pattern, Locale.US)
                format.timeZone = TimeZone.getTimeZone("UTC")
                return format.parse(value)?.time ?: 0L
            }
        }
        return 0L
    }

    private fun formatTime(millis: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(if (millis > 0) millis else System.currentTimeMillis()))
    }

    private fun formatDouble(value: Double): String = String.format(Locale.US, "%.7f", value)

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
