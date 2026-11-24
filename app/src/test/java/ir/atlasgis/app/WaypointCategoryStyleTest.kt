package ir.atlasgis.app

import ir.atlasgis.app.data.OverlayGeoJson
import ir.atlasgis.app.data.Waypoint
import ir.atlasgis.app.data.WaypointCategory
import ir.atlasgis.app.data.WaypointCategoryStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Categories are persisted per key, so their keys and colours must stay stable. */
class WaypointCategoryStyleTest {

    private fun waypoint(category: String) = Waypoint(
        id = "wp-1",
        name = "نقطه",
        note = "",
        category = category,
        lat = 35.7,
        lon = 51.4,
        elevation = null,
        createdAt = 1L,
        updatedAt = 1L,
    )

    @Test
    fun defaultsCoverEveryCategoryWithKnownIcons() {
        val styles = WaypointCategoryStyle.defaults()
        assertEquals(WaypointCategory.entries.size, styles.size)
        WaypointCategory.entries.forEach { category ->
            val style = styles.firstOrNull { it.key == category.key }
            assertTrue("missing style for ${category.key}", style != null)
            assertTrue("blank icon for ${category.key}", !style!!.icon.isBlank())
            assertTrue("blank label for ${category.key}", style.label.isNotBlank())
        }
        assertEquals(styles.size, styles.map { it.key }.toSet().size)
    }

    @Test
    fun builtInCategoriesAreProtectedFromDeletion() {
        WaypointCategory.entries.forEach { category ->
            assertTrue(category.key, WaypointCategoryStyle.isBuiltIn(category.key))
        }
        assertFalse(WaypointCategoryStyle.isBuiltIn(WaypointCategoryStyle.custom("دکل", 0xFF3ED598.toInt(), "flag").key))
    }

    @Test
    fun hexColorFormatsRgbWithoutAlpha() {
        val style = WaypointCategoryStyle.defaults().first { it.key == WaypointCategory.WATER.key }
        assertEquals("#5FB8FF", style.hexColor)
        assertEquals("#000000", style.copy(color = 0x00000000).hexColor)
    }

    @Test
    fun mapMarkersUseTheEditedCategoryColour() {
        val json = OverlayGeoJson.waypoints(
            listOf(waypoint(WaypointCategory.WATER.key)),
            selectedId = null,
            categoryColors = mapOf(WaypointCategory.WATER.key to "#123456"),
        )
        assertTrue(json.contains("\"color\":\"#123456\""))

        val fallback = OverlayGeoJson.waypoints(listOf(waypoint(WaypointCategory.WATER.key)), null)
        assertTrue(fallback.contains(OverlayGeoJson.colorFor(WaypointCategory.WATER.key)))
    }
}
