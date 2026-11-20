package ir.atlasgis.app.data

import ir.atlasgis.app.data.MapStyleBuilder.zoomInterpolate
import org.json.JSONArray
import org.json.JSONObject

/**
 * Vector MBTiles carry no symbology, only layer names. These rules cover the common
 * schemas (OpenMapTiles, Mapbox Streets, Shortbread) so an imported archive renders
 * as a readable map without shipping a hand-written style for every provider.
 */
internal object VectorStyleHeuristics {

    private const val ACCENT = "#3ED598"

    fun layersFor(sourceId: String, sourceLayers: List<String>, opacity: Float): JSONArray {
        val result = JSONArray()
        val names = sourceLayers.ifEmpty { listOf("water", "landuse", "transportation", "building", "place") }
        for (name in names) {
            when (classify(name)) {
                Kind.WATER -> result.put(fill(sourceId, name, waterColor(name), opacity))
                Kind.WATERWAY -> result.put(
                    line(
                        sourceId, name, "#2C6E9B", opacity,
                        width = zoomInterpolate(6 to 0.4, 12 to 1.6, 16 to 3.2, 20 to 7.0),
                    ),
                )
                Kind.GREEN -> result.put(fill(sourceId, name, greenColor(name), opacity * 0.92f))
                Kind.BUILDING -> result.put(
                    fill(sourceId, name, "#26313F", opacity * 0.95f, minZoom = 13)
                        .put("paint", fillPaint("#26313F", opacity * 0.95f).put("fill-outline-color", "#33445A")),
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
                                    .put("line-color", "#0A111C")
                                    .put("line-opacity", (opacity * 0.55f).toDouble())
                                    .put(
                                        "line-width",
                                        zoomInterpolate(8 to 1.2, 12 to 3.0, 15 to 9.0, 18 to 22.0),
                                    ),
                            ),
                    )
                    result.put(majorRoadLayer(sourceId, name, opacity))
                }
                Kind.RAIL -> result.put(
                    line(
                        sourceId, name, "#64748B", opacity,
                        width = zoomInterpolate(10 to 0.6, 14 to 1.6, 18 to 3.0),
                    ).put(
                        "paint",
                        JSONObject()
                            .put("line-color", "#64748B")
                            .put("line-opacity", (opacity * 0.9f).toDouble())
                            .put("line-width", zoomInterpolate(10 to 0.6, 14 to 1.6, 18 to 3.0))
                            .put("line-dasharray", JSONArray().put(3).put(2)),
                    ),
                )
                Kind.BOUNDARY -> result.put(
                    JSONObject()
                        .put("type", "line")
                        .put("source", sourceId)
                        .put("source-layer", name)
                        .put(
                            "paint",
                            JSONObject()
                                .put("line-color", "#5E7896")
                                .put("line-opacity", (opacity * 0.8f).toDouble())
                                .put("line-width", 1.1)
                                .put("line-dasharray", JSONArray().put(4).put(2)),
                        ),
                )
                Kind.POI -> result.put(
                    JSONObject()
                        .put("type", "circle")
                        .put("source", sourceId)
                        .put("source-layer", name)
                        .put("minzoom", 9)
                        .put(
                            "paint",
                            JSONObject()
                                .put("circle-radius", zoomInterpolate(9 to 1.6, 14 to 3.2, 18 to 4.5))
                                .put("circle-color", ACCENT)
                                .put("circle-opacity", (opacity * 0.75f).toDouble()),
                        ),
                )
                Kind.NONE -> Unit
            }
        }
        if (result.length() == 0) {
            // Unknown schema: render every layer as a neutral area plus outline so the
            // archive is still explorable.
            for (name in names) {
                result.put(fill(sourceId, name, "#1B2A3B", opacity * 0.9f))
                result.put(
                    line(
                        sourceId, name, "#3A4C63", opacity * 0.8f,
                        width = zoomInterpolate(8 to 0.4, 14 to 1.0),
                    ),
                )
            }
        }
        return result
    }

    private enum class Kind { WATER, WATERWAY, GREEN, BUILDING, ROAD, RAIL, BOUNDARY, POI, NONE }

    private fun classify(rawName: String): Kind {
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

    private fun waterColor(name: String): String =
        if (name.contains("ocean") || name.contains("sea")) "#0F3350" else "#123A57"

    private fun greenColor(name: String): String = when {
        name.contains("wood") || name.contains("forest") -> "#12301F"
        name.contains("park") || name.contains("grass") -> "#1B4A31"
        name.contains("glacier") || name.contains("ice") -> "#2A4356"
        else -> "#163327"
    }

    private fun fill(
        sourceId: String,
        sourceLayer: String,
        color: String,
        opacity: Float,
        minZoom: Int? = null,
    ): JSONObject {
        val layer = JSONObject()
            .put("type", "fill")
            .put("source", sourceId)
            .put("source-layer", sourceLayer)
            .put("paint", fillPaint(color, opacity))
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
        width: JSONArray,
    ): JSONObject = JSONObject()
        .put("type", "line")
        .put("source", sourceId)
        .put("source-layer", sourceLayer)
        .put(
            "layout",
            JSONObject().put("line-cap", "round").put("line-join", "round"),
        )
        .put(
            "paint",
            JSONObject()
                .put("line-color", color)
                .put("line-opacity", opacity.coerceIn(0.05f, 1.0f).toDouble())
                .put("line-width", width),
        )

    private fun majorRoadLayer(sourceId: String, sourceLayer: String, opacity: Float): JSONObject {
        val classColor = JSONArray()
            .put("match")
            .put(
                JSONArray()
                    .put("coalesce")
                    .put(JSONArray().put("get").put("class"))
                    .put(JSONArray().put("get").put("kind"))
                    .put(""),
            )
            .put("motorway").put("#F0B967")
            .put("trunk").put("#E3AE6A")
            .put("primary").put("#C9D6E4")
            .put("secondary").put("#B0C0D2")
            .put("tertiary").put("#9FB0C4")
            .put("minor").put("#8FA2B8")
            .put("service").put("#6F7F93")
            .put("track").put("#6F7F93")
            .put("#8FA2B8")

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
                    .put("line-opacity", (opacity * 0.95f).toDouble())
                    .put("line-width", zoomInterpolate(8 to 0.7, 12 to 2.0, 15 to 6.0, 18 to 15.0)),
            )
    }
}
