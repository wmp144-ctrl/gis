package ir.atlasgis.app

import ir.atlasgis.app.core.BoundingBox
import ir.atlasgis.app.data.MapLayer
import ir.atlasgis.app.data.MapStyleBuilder
import ir.atlasgis.app.data.MapStylePreset
import ir.atlasgis.app.data.TileFormat
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The built-in palettes must reach the generated style instead of the old fixed colours. */
class MapStylePresetTest {

    private fun vectorLayer(preset: MapStylePreset) = MapLayer(
        id = "vector-${preset.key}",
        name = "Vector map",
        filePath = File.createTempFile("map", ".mbtiles").apply { writeText("x") }.absolutePath,
        format = TileFormat.VECTOR,
        minZoom = 0,
        maxZoom = 14,
        bounds = BoundingBox(35.0, 51.0, 36.0, 52.0),
        tileSize = 512,
        visible = true,
        opacity = 1f,
        sortOrder = 0,
        attribution = null,
        vectorLayers = listOf("water", "landuse", "transportation", "building", "place"),
        fileSizeBytes = 1024,
        addedAt = 1L,
        stylePreset = preset,
    )

    @Test
    fun everyPresetPaintsItsOwnBackgroundAndWater() {
        for (preset in MapStylePreset.entries) {
            val style = JSONObject(MapStyleBuilder.build(listOf(vectorLayer(preset))))
            val layers = style.getJSONArray("layers")

            val background = layers.getJSONObject(0)
            assertEquals("background", background.getString("type"))
            assertEquals(
                preset.palette.background,
                background.getJSONObject("paint").getString("background-color"),
            )

            var waterColor: String? = null
            for (i in 0 until layers.length()) {
                val layer = layers.getJSONObject(i)
                if (layer.optString("source-layer") == "water") {
                    waterColor = layer.getJSONObject("paint").optString("fill-color")
                    break
                }
            }
            assertNotNull("no water layer for ${preset.key}", waterColor)
            assertEquals(preset.palette.water, waterColor)
        }
    }

    @Test
    fun presetsAreVisuallyDistinct() {
        val backgrounds = MapStylePreset.entries.map { it.palette.background }
        assertEquals(backgrounds.size, backgrounds.toSet().size)
        assertEquals(MapStylePreset.entries.size, MapStylePreset.entries.map { it.label }.toSet().size)
        assertTrue(MapStylePreset.NIGHT.palette.background != MapStylePreset.LIGHT.palette.background)
    }

    @Test
    fun persistedKeysAreStableAndUnknownKeysFallBack() {
        assertEquals("night", MapStylePreset.NIGHT.key)
        assertEquals("light", MapStylePreset.LIGHT.key)
        assertEquals("outdoor", MapStylePreset.OUTDOOR.key)
        assertEquals("contrast", MapStylePreset.CONTRAST.key)
        MapStylePreset.entries.forEach { assertEquals(it, MapStylePreset.fromKey(it.key)) }
        assertEquals(MapStylePreset.DEFAULT, MapStylePreset.fromKey(null))
        assertEquals(MapStylePreset.DEFAULT, MapStylePreset.fromKey("does-not-exist"))
    }
}
