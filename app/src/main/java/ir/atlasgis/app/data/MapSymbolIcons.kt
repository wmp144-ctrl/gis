package ir.atlasgis.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Draws the POI plates once at startup and hands them to MapLibre as style images.
 *
 * The glyphs come from the bundled Material Icons font, so every icon is a real,
 * crisp vector shape with no sprite sheet to download and no per-provider asset.
 * Plain bitmaps (rather than SDF icons) are the cheapest option for the renderer:
 * they are uploaded once and reused by every tile.
 */
object MapSymbolIcons {

    private const val BITMAP_PX = 64
    private const val GLYPH_PX = 38f
    private const val PLATE_INSET = 3f

    /** Icon key -> (Material Icons codepoint, plate tint). */
    private val glyphs: Map<String, Pair<Int, Int>> = mapOf(
        "fuel" to (0xe546 to 0xF0A020),
        "charging" to (0xe56d to 0x24A148),
        "parking" to (0xe54f to 0x2C6FDB),
        "bicycle_parking" to (0xe52f to 0x2C6FDB),
        "shop" to (0xe547 to 0x7C5CFF),
        "market" to (0xea12 to 0x7C5CFF),
        "bakery" to (0xea53 to 0xC2703A),
        "butcher" to (0xe14e to 0xC2703A),
        "cafe" to (0xe541 to 0xA9662F),
        "restaurant" to (0xe56c to 0xF97316),
        "fastfood" to (0xe57a to 0xF97316),
        "bar" to (0xe540 to 0xB45309),
        "icecream" to (0xea69 to 0xE5484D),
        "alcohol" to (0xea60 to 0xB45309),
        "florist" to (0xe545 to 0xE5484D),
        "hardware" to (0xea59 to 0x6B7280),
        "laundry" to (0xe54a to 0x2C6FDB),
        "electronics" to (0xe8d1 to 0x7C5CFF),
        "hospital" to (0xe548 to 0xE5484D),
        "pharmacy" to (0xe550 to 0xE5484D),
        "veterinary" to (0xe91d to 0x22A06B),
        "school" to (0xe80c to 0x3E7BFA),
        "university" to (0xe80c to 0x3E7BFA),
        "library" to (0xe54b to 0x3E7BFA),
        "museum" to (0xea36 to 0xB7791F),
        "attraction" to (0xea52 to 0xB7791F),
        "cinema" to (0xea66 to 0xB7791F),
        "hotel" to (0xe53a to 0x8B5CF6),
        "bank" to (0xe84f to 0x0E9F6E),
        "atm" to (0xe53e to 0x0E9F6E),
        "post" to (0xe554 to 0xB7791F),
        "police" to (0xef56 to 0x1D4ED8),
        "fire" to (0xef55 to 0xE5484D),
        "worship" to (0xeab2 to 0x6D5BD0),
        "church" to (0xeaae to 0x6D5BD0),
        "mosque" to (0xeab2 to 0x6D5BD0),
        "synagogue" to (0xeab0 to 0x6D5BD0),
        "temple" to (0xeaaf to 0x6D5BD0),
        "bus" to (0xe530 to 0x2563EB),
        "train" to (0xe570 to 0x2563EB),
        "tram" to (0xe571 to 0x2563EB),
        "airport" to (0xe539 to 0x2563EB),
        "park" to (0xea63 to 0x22A06B),
        "forest" to (0xea99 to 0x1F8A5B),
        "playground" to (0xeb43 to 0x22A06B),
        "sport" to (0xea2f to 0x22A06B),
        "swimming" to (0xeb48 to 0x2C6E9B),
        "water" to (0xe798 to 0x2C6E9B),
        "toilets" to (0xe63d to 0x6B7280),
        "view" to (0xe3f7 to 0x22A06B),
        "peak" to (0xe564 to 0x7A5C3E),
        "camp" to (0xe587 to 0x22A06B),
        "repair" to (0xea56 to 0x6B7280),
        "carwash" to (0xe542 to 0x2C6FDB),
        "car" to (0xe531 to 0x6B7280),
        "taxi" to (0xe559 to 0xF0A020),
        "industrial" to (0xebbc to 0x6B7280),
        "office" to (0xe0af to 0x6B7280),
        "residential" to (0xea40 to 0x6B7280),
        "info" to (0xe88e to 0x2C6FDB),
        PoiClassifier.GENERIC to (0xe55f to 0x6B7280),
    )

    /** Every icon id registered in the style, keyed by style image name. */
    val imageNames: Set<String> = glyphs.keys.map { PoiClassifier.imageId(it) }.toSet()

    fun bitmaps(context: Context): Map<String, Bitmap> {
        val typeface = runCatching {
            Typeface.createFromAsset(context.assets, "fonts/MaterialIcons-Regular.ttf")
        }.getOrNull() ?: return emptyMap()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = GLYPH_PX
            textAlign = Paint.Align.CENTER
            this.typeface = typeface
        }
        val plate = RectF(PLATE_INSET, PLATE_INSET, BITMAP_PX - PLATE_INSET, BITMAP_PX - PLATE_INSET)
        val result = LinkedHashMap<String, Bitmap>(glyphs.size)

        glyphs.forEach { (key, spec) ->
            val (codepoint, tint) = spec
            val bitmap = Bitmap.createBitmap(BITMAP_PX, BITMAP_PX, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val radius = 15f

            paint.style = Paint.Style.FILL
            paint.color = Color.argb(236, 255, 255, 255)
            canvas.drawRoundRect(plate, radius, radius, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.argb(38, 0, 0, 0)
            canvas.drawRoundRect(plate, radius, radius, paint)

            paint.style = Paint.Style.FILL
            paint.color = tint
            val metrics = paint.fontMetrics
            val baseline = BITMAP_PX / 2f - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(String(Character.toChars(codepoint)), BITMAP_PX / 2f, baseline, paint)

            result[PoiClassifier.imageId(key)] = bitmap
        }
        return result
    }
}
