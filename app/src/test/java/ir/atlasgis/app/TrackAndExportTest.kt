package ir.atlasgis.app

import ir.atlasgis.app.core.LatLon
import ir.atlasgis.app.data.GeoFileCodec
import ir.atlasgis.app.data.GpsFix
import ir.atlasgis.app.data.Waypoint
import ir.atlasgis.app.data.WaypointCategory
import ir.atlasgis.app.domain.TrackSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackAndExportTest {

    private var clock = 1_000_000L

    private fun fix(lat: Double, lon: Double, accuracy: Float = 5f, seconds: Int = 1): GpsFix {
        clock += seconds * 1000L
        return GpsFix(
            lat = lat,
            lon = lon,
            accuracyMeters = accuracy,
            altitude = 1200.0 + (lat - 35.0) * 1000,
            speedMps = null,
            bearing = 0.0,
            satellites = 9,
            timestamp = clock,
        )
    }

    @Test
    fun jitterAndInaccurateFixesAreRejected() {
        val session = TrackSession("id", "t", clock) { clock }
        assertTrue(session.addFix(fix(35.0, 51.0)))
        // ~11 m per 0.0001 degree of latitude.
        assertTrue(session.addFix(fix(35.0001, 51.0)))
        val afterTwo = session.distanceMeters
        // Half-metre jitter must not accumulate.
        assertFalse(session.addFix(fix(35.0001045, 51.0)))
        assertEquals(afterTwo, session.distanceMeters, 1e-6)
        // Bad accuracy is ignored.
        assertFalse(session.addFix(fix(35.001, 51.0, accuracy = 200f)))
        assertEquals(2, session.pointCount)
        assertTrue(session.distanceMeters > 10.0)
    }

    @Test
    fun pauseFreezesDistanceAndDuration() {
        val session = TrackSession("id2", "t", clock) { clock }
        session.addFix(fix(35.0, 51.0))
        session.addFix(fix(35.001, 51.0))
        val distanceBeforePause = session.distanceMeters
        clock += 30_000
        val elapsedBeforePause = session.elapsedSeconds()
        session.pause()
        clock += 120_000
        assertFalse(session.addFix(fix(35.01, 51.0)))
        assertEquals(distanceBeforePause, session.distanceMeters, 1e-9)
        assertEquals(elapsedBeforePause, session.elapsedSeconds())
        session.resume()
        assertTrue(session.addFix(fix(35.011, 51.0)))
        assertTrue(session.distanceMeters > distanceBeforePause)
        assertNotNull(session.toTrack(recording = false))
        assertTrue(session.snapshot().paused.not())
    }

    @Test
    fun geoJsonImportRestoresPointsAndLines() {
        val geoJson = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"name":"A","description":"d"},
               "geometry":{"type":"Point","coordinates":[51.4,35.7]}},
              {"type":"Feature","properties":{"name":"path"},
               "geometry":{"type":"LineString","coordinates":[[51.4,35.7],[51.41,35.71],[51.42,35.72]]}},
              {"type":"Feature","properties":{"name":"area"},
               "geometry":{"type":"Polygon","coordinates":[[[51.4,35.7],[51.41,35.7],[51.41,35.71],[51.4,35.7]]]}}
            ]}
        """.trimIndent()
        val result = GeoFileCodec.parse(geoJson)
        assertEquals(1, result.waypoints.size)
        assertEquals("A", result.waypoints[0].name)
        assertEquals(35.7, result.waypoints[0].lat, 1e-9)
        assertEquals(2, result.tracks.size)
        assertTrue(result.tracks.all { it.points.size >= 3 })
        assertTrue(result.tracks[0].distanceMeters > 0)
    }

    @Test
    fun gpxExportContainsWaypointsAndTracks() {
        val now = 1_700_000_000_000L
        val waypoint = Waypoint("w", "چشمه & آب", "یادداشت", WaypointCategory.WATER.key, 35.7, 51.4, 1300.0, now, now)
        val session = TrackSession("t", "t", now - 60_000) { now }
        session.addFix(fix(35.0, 51.0))
        session.addFix(fix(35.001, 51.0))
        val gpx = GeoFileCodec.toGpx(listOf(waypoint), listOf(session.toTrack(false)))

        assertTrue(gpx.startsWith("<?xml"))
        assertTrue(gpx.contains("<wpt lat=\"35.7000000\" lon=\"51.4000000\">"))
        assertTrue(gpx.contains("چشمه &amp; آب"))
        assertTrue(gpx.contains("<trk>"))
        assertTrue(gpx.contains("<trkpt"))
        assertTrue(gpx.trimEnd().endsWith("</gpx>"))
    }

    @Test
    fun geoJsonExportIsParsable() {
        val now = 1_700_000_000_000L
        val waypoint = Waypoint("w", "n", "", WaypointCategory.GENERAL.key, 35.7, 51.4, null, now, now)
        val exported = GeoFileCodec.waypointsToGeoJson(listOf(waypoint))
        val reparsed = GeoFileCodec.parse(exported)
        assertEquals(1, reparsed.waypoints.size)
        assertEquals(51.4, reparsed.waypoints[0].lon, 1e-9)
        assertTrue(GeoFileCodec.parse("not a map file").waypoints.isEmpty())
        assertTrue(LatLon(0.0, 0.0).isValid())
    }
}
