package ir.atlasgis.app.data

import ir.atlasgis.app.data.MapStyleBuilder.zoomInterpolate
import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns the naming layers of an offline vector archive into real map labels:
 * street and alley names along the line, city/town/village names, water names and
 * every POI as an icon with its name below it.
 *
 * Two rules keep it fast. First, labels only ever come from layers that pass a
 * name filter, so an archive without names pays almost nothing. Second, the
 * placement priority follows Google Maps: water and street names first, then
 * settlements, then POIs, so important text wins the space.
 */
internal object MapLabelLayers {

    // Names of the bundled glyph sets under assets/glyphs; the style turns them into
    // "asset://glyphs/{fontstack}/{range}.pbf", so they must match the folder names.
    const val FONT_REGULAR = "AtlasRegular"
    const val FONT_BOLD = "AtlasBold"

    private val roadNameLayers = setOf(
        "transportation_name", "transportation_names", "street_labels", "street_label",
        "street_labels_points", "roads_name", "road_labels", "road_label", "roads_labels",
        "street_name", "streets_name", "highway_name", "highway_labels", "transportation_label",
    )
    private val placeLayers = setOf(
        "place", "places", "place_labels", "place_label", "place_label_points", "localities",
    )
    private val poiLayers = setOf("poi", "pois", "poi_label", "poi_labels", "pois_points")
    private val waterNameLayers = setOf(
        "water_name", "water_names", "water_label", "water_labels", "waterway_name",
        "sea_label", "seas",
    )
    private val airportLayers = setOf("aerodrome_label", "airport_label", "airports", "aerodrome")
    private val peakLayers = setOf("mountain_peak", "mountain_peaks", "peak", "peaks")

    private val majorRoads = listOf("motorway", "trunk", "primary")
    private val minorRoads = listOf(
        "secondary", "tertiary", "minor", "residential", "unclassified", "service", "living_street",
        "pedestrian", "track", "path", "footway", "cycleway", "steps", "bridleway", "road",
        "street", "motorway_link", "trunk_link", "primary_link", "secondary_link", "tertiary_link",
    )
    private val urbanPlaces = listOf(
        "village", "suburb", "hamlet", "quarter", "neighbourhood", "neighborhood", "locality",
        "isolated_dwelling", "borough", "city_district", "district", "townland", "farm", "allotments",
    )
    private val cityPlaces = listOf("city", "town", "county", "island", "archipelago")
    private val regionPlaces = listOf("state", "province", "region", "continent", "country")

    fun append(
        result: JSONArray,
        sourceId: String,
        sourceLayers: List<String>,
        opacity: Float,
        palette: MapPalette,
    ) {
        if (sourceLayers.isEmpty()) return
        val names = sourceLayers.map { it.lowercase() }
        val handled = HashSet<String>()

        // 1. Water names (rivers along the line, seas and lakes on the area).
        for (name in sourceLayers) {
            val raw = name.lowercase()
            if (raw !in waterNameLayers && !raw.contains("water_name")) continue
            handled.add(raw)
            result.put(waterLineLabels(sourceId, name, opacity, palette))
            result.put(waterAreaLabels(sourceId, name, opacity, palette))
        }

        // 2. Street names: the dedicated naming layer when the schema has one,
        //    otherwise the road layer itself for archives that name their lines.
        val namedRoads = sourceLayers.filter { it.lowercase() in roadNameLayers }
        if (namedRoads.isNotEmpty()) {
            namedRoads.forEach { name -> handled.add(name.lowercase()) }
            namedRoads.forEach { name ->
                result.put(roadLabels(sourceId, name, opacity, palette, major = true))
                result.put(roadLabels(sourceId, name, opacity, palette, major = false))
            }
        } else {
            sourceLayers.filter { VectorStyleHeuristics.classify(it) == VectorStyleHeuristics.Kind.ROAD }
                .forEach { name ->
                    handled.add(name.lowercase())
                    result.put(roadLabels(sourceId, name, opacity, palette, major = true))
                    result.put(roadLabels(sourceId, name, opacity, palette, major = false))
                }
        }

        // 3. Settlements, from countries down to neighbourhoods.
        for (name in sourceLayers) {
            val raw = name.lowercase()
            if (raw !in placeLayers) continue
            handled.add(raw)
            result.put(placeLabels(sourceId, name, palette, regionPlaces, 3.0, 11.0, 15.0, true, letterSpacing = 0.08, uppercase = true))
            result.put(placeLabels(sourceId, name, palette, cityPlaces, 5.0, 11.5, 17.0, true, letterSpacing = 0.02))
            result.put(placeLabels(sourceId, name, palette, listOf("borough", "county", "suburb"), 9.0, 10.5, 13.5, false))
            result.put(placeLabels(sourceId, name, palette, urbanPlaces, 12.0, 9.5, 12.5, false))
            // Schemas that carry no class at all: still show the point at a usable zoom.
            result.put(placeLabels(sourceId, name, palette, emptyList(), 10.0, 10.5, 13.0, false))
        }

        // 4. Airports and peaks, before the dense POI layer.
        for (name in sourceLayers) {
            val raw = name.lowercase()
            if (raw in airportLayers) {
                handled.add(raw)
                result.put(airportLabels(sourceId, name, opacity, palette))
            }
            if (raw in peakLayers) {
                handled.add(raw)
                result.put(peakLabels(sourceId, name, palette))
            }
        }

        // 5. POIs: icon plus name. Schemas call this layer poi, pois, amenity,
        //    amenities, shops, tourism… so the check looks at the meaning, not at
        //    one exact name.
        for (name in sourceLayers) {
            val raw = name.lowercase()
            if (!isPoiLayer(raw)) continue
            handled.add(raw)
            result.put(poiSymbols(sourceId, name, opacity, palette))
        }

        // 6. Unknown schema: any remaining layer can still hold named points, so
        //    they are drawn as icons with captions instead of disappearing.
        for (name in sourceLayers) {
            val raw = name.lowercase()
            if (raw in handled) continue
            val kind = VectorStyleHeuristics.classify(raw)
            if (kind != VectorStyleHeuristics.Kind.NONE && kind != VectorStyleHeuristics.Kind.POI) continue
            if (raw.contains("label") || raw.contains("name")) continue
            handled.add(raw)
            result.put(poiSymbols(sourceId, name, opacity, palette, requireName = true))
        }

        // 7. Any other naming layer (suburb labels, station labels, …).
        for (name in sourceLayers) {
            val raw = name.lowercase()
            if (raw in handled) continue
            if (!raw.contains("label") && !raw.contains("name")) continue
            result.put(genericLabels(sourceId, name, palette))
        }
    }

    // --------------------------------------------------------------- expressions

    /** Layer names that stand for points of interest in the common schemas. */
    private fun isPoiLayer(raw: String): Boolean =
        raw in poiLayers || raw.contains("poi") || raw.contains("amenit") ||
            raw.contains("shop") || raw.contains("tourism") || raw.contains("leisure") ||
            raw.contains("food") || raw.contains("health") || raw.contains("accommodation")

    private fun get(property: String): JSONArray = JSONArray().put("get").put(property)

    /** Persian first, then any local name, then the English/latin fallback. */
    private fun nameExpression(): JSONArray = JSONArray()
        .put("coalesce")
        .put(get("name:fa"))
        .put(get("name_fa"))
        .put(get("name"))
        .put(get("name:en"))
        .put(get("name_en"))
        .put(get("name:latin"))
        .put(get("name_int"))
        .put("")

    private fun classExpression(): JSONArray = JSONArray()
        .put("to-string")
        .put(
            JSONArray()
                .put("coalesce")
                .put(get("class"))
                .put(get("kind"))
                .put(get("type"))
                .put(get("place"))
                .put(""),
        )

    private fun rankExpression(default: Int = 100): JSONArray = JSONArray()
        .put("to-number")
        .put(JSONArray().put("coalesce").put(get("rank")).put(default))

    private fun hasName(): JSONArray = JSONArray().put("!=").put(nameExpression()).put("")

    private fun inClasses(classes: List<String>): JSONArray {
        val values = JSONArray()
        classes.forEach { values.put(it) }
        return JSONArray()
            .put("in")
            .put(JSONArray().put("downcase").put(classExpression()))
            .put(JSONArray().put("literal").put(values))
    }

    private fun iconExpression(): JSONArray {
        val tags = PoiClassifier.tags
        val kind = JSONArray().put("downcase").put(
            JSONArray()
                .put("to-string")
                .put(
                    JSONArray().put("coalesce").apply {
                        PoiClassifier.kindProperties.forEach { property -> put(get(property)) }
                        put("")
                    },
                ),
        )

        fun match(input: JSONArray): JSONArray {
            val expression = JSONArray().put("match").put(input)
            tags.forEach { (tag, key) -> expression.put(tag).put(PoiClassifier.imageId(key)) }
            return expression
        }

        // Two levels: the fine tag first (subclass / amenity / shop / maki …), then
        // the coarse `class`, and a plain marker for anything unrecognised. Without
        // that last fallback an unknown tag renders no icon, and MapLibre drops the
        // whole symbol — text included.
        val byClass = match(JSONArray().put("downcase").put(classExpression()))
        byClass.put(PoiClassifier.imageId(PoiClassifier.GENERIC))
        val nested = match(kind)
        nested.put(byClass)
        return nested
    }

    // -------------------------------------------------------------------- layers

    private fun roadLabels(
        sourceId: String,
        sourceLayer: String,
        opacity: Float,
        palette: MapPalette,
        major: Boolean,
    ): JSONObject {
        val classes = if (major) majorRoads else minorRoads
        return JSONObject()
            .put("type", "symbol")
            .put("source", sourceId)
            .put("source-layer", sourceLayer)
            .put("minzoom", if (major) 10 else 14)
            .put(
                "filter",
                JSONArray().put("all")
                    .put(hasName())
                    .put(inClasses(classes)),
            )
            .put(
                "layout",
                JSONObject()
                    .put("symbol-placement", "line")
                    .put("symbol-spacing", if (major) 380 else 320)
                    .put("text-field", nameExpression())
                    .put("text-font", JSONArray().put(FONT_REGULAR))
                    .put(
                        "text-size",
                        zoomInterpolate(
                            10 to (if (major) 10.5 else 9.5),
                            14 to (if (major) 12.0 else 10.5),
                            18 to (if (major) 14.0 else 12.0),
                        ),
                    )
                    .put("text-letter-spacing", 0.03)
                    .put("text-padding", 2)
                    .put("text-max-angle", 32)
                    .put("text-rotation-alignment", "map")
                    .put("text-pitch-alignment", "viewport")
                    .put("text-keep-upright", true)
                    .put("symbol-sort-key", rankExpression()),
            )
            .put(
                "paint",
                JSONObject()
                    .put("text-color", palette.roadLabel)
                    .put("text-opacity", opacity.coerceIn(0.4f, 1f).toDouble())
                    .put("text-halo-color", palette.labelHalo)
                    .put("text-halo-width", 1.7)
                    .put("text-halo-blur", 0.3),
            )
    }

    private fun placeLabels(
        sourceId: String,
        sourceLayer: String,
        palette: MapPalette,
        classes: List<String>,
        minZoom: Double,
        minSize: Double,
        maxSize: Double,
        bold: Boolean,
        letterSpacing: Double = 0.0,
        uppercase: Boolean = false,
    ): JSONObject {
        val filter = JSONArray().put("all").put(hasName())
        if (classes.isEmpty()) {
            // No class property in this schema: only take the points no class filter
            // would have claimed, so the other tiers stay exclusive.
            filter.put(
                JSONArray().put("==")
                    .put(JSONArray().put("to-string").put(classExpression()))
                    .put(""),
            )
        } else {
            filter.put(inClasses(classes))
        }
        val layout = JSONObject()
            .put("text-field", nameExpression())
            .put("text-font", JSONArray().put(if (bold) FONT_BOLD else FONT_REGULAR))
            .put(
                "text-size",
                zoomInterpolate(
                    minZoom to minSize,
                    (minZoom + 3) to ((minSize + maxSize) / 2.0),
                    (minZoom + 7) to maxSize,
                ),
            )
            .put("text-max-width", 8)
            .put("text-padding", if (minZoom < 8) 8 else 5)
            .put("text-letter-spacing", letterSpacing)
            .put("text-allow-overlap", false)
            .put("text-optional", true)
            .put("symbol-sort-key", rankExpression())
        if (uppercase) layout.put("text-transform", "uppercase")
        return JSONObject()
            .put("type", "symbol")
            .put("source", sourceId)
            .put("source-layer", sourceLayer)
            .put("minzoom", minZoom)
            .put("filter", filter)
            .put("layout", layout)
            .put(
                "paint",
                JSONObject()
                    .put("text-color", palette.placeText)
                    .put("text-halo-color", palette.labelHalo)
                    .put("text-halo-width", if (bold) 2.0 else 1.7)
                    .put("text-halo-blur", 0.3),
            )
    }

    private fun waterLineLabels(
        sourceId: String,
        sourceLayer: String,
        opacity: Float,
        palette: MapPalette,
    ): JSONObject = JSONObject()
        .put("type", "symbol")
        .put("source", sourceId)
        .put("source-layer", sourceLayer)
        .put("minzoom", 12)
        .put(
            "filter",
            JSONArray().put("all")
                .put(hasName())
                .put(inClasses(listOf("river", "stream", "canal", "waterway", "ditch", "drain"))),
        )
        .put(
            "layout",
            JSONObject()
                .put("symbol-placement", "line")
                .put("symbol-spacing", 420)
                .put("text-field", nameExpression())
                .put("text-font", JSONArray().put(FONT_REGULAR))
                .put("text-size", zoomInterpolate(12 to 10.5, 16 to 12.5, 19 to 14.0))
                .put("text-letter-spacing", 0.04)
                .put("text-max-angle", 30)
                .put("text-rotation-alignment", "map")
                .put("text-pitch-alignment", "viewport")
                .put("text-keep-upright", true),
        )
        .put(
            "paint",
            JSONObject()
                .put("text-color", palette.waterLabel)
                .put("text-opacity", opacity.coerceIn(0.4f, 1f).toDouble())
                .put("text-halo-color", palette.labelHalo)
                .put("text-halo-width", 1.4)
                .put("text-halo-blur", 0.3),
        )

    private fun waterAreaLabels(
        sourceId: String,
        sourceLayer: String,
        opacity: Float,
        palette: MapPalette,
    ): JSONObject = JSONObject()
        .put("type", "symbol")
        .put("source", sourceId)
        .put("source-layer", sourceLayer)
        .put("minzoom", 5)
        .put(
            "filter",
            JSONArray().put("all")
                .put(hasName())
                .put(
                    JSONArray().put("!")
                        .put(inClasses(listOf("river", "stream", "canal", "waterway", "ditch", "drain"))),
                ),
        )
        .put(
            "layout",
            JSONObject()
                .put("text-field", nameExpression())
                .put("text-font", JSONArray().put(FONT_REGULAR))
                .put("text-size", zoomInterpolate(6 to 11.5, 12 to 13.5, 16 to 15.0))
                .put("text-letter-spacing", 0.06)
                .put("text-max-width", 7)
                .put("symbol-sort-key", rankExpression()),
        )
        .put(
            "paint",
            JSONObject()
                .put("text-color", palette.waterLabel)
                .put("text-opacity", opacity.coerceIn(0.4f, 1f).toDouble())
                .put("text-halo-color", palette.labelHalo)
                .put("text-halo-width", 1.4)
                .put("text-halo-blur", 0.3),
        )

    private fun poiSymbols(
        sourceId: String,
        sourceLayer: String,
        opacity: Float,
        palette: MapPalette,
        requireName: Boolean = false,
    ): JSONObject = JSONObject()
        .put("type", "symbol")
        .put("source", sourceId)
        .put("source-layer", sourceLayer)
        .put("minzoom", 14)
        .put(
            "filter",
            JSONArray().put("all")
                .put(JSONArray().put("==").put(JSONArray().put("geometry-type")).put("Point"))
                .apply { if (requireName) put(hasName()) },
        )
        .put(
            "layout",
            JSONObject()
                .put("icon-image", iconExpression())
                // Plates are 64 px in the atlas; these sizes put a POI at roughly
                // 32-52 px on screen, the size range a modern day map uses.
                .put("icon-size", zoomInterpolate(14 to 0.50, 17 to 0.66, 20 to 0.82))
                .put("icon-allow-overlap", false)
                .put("icon-ignore-placement", false)
                .put("icon-padding", 3)
                // A plate the atlas cannot resolve must never swallow the name too.
                .put("icon-optional", true)
                .put("text-field", nameExpression())
                .put("text-font", JSONArray().put(FONT_REGULAR))
                .put("text-size", zoomInterpolate(14 to 10.5, 17 to 12.5, 20 to 14.0))
                .put("text-anchor", "top")
                .put("text-offset", JSONArray().put(0).put(1.05))
                .put("text-max-width", 6.5)
                .put("text-padding", 1)
                .put("text-optional", true)
                .put("symbol-sort-key", rankExpression()),
        )
        .put(
            "paint",
            JSONObject()
                .put("icon-opacity", opacity.coerceIn(0.4f, 1f).toDouble())
                .put("text-color", palette.poiText)
                .put("text-halo-color", palette.labelHalo)
                .put("text-halo-width", 1.6)
                .put("text-halo-blur", 0.3),
        )

    private fun airportLabels(
        sourceId: String,
        sourceLayer: String,
        opacity: Float,
        palette: MapPalette,
    ): JSONObject = JSONObject()
        .put("type", "symbol")
        .put("source", sourceId)
        .put("source-layer", sourceLayer)
        .put("minzoom", 9)
        .put("filter", hasName())
        .put(
            "layout",
            JSONObject()
                .put("icon-image", PoiClassifier.imageId("airport"))
                .put("icon-size", zoomInterpolate(9 to 0.56, 14 to 0.72))
                .put("icon-optional", true)
                .put("text-field", nameExpression())
                .put("text-font", JSONArray().put(FONT_BOLD))
                .put("text-size", zoomInterpolate(9 to 11.0, 14 to 13.0))
                .put("text-anchor", "top")
                .put("text-offset", JSONArray().put(0).put(1.1))
                .put("text-max-width", 8)
                .put("text-optional", true)
                .put("symbol-sort-key", rankExpression()),
        )
        .put(
            "paint",
            JSONObject()
                .put("icon-opacity", opacity.coerceIn(0.4f, 1f).toDouble())
                .put("text-color", palette.placeText)
                .put("text-halo-color", palette.labelHalo)
                .put("text-halo-width", 1.6),
        )

    private fun peakLabels(sourceId: String, sourceLayer: String, palette: MapPalette): JSONObject =
        JSONObject()
            .put("type", "symbol")
            .put("source", sourceId)
            .put("source-layer", sourceLayer)
            .put("minzoom", 11)
            .put("filter", hasName())
            .put(
                "layout",
                JSONObject()
                    .put("text-field", nameExpression())
                    .put("text-font", JSONArray().put(FONT_REGULAR))
                    .put("text-size", zoomInterpolate(11 to 10.0, 16 to 12.0))
                    .put("text-letter-spacing", 0.04)
                    .put("text-max-width", 7)
                    .put("symbol-sort-key", rankExpression()),
            )
            .put(
                "paint",
                JSONObject()
                    .put("text-color", palette.placeText)
                    .put("text-halo-color", palette.labelHalo)
                    .put("text-halo-width", 1.4),
            )

    private fun genericLabels(sourceId: String, sourceLayer: String, palette: MapPalette): JSONObject =
        JSONObject()
            .put("type", "symbol")
            .put("source", sourceId)
            .put("source-layer", sourceLayer)
            .put("minzoom", 12)
            .put("filter", hasName())
            .put(
                "layout",
                JSONObject()
                    .put("text-field", nameExpression())
                    .put("text-font", JSONArray().put(FONT_REGULAR))
                    .put("text-size", zoomInterpolate(12 to 9.5, 17 to 12.0))
                    .put("text-max-width", 7)
                    .put("text-optional", true)
                    .put("symbol-sort-key", rankExpression()),
            )
            .put(
                "paint",
                JSONObject()
                    .put("text-color", palette.poiText)
                    .put("text-halo-color", palette.labelHalo)
                    .put("text-halo-width", 1.4)
                    .put("text-halo-blur", 0.3),
            )
}
