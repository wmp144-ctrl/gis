package ir.atlasgis.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Applies a user supplied `style.json` to a local vector MBTiles archive.
 *
 * The template keeps its own layers, filters, expressions and paint exactly as the
 * author wrote them; the only thing rewritten is where the tiles come from: every
 * `vector` source is repointed at `mbtiles://<local file>` so the archive stays
 * offline. Sources of other kinds (inline geojson, raster, image) are kept as-is.
 */
internal object CustomStyleTemplate {

    const val MAX_BYTES = 2 * 1024 * 1024

    private const val URL_SCHEME_PREFIX = "mbtiles://"

    private val opacityProperty = mapOf(
        "fill" to "fill-opacity",
        "line" to "line-opacity",
        "circle" to "circle-opacity",
        "heatmap" to "heatmap-opacity",
        "fill-extrusion" to "fill-extrusion-opacity",
    )

    /** Throws [IllegalArgumentException] with a Persian message when unusable. */
    fun validate(raw: String) {
        val root = parse(raw)
        require(root.optInt("version", 0) == 8) {
            "نسخهٔ این استایل پشتیبانی نمی‌شود؛ فایل باید بر اساس style specification نسخهٔ ۸ باشد"
        }
        val templateLayers = root.optJSONArray("layers")
        require(templateLayers != null && templateLayers.length() > 0) {
            "این استایل هیچ لایه‌ای ندارد"
        }
        require(vectorSourceIds(root).isNotEmpty()) {
            "این استایل هیچ منبع برداری (vector) ندارد؛ برای نقشه‌های برداری به منبعی با type برابر vector نیاز است"
        }
    }

    /**
     * Merges the template into the app style. Returns false when the template is
     * unusable, so the caller can fall back to the automatic style.
     */
    fun apply(
        root: JSONObject,
        sources: JSONObject,
        styleLayers: JSONArray,
        layer: MapLayer,
        sourceId: String,
        raw: String,
    ): Boolean {
        val template = runCatching { parse(raw) }.getOrNull() ?: return false
        if (template.optInt("version", 0) != 8) return false
        val templateLayers = template.optJSONArray("layers") ?: return false
        if (templateLayers.length() == 0) return false
        val templateSources = template.optJSONObject("sources") ?: return false
        if (vectorSourceIds(template).isEmpty()) return false

        // Rewrite every source under a per-layer prefix so two maps with custom
        // styles can never clash, and repoint vector sources at the local archive.
        val renamed = HashMap<String, String>()
        val keys = templateSources.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val original = templateSources.optJSONObject(key) ?: continue
            val newKey = "$sourceId~$key"
            renamed[key] = newKey
            val rewritten = JSONObject(original.toString())
            if (original.optString("type") == "vector") {
                rewritten.put("type", "vector")
                rewritten.put("url", URL_SCHEME_PREFIX + layer.filePath)
                rewritten.remove("tiles")
                rewritten.put("minzoom", layer.minZoom)
                rewritten.put("maxzoom", layer.maxZoom)
                layer.bounds?.let { bounds ->
                    rewritten.put(
                        "bounds",
                        JSONArray().put(bounds.west).put(bounds.south)
                            .put(bounds.east).put(bounds.north),
                    )
                }
            }
            sources.put(newKey, rewritten)
        }

        copyRemoteResource(root, template, "glyphs")
        copyRemoteResource(root, template, "sprite")

        val opacity = layer.opacity.coerceIn(0.1f, 1.0f)
        for (i in 0 until templateLayers.length()) {
            val spec = templateLayers.optJSONObject(i) ?: continue
            val copy = JSONObject(spec.toString())
            copy.put("id", "$sourceId-c$i")
            val source = copy.optString("source")
            if (source.isNotBlank()) copy.put("source", renamed[source] ?: source)
            if (opacity < 0.999f) scaleOpacity(copy, opacity)
            styleLayers.put(copy)
        }
        return true
    }

    private fun parse(raw: String): JSONObject =
        runCatching { JSONObject(raw) }.getOrElse {
            throw IllegalArgumentException("فایل انتخاب‌شده یک style.json معتبر نیست")
        }

    private fun vectorSourceIds(root: JSONObject): List<String> {
        val sources = root.optJSONObject("sources") ?: return emptyList()
        val result = ArrayList<String>()
        val keys = sources.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (sources.optJSONObject(key)?.optString("type") == "vector") result.add(key)
        }
        return result
    }

    /**
     * Glyphs and sprites that point at a remote http(s) URL are kept; local or
     * relative paths cannot be resolved by an inline style, so they are dropped and
     * MapLibre simply skips the text/icon part instead of failing the whole style.
     */
    private fun copyRemoteResource(target: JSONObject, template: JSONObject, key: String) {
        if (target.has(key)) return
        val value = template.optString(key)
        if (value.startsWith("http://") || value.startsWith("https://")) target.put(key, value)
    }

    /** Multiplies the layer's own opacity so the slider still works with a template. */
    private fun scaleOpacity(spec: JSONObject, opacity: Float) {
        val paint = spec.optJSONObject("paint") ?: JSONObject().also { spec.put("paint", it) }
        val keys = when (spec.optString("type")) {
            "symbol" -> listOf("icon-opacity", "text-opacity")
            "raster" -> listOf("raster-opacity")
            "background" -> listOf("background-opacity")
            else -> listOfNotNull(opacityProperty[spec.optString("type")])
        }
        for (key in keys) {
            val existing = paint.opt(key)
            val merged: Any = when {
                existing == null || existing == JSONObject.NULL -> opacity.toDouble()
                existing is Number -> existing.toDouble() * opacity
                existing is JSONArray ->
                    JSONArray().put("*").put(existing).put(opacity.toDouble())
                else -> opacity.toDouble()
            }
            paint.put(key, merged)
        }
    }
}
