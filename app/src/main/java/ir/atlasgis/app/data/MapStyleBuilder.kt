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
    const val SRC_SEARCH = "atlas-search"

    const val LAYER_MEASURE_FILL = "atlas-measure-fill"
    const val LAYER_MEASURE_LINE = "atlas-measure-line"
    const val LAYER_MEASURE_VERTEX = "atlas-measure-vertex"
    const val LAYER_WAYPOINT_HALO = "atlas-waypoint-halo"
    const val LAYER_WAYPOINT = "atlas-waypoint"
    const val LAYER_WAYPOINT_SELECTED = "atlas-waypoint-selected"
    const val LAYER_TRACK_LINE = "atlas-track-line"
    const val LAYER_ACTIVE_TRACK = "atlas-active-track-line"
    const val LAYER_GPS_ACCURACY = "atlas-gps-accuracy"
    const val LAYER_GPS_HALO = "atlas-gps-halo"
    const val LAYER_GPS_RING = "atlas-gps-ring"
    const val LAYER_GPS_DOT = "atlas-gps-dot"
    const val LAYER_GPS_CORE = "atlas-gps-core"
    const val LAYER_SEARCH_HALO = "atlas-search-halo"
    const val LAYER_SEARCH_RING = "atlas-search-ring"
    const val LAYER_SEARCH_DOT = "atlas-search-dot"
    const val LAYER_BACKGROUND = "atlas-background"

    private const val ACCENT = "#3ED598"
    private const val MEASURE_COLOR = "#7C8CFF"
    private const val SEARCH_ACCENT = "#FFC15E"

    /**
     * Offline text: no glyph server, no network. MapLibre Native rasterises the
     * bundled TrueType faces with FreeType and shapes them with HarfBuzz, which is
     * what makes Persian street names join correctly. Latin and Arabic faces are
     * split by Unicode range so the same `text-font` name works for both scripts.
     */
    private fun fontFaces(): JSONObject {
        val latinRanges = JSONArray()
            .put("U+0000-024F")
            .put("U+1E00-1EFF")
            .put("U+2000-206F")
            .put("U+20A0-20CF")
            .put("U+2100-214F")
            .put("U+2C60-2C7F")
            .put("U+A720-A7FF")
        val arabicRanges = JSONArray()
            .put("U+0600-06FF")
            .put("U+0750-077F")
            .put("U+08A0-08FF")
            .put("U+FB50-FDFF")
            .put("U+FE70-FEFF")

        fun face(regular: String, bold: String): JSONArray = JSONArray()
            .put(JSONObject().put("url", regular).put("unicode-range", latinRanges))
            .put(JSONObject().put("url", bold).put("unicode-range", arabicRanges))

        return JSONObject()
            .put(
                MapLabelLayers.FONT_REGULAR,
                face(
                    "asset://fonts/NotoSans-Regular.ttf",
                    "asset://fonts/NotoSansArabic-Regular.ttf",
                ),
            )
            .put(
                MapLabelLayers.FONT_BOLD,
                face(
                    "asset://fonts/NotoSans-Bold.ttf",
                    "asset://fonts/NotoSansArabic-Bold.ttf",
                ),
            )
    }

    private fun emptyCollection(): JSONObject = JSONObject()
        .put("type", "FeatureCollection")
        .put("features", JSONArray())

    fun build(layers: List<MapLayer>): String {
        val root = JSONObject()
        root.put("version", 8)
        root.put("name", "AtlasGIS Offline")
        root.put("font-faces", fontFaces())

        val sources = JSONObject()
        val styleLayers = JSONArray()

        // The topmost visible vector base decides the backdrop, so the colour behind
        // the tiles matches the palette instead of a hard-coded night blue.
        val preset = layers.filter { it.visible && it.format == TileFormat.VECTOR }
            .maxByOrNull { it.sortOrder }
            ?.stylePreset
            ?: MapStylePreset.DEFAULT

        styleLayers.put(
            JSONObject()
                .put("id", LAYER_BACKGROUND)
                .put("type", "background")
                .put("paint", JSONObject().put("background-color", preset.palette.background)),
        )

        val ordered = layers.filter { it.visible }.sortedBy { it.sortOrder }
        ordered.forEach { layer ->
            addBaseLayer(root, sources, styleLayers, layer)
        }

        // Overlays, bottom to top.
        sources.put(SRC_MEASURE, geojsonSource())
        sources.put(SRC_TRACKS, geojsonSource())
        sources.put(SRC_ACTIVE_TRACK, geojsonSource())
        sources.put(SRC_WAYPOINTS, geojsonSource())
        sources.put(SRC_SEARCH, geojsonSource())
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
        // Search focus: a warm pin that reads on every preset.
        styleLayers.put(
            JSONObject()
                .put("id", LAYER_SEARCH_HALO)
                .put("type", "circle")
                .put("source", SRC_SEARCH)
                .put(
                    "paint",
                    JSONObject()
                        .put("circle-radius", 26)
                        .put("circle-color", SEARCH_ACCENT)
                        .put("circle-opacity", 0.18)
                        .put("circle-blur", 0.8),
                ),
        )
        styleLayers.put(
            JSONObject()
                .put("id", LAYER_SEARCH_RING)
                .put("type", "circle")
                .put("source", SRC_SEARCH)
                .put(
                    "paint",
                    JSONObject()
                        .put("circle-radius", 11)
                        .put("circle-color", SEARCH_ACCENT)
                        .put("circle-stroke-width", 2.4)
                        .put("circle-stroke-color", "#0B1220"),
                ),
        )
        styleLayers.put(
            JSONObject()
                .put("id", LAYER_SEARCH_DOT)
                .put("type", "circle")
                .put("source", SRC_SEARCH)
                .put(
                    "paint",
                    JSONObject()
                        .put("circle-radius", 4.2)
                        .put("circle-color", "#FFFFFF"),
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
        // Location puck: soft halo, a crisp contrasting ring and a small core. It
        // reads on both dark and light map palettes and replaces the plain circle.
        val markerRing = if (preset.isLight) "#22303F" else "#F7FBFF"
        styleLayers.put(
            JSONObject()
                .put("id", LAYER_GPS_HALO)
                .put("type", "circle")
                .put("source", SRC_GPS)
                .put(
                    "paint",
                    JSONObject()
                        .put("circle-radius", 22)
                        .put("circle-color", ACCENT)
                        .put("circle-opacity", 0.14)
                        .put("circle-blur", 0.9)
                        .put("circle-pitch-alignment", "map"),
                ),
        )
        styleLayers.put(
            JSONObject()
                .put("id", LAYER_GPS_RING)
                .put("type", "circle")
                .put("source", SRC_GPS)
                .put(
                    "paint",
                    JSONObject()
                        .put("circle-radius", 10.5)
                        .put("circle-color", markerRing)
                        .put("circle-opacity", 1.0)
                        .put("circle-pitch-alignment", "map"),
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
                        .put("circle-pitch-alignment", "map"),
                ),
        )
        styleLayers.put(
            JSONObject()
                .put("id", LAYER_GPS_CORE)
                .put("type", "circle")
                .put("source", SRC_GPS)
                .put(
                    "paint",
                    JSONObject()
                        .put("circle-radius", 3.2)
                        .put("circle-color", markerRing)
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

    private fun addBaseLayer(
        root: JSONObject,
        sources: JSONObject,
        layers: JSONArray,
        layer: MapLayer,
    ) {
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
            val template = layer.stylePath
                ?.let { path -> runCatching { java.io.File(path).readText() }.getOrNull() }
            val applied = template != null && CustomStyleTemplate.apply(
                root, sources, layers, layer, sourceId, template,
            )
            if (!applied) {
                val specs = VectorStyleHeuristics.layersFor(
                    sourceId, layer.vectorLayers, layer.opacity, layer.stylePreset,
                )
                for (i in 0 until specs.length()) {
                    val spec = specs.optJSONObject(i) ?: continue
                    spec.put("id", "atlas-vec-${layer.id}-$i")
                    layers.put(spec)
                }
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
