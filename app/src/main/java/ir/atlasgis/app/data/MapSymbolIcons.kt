package ir.atlasgis.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import org.json.JSONArray

/**
 * Draws the POI plates once at startup and hands them to MapLibre as style images.
 *
 * The category table — one icon codepoint and one plate colour per POI category —
 * has a single home: `assets/poi-icons.json`. `tools/generate_poi_sprite.py` renders
 * the same table into the style's sprite (`assets/sprite/atlas.png` + `atlas.json`),
 * so the runtime plates here and the sprite the style loads can never disagree about
 * a category, its glyph or its colour.
 *
 * The glyphs come from the bundled Material Icons font, so every icon is a real,
 * crisp vector shape with no sprite sheet to download and no per-provider asset.
 * Plain bitmaps (rather than SDF icons) are the cheapest option for the renderer:
 * they are uploaded once and reused by every tile.
 */
object MapSymbolIcons {

    /** The shared category table, read from the app's assets. */
    const val TABLE_ASSET = "poi-icons.json"

    // Plate geometry, mirrored by tools/generate_poi_sprite.py.
    private const val BITMAP_PX = 64
    private const val GLYPH_PX = 38f
    private const val PLATE_INSET = 3f
    private const val PLATE_RADIUS = 15f

    private class Spec(val key: String, val codepoint: Int, val tint: Int)

    /**
     * Parses the table in atlas order. A category whose entry is malformed is
     * skipped rather than taking the whole icon set down with it.
     */
    private fun table(context: Context): List<Spec> {
        val text = runCatching {
            context.assets.open(TABLE_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()
        val entries = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        val result = ArrayList<Spec>(entries.length())
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            val key = entry.optString("key").takeIf { it.isNotEmpty() } ?: continue
            val codepoint = entry.optInt("codepoint", -1)
            val tint = parseColor(entry.optString("tint"))
            if (codepoint <= 0 || tint == null) continue
            result.add(Spec(key, codepoint, tint))
        }
        return result
    }

    private fun parseColor(raw: String): Int? {
        val hex = raw.trim().removePrefix("#")
        if (hex.length != 6) return null
        val value = hex.toIntOrNull(16) ?: return null
        return Color.rgb((value shr 16) and 0xFF, (value shr 8) and 0xFF, value and 0xFF)
    }

    /** Every icon registered for the style, keyed by style image name. */
    fun imageNames(context: Context): Set<String> =
        table(context).map { PoiClassifier.imageId(it.key) }.toSet()

    fun bitmaps(context: Context): Map<String, Bitmap> {
        val specs = table(context)
        if (specs.isEmpty()) return emptyMap()
        val typeface = runCatching {
            Typeface.createFromAsset(context.assets, "fonts/MaterialIcons-Regular.ttf")
        }.getOrNull() ?: return emptyMap()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = GLYPH_PX
            textAlign = Paint.Align.CENTER
            this.typeface = typeface
        }
        val plate = RectF(PLATE_INSET, PLATE_INSET, BITMAP_PX - PLATE_INSET, BITMAP_PX - PLATE_INSET)
        val result = LinkedHashMap<String, Bitmap>(specs.size)

        specs.forEach { spec ->
            val bitmap = Bitmap.createBitmap(BITMAP_PX, BITMAP_PX, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            paint.style = Paint.Style.FILL
            paint.color = Color.argb(236, 255, 255, 255)
            canvas.drawRoundRect(plate, PLATE_RADIUS, PLATE_RADIUS, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.argb(38, 0, 0, 0)
            canvas.drawRoundRect(plate, PLATE_RADIUS, PLATE_RADIUS, paint)

            paint.style = Paint.Style.FILL
            paint.color = spec.tint
            val metrics = paint.fontMetrics
            val baseline = BITMAP_PX / 2f - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(String(Character.toChars(spec.codepoint)), BITMAP_PX / 2f, baseline, paint)

            result[PoiClassifier.imageId(spec.key)] = bitmap
        }
        return result
    }
}
