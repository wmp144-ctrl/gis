package ir.atlasgis.app

import ir.atlasgis.app.data.MapLayer
import ir.atlasgis.app.data.MapLabelLayers
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
 * Labels are the difference between "lines and dots" and a readable offline map.
 * These tests pin the two things that make them work: offline font faces with a
 * Persian range, and symbol layers for street, settlement and POI names.
 */
class MapLabelLayerTest {

    private fun vectorLayer(
        path: String,
        layers: List<String> = listOf(
            "water", "landuse", "transportation", "transportation_name",
            "building", "place", "poi", "water_name", "mountain_peak", "aerodrome_label",
        ),
    ) = MapLayer(
        id = "vector-1",
        name = "Vector map",
        filePath = path,
        format = TileFormat.VECTOR,
        minZoom = 2,
        maxZoom = 15,
        bounds = null,
        tileSize = 512,
        visible = true,
        opacity = 1f,
        sortOrder = 0,
        attribution = null,
        vectorLayers = layers,
        fileSizeBytes = 1024,
        addedAt = 1L,
    )

    private fun styleFor(layers: List<String>? = null): JSONObject {
        val file = File.createTempFile("vector", ".mbtiles").apply { writeText("x") }
        val layer = if (layers == null) vectorLayer(file.absolutePath) else vectorLayer(file.absolutePath, layers)
        return JSONObject(MapStyleBuilder.build(listOf(layer)))
    }

    private fun symbolLayers(style: JSONObject): List<JSONObject> {
        val layers = style.getJSONArray("layers")
        return (0 until layers.length())
            .map { layers.getJSONObject(it) }
            .filter { it.optString("type") == "symbol" }
    }

    @Test
    fun styleShipsOfflineFontFacesForLatinAndPersian() {
        val style = styleFor()
        assertFalse("native builds must not fetch a glyph server", style.has("glyphs"))
        val faces = style.getJSONObject("font-faces")
        assertNotNull(faces.optJSONArray(MapLabelLayers.FONT_REGULAR))
        assertNotNull(faces.optJSONArray(MapLabelLayers.FONT_BOLD))

        val regular = faces.getJSONArray(MapLabelLayers.FONT_REGULAR)
        assertEquals(2, regular.length())
        val latin = regular.getJSONObject(0)
        val arabic = regular.getJSONObject(1)
        assertTrue(latin.getString("url").startsWith("asset://fonts/NotoSans"))
        assertTrue(arabic.getString("url").contains("Arabic"))
        // Persian needs U+0600-06FF, which only the Arabic face declares.
        val arabicRanges = arabic.getJSONArray("unicode-range").toString()
        assertTrue("Arabic range missing: $arabicRanges", arabicRanges.contains("U+0600-06FF"))
        assertFalse(latin.getJSONArray("unicode-range").toString().contains("U+0600-06FF"))
    }

    @Test
    fun streetNamesAreDrawnAlongTheLine() {
        val style = styleFor()
        val streetLabels = symbolLayers(style).filter {
            it.optString("source-layer") == "transportation_name"
        }
        assertTrue("street labels expected", streetLabels.size >= 2)
        streetLabels.forEach { layer ->
            val layout = layer.getJSONObject("layout")
            assertEquals("line", layout.getString("symbol-placement"))
            assertEquals(MapLabelLayers.FONT_REGULAR, layout.getJSONArray("text-font").getString(0))
            assertTrue("text-field must be a name expression", layout.get("text-field") is JSONArray)
        }
        // Alleys and residential streets appear later than motorways.
        val zoomGates = streetLabels.map { it.getInt("minzoom") }.toSet()
        assertTrue(zoomGates.contains(10))
        assertTrue(zoomGates.contains(14))
    }

    @Test
    fun settlementNamesUseZoomTiers() {
        val style = styleFor()
        val placeLabels = symbolLayers(style).filter { it.optString("source-layer") == "place" }
        assertTrue("place tiers expected", placeLabels.size >= 3)
        assertTrue(placeLabels.any { it.getJSONObject("layout").getJSONArray("text-font").getString(0) == MapLabelLayers.FONT_BOLD })
        assertTrue(placeLabels.map { it.getInt("minzoom") }.toSet().containsAll(listOf(3, 5, 12)))
    }

    @Test
    fun poisGetIconsAndNames() {
        val style = styleFor()
        val poi = symbolLayers(style).firstOrNull { it.optString("source-layer") == "poi" }
        assertNotNull("POI symbol layer expected", poi)
        val layout = poi!!.getJSONObject("layout")
        assertEquals(14, poi.getInt("minzoom"))
        assertTrue(layout.get("icon-image") is JSONArray)
        assertTrue(layout.get("text-field") is JSONArray)
        assertEquals("top", layout.getString("text-anchor"))

        val iconExpression = layout.getJSONArray("icon-image")
        val tags = HashSet<String>()
        for (i in 2 until iconExpression.length()) {
            val value = iconExpression.opt(i)
            if (value is String) tags.add(value)
        }
        // The tags the user named explicitly must map to a real style image.
        listOf("fuel", "parking", "school", "supermarket", "hospital").forEach { tag ->
            assertTrue("no icon for $tag", tags.contains(tag))
        }
        val images = tags.filter { it.startsWith("atlas-") }.toSet()
        assertTrue(images.contains("atlas-fuel"))
        assertTrue(images.contains("atlas-school"))
    }

    @Test
    fun waterAndAirportLabelsExist() {
        val style = styleFor()
        val symbols = symbolLayers(style)
        assertTrue(symbols.any { it.optString("source-layer") == "water_name" && it.getInt("minzoom") == 12 })
        assertTrue(symbols.any { it.optString("source-layer") == "aerodrome_label" })
        assertTrue(symbols.any { it.optString("source-layer") == "mountain_peak" })
    }

    @Test
    fun archivesWithoutNamingLayersStayCheap() {
        // No place/poi/water_name layer: the only symbols are street names that the
        // road layer itself may carry, and they disappear on archives without names.
        val style = styleFor(listOf("water", "landuse", "transportation", "building"))
        val symbols = symbolLayers(style)
        assertTrue(symbols.all { it.optString("source-layer") == "transportation" })
        assertTrue(symbols.all { it.optJSONArray("filter")?.getString(0) == "all" })
    }
}
