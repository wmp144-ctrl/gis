package ir.atlasgis.app.data

import ir.atlasgis.app.data.MapStyleBuilder.zoomInterpolate
import org.json.JSONArray
import org.json.JSONObject

/**
 * Vector MBTiles carry no symbology, only layer names. These rules cover the common
 * schemas (OpenMapTiles, Mapbox Streets, Shortbread) so an imported archive renders
 * as a readable map without shipping a hand-written style for every provider.
 *
 * Every colour comes from a [MapStylePreset], so the same archive can be painted as
 * a night, daylight, outdoor or high-contrast map.
 */
internal object VectorStyleHeuristics {

    fun layersFor(
        sourceId: String,
        sourceLayers: List<String>,
        opacity: Float,
        preset: MapStylePreset = MapStylePreset.DEFAULT,
    ): JSONArray {
        val palette = preset.palette
        val result = JSONArray()
        val names = sourceLayers.ifEmpty { listOf("water", "landuse", "transportation", "building", "place") }
        // Naming layers (transportation_name, water_name, street_labels, …) carry the
        // same lines/points again just for their labels. Painting them would double
        // every road and turn a line-only water_name into a stray fill, so only the
        // geometry layers are painted here; the labels are added at the end.
        val paintable = names.filter { !isNamingLayer(it) }

        // Fill pass first: water, land cover and buildings. Building footprints must
        // never end up above the roads, whatever order the archive lists its layers in.
        for (name in paintable) {
            when (classify(name)) {
                Kind.WATER -> result.put(
                    fill(sourceId, name, waterColor(name, palette), opacity, outline = palette.waterDeep),
                )
                Kind.GREEN -> result.put(greenFill(sourceId, name, opacity, palette))
                Kind.BUILDING -> result.put(
                    fill(
                        sourceId, name, palette.building, opacity * 0.95f,
                        minZoom = 13, outline = palette.buildingOutline,
                    ),
                )
                else -> Unit
            }
        }

        // Line pass: waterways, road casings and fills, rail and boundaries.
        for (name in paintable) {
            when (classify(name)) {
                Kind.WATERWAY -> result.put(
                    line(
                        sourceId, name, palette.waterway, opacity,
                        width = zoomInterpolate(6 to 0.5, 12 to 2.0, 16 to 3.6, 20 to 7.5),
                    ),
                )
                Kind.ROAD -> {
                    result.put(
                        JSONObject()
                            .put("type", "line")
                            .put("source", sourceId)
                            .put("source-layer", name)
                            .put("minzoom", 5)
                            .put(
                                "layout",
                                JSONObject().put("line-cap", "round").put("line-join", "round"),
                            )
                            .put(
                                "paint",
                                JSONObject()
                                    .put("line-color", palette.roadCasing)
                                    .put("line-opacity", (opacity * 0.9f).toDouble())
                                    .put(
                                        "line-width",
                                        zoomInterpolate(8 to 2.2, 12 to 5.0, 15 to 12.0, 18 to 26.0),
                                    ),
                            ),
                    )
                    result.put(majorRoadLayer(sourceId, name, opacity, palette))
                }
                Kind.RAIL -> result.put(
                    line(
                        sourceId, name, palette.rail, opacity,
                        width = zoomInterpolate(10 to 0.6, 14 to 1.6, 18 to 3.0),
                        dash = listOf(3, 2),
                    ),
                )
                Kind.BOUNDARY -> result.put(
                    line(
                        sourceId, name, palette.boundary, opacity * 0.8f,
                        width = 1.1,
                        dash = listOf(4, 2),
                    ),
                )
                // Places and POIs are drawn once, as icons with names, by
                // [MapLabelLayers] below; a second dot layer would only add noise.
                Kind.POI -> Unit
                Kind.NONE -> Unit
                else -> Unit
            }
        }
        if (result.length() == 0) {
            // Unknown schema: render every unrecognised layer as a neutral area plus
            // outline so the archive is still explorable.
            for (name in paintable.filter { classify(it) == Kind.NONE }) {
                result.put(fill(sourceId, name, palette.unknownLand, opacity * 0.9f))
                result.put(
                    line(
                        sourceId, name, palette.unknownOutline, opacity * 0.8f,
                        width = zoomInterpolate(8 to 0.4, 14 to 1.0),
                    ),
                )
            }
        }
        // Street names, settlement names and every POI icon go on top of the geometry.
        MapLabelLayers.append(result, sourceId, sourceLayers, opacity, palette)
        return result
    }

    internal enum class Kind { WATER, WATERWAY, GREEN, BUILDING, ROAD, RAIL, BOUNDARY, POI, NONE }

    /** Layers that exist only to place text; see [MapLabelLayers]. */
    private val namingLayers = setOf(
        "transportation_name", "transportation_names", "transportation_label",
        "street_labels", "street_label", "street_labels_points", "street_name", "streets_name",
        "roads_name", "roads_labels", "road_label", "road_labels", "highway_name",
        "highway_labels", "water_name", "water_names", "water_label", "water_labels",
        "waterway_name", "sea_label", "seas", "aerodrome_label", "airport_label", "airports",
        "mountain_peak", "mountain_peaks", "peak", "peaks", "place", "places",
        "place_label", "place_labels", "locality", "localities", "poi", "pois",
        "poi_label", "poi_labels",
    )

    internal fun isNamingLayer(rawName: String): Boolean {
        val name = rawName.lowercase()
        return name in namingLayers || name.endsWith("_name") || name.endsWith("_names") ||
            name.endsWith("_label") || name.endsWith("_labels") || name.contains("label")
    }

    internal fun classify(rawName: String): Kind {
        val name = rawName.lowercase()
        val tokens = name.split('_', '-', ' ').filter { it.isNotBlank() }
        return when {
            name.contains("building") -> Kind.BUILDING
            name.contains("waterway") || name.contains("river") || name.contains("stream") ||
                name.contains("canal") -> Kind.WATERWAY
            name.contains("water") || name.contains("ocean") ||
                tokens.any { it in setOf("sea", "seas", "lake", "lakes", "pond", "reservoir") } -> Kind.WATER
            name.contains("rail") || name.contains("transit") -> Kind.RAIL
            name.contains("boundary") || name.contains("admin") -> Kind.BOUNDARY
            name.contains("transport") || name.contains("road") || name.contains("street") ||
                name.contains("highway") || name.contains("aeroway") || name.contains("runway") ||
                name.contains("pier") || name.contains("bridge") -> Kind.ROAD
            // A `parking` layer is not a park; match the token, not the substring.
            tokens.any {
                it in setOf(
                    "park", "parks", "grass", "grassland", "wood", "forest", "glacier",
                    "vegetation", "landcover", "landuse", "meadow", "scrub", "orchard",
                    "cemetery", "garden", "golf", "pitch", "playground", "recreation",
                )
            } || name == "land" -> Kind.GREEN
            name.contains("place") || name.contains("poi") || name.contains("peak") ||
                name.contains("label") || name.contains("city") || name.contains("town") ||
                name.contains("locality") -> Kind.POI
            else -> Kind.NONE
        }
    }

    private fun waterColor(name: String, palette: MapPalette): String =
        if (name.contains("ocean") ||
            name.split('_', '-').any { it == "sea" || it == "seas" }
        ) {
            palette.waterDeep
        } else {
            palette.water
        }

    /**
     * Land cover uses one colour on purpose, but `landuse` also contains dense urban
     * classes: painting residential, commercial and industrial blocks green made a
     * whole city look like a park. The match keeps greens green and renders the built
     * classes as a neutral ground colour.
     */
    private fun greenFill(
        sourceId: String,
        sourceLayer: String,
        opacity: Float,
        palette: MapPalette,
    ): JSONObject {
        val layer = fill(sourceId, sourceLayer, palette.green, opacity * 0.92f)
        val klass = JSONArray().put("downcase").put(
            JSONArray().put("to-string").put(
                JSONArray().put("coalesce")
                    .put(JSONArray().put("get").put("class"))
                    .put(JSONArray().put("get").put("subclass"))
                    .put(JSONArray().put("get").put("kind"))
                    .put(""),
            ),
        )
        val colors = JSONArray().put("match").put(klass)
        listOf("wood", "forest", "tree", "vegetation", "scrub", "heath", "orchard")
            .forEach { colors.put(it).put(palette.forest) }
        listOf(
            "park", "grass", "garden", "recreation_ground", "pitch", "playground",
            "golf_course", "meadow", "village_green", "nature_reserve", "national_park",
            "greenfield", "cemetery",
        ).forEach { colors.put(it).put(palette.park) }
        listOf("glacier", "ice").forEach { colors.put(it).put(palette.ice) }
        listOf(
            "residential", "commercial", "retail", "industrial", "railway", "hospital",
            "school", "university", "military", "quarry", "landfill", "construction",
        ).forEach { colors.put(it).put(palette.unknownLand) }
        colors.put(palette.green)
        layer.getJSONObject("paint").put("fill-color", colors)
        return layer
    }


    private fun fill(
        sourceId: String,
        sourceLayer: String,
        color: String,
        opacity: Float,
        minZoom: Int? = null,
        outline: String? = null,
    ): JSONObject {
        val paint = fillPaint(color, opacity)
        if (outline != null) paint.put("fill-outline-color", outline)
        val layer = JSONObject()
            .put("type", "fill")
            .put("source", sourceId)
            .put("source-layer", sourceLayer)
            .put("paint", paint)
        if (minZoom != null) layer.put("minzoom", minZoom)
        return layer
    }

    private fun fillPaint(color: String, opacity: Float): JSONObject = JSONObject()
        .put("fill-color", color)
        .put("fill-opacity", opacity.coerceIn(0.05f, 1.0f).toDouble())

    private fun line(
        sourceId: String,
        sourceLayer: String,
        color: String,
        opacity: Float,
        width: Any,
        dash: List<Int>? = null,
    ): JSONObject {
        val paint = JSONObject()
            .put("line-color", color)
            .put("line-opacity", opacity.coerceIn(0.05f, 1.0f).toDouble())
            .put("line-width", width)
        if (dash != null) {
            val array = JSONArray()
            dash.forEach { array.put(it) }
            paint.put("line-dasharray", array)
        }
        return JSONObject()
            .put("type", "line")
            .put("source", sourceId)
            .put("source-layer", sourceLayer)
            .put(
                "layout",
                JSONObject().put("line-cap", "round").put("line-join", "round"),
            )
            .put("paint", paint)
    }

    private fun majorRoadLayer(
        sourceId: String,
        sourceLayer: String,
        opacity: Float,
        palette: MapPalette,
    ): JSONObject {
        // `class` (OpenMapTiles / Mapbox) and `kind` (Shortbread) are both common.
        val classColor = JSONArray()
            .put("match")
            .put(
                JSONArray()
                    .put("coalesce")
                    .put(JSONArray().put("get").put("class"))
                    .put(JSONArray().put("get").put("kind"))
                    .put(""),
            )
            .put("motorway").put(palette.roadMotorway)
            .put("trunk").put(palette.roadTrunk)
            .put("primary").put(palette.roadPrimary)
            .put("secondary").put(palette.roadSecondary)
            .put("tertiary").put(palette.roadTertiary)
            .put("minor").put(palette.roadMinor)
            .put("service").put(palette.roadService)
            .put("track").put(palette.roadService)
            .put(palette.roadMinor)

        return JSONObject()
            .put("type", "line")
            .put("source", sourceId)
            .put("source-layer", sourceLayer)
            .put("minzoom", 7)
            .put(
                "layout",
                JSONObject().put("line-cap", "round").put("line-join", "round"),
            )
            .put(
                "paint",
                JSONObject()
                    .put("line-color", classColor)
                    .put("line-opacity", (opacity * 0.98f).toDouble())
                    .put("line-width", zoomInterpolate(8 to 0.8, 12 to 2.4, 15 to 7.0, 18 to 17.0)),
            )
    }
}
