package ir.atlasgis.app

import ir.atlasgis.app.core.BoundingBox
import ir.atlasgis.app.core.LatLon
import ir.atlasgis.app.data.MapLayer
import ir.atlasgis.app.data.MapStyleBuilder
import ir.atlasgis.app.data.MeasureMode
import ir.atlasgis.app.data.OverlayGeoJson
import ir.atlasgis.app.data.TileFormat
import ir.atlasgis.app.data.Track
import ir.atlasgis.app.data.TrackPoint
import ir.atlasgis.app.data.Waypoint
import ir.atlasgis.app.data.WaypointCategory
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The generated style is the riskiest artefact: a malformed expression makes
 * MapLibre render a black screen without any exception. These tests parse it and
 * assert the invariants the renderer requires.
 */
class StyleAndOverlayJsonTest {

    private fun rasterLayer(path: String) = MapLayer(
        id = "raster-1",
        name = "Raster map",
        filePath = path,
        format = TileFormat.RASTER,
        minZoom = 0,
        maxZoom = 14,
        bounds = BoundingBox(35.0, 51.0, 36.0, 52.0),
        tileSize = 256,
        visible = true,
        opacity = 0.8f,
        sortOrder = 0,
        attribution = "© OSM",
        vectorLayers = emptyList(),
        fileSizeBytes = 1024,
        addedAt = 1L,
    )

    private fun vectorLayer(path: String) = rasterLayer(path).copy(
        id = "vector-1",
        format = TileFormat.VECTOR,
        sortOrder = 1,
        vectorLayers = listOf("water", "landuse", "transportation", "building", "place"),
    )

    @Test
    fun rasterStyleIsStructurallyValid() {
        val file = File.createTempFile("map", ".mbtiles").apply { writeText("x") }
        val style = JSONObject(MapStyleBuilder.build(listOf(rasterLayer(file.absolutePath))))

        assertEquals(8, style.getInt("version"))
        val layers = style.getJSONArray("layers")
        assertTrue(layers.length() >= 10)
        assertEquals("background", layers.getJSONObject(0).optString("type"))

        val sources = style.getJSONObject("sources")
        listOf(
            MapStyleBuilder.SRC_WAYPOINTS,
            MapStyleBuilder.SRC_MEASURE,
            MapStyleBuilder.SRC_TRACKS,
            MapStyleBuilder.SRC_ACTIVE_TRACK,
            MapStyleBuilder.SRC_GPS,
            MapStyleBuilder.SRC_GPS_ACCURACY,
        ).forEach { assertNotNull("missing source $it", sources.optJSONObject(it)) }

        val rasterSource = sources.getJSONObject("atlas-src-raster-1")
        assertEquals("raster", rasterSource.getString("type"))
        assertEquals("mbtiles://${file.absolutePath}", rasterSource.getString("url"))
        assertEquals(256, rasterSource.getInt("tileSize"))

        // Every layer must point at a real source and use a non-empty id.
        val ids = HashSet<String>()
        for (i in 0 until layers.length()) {
            val layer = layers.getJSONObject(i)
            val id = layer.getString("id")
            assertTrue("duplicate layer id $id", ids.add(id))
            val type = layer.getString("type")
            if (type != "background") {
                val source = layer.getString("source")
                assertNotNull("layer $id references missing source $source", sources.optJSONObject(source))
            }
        }

        // Raster opacity follows the layer setting.
        val rasterLayerJson = (0 until layers.length())
            .map { layers.getJSONObject(it) }
            .first { it.getString("id") == "atlas-raster-raster-1" }
        assertEquals(0.8, rasterLayerJson.getJSONObject("paint").getDouble("raster-opacity"), 0.001)

        // Version 8 styles must not reference an unavailable glyph endpoint.
        assertFalse(style.has("glyphs"))
    }

    @Test
    fun vectorStyleGeneratesLayersForKnownSchemas() {
        val file = File.createTempFile("vector", ".mbtiles").apply { writeText("x") }
        val style = JSONObject(MapStyleBuilder.build(listOf(vectorLayer(file.absolutePath))))
        val layers = style.getJSONArray("layers")

        val sourceLayers = HashSet<String>()
        for (i in 0 until layers.length()) {
            layers.getJSONObject(i).optString("source-layer").takeIf { it.isNotEmpty() }
                ?.let { sourceLayers.add(it) }
        }
        assertTrue(sourceLayers.contains("water"))
        assertTrue(sourceLayers.contains("transportation"))
        assertTrue(sourceLayers.contains("building"))
        assertTrue(sourceLayers.contains("place"))
        assertEquals("vector", style.getJSONObject("sources").getJSONObject("atlas-src-vector-1").getString("type"))
    }

    @Test
    fun zoomInterpolationAndFiltersAreWellFormed() {
        val file = File.createTempFile("map2", ".mbtiles").apply { writeText("x") }
        val style = JSONObject(MapStyleBuilder.build(listOf(rasterLayer(file.absolutePath))))
        val layers = style.getJSONArray("layers")

        var checkedInterpolation = false
        for (i in 0 until layers.length()) {
            val layer = layers.getJSONObject(i)
            val paint = layer.optJSONObject("paint") ?: continue
            val width = paint.opt("line-width")
            if (width is JSONArray && width.optString(0) == "interpolate") {
                checkedInterpolation = true
                assertEquals("linear", width.getJSONArray(1).getString(0))
                assertEquals("zoom", width.getJSONArray(2).getString(0))
                assertTrue(
                    "interpolation must contain zoom/value pairs",
                    (width.length() - 3) % 2 == 0 && width.length() >= 5,
                )
            }
            val filter = layer.optJSONArray("filter")
            if (filter != null) {
                assertEquals("==", filter.getString(0))
                assertTrue(filter.get(1) is JSONArray)
            }
        }
        assertTrue(checkedInterpolation)
    }

    @Test
    fun waypointGeoJsonCarriesIdColourAndSelection() {
        val now = 1_700_000_000_000L
        val waypoints = listOf(
            Waypoint("a", "چشمه", "", WaypointCategory.WATER, 35.6, 51.4, null, now, now),
            Waypoint("b", "خطر", "note", WaypointCategory.HAZARD, 35.7, 51.5, 1200.0, now, now),
        )
        val json = JSONObject(OverlayGeoJson.waypoints(waypoints, "b"))
        val features = json.getJSONArray("features")
        assertEquals(2, features.length())
        val first = features.getJSONObject(0)
        assertEquals("a", first.getJSONObject("properties").getString("id"))
        assertEquals("#5FB8FF", first.getJSONObject("properties").getString("color"))
        assertFalse(first.getJSONObject("properties").getBoolean("selected"))
        assertTrue(features.getJSONObject(1).getJSONObject("properties").getBoolean("selected"))
        val coordinates = first.getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(51.4, coordinates.getDouble(0), 1e-6)
        assertEquals(35.6, coordinates.getDouble(1), 1e-6)
    }

    @Test
    fun areaMeasurementProducesClosedPolygonAndVertices() {
        val points = listOf(LatLon(35.0, 51.0), LatLon(35.0, 51.01), LatLon(35.01, 51.01))
        val json = JSONObject(OverlayGeoJson.measurement(points, closed = true))
        val features = json.getJSONArray("features")
        val polygon = features.getJSONObject(0).getJSONObject("geometry")
        assertEquals("Polygon", polygon.getString("type"))
        val ring = polygon.getJSONArray("coordinates").getJSONArray(0)
        assertEquals(points.size + 1, ring.length())
        assertEquals(ring.getJSONArray(0).getDouble(0), ring.getJSONArray(ring.length() - 1).getDouble(0), 1e-9)
        // Plus one Point feature per vertex.
        assertEquals(points.size, features.length() - 1)
    }

    @Test
    fun distanceMeasurementIsLineString() {
        val points = listOf(LatLon(35.0, 51.0), LatLon(35.01, 51.01))
        val json = JSONObject(OverlayGeoJson.measurement(points, closed = false))
        val geometry = json.getJSONArray("features").getJSONObject(0).getJSONObject("geometry")
        assertEquals("LineString", geometry.getString("type"))
        val coordinates = geometry.getJSONArray("coordinates")
        assertEquals(2, coordinates.length())
        val position = coordinates.getJSONArray(0)
        assertEquals(2, position.length())
        assertEquals(51.0, position.getDouble(0), 1e-6)
        assertEquals(35.0, position.getDouble(1), 1e-6)
    }

    @Test
    fun tracksAndGpsOverlaysAreValid() {
        val track = Track(
            id = "t1",
            name = "t",
            points = listOf(TrackPoint(35.0, 51.0), TrackPoint(35.001, 51.001)),
            distanceMeters = 100.0,
            durationSeconds = 60,
            startedAt = 1,
            endedAt = 2,
            ascentMeters = 0.0,
            descentMeters = 0.0,
            maxSpeedMps = 1.0,
            color = "#FF9F45",
            isRecording = false,
        )
        val tracks = JSONObject(OverlayGeoJson.tracks(listOf(track)))
        assertEquals(1, tracks.getJSONArray("features").length())
        val trackCoordinates = tracks.getJSONArray("features").getJSONObject(0)
            .getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(2, trackCoordinates.getJSONArray(0).length())
        assertEquals(2, trackCoordinates.length())

        val empty = JSONObject(OverlayGeoJson.empty())
        assertEquals(0, empty.getJSONArray("features").length())

        assertTrue(OverlayGeoJson.measurement(emptyList(), true).contains("FeatureCollection"))
        assertEquals(
            0,
            JSONObject(OverlayGeoJson.line(listOf(LatLon(1.0, 1.0)))).getJSONArray("features").length(),
        )
    }
}
