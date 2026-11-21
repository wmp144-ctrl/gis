package ir.atlasgis.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cabin
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import ir.atlasgis.app.data.WaypointCategoryStyle

/** Category styling supplied by the ViewModel; defaults keep previews/tests working. */
val LocalWaypointCategoryStyles = staticCompositionLocalOf { WaypointCategoryStyle.defaults() }

/** Icons a user can assign to a waypoint category. */
val CategoryIconCatalog: List<Pair<String, ImageVector>> = listOf(
    "place" to Icons.Rounded.Place,
    "water" to Icons.Rounded.WaterDrop,
    "food" to Icons.Rounded.Restaurant,
    "camp" to Icons.Rounded.Cabin,
    "hazard" to Icons.Rounded.Warning,
    "parking" to Icons.Rounded.LocalParking,
    "view" to Icons.Rounded.Landscape,
    "flag" to Icons.Rounded.Flag,
    "star" to Icons.Rounded.Star,
    "home" to Icons.Rounded.Home,
    "tree" to Icons.Rounded.Forest,
    "fuel" to Icons.Rounded.LocalGasStation,
    "hospital" to Icons.Rounded.LocalHospital,
    "cafe" to Icons.Rounded.LocalCafe,
    "photo" to Icons.Rounded.PhotoCamera,
    "shopping" to Icons.Rounded.ShoppingBag,
    "school" to Icons.Rounded.School,
    "info" to Icons.Rounded.Info,
)

fun categoryIconFor(iconKey: String): ImageVector =
    CategoryIconCatalog.firstOrNull { it.first == iconKey }?.second ?: Icons.Rounded.Place

/** Looks up a category by key; unknown keys (e.g. a deleted category) degrade gracefully. */
@Composable
fun categoryStyle(categoryKey: String): WaypointCategoryStyle =
    LocalWaypointCategoryStyles.current.firstOrNull { it.key == categoryKey }
        ?: WaypointCategoryStyle(
            key = categoryKey,
            label = "عمومی",
            color = 0xFF3ED598.toInt(),
            icon = WaypointCategoryStyle.ICON_PLACE,
        )

@Composable
fun categoryLabel(categoryKey: String): String = categoryStyle(categoryKey).label

@Composable
fun categoryIcon(categoryKey: String): ImageVector = categoryIconFor(categoryStyle(categoryKey).icon)

@Composable
fun categoryColor(categoryKey: String): Color = Color(categoryStyle(categoryKey).color)
