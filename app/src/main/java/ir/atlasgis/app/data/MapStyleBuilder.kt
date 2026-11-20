package ir.atlasgis.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the single MapLibre style used by the app. All overlay sources exist from
 * the first frame (empty FeatureCollections), so updating geometry later only calls
 * `setGeoJson` and never reloads the style — that is what keeps pan/zoom smooth.
 */
object MapStyleBuilder {

    const val SRC_WAYPOINTS = "atlas-waypoints"
    const val SRC_MEASURE = "atlas-measure"
    const val SRC_TRACKS = "atlas-tracks"
    const val SRC_ACTIVE_TRACK = "atlas-active-track"
    const val SRC_GPS = "atlas-gps"
    const val SRC_GPS_ACCURACY = "atlas-gps-accuracy"

    const val LAYER_MEASURE_FILL = "atlas-measure-fill"
    const val LAYER_MEASURE_LINE = "atlas-measure-line"
    const val LAYER_MEASURE_VERTEX = "atlas-measure-vertex"
    const val LAYER_WAYPOINT_HALO = "atlas-waypoint-halo"
    const val LAYER_WAYPOINT = "atlas-waypoint"
    const val LAYER_WAYPOINT_SELECTED = "atlas-waypoint-selected"
    const val LAYER_TRACK_LINE = "atlas-track-line"
    const val LAYER_ACTIVE_TRACK = "atlas-active-track-line"
    const val LAYER_GPS_ACCURACY = "atlas-gps-accuracy"
    const val LAYER_GPS_DOT = "atlas-gps-dot"
    const val LAYER_BACKGROUND = "atlas-background"

    private const val ACCENT = "#3ED598"
    private const val MEASURE_COLOR = "#7C8CFF"

    private fun emptyCollection(): JSONObject = JSONObject()
        .put("type", "FeatureCollection")
        .put("features", JSONArray())

    fun build(layers: List<MapLayer>): String {
        val root = JSONObject()
        root.put("version", 8)
        root.put("name", "AtlasGIS Offline")

        val sources = JSONObject()
        val styleLayers = JSONArray()

        styleLayers.put(
            JSONObject()
                .put("id", LAYER_BACKGROUND)
                .put("type", "background")
                .put("paint", JSONObject().put("background-color", "#0B1220")),
        )

        val ordered = layers.filter { it.visible }.sortedBy { it.sortOrder }
        ordered.forEach { layer ->
            addBaseLayer(sources, styleLayers, layer)
        }

        // Overlays, bottom to top.
        sources.put(SRC_MEASURE, geojsonSource())
        sources.put(SRC_TRACKS, geojsonSource())
        sources.put(SRC_ACTIVE_TRACK, geojsonSource())
        sources.put(SRC_WAYPOINTS, geojsonSource())
        sources.put(SRC_GPS_ACCURACY, geojsonSource())
        sources.put(SRC_GPS, geojsonSource())

        styleLayers.put(
            JSONObject()
                .put("id", LAYER_MEASURE_FILL)
                .put("type", "fill")
                .put("source", SRC_MEASURE)
                .put(
                    "filter",
                    JSONArray().put("==").put(JSONArray().put("geometry-type")).put("Polygon"),
                )
                .put(
                    "paint",
                    JSONObject()
                        .put("fill-color", MEASURE_COLOR)
                        .put("fill-opacity", 0.18),
                ),
        )
        styleLayers.put(
            JSONObject()
                .put("id", LAYER_TRACK_LINE)
                .put("type", "line")
                .put("source", SRC_TRACKS)
                .put(
                    "layout",
                    JSONObject()
                        .put("line-cap", "round")
                        .put("line-join", "round"),
                )
                .put(
                    "paint",
                    JSONObject()
                        .put("line-color", expression("color", "#FF9F45"))
                        .put("line-width", zoomInterpolate(8 to 1.5, 14 to 3.5, 20 to 6.0))
                        .put("line-opacity", 0.95),
                ),
        )
        styleLayers.put(
            JSONObject()
                .put("id", LAYER_ACTIVE_TRACK)
                .put("type", "line")
                .put("source", SRC_ACTIVE_TRACK)
                .put(
                    "layout",
                    JSONObject()
                        .put("line-cap", "round")
                        .put("line-join", "round"),
                )
                .put(
                    "paint",
                    JSONObject()
                        .put("line-color", "#FF9F45")
                        .put("line-width", zoomInterpolate(8 to 2.5, 14 to 5.0, 20 to 8.0)),
                ),
        )
        styleLayers.put(
            JSONObject()
                .put("id", LAYER_MEASURE_LINE)
                .put("type", "line")
                .put("source", SRC_MEASURE)
                .put(
                    "layout",
                    JSONObject()
                        .put("line-cap", "round")
                        .put("line-join", "round"),
                )
                .put(
                    "paint",
                    JSONObject()
                        .put("line-color", MEASURE_COLOR)
                        .put("line-width", 2.6)
                        .put("line-dasharray", JSONArray().put(2).put(1.2)),
                ),
        )
        styleLayers.put(
            JSONObject()
                .put("id", LAYER_MEASURE_VERTEX)
                .put("type", "circle")
                .put("source", SRC_MEASURE)
                .put("filter", lineOnlyFilter())
                .put(
                    "paint",
                    JSONObject()
                        .put("circle-radius", 5.5)
                        .put("circle-color", "#0B1220")
                        .put("circle-stroke-width", 2.2)
                        .put("circle-stroke-color", MEASURE_COLOR),
                ),
        )
        styleLayers.put(
            JSONObject()
                .put("id", LAYER_WAYPOINT_HALO)
                .put("type", "circle")
                .put("source", SRC_WAYPOINTS)
                .put(
                    "paint",
                    JSONObject()
                        .put("circle-radius", 12)
                        .put("circle-color", expression("color", ACCENT))
                        .put("circle-opacity", 0.16),
                ),
        )
        styleLayers.put(
            JSONObject()
                .put("id", LAYER_WAYPOINT)
                .put("type", "circle")
                .put("source", SRC_WAYPOINTS)
                .put(
                    "paint",
                    JSONObject()
                        .put("circle-radius", 6.5)
                        .put("circle-color", expression("color", ACCENT))
                        .put("circle-stroke-width", 1.6)
                        .put("circle-stroke-color", "#F4FAFF"),
                ),
        )
        styleLayers.put(
            JSONObject()
                .put("id", LAYER_WAYPOINT_SELECTED)
                .put("type", "circle")
                .put("source", SRC_WAYPOINTS)
                .put(
                    "filter",
                    JSONArray().put("==").put(JSONArray().put("get").put("selected")).put(true),
                )
                .put(
                    "paint",
                    JSONObject()
                        .put("circle-radius", 17)
                        .put("circle-color", "#000000")
                        .put("circle-opacity", 0)
                        .put("circle-stroke-width", 2)
                        .put("circle-stroke-color", ACCENT),
                ),
        )
        styleLayers.put(
            JSONObject()
                .put("id", LAYER_GPS_ACCURACY)
                .put("type", "fill")
                .put("source", SRC_GPS_ACCURACY)
                .put(
                    "paint",
                    JSONObject()
                        .put("fill-color", ACCENT)
                        .put("fill-opacity", 0.14),
                ),
        )
        styleLayers.put(
            JSONObject()
                .put("id", LAYER_GPS_DOT)
                .put("type", "circle")
                .put("source", SRC_GPS)
                .put(
                    "paint",
                    JSONObject()
                        .put("circle-radius", 8)
                        .put("circle-color", ACCENT)
                        .put("circle-stroke-width", 2.5)
                        .put("circle-stroke-color", "#0B1220")
                        .put("circle-pitch-alignment", "map"),
                ),
        )

        root.put("sources", sources)
        root.put("layers", styleLayers)
        return root.toString()
    }

    private fun geojsonSource(): JSONObject = JSONObject()
        .put("type", "geojson")
        .put("data", emptyCollection())

    /** Data-driven colour from a feature property, with a fallback. */
    private fun expression(property: String, fallback: String): JSONArray = JSONArray()
        .put("coalesce")
        .put(JSONArray().put("get").put(property))
        .put(fallback)

    private fun lineOnlyFilter(): JSONArray = JSONArray()
        .put("==")
        .put(JSONArray().put("geometry-type"))
        .put("Point")

    /** `["interpolate", ["linear"], ["zoom"], z, value, ...]` */
    internal fun zoomInterpolate(vararg stops: Pair<Number, Number>): JSONArray {
        val array = JSONArray()
            .put("interpolate")
            .put(JSONArray().put("linear"))
            .put(JSONArray().put("zoom"))
        stops.forEach { (zoom, value) ->
            array.put(zoom.toDouble()).put(value.toDouble())
        }
        return array
    }

    private fun addBaseLayer(sources: JSONObject, layers: JSONArray, layer: MapLayer) {
        if (!MapFiles.exists(layer.filePath)) return
        val sourceId = "atlas-src-${layer.id}"
        val uri = "mbtiles://" + layer.filePath

        if (layer.format == TileFormat.RASTER) {
            sources.put(
                sourceId,
                JSONObject()
                    .put("type", "raster")
                    .put("url", uri)
                    .put("tileSize", layer.tileSize)
                    .put("minzoom", layer.minZoom)
                    .put("maxzoom", layer.maxZoom)
                    .applyBounds(layer),
            )
            layers.put(
                JSONObject()
                    .put("id", "atlas-raster-${layer.id}")
                    .put("type", "raster")
                    .put("source", sourceId)
                    .put(
                        "paint",
                        JSONObject()
                            .put("raster-opacity", layer.opacity.toDouble())
                            .put("raster-fade-duration", 60)
                            .put("raster-resampling", "linear"),
                    ),
            )
        } else {
            sources.put(
                sourceId,
                JSONObject()
                    .put("type", "vector")
                    .put("url", uri)
                    .put("minzoom", layer.minZoom)
                    .put("maxzoom", layer.maxZoom)
                    .applyBounds(layer),
            )
            val specs = VectorStyleHeuristics.layersFor(sourceId, layer.vectorLayers, layer.opacity)
            for (i in 0 until specs.length()) {
                val spec = specs.optJSONObject(i) ?: continue
                spec.put("id", "atlas-vec-${layer.id}-$i")
                layers.put(spec)
            }
        }
    }

    private fun JSONObject.applyBounds(layer: MapLayer): JSONObject {
        val bounds = layer.bounds ?: return this
        return put(
            "bounds",
            JSONArray()
                .put(bounds.west)
                .put(bounds.south)
                .put(bounds.east)
                .put(bounds.north),
        )
    }
}

internal object MapFiles {
    fun exists(path: String): Boolean = runCatching { java.io.File(path).exists() }.getOrDefault(false)
}
