package ir.atlasgis.app

import ir.atlasgis.app.data.MapLabelLayers
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the glyph PBFs that every map label is rendered from.
 *
 * Text on the map never goes through Android's font stack: MapLibre asks for the
 * glyphs of the codepoints it is about to draw. A font swap that silently misses a
 * block therefore cannot fail the build or the style test — it just makes names
 * disappear. That already happened once, when the Arabic Presentation Forms-A ranges
 * (FC00-FDFF) went from hundreds of glyphs to zero.
 */
class GlyphCoverageTest {

    private val assetsRoot: File by lazy {
        listOf("src/main/assets", "app/src/main/assets")
            .map { File(it) }
            .first { File(it, "glyphs").isDirectory }
    }

    private val cache = HashMap<String, Set<Int>>()
    private val stacks = listOf(MapLabelLayers.FONT_REGULAR, MapLabelLayers.FONT_BOLD)

    private val rangeNames = listOf(
        "0-255", "256-511", "512-767", "768-1023", "1536-1791", "1792-2047",
        "2048-2303", "8192-8447", "64256-64511", "64512-64767", "64768-65023",
        "65024-65279",
    )

    @Test
    fun persianLettersDigitsAndHalfSpaceAreBundled() {
        val required = buildList {
            addAll(0x0621..0x063A) // Arabic letters
            addAll(0x0641..0x064A) // Arabic letters (continued)
            addAll(listOf(0x067E, 0x0686, 0x0698, 0x06A9, 0x06AF, 0x06CC)) // پ چ ژ ک گ ی
            addAll(0x06F0..0x06F9) // Persian digits
            add(0x200C) // نیم‌فاصله / ZWNJ
        }
        stacks.forEach { stack -> assertCodepoints(stack, required) }
    }

    @Test
    fun arabicPresentationFormBLigaturesAreBundled() {
        // The shaping step asks for these forms; the lam-alef ligatures are the ones
        // a name like «لاله» depends on.
        val assigned = (0xFE70..0xFEFF).filter { assigned(it) }
        assertTrue("the Forms-B block shrank to ${assigned.size} assigned codepoints", assigned.size >= 140)
        stacks.forEach { stack -> assertCodepoints(stack, assigned) }
        stacks.forEach { stack ->
            listOf(0xFEFB, 0xFEFC).forEach { codepoint ->
                assertTrue(
                    "$stack has no lam-alef U+${hex(codepoint)}",
                    codepoint in glyphsFor(stack, codepoint),
                )
            }
        }
    }

    @Test
    fun arabicPresentationFormAIsNotSilentlyDropped() {
        // These are the ligatures Vazirmatn omits; tools/generate_glyphs.js fills them
        // from Noto Sans Arabic. An empty range here means pre-shaped names go blank.
        val assigned = (0xFC00..0xFDFF).filter { assigned(it) }
        assertTrue("the Forms-A block shrank to ${assigned.size} assigned codepoints", assigned.size >= 400)
        stacks.forEach { stack ->
            assertCodepoints(stack, assigned)
            listOf(0xFC00, 0xFC5E, 0xFDF2, 0xFDFA, 0xFDFB).forEach { codepoint ->
                assertTrue(
                    "$stack has no Forms-A U+${hex(codepoint)}",
                    codepoint in glyphsFor(stack, codepoint),
                )
            }
        }
    }

    @Test
    fun everyBundledRangeParsesAndCarriesGlyphs() {
        stacks.forEach { stack ->
            rangeNames.forEach { range ->
                assertTrue("$stack/$range is empty", glyphs(stack, range).isNotEmpty())
            }
        }
    }

    private fun assertCodepoints(stack: String, codepoints: List<Int>) {
        val missing = codepoints.filter { it !in glyphsFor(stack, it) }
        assertTrue(
            "$stack is missing ${missing.size} glyph(s): " +
                missing.take(12).joinToString { "U+${hex(it)}" },
            missing.isEmpty(),
        )
    }

    private fun glyphsFor(stack: String, codepoint: Int): Set<Int> {
        val start = codepoint and 0xFF.inv()
        return glyphs(stack, "$start-${start + 255}")
    }

    private fun glyphs(stack: String, range: String): Set<Int> = cache.getOrPut("$stack/$range") {
        val file = File(assetsRoot, "glyphs/$stack/$range.pbf")
        assertTrue("missing glyph range $stack/$range", file.isFile)
        glyphIds(file.readBytes())
    }

    private fun assigned(codepoint: Int): Boolean =
        Character.getType(codepoint) != Character.UNASSIGNED.toInt()

    private fun hex(codepoint: Int): String =
        codepoint.toString(16).uppercase().padStart(4, '0')

    /**
     * Reads the codepoints of one glyph PBF. A file wraps its fontstack in the
     * `glyphs` message while fontnik's in-memory output is a bare fontstack, so both
     * shapes are accepted.
     */
    private fun glyphIds(bytes: ByteArray): Set<Int> {
        val ids = LinkedHashSet<Int>()
        val outer = Proto(bytes)
        while (outer.hasMore()) {
            val key = outer.varint()
            val field = (key ushr 3).toInt()
            val wire = (key and 0x7).toInt()
            when {
                field == 1 && wire == 2 -> readFontstack(outer.lengthDelimited(), ids)
                field == 3 && wire == 2 -> readGlyph(outer.lengthDelimited(), ids)
                else -> outer.skip(wire)
            }
        }
        return ids
    }

    private fun readFontstack(bytes: ByteArray, ids: MutableSet<Int>) {
        val reader = Proto(bytes)
        while (reader.hasMore()) {
            val key = reader.varint()
            val field = (key ushr 3).toInt()
            val wire = (key and 0x7).toInt()
            if (field == 3 && wire == 2) {
                readGlyph(reader.lengthDelimited(), ids)
            } else {
                reader.skip(wire)
            }
        }
    }

    private fun readGlyph(bytes: ByteArray, ids: MutableSet<Int>) {
        val reader = Proto(bytes)
        while (reader.hasMore()) {
            val key = reader.varint()
            val field = (key ushr 3).toInt()
            val wire = (key and 0x7).toInt()
            if (field == 1 && wire == 0) {
                ids.add(reader.varint().toInt())
            } else {
                reader.skip(wire)
            }
        }
    }

    private class Proto(private val bytes: ByteArray) {
        private var position = 0

        fun hasMore(): Boolean = position < bytes.size

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (position < bytes.size) {
                val byte = bytes[position++].toInt() and 0xFF
                result = result or ((byte and 0x7F).toLong() shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
                if (shift > 63) return result
            }
            return result
        }

        fun lengthDelimited(): ByteArray {
            val length = varint().toInt()
            if (length <= 0) return ByteArray(0)
            val end = (position + length).coerceAtMost(bytes.size)
            val slice = bytes.copyOfRange(position, end)
            position = end
            return slice
        }

        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> position += 8
                2 -> {
                    // Read first: `position += varint()` would add the length to the
                    // stale position, because the left operand is read before the call.
                    val length = varint().toInt()
                    position += length
                }
                5 -> position += 4
                else -> position = bytes.size
            }
            if (position > bytes.size) position = bytes.size
        }
    }
}
