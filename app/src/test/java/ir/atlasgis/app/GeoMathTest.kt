package ir.atlasgis.app

import ir.atlasgis.app.core.CoordinateFormat
import ir.atlasgis.app.core.LatLon
import ir.atlasgis.app.core.boundingBoxOf
import ir.atlasgis.app.core.distanceMeters
import ir.atlasgis.app.core.formatArea
import ir.atlasgis.app.core.formatCoordinate
import ir.atlasgis.app.core.formatDistance
import ir.atlasgis.app.core.metersPerPixel
import ir.atlasgis.app.core.pathLengthMeters
import ir.atlasgis.app.core.polygonAreaSqMeters
import ir.atlasgis.app.core.toUtm
import ir.atlasgis.app.core.utmZoneNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {

    private val tehran = LatLon(35.6892, 51.3890)
    private val isfahan = LatLon(32.6546, 51.6680)

    @Test
    fun haversineMatchesKnownCityDistance() {
        val distance = distanceMeters(tehran, isfahan)
        // Tehran–Isfahan is ~336 km.
        assertTrue("distance=$distance", distance in 330_000.0..342_000.0)
    }

    @Test
    fun pathLengthSumsSegments() {
        val points = listOf(tehran, LatLon(35.70, 51.3890), LatLon(35.71, 51.3890))
        val expected = distanceMeters(points[0], points[1]) + distanceMeters(points[1], points[2])
        assertEquals(expected, pathLengthMeters(points), 0.5)
    }

    @Test
    fun polygonAreaIsCorrectWithinOnePercent() {
        // ~1 km x ~1.1 km box at latitude 35.
        val box = listOf(
            LatLon(35.00, 51.00),
            LatLon(35.00, 51.01),
            LatLon(35.01, 51.01),
            LatLon(35.01, 51.00),
        )
        val area = polygonAreaSqMeters(box)
        assertTrue("area=$area", area in 1_000_000.0..1_030_000.0)
        // Winding order must not matter.
        assertEquals(area, polygonAreaSqMeters(box.reversed()), 1.0)
    }

    @Test
    fun utmZoneAndRangeAreSane() {
        assertEquals(39, utmZoneNumber(tehran.lon))
        val utm = toUtm(tehran.lat, tehran.lon)
        assertEquals(39, utm.zone)
        assertEquals('N', utm.hemisphere)
        assertTrue("easting=${utm.easting}", utm.easting in 400_000.0..600_000.0)
        assertTrue("northing=${utm.northing}", utm.northing in 3_900_000.0..4_000_000.0)
    }

    @Test
    fun metersPerPixelMatchesWebMercator() {
        val mpp = metersPerPixel(35.0, 10.0)
        assertTrue("mpp=$mpp", mpp in 60.0..65.0)
        // Every zoom step halves the ground resolution.
        assertEquals(mpp / 2.0, metersPerPixel(35.0, 11.0), 0.01)
    }

    @Test
    fun boundingBoxAndFormatting() {
        val box = boundingBoxOf(listOf(tehran, isfahan))!!
        assertTrue(box.contains(tehran))
        assertTrue(box.contains(isfahan))
        assertEquals(isfahan.lat, box.south, 1e-9)

        assertTrue(formatDistance(850.0).contains(" m"))
        assertTrue(formatDistance(1250.0).contains(" km"))
        // Areas use squared units: m² below one square kilometre, km² above.
        assertTrue(formatArea(25_000.0).contains("m²"))
        assertTrue(formatArea(2_500_000.0).contains("km²"))
        assertTrue(formatCoordinate(tehran.lat, tehran.lon, CoordinateFormat.DMS).contains("°"))
        assertTrue(formatCoordinate(tehran.lat, tehran.lon, CoordinateFormat.UTM).contains("39"))
    }
}
