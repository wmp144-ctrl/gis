package ir.atlasgis.app

import ir.atlasgis.app.core.BoundingBox
import ir.atlasgis.app.core.CoordinateFormat
import ir.atlasgis.app.core.LatLon
import ir.atlasgis.app.core.analyzeTrack
import ir.atlasgis.app.core.estimateSpeedMps
import ir.atlasgis.app.core.locateOnRoute
import ir.atlasgis.app.data.BackupCodec
import ir.atlasgis.app.data.AppSettings
import ir.atlasgis.app.data.TileGrid
import ir.atlasgis.app.data.Track
import ir.atlasgis.app.data.TrackPoint
import ir.atlasgis.app.data.Waypoint
import ir.atlasgis.app.data.WaypointCategory
import ir.atlasgis.app.data.WaypointCategoryStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewFeatureLogicTest {

    // ------------------------------------------------------------- route guidance

    @Test
    fun routeProjectionMeasuresOffsetAndRemainingDistance() {
        val route = listOf(
            LatLon(35.0, 51.0),
            LatLon(35.0, 51.01),
            LatLon(35.0, 51.02),
        )

        val onLine = locateOnRoute(LatLon(35.0, 51.005), route)
        assertNotNull(onLine)
        assertTrue(onLine!!.offRouteMeters < 5.0)
        assertTrue(onLine.remainingMeters > 1000.0)
        assertTrue(onLine.fraction in 0f..1f)

        val offLine = locateOnRoute(LatLon(35.002, 51.005), route)!!
        assertTrue(offLine.offRouteMeters > 100.0)
        assertTrue(offLine.isOffRoute)

        assertNull(locateOnRoute(LatLon(35.0, 51.0), emptyList()))
        assertEquals(0, locateOnRoute(LatLon(35.0, 51.0), listOf(LatLon(35.0, 51.0)))!!.nextIndex)
    }

    @Test
    fun eitEstimateFallsBackToAverageMovingSpeed() {
        assertEquals(2.0, estimateSpeedMps(2.0, 0.0, 0)!!, 1e-9)
        assertEquals(1.0, estimateSpeedMps(0.0, 600.0, 600)!!, 1e-9)
        assertNull(estimateSpeedMps(null, 0.0, 0))
    }

    // --------------------------------------------------------------- track analysis

    @Test
    fun analysisSplitsTrackIntoKilometresAndSeparatesStoppedTime() {
        val start = 1_700_000_000_000L
        // ~111 m per 0.001° of longitude at the equator, one point per minute.
        val points = (0..12).map { index ->
            TrackPoint(
                lat = 0.0,
                lon = index * 0.001,
                elevation = 100.0 + index * 5.0,
                timestamp = start + index * 60_000L,
            )
        }
        val track = Track(
            id = "t1",
            name = "test",
            points = points,
            distanceMeters = 1300.0,
            durationSeconds = 1500L,
            startedAt = start,
            endedAt = start + 720_000L,
            ascentMeters = 60.0,
            descentMeters = 0.0,
            maxSpeedMps = 3.0,
            color = "#FF9F45",
            isRecording = false,
            movingSeconds = 720L,
        )

        val analysis = analyzeTrack(track)
        assertEquals(2, analysis.splits.size)
        assertTrue(analysis.splits.first().distanceMeters in 900.0..1200.0)
        assertTrue(analysis.splits.first().durationSeconds > 0)
        assertEquals(720L, analysis.totalSeconds)
        assertEquals(720L, analysis.movingSeconds)
        assertEquals(0L, analysis.stoppedSeconds)
        assertTrue(analysis.hasElevation)
        assertEquals(100.0, analysis.minElevation!!, 1e-6)
        assertEquals(160.0, analysis.maxElevation!!, 1e-6)
        assertTrue(analysis.ascentMeters >= 55.0)
        assertEquals(points.size, analysis.samples.size)
    }

    @Test
    fun analysisFallsBackToDistanceWhenTimestampsAreMissing() {
        val points = listOf(
            TrackPoint(35.0, 51.0, 1200.0, 0),
            TrackPoint(35.0, 51.005, 1210.0, 0),
            TrackPoint(35.0, 51.010, 1220.0, 0),
        )
        val track = Track(
            id = "t2",
            name = "imported",
            points = points,
            distanceMeters = 900.0,
            durationSeconds = 300L,
            startedAt = 0,
            endedAt = 0,
            ascentMeters = 0.0,
            descentMeters = 0.0,
            maxSpeedMps = 0.0,
            color = "#FF9F45",
            isRecording = false,
        )
        val analysis = analyzeTrack(track)
        assertEquals(300L, analysis.totalSeconds)
        assertEquals(300L, analysis.movingSeconds)
        assertTrue(analysis.samples.last().elapsedSeconds >= 290)
        assertEquals(1, analysis.splits.size)
    }

    // ------------------------------------------------------------------- tile grid

    @Test
    fun tileGridEstimatesAndRejectsOversizedRegions() {
        val tehran = BoundingBox(south = 35.60, west = 51.30, north = 35.75, east = 51.50)
        val count = TileGrid.estimate(tehran, 10, 13)
        assertTrue(count in 1..TileGrid.MAX_TILES)
        assertNull(TileGrid.validate(tehran, 10, 13))
        assertNotNull(TileGrid.validate(tehran, 13, 10))

        val world = BoundingBox(-85.0, -180.0, 85.0, 180.0)
        assertNotNull(TileGrid.validate(world, 0, 14))

        assertEquals(0, TileGrid.latToTileY(85.0, 5))
        assertEquals(0, TileGrid.lonToTileX(-180.0, 5))
        assertEquals(31, TileGrid.lonToTileX(179.9, 5))
        assertEquals(31, TileGrid.tmsRow(0, 5))
    }

    // -------------------------------------------------------------------- backup

    @Test
    fun backupRoundTripsWaypointsTracksAndCategories() {
        val track = Track(
            id = "track-1",
            name = "کوه",
            points = listOf(TrackPoint(35.0, 51.0, 1400.0, 1L), TrackPoint(35.001, 51.001, 1410.0, 2L)),
            distanceMeters = 141.0,
            durationSeconds = 120L,
            startedAt = 10L,
            endedAt = 130L,
            ascentMeters = 10.0,
            descentMeters = 0.0,
            maxSpeedMps = 2.0,
            color = "#FF9F45",
            isRecording = false,
            movingSeconds = 100L,
        )
        val snapshot = BackupCodec.Snapshot(
            waypoints = listOf(
                Waypoint(
                    id = "wp-1",
                    name = "چشمه",
                    note = "آب سرد",
                    category = WaypointCategory.WATER.key,
                    lat = 35.1,
                    lon = 51.2,
                    elevation = 2100.0,
                    createdAt = 5L,
                    updatedAt = 6L,
                ),
            ),
            tracks = listOf(track),
            categories = WaypointCategoryStyle.defaults(),
            settings = AppSettings(
                coordinateFormat = CoordinateFormat.UTM,
                keepScreenOn = false,
                showScaleBar = false,
                showCoordinates = false,
            ),
        )

        val decoded = BackupCodec.decode(BackupCodec.encode(snapshot))

        assertEquals(1, decoded.waypoints.size)
        assertEquals("چشمه", decoded.waypoints.first().name)
        assertEquals("آب سرد", decoded.waypoints.first().note)
        assertEquals(2100.0, decoded.waypoints.first().elevation!!, 1e-6)
        assertEquals(1, decoded.tracks.size)
        assertEquals(2, decoded.tracks.first().points.size)
        assertEquals(100L, decoded.tracks.first().movingSeconds)
        assertEquals(snapshot.categories.size, decoded.categories.size)
        assertTrue(BackupCodec.summary(decoded).contains("نقطه"))
        assertEquals(snapshot.settings, decoded.settings)
        assertEquals(CoordinateFormat.UTM, decoded.settings!!.coordinateFormat)
        assertTrue(BackupCodec.summary(decoded).contains("تنظیمات"))
    }

    @Test
    fun backupWithoutSettingsStillDecodesAndFallsBackToDefaults() {
        val legacy = """
            {
              "format": "atlasgis-backup",
              "version": 1,
              "waypoints": [],
              "tracks": [],
              "categories": []
            }
        """.trimIndent()
        assertNull(BackupCodec.decode(legacy).settings)

        val withUnknownFormat = """
            {
              "format": "atlasgis-backup",
              "version": 2,
              "settings": { "coordinateFormat": "MARS", "keepScreenOn": false },
              "waypoints": [],
              "tracks": [],
              "categories": []
            }
        """.trimIndent()
        val settings = BackupCodec.decode(withUnknownFormat).settings!!
        assertEquals(CoordinateFormat.DECIMAL, settings.coordinateFormat)
        assertEquals(false, settings.keepScreenOn)
        assertTrue(settings.showScaleBar)
        assertTrue(settings.showCoordinates)
    }
}
