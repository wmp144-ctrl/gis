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
        for (name in names) {
            when (classify(name)) {
                Kind.WATER -> result.put(
                    fill(sourceId, name, waterColor(name, palette), opacity, outline = palette.waterDeep),
                )
                Kind.WATERWAY -> result.put(
                    line(
                        sourceId, name, palette.waterway, opacity,
                        width = zoomInterpolate(6 to 0.5, 12 to 2.0, 16 to 3.6, 20 to 7.5),
                    ),
                )
                Kind.GREEN -> result.put(
                    fill(sourceId, name, greenColor(name, palette), opacity * 0.92f),
                )
                Kind.BUILDING -> result.put(
                    fill(
                        sourceId, name, palette.building, opacity * 0.95f,
                        minZoom = 13, outline = palette.buildingOutline,
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
            }
        }
        if (result.length() == 0) {
            // Unknown schema: render every layer as a neutral area plus outline so the
            // archive is still explorable.
            for (name in names) {
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

    internal fun classify(rawName: String): Kind {
        val name = rawName.lowercase()
        return when {
            name.contains("building") -> Kind.BUILDING
            name.contains("waterway") || name.contains("river") || name.contains("stream") ||
                name.contains("canal") -> Kind.WATERWAY
            name.contains("water") || name.contains("ocean") || name.contains("sea") ||
                name.contains("lake") -> Kind.WATER
            name.contains("rail") || name.contains("transit") -> Kind.RAIL
            name.contains("boundary") || name.contains("admin") -> Kind.BOUNDARY
            name.contains("transport") || name.contains("road") || name.contains("street") ||
                name.contains("highway") || name.contains("aeroway") || name.contains("runway") ||
                name.contains("pier") || name.contains("bridge") -> Kind.ROAD
            name.contains("landcover") || name.contains("landuse") || name.contains("park") ||
                name.contains("grass") || name.contains("wood") || name.contains("forest") ||
                name.contains("glacier") || name.contains("vegetation") ||
                name == "land" -> Kind.GREEN
            name.contains("place") || name.contains("poi") || name.contains("peak") ||
                name.contains("label") || name.contains("city") || name.contains("town") ||
                name.contains("locality") -> Kind.POI
            else -> Kind.NONE
        }
    }

    private fun waterColor(name: String, palette: MapPalette): String =
        if (name.contains("ocean") || name.contains("sea")) palette.waterDeep else palette.water

    private fun greenColor(name: String, palette: MapPalette): String = when {
        name.contains("wood") || name.contains("forest") -> palette.forest
        name.contains("park") || name.contains("grass") -> palette.park
        name.contains("glacier") || name.contains("ice") -> palette.ice
        else -> palette.green
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
