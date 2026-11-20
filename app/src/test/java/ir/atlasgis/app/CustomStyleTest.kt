package ir.atlasgis.app

import ir.atlasgis.app.core.BoundingBox
import ir.atlasgis.app.data.CustomStyleTemplate
import ir.atlasgis.app.data.MapLayer
import ir.atlasgis.app.data.MapStyleBuilder
import ir.atlasgis.app.data.TileFormat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A user style.json must render the local archive, not the URL it was written for,
 * and must survive into the single style the app keeps for the whole session.
 */
class CustomStyleTest {

    private val template = """
        {
          "version": 8,
          "sources": {
            "omt": {
              "type": "vector",
              "url": "https://example.com/tiles.json",
              "tiles": ["https://example.com/{z}/{x}/{y}.pbf"],
              "minzoom": 0,
              "maxzoom": 14
            }
          },
          "layers": [
            {
              "id": "water",
              "type": "fill",
              "source": "omt",
              "source-layer": "water",
              "paint": {"fill-color": "#123456"}
            },
            {
              "id": "roads",
              "type": "line",
              "source": "omt",
              "source-layer": "transportation",
              "paint": {
                "line-width": 2,
                "line-opacity": ["interpolate", ["linear"], ["zoom"], 8, 0.2, 14, 0.9]
              }
            }
          ]
        }
    """.trimIndent()

    private val archive = File.createTempFile("vector", ".mbtiles").apply { writeText("x") }

    private fun vectorLayer(stylePath: String?) = MapLayer(
        id = "vector-1",
        name = "Vector map",
        filePath = archive.absolutePath,
        format = TileFormat.VECTOR,
        minZoom = 3,
        maxZoom = 14,
        bounds = BoundingBox(35.0, 51.0, 36.0, 52.0),
        tileSize = 512,
        visible = true,
        opacity = 0.5f,
        sortOrder = 0,
        attribution = null,
        vectorLayers = listOf("water", "transportation"),
        fileSizeBytes = 2048,
        addedAt = 1L,
        stylePath = stylePath,
    )

    private fun styleFile(content: String): File =
        File.createTempFile("style", ".json").apply { writeText(content) }

    @Test
    fun customStyleRepointsVectorSourceAtLocalArchive() {
        val file = styleFile(template)
        val style = JSONObject(MapStyleBuilder.build(listOf(vectorLayer(file.absolutePath))))
        val sources = style.getJSONObject("sources")

        val customSource = sources.getJSONObject("atlas-src-vector-1~omt")
        assertEquals("vector", customSource.getString("type"))
        assertEquals("mbtiles://${archive.absolutePath}", customSource.getString("url"))
        assertFalse(customSource.has("tiles"))
        assertEquals(3, customSource.getInt("minzoom"))
        assertEquals(14, customSource.getInt("maxzoom"))

        // Overlay sources must still exist so live updates keep working.
        assertNotNull(sources.optJSONObject(MapStyleBuilder.SRC_WAYPOINTS))
        assertNotNull(sources.optJSONObject(MapStyleBuilder.SRC_MEASURE))

        val layers = style.getJSONArray("layers")
        val ids = HashSet<String>()
        val customLayers = ArrayList<JSONObject>()
        for (i in 0 until layers.length()) {
            val layer = layers.getJSONObject(i)
            assertTrue(ids.add(layer.getString("id")))
            if (layer.optString("source").startsWith("atlas-src-vector-1~")) customLayers.add(layer)
        }
        assertEquals(2, customLayers.size)
        assertTrue(customLayers.all { it.getString("source") == "atlas-src-vector-1~omt" })
        assertEquals("water", customLayers[0].getString("source-layer"))

        // The layer opacity slider still scales a template layer.
        assertEquals(0.5, customLayers[0].getJSONObject("paint").getDouble("fill-opacity"), 1e-6)
        val lineOpacity = customLayers[1].getJSONObject("paint").get("line-opacity") as JSONArray
        assertEquals("*", lineOpacity.getString(0))
        assertEquals("interpolate", lineOpacity.getJSONArray(1).getString(0))
        assertEquals(0.5, lineOpacity.getDouble(2), 1e-6)

        // Overlays stay on top of the template layers.
        val lastLayer = layers.getJSONObject(layers.length() - 1)
        assertTrue(lastLayer.getString("id").startsWith("atlas-"))
    }

    @Test
    fun templateWithoutVectorSourceFallsBackToAutomaticStyle() {
        val broken = """{"version":8,"sources":{},"layers":[{"id":"a","type":"fill","source":"x"}]}"""
        val file = styleFile(broken)
        val style = JSONObject(MapStyleBuilder.build(listOf(vectorLayer(file.absolutePath))))
        val layers = style.getJSONArray("layers")

        val generated = (0 until layers.length())
            .map { layers.getJSONObject(it) }
            .filter { it.optString("id").startsWith("atlas-vec-vector-1-") }
        assertTrue("automatic vector layers expected", generated.isNotEmpty())
        assertTrue(style.getJSONObject("sources").has("atlas-src-vector-1"))
    }

    @Test
    fun validateRejectsUnsupportedDocuments() {
        val cases = listOf(
            "not json at all",
            """{"version":7,"sources":{},"layers":[]}""",
            """{"version":8,"sources":{},"layers":[]}""",
            """{"version":8,"sources":{"r":{"type":"raster","url":"x"}},"layers":[{"id":"a","type":"raster","source":"r"}]}""",
        )
        cases.forEach { raw ->
            var failed = false
            try {
                CustomStyleTemplate.validate(raw)
            } catch (_: IllegalArgumentException) {
                failed = true
            }
            assertTrue("should reject: $raw", failed)
        }
        // A valid document must pass.
        CustomStyleTemplate.validate(template)
    }
}
