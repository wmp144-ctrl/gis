package ir.atlasgis.app.data

import java.util.Locale

/**
 * User-adjustable look of a waypoint category. The `key` stays bound to
 * [WaypointCategory], while the label, colour and icon can be changed and are
 * persisted so every list, chip and map popup uses the same styling.
 */
data class WaypointCategoryStyle(
    val key: String,
    val label: String,
    val color: Int,
    val icon: String,
    /** Whether this category's points are drawn on the map (per-category filter). */
    val visible: Boolean = true,
) {
    /** `#RRGGBB` for MapLibre expressions and GeoJSON properties. */
    val hexColor: String get() = String.format(Locale.US, "#%06X", color and 0xFFFFFF)

    companion object {
        /** Icon keys understood by the UI icon catalogue. */
        const val ICON_PLACE = "place"
        const val ICON_WATER = "water"
        const val ICON_FOOD = "food"
        const val ICON_CAMP = "camp"
        const val ICON_HAZARD = "hazard"
        const val ICON_PARKING = "parking"
        const val ICON_VIEW = "view"

        fun defaults(): List<WaypointCategoryStyle> = listOf(
            WaypointCategoryStyle(WaypointCategory.GENERAL.key, "عمومی", 0xFF3ED598.toInt(), ICON_PLACE),
            WaypointCategoryStyle(WaypointCategory.WATER.key, "آب", 0xFF5FB8FF.toInt(), ICON_WATER),
            WaypointCategoryStyle(WaypointCategory.FOOD.key, "غذا", 0xFFFFC15E.toInt(), ICON_FOOD),
            WaypointCategoryStyle(WaypointCategory.CAMP.key, "اردوگاه", 0xFFB98CFF.toInt(), ICON_CAMP),
            WaypointCategoryStyle(WaypointCategory.HAZARD.key, "خطر", 0xFFFF6B6B.toInt(), ICON_HAZARD),
            WaypointCategoryStyle(WaypointCategory.PARKING.key, "پارکینگ", 0xFF7C8CFF.toInt(), ICON_PARKING),
            WaypointCategoryStyle(WaypointCategory.VIEW.key, "چشم‌انداز", 0xFFFF9F45.toInt(), ICON_VIEW),
        )

        fun defaultFor(category: WaypointCategory): WaypointCategoryStyle =
            defaults().first { it.key == category.key }

        /** Built-in categories are re-seeded on every launch, so deleting them cannot stick. */
        fun isBuiltIn(key: String): Boolean = WaypointCategory.entries.any { it.key == key }

        /** A brand new user category with a stable, collision-free key. */
        fun custom(label: String, color: Int, icon: String): WaypointCategoryStyle =
            WaypointCategoryStyle(
                key = "custom-" + java.util.UUID.randomUUID().toString().take(8),
                label = label.trim().ifBlank { "دستهٔ جدید" },
                color = color,
                icon = icon,
            )
    }
}
